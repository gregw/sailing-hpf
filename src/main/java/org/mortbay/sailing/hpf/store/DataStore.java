package org.mortbay.sailing.hpf.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mortbay.sailing.hpf.data.Boat;
import org.mortbay.sailing.hpf.data.Club;
import org.mortbay.sailing.hpf.data.Design;
import org.mortbay.sailing.hpf.data.Loadable;
import org.mortbay.sailing.hpf.data.Maker;
import org.mortbay.sailing.hpf.data.Race;
import org.mortbay.sailing.hpf.importer.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the data store.
 * <p>
 * Layout:
 * {root}/races/{raceId}.json       — one file per Race
 * {root}/boats/{boatId}.json       — one file per Boat (embeds certificates)
 * {root}/designs/{designId}.json   — one file per Design
 * {root}/clubs/{clubId}.json       — one file per Club (embeds seasons and series)
 * {root}/catalogue/makers.json     — all Makers (small stable collection)
 * <p>
 * Call {@link #start()} to load all data into memory, {@link #save()} to flush dirty
 * entities to disk, and {@link #stop()} to flush and clear the in-memory maps.
 */
public class DataStore
{
    private static final Logger LOG = LoggerFactory.getLogger(DataStore.class);
    private static final JsonMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        .build();

    private final Path root;
    private final Path racesDir;
    private final Path boatsDir;
    private final Path designsDir;
    private final Path clubsDir;
    private final Path catalogueDir;

    // In-memory maps — null before start()
    private Map<String, Race> races;
    private Map<String, Boat> boats;
    private Map<String, Design> designs;
    private Map<String, Club> clubs;
    private List<Maker> makers;
    private boolean makersDirty;
    private Map<String, List<String>> boatsBySail; // derived index, maintained by putBoat()

    public DataStore(Path root)
    {
        this.root = root;
        this.racesDir = root.resolve("races");
        this.boatsDir = root.resolve("boats");
        this.designsDir = root.resolve("designs");
        this.clubsDir = root.resolve("clubs");
        this.catalogueDir = root.resolve("catalogue");
    }

    private static boolean nameMatches(Boat candidate, String incomingName, String normIncoming)
    {
        if (IdGenerator.normaliseName(candidate.name()).equals(normIncoming))
            return true;
        if (candidate.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(incomingName)))
            return true;
        return candidate.aliases().stream()
            .anyMatch(a -> IdGenerator.normaliseName(a).equals(normIncoming));
    }

    // --- Lifecycle ---

    /**
     * Resolves the data root directory using the standard lookup chain:
     * <ol>
     *   <li>First element of {@code args}, if provided</li>
     *   <li>{@code HPF_DATA} environment variable</li>
     *   <li>{@code ./hpf-data} in the current working directory, if it exists</li>
     *   <li>{@code $HOME/.hpf-data} as the default fallback</li>
     * </ol>
     */
    public static Path resolveDataRoot(String[] args)
    {
        if (args.length > 0)
            return Path.of(args[0]);

        String env = System.getenv("HPF_DATA");
        if (env != null && !env.isBlank())
            return Path.of(env);

        Path local = Path.of("hpf-data");
        if (Files.isDirectory(local))
            return local;

        return Path.of(System.getProperty("user.home"), ".hpf-data");
    }

    public Map<String, Boat> boats()
    {
        requireStarted();
        return Collections.unmodifiableMap(boats);
    }

    public Map<String, List<String>> boatsBySail()
    {
        requireStarted();
        return Collections.unmodifiableMap(boatsBySail);
    }

    // --- Read accessors (require started) ---

    public Map<String, Club> clubs()
    {
        requireStarted();
        return Collections.unmodifiableMap(clubs);
    }

    public Map<String, Design> designs()
    {
        requireStarted();
        return Collections.unmodifiableMap(designs);
    }

    public Boat findOrCreateBoat(String sailNo, String name, Design design)
    {
        String normSail = IdGenerator.normaliseSailNumber(sailNo);
        String normName = IdGenerator.normaliseName(name);
        String base = normSail + "-" + normName;
        String boatId = design == null ? base : base + "-" + design.id();

        Boat boat = boats.get(boatId);
        if (boat != null)
            return boat;

        List<String> candidates = List.copyOf(boatsBySail.getOrDefault(normSail, List.of()));
        for (String candidateId : candidates)
        {
            Boat candidate = boats.get(candidateId);
            if (candidate == null || !nameMatches(candidate, name, normName))
                continue;

            if (candidate.designId() == null && design != null)
            {
                removeBoat(candidate.id());
                Boat upgraded = new Boat(boatId, normSail, name, design.id(),
                    candidate.clubId(), candidate.aliases(), candidate.certificates(), null);
                putBoat(upgraded);
                LOG.info("Upgraded boat {} → {}", candidate.id(), boatId);
                return upgraded;
            }
            return candidate;
        }

        Boat newBoat = new Boat(boatId, normSail, name,
            design == null ? null : design.id(), null, List.of(), List.of(), null);
        putBoat(newBoat);
        LOG.info("Created new boat {}", newBoat);
        return newBoat;
    }

    public Design findOrCreateDesign(String className)
    {
        if (className == null || className.isBlank())
            return null;
        String designId = IdGenerator.normaliseName(className);
        Design design = designs.get(designId);
        if (design != null)
            return design;
        for (Design d : designs.values())
        {
            if (d.aliases().stream().anyMatch(a -> IdGenerator.normaliseName(a).equals(designId)))
                return d;
        }
        design = new Design(designId, className.trim(), List.of(), List.of(), null);
        putDesign(design);
        return design;
    }

    public List<Maker> makers()
    {
        requireStarted();
        return Collections.unmodifiableList(makers);
    }

    public void putBoat(Boat boat)
    {
        requireStarted();
        Boat existing = boats.get(boat.id());
        if (existing != null)
        {
            List<String> ids = boatsBySail.get(existing.sailNumber());
            if (ids != null)
                ids.remove(boat.id());
        }
        boats.put(boat.id(), boat);
        List<String> ids = boatsBySail.computeIfAbsent(boat.sailNumber(), k -> new ArrayList<>());
        if (!ids.contains(boat.id()))
            ids.add(boat.id());
    }

    // --- Write mutators (require started; loadedAt = null → always written by save()) ---

    public void putClub(Club club)
    {
        requireStarted();
        clubs.put(club.id(), club);
    }

    public void putDesign(Design design)
    {
        requireStarted();
        designs.put(design.id(), design);
    }

    public void putMakers(List<Maker> makers)
    {
        requireStarted();
        this.makers = new ArrayList<>(makers);
        makersDirty = true;
    }

    public void putRace(Race race)
    {
        requireStarted();
        races.put(race.id(), race);
    }

    public Map<String, Race> races()
    {
        requireStarted();
        return Collections.unmodifiableMap(races);
    }

    public void removeBoat(String id)
    {
        requireStarted();
        Boat existing = boats.remove(id);
        if (existing != null)
        {
            List<String> ids = boatsBySail.get(existing.sailNumber());
            if (ids != null)
                ids.remove(id);
            try
            {
                Files.deleteIfExists(boatsDir.resolve(id + ".json"));
            }
            catch (IOException e)
            {
                LOG.warn("Could not delete boat file {}: {}", id, e.getMessage());
            }
        }
    }

    /**
     * Write all dirty entities to disk (dirty-check via loadedAt). Keeps maps loaded.
     */
    public void save()
    {
        requireStarted();
        boats.values().forEach(b -> write(boatsDir.resolve(b.id() + ".json"), b));
        designs.values().forEach(d -> write(designsDir.resolve(d.id() + ".json"), d));
        clubs.values().forEach(c -> write(clubsDir.resolve(c.id() + ".json"), c));
        races.values().forEach(r -> write(racesDir.resolve(r.id() + ".json"), r));
        if (makersDirty)
        {
            write(catalogueDir.resolve("makers.json"), makers);
            makersDirty = false;
        }
    }

    /**
     * Load all raw data from disk into in-memory maps.
     */
    public void start()
    {
        LOG.info("Start DataStore root={}", root.toAbsolutePath());

        boats = new LinkedHashMap<>();
        loadDir(boatsDir, Boat.class).forEach(b -> boats.put(b.id(), b));
        designs = new LinkedHashMap<>();
        loadDir(designsDir, Design.class).forEach(d -> designs.put(d.id(), d));
        clubs = new LinkedHashMap<>();
        loadDir(clubsDir, Club.class).forEach(c -> clubs.put(c.id(), c));
        races = new LinkedHashMap<>();
        loadDir(racesDir, Race.class).forEach(r -> races.put(r.id(), r));
        makers = new ArrayList<>(loadList(catalogueDir.resolve("makers.json"), Maker.class));
        makersDirty = false;
        boatsBySail = new LinkedHashMap<>();
        for (Boat b : boats.values())
        {
            boatsBySail.computeIfAbsent(b.sailNumber(), k -> new ArrayList<>()).add(b.id());
        }
    }

    /**
     * save() then clear in-memory maps.
     */
    public void stop()
    {
        save();
        races = null;
        boats = null;
        designs = null;
        clubs = null;
        makers = null;
        boatsBySail = null;
        makersDirty = false;
    }

    // --- Internal helpers ---

    private <T> List<T> loadDir(Path dir, Class<T> type)
    {
        List<T> loaded;
        if (!Files.exists(dir))
            loaded = Collections.emptyList();
        else
        {
            try (var stream = Files.list(dir))
            {
                loaded = stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p ->
                    {
                        try
                        {
                            T entity = MAPPER.readValue(p.toFile(), type);
                            if (entity instanceof Loadable<?>)
                            {
                                Instant modified = Files.getLastModifiedTime(p).toInstant();
                                @SuppressWarnings("unchecked")
                                T stamped = (T)((Loadable<T>)entity).withLoadedAt(modified);
                                entity = stamped;
                            }
                            return entity;
                        }
                        catch (IOException e)
                        {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }

        LOG.info("Loaded {} {}(s)", loaded.size(), type.getSimpleName());
        return loaded;
    }

    private <T> List<T> loadList(Path path, Class<T> type)
    {
        if (!Files.exists(path))
            return Collections.emptyList();
        try
        {
            return MAPPER.readValue(path.toFile(),
                MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private void requireStarted()
    {
        if (boats == null)
            throw new IllegalStateException("DataStore not started — call start() first");
    }

    private void write(Path path, Object value)
    {
        // Dirty check: skip if file exists and modification time matches loadedAt
        if (value instanceof Loadable<?> l && l.loadedAt() != null)
        {
            try
            {
                if (Files.exists(path) &&
                    Files.getLastModifiedTime(path).toInstant().equals(l.loadedAt()))
                {
                    LOG.debug("Skipping unchanged {}", path.getFileName());
                    return;
                }
            }
            catch (IOException ignored)
            { /* fall through and write */ }
        }

        boolean isNew = !Files.exists(path);
        try
        {
            Files.createDirectories(path.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
            if (isNew)
                LOG.info("Created {}", path.getFileName());
            else
                LOG.info("Updated {}", path.getFileName());
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
