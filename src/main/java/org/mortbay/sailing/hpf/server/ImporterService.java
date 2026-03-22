package org.mortbay.sailing.hpf.server;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eclipse.jetty.client.HttpClient;
import org.mortbay.sailing.hpf.importer.AmsImporter;
import org.mortbay.sailing.hpf.importer.OrcImporter;
import org.mortbay.sailing.hpf.importer.SailSysBoatImporter;
import org.mortbay.sailing.hpf.importer.SailSysRaceImporter;
import org.mortbay.sailing.hpf.importer.TopYachtImporter;
import org.mortbay.sailing.hpf.store.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImporterService
{
    private static final Logger LOG = LoggerFactory.getLogger(ImporterService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private final DataStore store;
    private final HttpClient httpClient;
    private final Path dataRoot;
    private final Path configFile;
    private volatile AnalysisCache cache;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ScheduleConfig> schedules = new LinkedHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new LinkedHashMap<>();

    private volatile ImportStatus currentStatus;

    public record ImportStatus(String importerName, String mode, Instant startedAt) {}

    public record ScheduleConfig(String name, boolean enabled, DayOfWeek day, LocalTime time, String mode) {}

    private record AdminConfig(List<ScheduleConfig> schedules) {}

    public ImporterService(DataStore store, HttpClient httpClient, Path dataRoot)
    {
        this.store = store;
        this.httpClient = httpClient;
        this.dataRoot = dataRoot;
        this.configFile = dataRoot.resolve("admin-config.json");
    }

    public void start()
    {
        if (!Files.exists(configFile))
            return;

        try
        {
            AdminConfig config = MAPPER.readValue(configFile.toFile(), AdminConfig.class);
            for (ScheduleConfig sc : config.schedules())
            {
                schedules.put(sc.name(), sc);
                if (sc.enabled())
                    armSchedule(sc);
            }
            LOG.info("Loaded {} schedule(s) from {}", schedules.size(), configFile);
        }
        catch (IOException e)
        {
            LOG.warn("Failed to load admin-config.json: {}", e.getMessage());
        }
    }

    public void stop()
    {
        scheduler.shutdown();
        importExecutor.shutdown();
    }

    /**
     * Submit an import job. Returns false (caller should send 409) if one is already running.
     */
    public boolean submit(String name, String mode)
    {
        if (!running.compareAndSet(false, true))
            return false;

        try
        {
            importExecutor.submit(() ->
            {
                try
                {
                    currentStatus = new ImportStatus(name, mode, Instant.now());
                    LOG.info("Starting importer={} mode={}", name, mode);
                    runImporter(name, mode);
                    store.save();
                    LOG.info("Finished importer={}", name);
                    if (cache != null)
                        cache.refresh();
                }
                catch (Exception e)
                {
                    LOG.error("Importer {} failed", name, e);
                }
                finally
                {
                    currentStatus = null;
                    running.set(false);
                }
            });
            return true;
        }
        catch (Exception e)
        {
            running.set(false);
            return false;
        }
    }

    public void setCache(AnalysisCache cache)
    {
        this.cache = cache;
    }

    public ImportStatus currentStatus()
    {
        return currentStatus;
    }

    public Map<String, ScheduleConfig> schedules()
    {
        return Map.copyOf(schedules);
    }

    public void setSchedule(String name, ScheduleConfig config)
    {
        schedules.put(name, config);
        persistConfig();

        ScheduledFuture<?> existing = scheduledFutures.remove(name);
        if (existing != null)
            existing.cancel(false);

        if (config.enabled())
            armSchedule(config);
    }

    private void armSchedule(ScheduleConfig sc)
    {
        Duration delay = delayUntilNext(sc.day(), sc.time());
        LOG.info("Scheduling importer={} at {} {} (delay={})", sc.name(), sc.day(), sc.time(), delay);
        ScheduledFuture<?> future = scheduler.schedule(() ->
        {
            submit(sc.name(), sc.mode());
            ScheduleConfig current = schedules.get(sc.name());
            if (current != null && current.enabled())
                armSchedule(current);
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        scheduledFutures.put(sc.name(), future);
    }

    private Duration delayUntilNext(DayOfWeek day, LocalTime time)
    {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(TemporalAdjusters.nextOrSame(day)).with(time);
        if (!next.isAfter(now))
            next = now.with(TemporalAdjusters.next(day)).with(time);
        return Duration.between(now, next);
    }

    private void runImporter(String name, String mode) throws Exception
    {
        switch (name)
        {
            case "sailsys-boats" ->
            {
                SailSysBoatImporter importer = new SailSysBoatImporter(store, httpClient);
                if ("api".equals(mode))
                    importer.runFromApi(1);
                else
                    importer.runFromDirectory(dataRoot.resolve("import/sailsys/boats"));
            }
            case "sailsys-races" ->
            {
                SailSysRaceImporter importer = new SailSysRaceImporter(store, httpClient);
                if ("api".equals(mode))
                    importer.runFromApi(1);
                else
                    importer.runFromDirectory(dataRoot.resolve("import/sailsys/races"));
            }
            case "orc" -> new OrcImporter(store, httpClient).run();
            case "ams" -> new AmsImporter(store, httpClient).run();
            case "topyacht" -> new TopYachtImporter(store, httpClient).run();
            default -> throw new IllegalArgumentException("Unknown importer: " + name);
        }
    }

    private void persistConfig()
    {
        try
        {
            Files.createDirectories(configFile.getParent());
            AdminConfig config = new AdminConfig(new ArrayList<>(schedules.values()));
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), config);
        }
        catch (IOException e)
        {
            LOG.warn("Failed to persist admin-config.json: {}", e.getMessage());
        }
    }
}
