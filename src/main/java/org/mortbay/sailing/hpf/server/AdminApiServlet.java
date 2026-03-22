package org.mortbay.sailing.hpf.server;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mortbay.sailing.hpf.analysis.BoatReferenceFactors;
import org.mortbay.sailing.hpf.data.Boat;
import org.mortbay.sailing.hpf.data.Design;
import org.mortbay.sailing.hpf.data.Factor;
import org.mortbay.sailing.hpf.data.Race;
import org.mortbay.sailing.hpf.store.DataStore;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminApiServlet extends HttpServlet
{
    private static final JsonMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final DataStore store;
    private final ImporterService importerService;
    private final AnalysisCache cache;

    public AdminApiServlet(DataStore store, ImporterService importerService, AnalysisCache cache)
    {
        this.store = store;
        this.importerService = importerService;
        this.cache = cache;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        String path = req.getPathInfo();
        if (path == null)
            path = "/";

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if ("/stats".equals(path))
            handleStats(resp);
        else if (path.matches("/boats/[^/]+/reference"))
            handleBoatReference(path.replaceAll("^/boats/|/reference$", ""), resp);
        else if (path.startsWith("/boats"))
            handleBoats(path.substring("/boats".length()), req, resp);
        else if (path.startsWith("/designs"))
            handleDesigns(path.substring("/designs".length()), req, resp);
        else if (path.startsWith("/races"))
            handleRaces(path.substring("/races".length()), req, resp);
        else if ("/importers/status".equals(path))
            handleImporterStatus(resp);
        else if ("/importers".equals(path))
            handleImporters(resp);
        else
            resp.sendError(404);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        String path = req.getPathInfo();
        if (path == null)
            path = "/";

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (path.startsWith("/importers/") && path.endsWith("/run"))
        {
            String name = path.substring("/importers/".length(), path.length() - "/run".length());
            String mode = req.getParameter("mode");
            if (mode == null)
                mode = "api";
            handleImporterRun(name, mode, resp);
        }
        else if (path.startsWith("/importers/") && path.endsWith("/schedule"))
        {
            String name = path.substring("/importers/".length(), path.length() - "/schedule".length());
            handleSchedule(name, req, resp);
        }
        else
        {
            resp.sendError(404);
        }
    }

    private void handleStats(HttpServletResponse resp) throws IOException
    {
        writeJson(resp, Map.of(
            "races", store.races().size(),
            "boats", store.boats().size(),
            "designs", store.designs().size()
        ));
    }

    private void handleBoats(String sub, HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        if (sub.isEmpty() || "/".equals(sub))
        {
            int page = parseIntParam(req, "page", 0);
            int size = parseIntParam(req, "size", DEFAULT_PAGE_SIZE);
            String q = req.getParameter("q");
            String lower = q != null && !q.isBlank() ? q.toLowerCase() : null;

            List<Boat> all = store.boats().values().stream()
                .filter(b -> lower == null
                    || b.id().toLowerCase().contains(lower)
                    || b.name().toLowerCase().contains(lower))
                .collect(Collectors.toList());

            writeJson(resp, paginate(all, page, size));
        }
        else
        {
            String id = sub.startsWith("/") ? sub.substring(1) : sub;
            Boat boat = store.boats().get(id);
            if (boat == null)
            {
                resp.sendError(404);
                return;
            }
            writeJson(resp, boat);
        }
    }

    private void handleBoatReference(String id, HttpServletResponse resp) throws IOException
    {
        if (store.boats().get(id) == null)
        {
            resp.sendError(404);
            return;
        }
        BoatReferenceFactors factors = cache.referenceFactors().get(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("boatId", id);
        result.put("currentYear", LocalDate.now().getYear());
        result.put("spin",      factors != null ? factorMap(factors.spin())      : null);
        result.put("nonSpin",   factors != null ? factorMap(factors.nonSpin())   : null);
        result.put("twoHanded", factors != null ? factorMap(factors.twoHanded()) : null);
        writeJson(resp, result);
    }

    private Map<String, Object> factorMap(Factor f)
    {
        if (f == null)
            return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value",  f.value());
        m.put("weight", f.weight());
        return m;
    }

    private void handleDesigns(String sub, HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        if (sub.isEmpty() || "/".equals(sub))
        {
            int page = parseIntParam(req, "page", 0);
            int size = parseIntParam(req, "size", DEFAULT_PAGE_SIZE);
            String q = req.getParameter("q");
            String lower = q != null && !q.isBlank() ? q.toLowerCase() : null;

            List<Design> all = store.designs().values().stream()
                .filter(d -> lower == null
                    || d.id().toLowerCase().contains(lower)
                    || d.canonicalName().toLowerCase().contains(lower))
                .collect(Collectors.toList());

            writeJson(resp, paginate(all, page, size));
        }
        else
        {
            String id = sub.startsWith("/") ? sub.substring(1) : sub;
            Design design = store.designs().get(id);
            if (design == null)
            {
                resp.sendError(404);
                return;
            }
            writeJson(resp, design);
        }
    }

    private void handleRaces(String sub, HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        if (sub.isEmpty() || "/".equals(sub))
        {
            int page = parseIntParam(req, "page", 0);
            int size = parseIntParam(req, "size", DEFAULT_PAGE_SIZE);
            String q = req.getParameter("q");
            String lower = q != null && !q.isBlank() ? q.toLowerCase() : null;

            List<Race> all = store.races().values().stream()
                .filter(r -> lower == null
                    || r.id().toLowerCase().contains(lower)
                    || (r.clubId() != null && r.clubId().toLowerCase().contains(lower)))
                .collect(Collectors.toList());

            writeJson(resp, paginate(all, page, size));
        }
        else
        {
            String id = sub.startsWith("/") ? sub.substring(1) : sub;
            Race race = store.races().get(id);
            if (race == null)
            {
                resp.sendError(404);
                return;
            }
            writeJson(resp, race);
        }
    }

    private void handleImporters(HttpServletResponse resp) throws IOException
    {
        Map<String, ImporterService.ScheduleConfig> schedules = importerService.schedules();
        ImporterService.ImportStatus status = importerService.currentStatus();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : List.of("sailsys-boats", "sailsys-races", "orc", "ams", "topyacht"))
        {
            ImporterService.ScheduleConfig sc = schedules.get(name);
            boolean isRunning = status != null && name.equals(status.importerName());
            result.add(Map.of(
                "name", name,
                "schedule", sc != null ? sc : Map.of("enabled", false),
                "status", isRunning ? "running" : "idle"
            ));
        }
        writeJson(resp, result);
    }

    private void handleImporterStatus(HttpServletResponse resp) throws IOException
    {
        ImporterService.ImportStatus status = importerService.currentStatus();
        if (status == null)
        {
            writeJson(resp, Map.of("running", false));
        }
        else
        {
            writeJson(resp, Map.of(
                "running", true,
                "name", status.importerName(),
                "mode", status.mode(),
                "startedAt", status.startedAt().toString()
            ));
        }
    }

    private void handleImporterRun(String name, String mode, HttpServletResponse resp) throws IOException
    {
        boolean accepted = importerService.submit(name, mode);
        if (accepted)
        {
            resp.setStatus(202);
            writeJson(resp, Map.of("accepted", true, "name", name, "mode", mode));
        }
        else
        {
            resp.setStatus(409);
            writeJson(resp, Map.of("error", "An import is already running"));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSchedule(String name, HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        try
        {
            Map<String, Object> body = MAPPER.readValue(req.getInputStream(), Map.class);
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
            String dayStr = body.containsKey("day") ? (String)body.get("day") : "FRIDAY";
            String timeStr = body.containsKey("time") ? (String)body.get("time") : "03:00";
            String mode = body.containsKey("mode") ? (String)body.get("mode") : "api";

            DayOfWeek day = DayOfWeek.valueOf(dayStr.toUpperCase());
            LocalTime time = LocalTime.parse(timeStr);

            ImporterService.ScheduleConfig config = new ImporterService.ScheduleConfig(name, enabled, day, time, mode);
            importerService.setSchedule(name, config);
            resp.setStatus(200);
            writeJson(resp, config);
        }
        catch (Exception e)
        {
            resp.setStatus(400);
            writeJson(resp, Map.of("error", e.getMessage()));
        }
    }

    private <T> Map<String, Object> paginate(List<T> all, int page, int size)
    {
        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return Map.of(
            "items", all.subList(from, to),
            "total", total,
            "page", page,
            "size", size
        );
    }

    private int parseIntParam(HttpServletRequest req, String name, int defaultValue)
    {
        String v = req.getParameter(name);
        if (v == null)
            return defaultValue;
        try
        {
            return Integer.parseInt(v);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private void writeJson(HttpServletResponse resp, Object obj) throws IOException
    {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(resp.getWriter(), obj);
    }
}
