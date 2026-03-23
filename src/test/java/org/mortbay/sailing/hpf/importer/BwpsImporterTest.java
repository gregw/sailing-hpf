package org.mortbay.sailing.hpf.importer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mortbay.sailing.hpf.data.Boat;
import org.mortbay.sailing.hpf.data.Certificate;
import org.mortbay.sailing.hpf.data.Division;
import org.mortbay.sailing.hpf.data.Finisher;
import org.mortbay.sailing.hpf.data.Race;
import org.mortbay.sailing.hpf.importer.BwpsImporter.BoatDetail;
import org.mortbay.sailing.hpf.importer.BwpsImporter.LhRow;
import org.mortbay.sailing.hpf.importer.BwpsImporter.RaceOption;
import org.mortbay.sailing.hpf.importer.BwpsImporter.StandingsRow;
import org.mortbay.sailing.hpf.importer.BwpsImporter.YearOption;
import org.mortbay.sailing.hpf.store.DataStore;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BwpsImporterTest
{
    @TempDir Path tempDir;
    private DataStore store;
    private BwpsImporter importer;

    @BeforeEach
    void setUp()
    {
        store = new DataStore(tempDir);
        store.start();
        importer = new BwpsImporter(store, null); // httpClient not used in parse methods
    }

    @AfterEach
    void tearDown()
    {
        store.stop();
    }

    // --- parseRaceSelector ---

    @Test
    void parseRaceSelectorExtractsAllRaces()
    {
        String html = fixture("standings-main.html");
        List<RaceOption> races = importer.parseRaceSelector(html);

        assertEquals(2, races.size());
        assertEquals("Flinders Islet Race", races.get(0).name());
        assertEquals("/standings?seriesId=11", races.get(0).url());
        assertEquals("Rolex Sydney Hobart Yacht Race", races.get(1).name());
        assertEquals("/standings?seriesId=15", races.get(1).url());
    }

    @Test
    void parseRaceSelectorReturnsEmptyWhenSelectorAbsent()
    {
        List<RaceOption> races = importer.parseRaceSelector("<html><body></body></html>");
        assertTrue(races.isEmpty());
    }

    // --- parseYearSelector ---

    @Test
    void parseYearSelectorExtractsYears()
    {
        String html = fixture("standings-main.html");
        List<YearOption> years = importer.parseYearSelector(html);

        assertEquals(4, years.size());
        assertEquals(2025, years.get(0).year());
        assertEquals("2025", years.get(0).yearLabel());
        assertEquals("/standings?categoryId=1071&raceId=187&seriesId=11", years.get(0).url());
        assertEquals(2018, years.get(3).year());
    }

    @Test
    void parseYearSelectorFiltersOutNonIntegerLabels()
    {
        String html = "<html><body>" +
            "<select aria-labelledby=\"standings-filters-year-label\">" +
            "<option value=\"/s?y=2023\">2023</option>" +
            "<option value=\"/s?y=bad\">LATEST</option>" +
            "</select></body></html>";
        List<YearOption> years = importer.parseYearSelector(html);

        assertEquals(1, years.size());
        assertEquals(2023, years.get(0).year());
    }

    // --- parseCategoryTabs ---

    @Test
    void parseCategoryTabsFindsIrcAndLineHonours()
    {
        String html = fixture("standings-irc.html");
        Map<String, String> tabs = importer.parseCategoryTabs(html);

        assertTrue(tabs.containsKey("IRC"));
        assertTrue(tabs.containsKey("Line Honours"));
        assertTrue(tabs.containsKey("PHS")); // PHS tab is present; ignored during processing
        assertEquals("/Standings?categoryId=1071&raceId=187&seriesId=11", tabs.get("IRC"));
        assertEquals("/Standings?categoryId=1068&raceId=187&seriesId=11", tabs.get("Line Honours"));
    }

    @Test
    void parseCategoryTabsDeduplicatesRepeatedTabs()
    {
        String html = "<html><body>" +
            "<a href=\"/Standings?categoryId=1&raceId=1&seriesId=1\">IRC</a>" +
            "<a href=\"/Standings?categoryId=1&raceId=1&seriesId=1\">IRC</a>" +
            "</body></html>";
        Map<String, String> tabs = importer.parseCategoryTabs(html);

        assertEquals(1, tabs.size());
        assertTrue(tabs.containsKey("IRC"));
    }

    // --- parseStandingsTable ---

    @Test
    void parseStandingsTableExtractsRows()
    {
        String html = fixture("standings-irc.html");
        List<StandingsRow> rows = importer.parseStandingsTable(html, "IRC");

        assertEquals(3, rows.size());

        StandingsRow first = rows.get(0);
        assertEquals("/the-yachts/flinders-islet-race/2025/moneypenny/?raceId=187&seriesId=11",
            first.boatDetailUrl());
        assertEquals("Moneypenny", first.boatName());
        assertEquals("1", first.div());
        assertEquals("FINISHED", first.status());
        assertEquals(1.560, first.hcap(), 0.0001);
        assertEquals("IRC", first.system());
    }

    @Test
    void parseStandingsTableSkipsRowsWithNoLink()
    {
        String html = "<html><body><table class=\"standings\">" +
            "<thead><tr><th colspan=\"2\">Yacht</th><th>DIV</th><th>Position</th><th>HCAP</th><th>Time</th></tr></thead>" +
            "<tbody><tr><td>1</td><td>No link</td><td>1</td><td>FINISHED</td><td>1.500</td><td></td></tr></tbody>" +
            "</table></body></html>";
        List<StandingsRow> rows = importer.parseStandingsTable(html, "IRC");
        assertTrue(rows.isEmpty());
    }

    @Test
    void parseStandingsTableReturnsEmptyWhenNoHcapColumn()
    {
        // Line Honours table has no HCAP column — should return empty
        String html = fixture("standings-lh.html");
        List<StandingsRow> rows = importer.parseStandingsTable(html, "IRC");
        assertTrue(rows.isEmpty());
    }

    // --- parseLineHonoursTable ---

    @Test
    void parseLineHonoursTableExtractsElapsedAndFinishText()
    {
        String html = fixture("standings-lh.html");
        List<LhRow> rows = importer.parseLineHonoursTable(html);

        assertEquals(2, rows.size());

        LhRow first = rows.get(0);
        assertEquals("/the-yachts/flinders-islet-race/2025/moneypenny/?raceId=187&seriesId=11",
            first.boatDetailUrl());
        assertEquals("Moneypenny", first.boatName());
        assertEquals("FINISHED", first.status());
        assertEquals(Duration.ofHours(6).plusMinutes(15).plusSeconds(44), first.elapsed());
        assertEquals("20 Sep 04:15:44 PM", first.finishText());
    }

    // --- parseBoatDetail ---

    @Test
    void parseBoatDetailExtractsAllFields()
    {
        String html = fixture("boat-moneypenny.html");
        BoatDetail detail = importer.parseBoatDetail(html);

        assertEquals("Moneypenny", detail.yachtName());
        assertEquals("AUS1234", detail.sailNumber());
        assertEquals("Robert Appleyard", detail.owner());
        assertEquals("WA", detail.state());
        assertEquals("RPYC", detail.club());
        assertEquals("TP52", detail.type());
    }

    @Test
    void parseBoatDetailHandlesMissingFields()
    {
        String html = "<html><body><table><tbody>" +
            "<tr><td>Yacht Name</td><td>Mystery</td></tr>" +
            "</tbody></table></body></html>";
        BoatDetail detail = importer.parseBoatDetail(html);

        assertEquals("Mystery", detail.yachtName());
        assertNull(detail.sailNumber());
        assertNull(detail.type());
    }

    // --- parseElapsed ---

    @Test
    void parseElapsedHandlesMultiDayFormat()
    {
        Duration d = BwpsImporter.parseElapsed("03:01:39:32");
        assertNotNull(d);
        assertEquals(Duration.ofDays(3).plusHours(1).plusMinutes(39).plusSeconds(32), d);
    }

    @Test
    void parseElapsedHandlesSameDayFormat()
    {
        Duration d = BwpsImporter.parseElapsed("00:06:15:44");
        assertNotNull(d);
        assertEquals(Duration.ofHours(6).plusMinutes(15).plusSeconds(44), d);
    }

    @Test
    void parseElapsedIgnoresTrailingSpeedAndDate()
    {
        Duration d = BwpsImporter.parseElapsed("00:06:15:44 14.1 20 Sep 04:15:44 PM");
        assertNotNull(d);
        assertEquals(Duration.ofHours(6).plusMinutes(15).plusSeconds(44), d);
    }

    @Test
    void parseElapsedReturnsNullForBlank()
    {
        assertNull(BwpsImporter.parseElapsed(null));
        assertNull(BwpsImporter.parseElapsed(""));
        assertNull(BwpsImporter.parseElapsed("   "));
    }

    @Test
    void parseElapsedReturnsNullForThreePartFormat()
    {
        // TopYacht-style H:MM:SS is not valid as BWPS elapsed time
        assertNull(BwpsImporter.parseElapsed("6:15:44"));
    }

    // --- computeRaceDate ---

    @Test
    void computeRaceDateFromFlindersFirstFinisher()
    {
        // Moneypenny: finished 20 Sep 04:15:44 PM, elapsed 6h15m44s → start ~10:00 AM 20 Sep
        LhRow row = new LhRow(
            "/the-yachts/test/", "Moneypenny", "FINISHED",
            Duration.ofHours(6).plusMinutes(15).plusSeconds(44),
            "20 Sep 04:15:44 PM");
        LocalDate date = BwpsImporter.computeRaceDate(List.of(row), 2025);
        assertEquals(LocalDate.of(2025, 9, 20), date);
    }

    @Test
    void computeRaceDateFromSydneyHobartFirstFinisher()
    {
        // Sydney Hobart: finished 29 Dec 02:39:32 PM, elapsed 3d1h39m32s → start 26 Dec
        LhRow row = new LhRow(
            "/the-yachts/test/", "Fast Boat", "FINISHED",
            Duration.ofDays(3).plusHours(1).plusMinutes(39).plusSeconds(32),
            "29 Dec 02:39:32 PM");
        LocalDate date = BwpsImporter.computeRaceDate(List.of(row), 2025);
        assertEquals(LocalDate.of(2025, 12, 26), date);
    }

    @Test
    void computeRaceDateUsesNextYearWhenFinishIsJanuary()
    {
        // Race year 2025, boat finishes 5 Jan after 10 days → finish is 5 Jan 2026, start 26 Dec 2025
        LhRow row = new LhRow(
            "/the-yachts/test/", "Slow Boat", "FINISHED",
            Duration.ofDays(10),
            "5 Jan 12:00:00 PM");
        LocalDate date = BwpsImporter.computeRaceDate(List.of(row), 2025);
        assertEquals(LocalDate.of(2025, 12, 26), date);
    }

    @Test
    void computeRaceDateReturnsNullWhenNoFinishers()
    {
        assertNull(BwpsImporter.computeRaceDate(List.of(), 2025));
    }

    @Test
    void computeRaceDateSkipsRetiredAndUsesFirstFinished()
    {
        LhRow retired  = new LhRow("/r/", "Retired", "RETIRED",
            Duration.ofHours(5), null);
        LhRow finished = new LhRow("/f/", "Finisher", "FINISHED",
            Duration.ofHours(6).plusMinutes(15).plusSeconds(44),
            "20 Sep 04:15:44 PM");
        LocalDate date = BwpsImporter.computeRaceDate(List.of(retired, finished), 2025);
        assertEquals(LocalDate.of(2025, 9, 20), date);
    }

    // --- processRaceEdition (integration) ---

    @Test
    void processRaceEditionStoresRaceAndFinishers() throws Exception
    {
        // Subclass that overrides fetchHtml to serve local fixtures
        BwpsImporter fixtureImporter = new BwpsImporter(store, null)
        {
            @Override
            String fetchHtml(String url)
            {
                if (url.contains("categoryId=1071"))
                    return fixture("standings-irc.html");
                if (url.contains("categoryId=1068"))
                    return fixture("standings-lh.html");
                if (url.contains("moneypenny"))
                    return fixture("boat-moneypenny.html");
                if (url.contains("speedy"))
                    return fixture("boat-speedy.html");
                return "<html><body></body></html>";
            }
        };

        fixtureImporter.processRaceEdition(
            "Flinders Islet Race", 2025,
            "/Standings?categoryId=1071&raceId=187&seriesId=11");

        // Race stored
        assertEquals(1, store.races().size());
        Race race = store.races().values().iterator().next();
        assertEquals(BwpsImporter.CLUB_ID, race.clubId());
        assertEquals("IRC", race.handicapSystem());
        assertEquals(LocalDate.of(2025, 9, 20), race.date());
        assertFalse(race.divisions().isEmpty());

        // Two finished boats (retired boat excluded)
        int totalFinishers = race.divisions().stream()
            .mapToInt(d -> d.finishers().size()).sum();
        assertEquals(2, totalFinishers);

        // Both boats stored with IRC certificates
        assertEquals(2, store.boats().size());
        for (Boat boat : store.boats().values())
        {
            assertFalse(boat.certificates().isEmpty(),
                "boat " + boat.id() + " should have an inferred certificate");
            Certificate cert = boat.certificates().get(0);
            assertEquals("IRC", cert.system());
            assertEquals(2025, cert.year());
            assertTrue(cert.certificateNumber().startsWith("bwps-irc-"));
        }

        // All finishers have elapsed times
        List<Finisher> all = race.divisions().stream()
            .flatMap(d -> d.finishers().stream()).toList();
        assertTrue(all.stream().allMatch(f -> f.elapsedTime() != null));
        assertTrue(all.stream().anyMatch(f ->
            Duration.ofHours(6).plusMinutes(15).plusSeconds(44).equals(f.elapsedTime())));
    }

    @Test
    void processRaceEditionIsIdempotent() throws Exception
    {
        BwpsImporter fixtureImporter = new BwpsImporter(store, null)
        {
            @Override
            String fetchHtml(String url)
            {
                if (url.contains("categoryId=1071"))
                    return fixture("standings-irc.html");
                if (url.contains("categoryId=1068"))
                    return fixture("standings-lh.html");
                if (url.contains("moneypenny"))
                    return fixture("boat-moneypenny.html");
                if (url.contains("speedy"))
                    return fixture("boat-speedy.html");
                return "<html><body></body></html>";
            }
        };

        fixtureImporter.processRaceEdition(
            "Flinders Islet Race", 2025,
            "/Standings?categoryId=1071&raceId=187&seriesId=11");
        fixtureImporter.processRaceEdition(
            "Flinders Islet Race", 2025,
            "/Standings?categoryId=1071&raceId=187&seriesId=11");

        assertEquals(1, store.races().size(), "second run should not create a duplicate race");
    }

    // --- Helpers ---

    private static String fixture(String name)
    {
        URL url = BwpsImporterTest.class.getClassLoader().getResource("bwps/" + name);
        assertNotNull(url, "fixture not found: bwps/" + name);
        try
        {
            return Files.readString(Path.of(url.toURI()), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new RuntimeException("failed to read fixture: " + name, e);
        }
    }
}
