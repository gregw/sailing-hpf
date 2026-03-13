package org.mortbay.sailing.hpf.importer;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.mortbay.sailing.hpf.data.Boat;
import org.mortbay.sailing.hpf.data.Design;
import org.mortbay.sailing.hpf.data.HandicapSystem;
import org.mortbay.sailing.hpf.data.MeasurementCertificate;
import org.mortbay.sailing.hpf.store.DataStore;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches the ORC Australian certificate feed and persists Boat, Design and
 * MeasurementCertificate records via DataStore.
 *
 * Feed URL: https://data.orc.org/public/WPub.dll?action=activecerts&CountryId=AUS
 * Individual cert: https://data.orc.org/public/WPub.dll/CC/{dxtID}
 *
 * GPH is the general-purpose handicap value stored; convert to TCF via 600/GPH.
 * The certificateNumber field stores the ORC dxtID for idempotency checking.
 */
public class OrcCertificateImporter {

    private static final Logger LOG = Logger.getLogger(OrcCertificateImporter.class.getName());

    private static final String LIST_URL =
            "https://data.orc.org/public/WPub.dll?action=activecerts&CountryId=AUS";
    private static final String CERT_URL_PREFIX =
            "https://data.orc.org/public/WPub.dll/CC/";

    // CertType values indicating non-spinnaker
    private static final Set<String> NON_SPIN_CERT_TYPES = Set.of("10", "11");

    private final DataStore store;

    public OrcCertificateImporter(DataStore store) {
        this.store = store;
    }

    public void run() throws Exception {
        HttpClient client = new HttpClient();
        client.start();
        try {
            // Step 1: Fetch the active certificate list
            LOG.info("Fetching ORC certificate list...");
            String listXml = get(client, LIST_URL);
            Document listDoc = parseXml(listXml);

            // Step 2: Load existing data into mutable maps keyed by id
            Map<String, Boat> boats = toMap(store.loadBoats(), Boat::id);
            Map<String, Design> designs = toMap(store.loadDesigns(), Design::id);
            Map<String, MeasurementCertificate> certs = toMap(store.loadCertificates(),
                    MeasurementCertificate::id);

            // Build a reverse index: normalised sail number → list of boat IDs
            Map<String, List<String>> boatsBySail = buildSailIndex(boats);

            // Build a set of existing dxtIDs for idempotency
            Set<String> existingDxtIds = certs.values().stream()
                    .filter(c -> c.certificateNumber() != null)
                    .map(MeasurementCertificate::certificateNumber)
                    .collect(java.util.stream.Collectors.toSet());

            // Step 3: Process each certificate element
            NodeList certNodes = listDoc.getElementsByTagName("Certificate");
            if (certNodes.getLength() == 0) {
                // Some feeds wrap in a different root — try "Yacht" or "Boat"
                certNodes = listDoc.getElementsByTagName("Yacht");
            }
            LOG.info("Found " + certNodes.getLength() + " certificate entries");

            List<Boat> newBoats = new ArrayList<>();
            List<Design> newDesigns = new ArrayList<>();
            List<MeasurementCertificate> newCerts = new ArrayList<>();

            for (int i = 0; i < certNodes.getLength(); i++) {
                Element el = (Element) certNodes.item(i);
                processCertElement(el, client, boats, designs, certs, boatsBySail, existingDxtIds,
                        newBoats, newDesigns, newCerts);
            }

            // Step 4: Persist only newly created entities
            newBoats.forEach(store::saveBoat);
            newDesigns.forEach(store::saveDesign);
            newCerts.forEach(store::saveCertificate);

            LOG.info("Done. newBoats=" + newBoats.size() + " newDesigns=" + newDesigns.size()
                    + " newCerts=" + newCerts.size());
        } finally {
            client.stop();
        }
    }

    private void processCertElement(
            Element el,
            HttpClient client,
            Map<String, Boat> boats,
            Map<String, Design> designs,
            Map<String, MeasurementCertificate> certs,
            Map<String, List<String>> boatsBySail,
            Set<String> existingDxtIds,
            List<Boat> newBoats,
            List<Design> newDesigns,
            List<MeasurementCertificate> newCerts) {

        // Extract required fields — skip record if any are missing
        String dxtId = child(el, "dxtID");
        if (dxtId == null) dxtId = attr(el, "dxtID");
        String yachtName = child(el, "YachtName");
        String sailNo = child(el, "SailNo");
        String vppYearStr = child(el, "VPPYear");
        String certType = child(el, "CertType");
        String expiryStr = child(el, "Expiry");
        String className = child(el, "Class");
        String familyName = child(el, "FamilyName");

        if (dxtId == null || dxtId.isBlank()) {
            LOG.warning("Skipping cert with missing dxtID");
            return;
        }
        if (yachtName == null || sailNo == null || vppYearStr == null) {
            LOG.warning("Skipping cert dxtID=" + dxtId + ": missing required fields");
            return;
        }

        // Idempotency: skip if already imported
        if (existingDxtIds.contains(dxtId)) {
            return;
        }

        int year;
        try {
            year = Integer.parseInt(vppYearStr.trim());
        } catch (NumberFormatException e) {
            LOG.warning("Skipping cert dxtID=" + dxtId + ": invalid VPPYear=" + vppYearStr);
            return;
        }

        boolean nonSpinnaker = (familyName != null && familyName.contains("Non Spinnaker"))
                || NON_SPIN_CERT_TYPES.contains(certType == null ? "" : certType.trim());

        LocalDate expiry = null;
        if (expiryStr != null && !expiryStr.isBlank()) {
            try {
                expiry = LocalDate.parse(expiryStr.trim());
            } catch (Exception e) {
                LOG.fine("Cannot parse expiry date for dxtID=" + dxtId + ": " + expiryStr);
            }
        }

        // Fetch GPH from individual cert page
        double gph;
        try {
            String certXml = get(client, CERT_URL_PREFIX + dxtId);
            Thread.sleep(200);
            gph = parseGph(certXml, dxtId);
        } catch (Exception e) {
            LOG.warning("Skipping cert dxtID=" + dxtId + ": failed to fetch GPH — " + e.getMessage());
            return;
        }
        if (Double.isNaN(gph)) {
            LOG.warning("Skipping cert dxtID=" + dxtId + ": GPH not found in cert XML");
            return;
        }

        // Find-or-create Design
        String designId = null;
        if (className != null && !className.isBlank()) {
            designId = IdGenerator.normaliseName(className);
            if (!designs.containsKey(designId)) {
                Design newDesign = new Design(designId, className.trim(), List.of(), List.of());
                designs.put(designId, newDesign);
                newDesigns.add(newDesign);
                LOG.fine("New design: " + designId);
            }
        }

        // Find-or-create Boat
        String normSail = IdGenerator.normaliseSailNumber(sailNo);
        String normFirstWord = IdGenerator.normaliseName(firstWord(yachtName));
        String boatId = findOrCreateBoat(normSail, normFirstWord, yachtName.trim(), designId,
                boats, boatsBySail, newBoats);

        // Create certificate
        String certId = IdGenerator.generateCertId(boatId, year, certs.keySet());
        MeasurementCertificate cert = new MeasurementCertificate(
                certId, boatId, HandicapSystem.ORC, year, gph, nonSpinnaker, dxtId, expiry);
        certs.put(certId, cert);
        newCerts.add(cert);
        existingDxtIds.add(dxtId);
        LOG.fine("New cert: " + certId + " GPH=" + gph + " boat=" + boatId);
    }

    /** Find existing boat matching sail+firstWord, or create a new one. */
    private String findOrCreateBoat(
            String normSail, String normFirstWord, String canonicalName, String designId,
            Map<String, Boat> boats, Map<String, List<String>> boatsBySail, List<Boat> newBoats) {

        List<String> candidates = boatsBySail.getOrDefault(normSail, List.of());
        for (String id : candidates) {
            Boat b = boats.get(id);
            if (b != null && IdGenerator.normaliseName(firstWord(b.name())).equals(normFirstWord)) {
                return id; // existing match
            }
        }

        // Not found — create new
        String newId = IdGenerator.generateBoatId(normSail, canonicalName, boats.keySet());
        Boat newBoat = new Boat(newId, normSail, canonicalName, designId, null, List.of());
        boats.put(newId, newBoat);
        newBoats.add(newBoat);
        boatsBySail.computeIfAbsent(normSail, k -> new ArrayList<>()).add(newId);
        LOG.fine("New boat: " + newId);
        return newId;
    }

    /** Parse GPH from an individual ORC cert XML document. Returns NaN if not found. */
    private double parseGph(String xml, String dxtId) {
        try {
            Document doc = parseXml(xml);
            // GPH may appear as a child element or attribute
            String val = child(doc.getDocumentElement(), "GPH");
            if (val == null) val = attr(doc.getDocumentElement(), "GPH");
            if (val == null) {
                // Try scanning all elements for GPH
                NodeList all = doc.getElementsByTagName("GPH");
                if (all.getLength() > 0) val = all.item(0).getTextContent();
            }
            if (val != null && !val.isBlank()) return Double.parseDouble(val.trim());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error parsing GPH for dxtID=" + dxtId, e);
        }
        return Double.NaN;
    }

    // --- XML helpers ---

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** Get the text content of the first direct child element with the given tag, or null. */
    private String child(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        String text = nodes.item(0).getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** Get an attribute value, or null. */
    private String attr(Element el, String name) {
        String val = el.getAttribute(name);
        return val == null || val.isBlank() ? null : val.trim();
    }

    // --- HTTP helper ---

    private String get(HttpClient client, String url) throws Exception {
        ContentResponse response = client.GET(url);
        if (response.getStatus() != 200) {
            throw new RuntimeException("HTTP " + response.getStatus() + " for " + url);
        }
        return response.getContentAsString();
    }

    // --- Utility ---

    private static String firstWord(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().split("\\s+")[0];
    }

    private static <T> Map<String, T> toMap(List<T> items,
            java.util.function.Function<T, String> keyFn) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T item : items) map.put(keyFn.apply(item), item);
        return map;
    }

    private static Map<String, List<String>> buildSailIndex(Map<String, Boat> boats) {
        Map<String, List<String>> index = new LinkedHashMap<>();
        for (Boat b : boats.values()) {
            index.computeIfAbsent(b.sailNumber(), k -> new ArrayList<>()).add(b.id());
        }
        return index;
    }

    // --- Entry point ---

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: OrcCertificateImporter <dataRootPath>");
            System.exit(1);
        }
        DataStore store = new DataStore(Path.of(args[0]));
        new OrcCertificateImporter(store).run();
    }
}
