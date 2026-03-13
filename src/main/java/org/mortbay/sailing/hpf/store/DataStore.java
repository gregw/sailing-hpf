package org.mortbay.sailing.hpf.store;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mortbay.sailing.hpf.data.Boat;
import org.mortbay.sailing.hpf.data.Club;
import org.mortbay.sailing.hpf.data.Design;
import org.mortbay.sailing.hpf.data.Maker;
import org.mortbay.sailing.hpf.data.MeasurementCertificate;
import org.mortbay.sailing.hpf.data.Race;
import org.mortbay.sailing.hpf.data.Season;
import org.mortbay.sailing.hpf.data.Series;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Reads and writes the data store.
 *
 * Layout:
 *   {root}/races/{raceId}.json                       — one file per Race
 *   {root}/boats/{boatId}.json                       — one file per Boat
 *   {root}/designs/{designId}.json                   — one file per Design
 *   {root}/certificates/{certId}.json                — one file per MeasurementCertificate
 *   {root}/catalogue/clubs.json                      — all Clubs (small stable collection)
 *   {root}/catalogue/seasons.json                    — all Seasons
 *   {root}/catalogue/series.json                     — all Series
 *   {root}/catalogue/makers.json                     — all Makers
 *
 * Adding or updating a single entity requires writing one file only.
 */
public class DataStore {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .build();

    private final Path racesDir;
    private final Path boatsDir;
    private final Path designsDir;
    private final Path certificatesDir;
    private final Path catalogueDir;

    public DataStore(Path root) {
        this.racesDir = root.resolve("races");
        this.boatsDir = root.resolve("boats");
        this.designsDir = root.resolve("designs");
        this.certificatesDir = root.resolve("certificates");
        this.catalogueDir = root.resolve("catalogue");
    }

    // --- Races ---

    public void saveRace(Race race) {
        write(racesDir.resolve(race.id() + ".json"), race);
    }

    public Race loadRace(String raceId) throws IOException {
        return MAPPER.readValue(racesDir.resolve(raceId + ".json").toFile(), Race.class);
    }

    public List<Race> loadAllRaces() {
        Path dir = racesDir;
        if (!Files.exists(dir)) return Collections.emptyList();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            return MAPPER.readValue(p.toFile(), Race.class);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- Catalogue ---

    public void saveBoat(Boat boat) {
        write(boatsDir.resolve(boat.id() + ".json"), boat);
    }

    public List<Boat> loadBoats() {
        return loadDir(boatsDir, Boat.class);
    }

    public void saveClubs(List<Club> clubs) {
        write(catalogueDir.resolve("clubs.json"), clubs);
    }

    public List<Club> loadClubs() {
        return loadList(catalogueDir.resolve("clubs.json"), Club.class);
    }

    public void saveSeasons(List<Season> seasons) {
        write(catalogueDir.resolve("seasons.json"), seasons);
    }

    public List<Season> loadSeasons() {
        return loadList(catalogueDir.resolve("seasons.json"), Season.class);
    }

    public void saveSeries(List<Series> series) {
        write(catalogueDir.resolve("series.json"), series);
    }

    public List<Series> loadSeries() {
        return loadList(catalogueDir.resolve("series.json"), Series.class);
    }

    public void saveDesign(Design design) {
        write(designsDir.resolve(design.id() + ".json"), design);
    }

    public List<Design> loadDesigns() {
        return loadDir(designsDir, Design.class);
    }

    public void saveMakers(List<Maker> makers) {
        write(catalogueDir.resolve("makers.json"), makers);
    }

    public List<Maker> loadMakers() {
        return loadList(catalogueDir.resolve("makers.json"), Maker.class);
    }

    public void saveCertificate(MeasurementCertificate cert) {
        write(certificatesDir.resolve(cert.id() + ".json"), cert);
    }

    public List<MeasurementCertificate> loadCertificates() {
        return loadDir(certificatesDir, MeasurementCertificate.class);
    }

    // --- Internal helpers ---

    private void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> List<T> loadList(Path path, Class<T> type) {
        if (!Files.exists(path)) return Collections.emptyList();
        try {
            return MAPPER.readValue(path.toFile(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> List<T> loadDir(Path dir, Class<T> type) {
        if (!Files.exists(dir)) return Collections.emptyList();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            return MAPPER.readValue(p.toFile(), type);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
