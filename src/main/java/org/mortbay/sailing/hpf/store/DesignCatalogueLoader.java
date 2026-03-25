package org.mortbay.sailing.hpf.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.mortbay.sailing.hpf.importer.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads {@code design.yaml} from the config directory (or classpath fallback) and returns
 * a {@link DesignCatalogue} that can answer whether a normalised design ID is excluded.
 */
class DesignCatalogueLoader
{
    private static final Logger LOG = LoggerFactory.getLogger(DesignCatalogueLoader.class);
    private static final String FILENAME = "design.yaml";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

    static DesignCatalogue load(Path configDir)
    {
        InputStream stream = openStream(configDir, FILENAME);
        if (stream == null)
        {
            LOG.warn("No design.yaml found; design catalogue not loaded");
            return DesignCatalogue.EMPTY;
        }
        try
        {
            CatalogueFile file = YAML_MAPPER.readValue(stream, CatalogueFile.class);
            return new DesignCatalogue(file);
        }
        catch (Exception e)
        {
            LOG.error("Failed to load design.yaml: {}", e.getMessage(), e);
            return DesignCatalogue.EMPTY;
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
        return DesignCatalogueLoader.class.getResourceAsStream("/" + filename);
    }

    // ---- YAML binding ----

    static class CatalogueFile
    {
        public List<String> excluded;
    }

    // ---- Catalogue result ----

    static class DesignCatalogue
    {
        static final DesignCatalogue EMPTY = new DesignCatalogue(null);

        private final Set<String> excludedIds;

        private DesignCatalogue(CatalogueFile file)
        {
            if (file == null || file.excluded == null || file.excluded.isEmpty())
            {
                excludedIds = Set.of();
                return;
            }
            Set<String> ids = new HashSet<>();
            for (String name : file.excluded)
            {
                if (name != null && !name.isBlank())
                    ids.add(IdGenerator.normaliseDesignName(name));
            }
            excludedIds = Collections.unmodifiableSet(ids);
            LOG.info("Loaded design catalogue: {} excluded design(s)", ids.size());
        }

        boolean isExcluded(String normalisedDesignId)
        {
            if (normalisedDesignId == null)
                return false;
            return excludedIds.contains(normalisedDesignId);
        }
    }
}
