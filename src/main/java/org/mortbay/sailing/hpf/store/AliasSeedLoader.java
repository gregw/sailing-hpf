package org.mortbay.sailing.hpf.store;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mortbay.sailing.hpf.data.TimedAlias;
import org.mortbay.sailing.hpf.importer.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code /aliases.yaml} from the classpath and provides lookup methods
 * for design and boat name aliases.
 * <p>
 * The loaded seed is lookup-only and is never written back to disk.
 */
class AliasSeedLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(AliasSeedLoader.class);
    private static final String FILENAME = "aliases.yaml";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
        .registerModule(new JavaTimeModule());

    static AliasSeed load(Path configDir)
    {
        InputStream stream = openStream(configDir, FILENAME);
        if (stream == null)
        {
            LOG.warn("No aliases.yaml found; alias seed not loaded");
            return AliasSeed.EMPTY;
        }
        try
        {
            SeedFile seedFile = YAML_MAPPER.readValue(stream, SeedFile.class);
            return new AliasSeed(seedFile);
        }
        catch (Exception e)
        {
            LOG.error("Failed to load aliases.yaml: {}", e.getMessage(), e);
            return AliasSeed.EMPTY;
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
        return AliasSeedLoader.class.getResourceAsStream("/" + filename);
    }

    // ---- YAML binding classes ----

    static class SeedFile
    {
        public Map<String, DesignSeedEntry> designs;
        public Map<String, BoatSeedEntry> boats;
    }

    static class DesignSeedEntry
    {
        public String canonicalName;
        public List<String> aliases;
    }

    static class BoatSeedEntry
    {
        public String canonicalName;
        public List<AliasEntry> aliases;
    }

    static class AliasEntry
    {
        public String name;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        public LocalDate from;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        public LocalDate until;
    }

    // ---- Seed result ----

    /**
     * Loaded alias data. Immutable after construction; all lookups are O(1).
     */
    static class AliasSeed
    {
        static final AliasSeed EMPTY = new AliasSeed(null);

        /** normAlias → canonical design ID */
        private final Map<String, String> designAliasIndex;
        /** canonical design ID → canonical display name */
        private final Map<String, String> designCanonicalNames;
        /** normalised sail number → timed aliases */
        private final Map<String, List<TimedAlias>> boatAliasMap;
        /** normalised sail number → canonical boat name */
        private final Map<String, String> boatCanonicalNames;

        private AliasSeed(SeedFile seed)
        {
            if (seed == null || (seed.designs == null && seed.boats == null))
            {
                designAliasIndex = Map.of();
                designCanonicalNames = Map.of();
                boatAliasMap = Map.of();
                boatCanonicalNames = Map.of();
                return;
            }

            // Build design indexes
            Map<String, String> aliasIdx = new HashMap<>();
            Map<String, String> designNames = new HashMap<>();
            if (seed.designs != null)
            {
                for (Map.Entry<String, DesignSeedEntry> e : seed.designs.entrySet())
                {
                    String canonicalId = e.getKey();
                    DesignSeedEntry entry = e.getValue();
                    if (entry.canonicalName != null)
                        designNames.put(canonicalId, entry.canonicalName);
                    if (entry.aliases != null)
                    {
                        for (String alias : entry.aliases)
                        {
                            String normAlias = IdGenerator.normaliseDesignName(alias);
                            aliasIdx.put(normAlias, canonicalId);
                        }
                    }
                }
            }
            designAliasIndex = Collections.unmodifiableMap(aliasIdx);
            designCanonicalNames = Collections.unmodifiableMap(designNames);

            // Build boat indexes
            Map<String, List<TimedAlias>> boatMap = new HashMap<>();
            Map<String, String> boatNames = new HashMap<>();
            if (seed.boats != null)
            {
                for (Map.Entry<String, BoatSeedEntry> e : seed.boats.entrySet())
                {
                    String normSail = IdGenerator.normaliseSailNumber(e.getKey());
                    BoatSeedEntry entry = e.getValue();
                    if (entry.canonicalName != null)
                        boatNames.put(normSail, entry.canonicalName);
                    if (entry.aliases != null)
                    {
                        List<TimedAlias> timedAliases = entry.aliases.stream()
                            .filter(a -> a.name != null)
                            .map(a -> new TimedAlias(a.name, a.from, a.until))
                            .toList();
                        boatMap.put(normSail, timedAliases);
                    }
                }
            }
            boatAliasMap = Collections.unmodifiableMap(boatMap);
            boatCanonicalNames = Collections.unmodifiableMap(boatNames);

            LOG.info("Loaded alias seed: {} design alias(es), {} boat entry(ies)",
                aliasIdx.size(), boatMap.size());
        }

        /**
         * Resolves a normalised design alias to a canonical design ID, or null if unknown.
         */
        String resolveDesignAlias(String normalisedAlias)
        {
            return designAliasIndex.get(normalisedAlias);
        }

        /**
         * Returns the canonical display name for a design ID, or null if not in seed.
         */
        String designCanonicalName(String designId)
        {
            return designCanonicalNames.get(designId);
        }

        /**
         * Returns the timed aliases for a normalised sail number, or empty list if unknown.
         */
        List<TimedAlias> boatAliases(String normalisedSailNumber)
        {
            return boatAliasMap.getOrDefault(normalisedSailNumber, List.of());
        }

        /**
         * Returns the canonical boat name for a normalised sail number, or null if not in seed.
         */
        String boatCanonicalName(String normalisedSailNumber)
        {
            return boatCanonicalNames.get(normalisedSailNumber);
        }
    }
}
