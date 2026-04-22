package org.mortbay.sailing.pf.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.mortbay.sailing.pf.data.Club;
import org.mortbay.sailing.pf.importer.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads {@code /clubs.yaml} from the classpath and returns stub {@link Club} records
 * for each entry that has a real (non-placeholder) domain.
 * <p>
 * Entries whose domain key starts with {@code "unknown.domain."} are skipped with a
 * warning — they are placeholders pending manual completion by the user.
 */
class ClubLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(ClubLoader.class);
    private static final String FILENAME = "clubs.yaml";
    private static final String PLACEHOLDER_PREFIX = "unknown.domain.";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
        new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    static Map<String, Club> load(Path configDir)
    {
        InputStream stream = openStream(configDir, FILENAME);
        if (stream == null)
        {
            LOG.warn("No clubs.yaml found; club seed not loaded");
            return Map.of();
        }
        try
        {
            SeedFile seedFile = YAML_MAPPER.readValue(stream, SeedFile.class);
            Map<String, Club> result = new LinkedHashMap<>();
            if (seedFile.clubs == null)
                return result;
            for (Map.Entry<String, SeedEntry> e : seedFile.clubs.entrySet())
            {
                String domain = e.getKey();
                SeedEntry entry = e.getValue();
                if (domain.startsWith(PLACEHOLDER_PREFIX))
                {
                    LOG.warn("Skipping club seed entry with placeholder domain: {}", domain);
                    continue;
                }
                Club stub = new Club(domain, entry.shortName, entry.fullName, entry.state,
                    Boolean.TRUE.equals(entry.excluded),
                    entry.aliases != null ? entry.aliases : List.of(),
                    entry.topyacht != null ? entry.topyacht : List.of(),
                    List.of(), null);
                result.put(domain, stub);
            }
            LOG.info("Loaded {} club seed entries from {}", result.size(), FILENAME);
            return result;
        }
        catch (Exception e)
        {
            LOG.error("Failed to load clubs.yaml: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    private static InputStream openStream(Path configDir, String filename)
    {
        Path file = configDir.resolve(filename);
        if (Files.exists(file))
        {
            try
            {
                LOG.info("Loading {} from {}", filename, file.toAbsolutePath());
                return Files.newInputStream(file);
            }
            catch (Exception e)
            {
                LOG.warn("Failed to open {}: {}", file, e.getMessage());
            }
        }
        // Fallback to classpath (test resources)
        return ClubLoader.class.getResourceAsStream("/" + filename);
    }

    static ClubCatalogue loadCatalogue(Path configDir)
    {
        InputStream stream = openStream(configDir, FILENAME);
        if (stream == null)
        {
            LOG.warn("No clubs.yaml found; club catalogue not loaded");
            return ClubCatalogue.EMPTY;
        }
        try
        {
            SeedFile seedFile = YAML_MAPPER.readValue(stream, SeedFile.class);
            return new ClubCatalogue(seedFile);
        }
        catch (Exception e)
        {
            LOG.error("Failed to load clubs.yaml for catalogue: {}", e.getMessage(), e);
            return ClubCatalogue.EMPTY;
        }
    }

    /**
     * Adds or updates a boat club override in {@code clubs.yaml}.
     * Appends a {@code ClubOverride} entry for the given sail number and name if not already present.
     */
    static void addClubOverride(Path configDir, String sailNumber, String name, String clubId)
    {
        Path file = configDir.resolve(FILENAME);
        SeedFile seedFile = null;
        if (Files.exists(file))
        {
            try
            {
                seedFile = YAML_MAPPER.readValue(file.toFile(), SeedFile.class);
            }
            catch (Exception e)
            {
                LOG.error("Failed to read {} for update: {}", file, e.getMessage());
                return;
            }
        }
        if (seedFile == null)
            seedFile = new SeedFile();
        if (seedFile.boatClubOverrides == null)
            seedFile.boatClubOverrides = new ArrayList<>();

        String normSail = IdGenerator.normaliseSailNumber(sailNumber);
        String normName = IdGenerator.normaliseName(name);
        boolean duplicate = seedFile.boatClubOverrides.stream().anyMatch(o ->
            Objects.equals(IdGenerator.normaliseSailNumber(o.sailNumber), normSail)
            && Objects.equals(IdGenerator.normaliseName(o.name), normName));
        if (!duplicate)
        {
            ClubOverride entry = new ClubOverride();
            entry.sailNumber = sailNumber;
            entry.name = name;
            entry.clubId = clubId;
            seedFile.boatClubOverrides.add(entry);
        }
        else
        {
            // Update existing entry's clubId
            for (ClubOverride o : seedFile.boatClubOverrides)
            {
                if (Objects.equals(IdGenerator.normaliseSailNumber(o.sailNumber), normSail)
                    && Objects.equals(IdGenerator.normaliseName(o.name), normName))
                {
                    o.clubId = clubId;
                    break;
                }
            }
        }

        try
        {
            Files.createDirectories(file.getParent());
            YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), seedFile);
            LOG.info("Updated {} with club override {} for {}/{}", file, clubId, sailNumber, name);
        }
        catch (Exception e)
        {
            LOG.error("Failed to write {}: {}", file, e.getMessage());
        }
    }

    static class SeedFile
    {
        public Map<String, SeedEntry> clubs;
        public List<ClubOverride> boatClubOverrides;
    }

    static class SeedEntry
    {
        public String shortName;
        public String state;
        public String fullName;
        public Boolean excluded;
        public List<String> aliases;
        public List<String> topyacht;
    }

    static class ClubOverride
    {
        public String sailNumber;
        public String name;
        public String clubId;
    }

    // ---- Catalogue result ----

    static class ClubCatalogue
    {
        static final ClubCatalogue EMPTY = new ClubCatalogue(null);

        /** "normSail|normName" → clubId */
        private final Map<String, String> overridesByKey;

        private ClubCatalogue(SeedFile file)
        {
            if (file == null || file.boatClubOverrides == null)
            {
                overridesByKey = Map.of();
                return;
            }
            Map<String, String> map = new HashMap<>();
            for (ClubOverride o : file.boatClubOverrides)
            {
                if (o.sailNumber == null || o.name == null || o.clubId == null) continue;
                String key = IdGenerator.normaliseSailNumber(o.sailNumber)
                    + "|" + IdGenerator.normaliseName(o.name);
                map.put(key, o.clubId);
            }
            overridesByKey = Collections.unmodifiableMap(map);
            if (!map.isEmpty())
                LOG.info("Loaded club catalogue: {} boat club override(s)", map.size());
        }

        /**
         * Returns the override clubId for the given sail number and name, or null if none.
         */
        String resolveClubOverride(String sailNumber, String name)
        {
            if (overridesByKey.isEmpty() || sailNumber == null || name == null)
                return null;
            String key = IdGenerator.normaliseSailNumber(sailNumber)
                + "|" + IdGenerator.normaliseName(name);
            return overridesByKey.get(key);
        }
    }
}
