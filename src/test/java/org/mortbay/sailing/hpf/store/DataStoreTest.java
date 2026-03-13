package org.mortbay.sailing.hpf.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mortbay.sailing.hpf.data.Boat;
import org.mortbay.sailing.hpf.data.Club;
import org.mortbay.sailing.hpf.data.Design;
import org.mortbay.sailing.hpf.data.Division;
import org.mortbay.sailing.hpf.data.Finisher;
import org.mortbay.sailing.hpf.data.HandicapSystem;
import org.mortbay.sailing.hpf.data.MeasurementCertificate;
import org.mortbay.sailing.hpf.data.Race;
import org.mortbay.sailing.hpf.data.Season;
import org.mortbay.sailing.hpf.data.Series;
import org.mortbay.sailing.hpf.store.DataStore;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataStoreTest {

    // --- Load from pre-built test data ---

    @Test
    void loadAllRacesFromTestData() throws URISyntaxException {
        DataStore store = testDataStore();

        List<Race> races = store.loadAllRaces();

        assertEquals(2, races.size());
        Race race = races.stream()
                .filter(r -> r.id().equals("myc.com.au-2020-09-13-0001"))
                .findFirst().orElseThrow();

        assertEquals("myc.com.au", race.clubId());
        assertEquals(LocalDate.of(2020, 9, 13), race.date());
        assertEquals(HandicapSystem.PHS, race.handicapSystem());
        assertEquals(2, race.divisions().size());

        Division div1 = race.divisions().getFirst();
        assertEquals("Division 1", div1.name());
        assertEquals(4, div1.finishers().size());

        Finisher shearMagic = div1.finishers().getFirst();
        assertEquals("myc100-shear-0001", shearMagic.boatId());
        assertEquals(Duration.ofMinutes(69).plusSeconds(42), shearMagic.elapsedTime());
        assertFalse(shearMagic.nonSpinnaker());

        Finisher sanToy = div1.finishers().get(2);
        assertEquals("myc12-san-0003", sanToy.boatId());
        assertTrue(sanToy.nonSpinnaker());
    }

    @Test
    void loadCatalogueFromTestData() throws URISyntaxException {
        DataStore store = testDataStore();

        List<Boat> boats = store.loadBoats();
        assertEquals(7, boats.size());
        Boat tensixty = boats.stream()
                .filter(b -> b.id().equals("myc7-tensixty-0002"))
                .findFirst().orElseThrow();
        assertEquals("Tensixty", tensixty.name());
        assertEquals("radford106", tensixty.designId());
        assertEquals(List.of("TenSixty", "1060"), tensixty.aliases());

        List<Club> clubs = store.loadClubs();
        assertEquals(1, clubs.size());
        assertEquals("MYC", clubs.getFirst().shortName());

        List<Season> seasons = store.loadSeasons();
        assertEquals(1, seasons.size());
        assertEquals("2020-21", seasons.getFirst().id());

        List<Series> seriesList = store.loadSeries();
        assertEquals(1, seriesList.size());
        assertFalse(seriesList.getFirst().isCatchAll());

        List<Design> designs = store.loadDesigns();
        assertEquals(4, designs.size());
        Design radford = designs.stream()
                .filter(d -> d.id().equals("radford106"))
                .findFirst().orElseThrow();
        assertEquals(List.of("1060"), radford.aliases());

        List<MeasurementCertificate> certs = store.loadCertificates();
        assertEquals(1, certs.size());
        MeasurementCertificate cert = certs.getFirst();
        assertEquals(HandicapSystem.ORC, cert.system());
        assertEquals(588.4, cert.value());
        assertEquals(LocalDate.of(2021, 6, 30), cert.expiryDate());
    }

    // --- Round-trip save/load ---

    @Test
    void roundTripRace(@TempDir Path tempDir) throws IOException {
        DataStore store = new DataStore(tempDir);
        Race race = buildRace();

        store.saveRace(race);
        Race loaded = store.loadRace(race.id());

        assertEquals(race, loaded);
    }

    @Test
    void roundTripCatalogue(@TempDir Path tempDir) {
        DataStore store = new DataStore(tempDir);

        List<Boat> boats = List.of(
                new Boat("myc100-shear-0001", "myc100", "Shear Magic", "adams10", "myc.com.au", List.of()),
                new Boat("myc7-tensixty-0002", "myc7", "Tensixty", "radford106", "myc.com.au", List.of("TenSixty", "1060"))
        );
        boats.forEach(store::saveBoat);
        assertEquals(boats, store.loadBoats().stream().sorted(java.util.Comparator.comparing(Boat::id)).toList());

        List<Club> clubs = List.of(new Club("myc.com.au", "MYC", "Manly Yacht Club", List.of()));
        store.saveClubs(clubs);
        assertEquals(clubs, store.loadClubs());

        List<Season> seasons = List.of(new Season("2020-21"));
        store.saveSeasons(seasons);
        assertEquals(seasons, store.loadSeasons());

        List<Series> series = List.of(
                new Series("myc.com.au/2020-21/club-championship", "myc.com.au", "2020-21", "Club Championship", false)
        );
        store.saveSeries(series);
        assertEquals(series, store.loadSeries());
    }

    @Test
    void emptyListWhenFileAbsent(@TempDir Path tempDir) {
        DataStore store = new DataStore(tempDir);
        assertEquals(List.of(), store.loadBoats());
        assertEquals(List.of(), store.loadAllRaces());
        assertEquals(List.of(), store.loadCertificates());
    }

    @Test
    void eachRaceInOwnFile(@TempDir Path tempDir) throws IOException {
        DataStore store = new DataStore(tempDir);
        Race race1 = buildRace();
        Race race2 = new Race("myc.com.au-2020-09-20-0002", "myc.com.au",
                List.of("myc.com.au/2020-21/club-championship"),
                LocalDate.of(2020, 9, 20), 2, null, HandicapSystem.PHS, false,
                List.of(new Division("Division 1", List.of(
                        new Finisher("myc7-tensixty-0002", Duration.ofMinutes(72).plusSeconds(5), false)
                ))));

        store.saveRace(race1);
        store.saveRace(race2);

        assertEquals(race1, store.loadRace(race1.id()));
        assertEquals(race2, store.loadRace(race2.id()));
        assertEquals(2, store.loadAllRaces().size());
    }

    @Test
    void eachBoatInOwnFile(@TempDir Path tempDir) {
        DataStore store = new DataStore(tempDir);
        Boat boat1 = new Boat("myc100-shear-0001", "myc100", "Shear Magic", "adams10", "myc.com.au", List.of());
        Boat boat2 = new Boat("myc7-tensixty-0002", "myc7", "Tensixty", "radford106", "myc.com.au", List.of("TenSixty", "1060"));

        store.saveBoat(boat1);
        store.saveBoat(boat2);

        assertTrue(tempDir.resolve("boats/myc100-shear-0001.json").toFile().exists());
        assertTrue(tempDir.resolve("boats/myc7-tensixty-0002.json").toFile().exists());
        List<Boat> loaded = store.loadBoats().stream()
                .sorted(java.util.Comparator.comparing(Boat::id)).toList();
        assertEquals(List.of(boat1, boat2), loaded);
    }

    // --- Helpers ---

    private DataStore testDataStore() throws URISyntaxException {
        Path testData = Path.of(getClass().getClassLoader().getResource("testdata").toURI());
        return new DataStore(testData);
    }

    private Race buildRace() {
        return new Race(
                "myc.com.au-2020-09-13-0001",
                "myc.com.au",
                List.of("myc.com.au/2020-21/club-championship"),
                LocalDate.of(2020, 9, 13),
                1,
                null,
                HandicapSystem.PHS,
                false,
                List.of(
                        new Division("Division 1", List.of(
                                new Finisher("myc100-shear-0001", Duration.ofMinutes(69).plusSeconds(42), false),
                                new Finisher("myc7-tensixty-0002", Duration.ofMinutes(67).plusSeconds(37), false),
                                new Finisher("myc12-san-0003", Duration.ofMinutes(67).plusSeconds(22), true),
                                new Finisher("5656-mondo-0004", Duration.ofMinutes(67).plusSeconds(19), false)
                        )),
                        new Division("Division 2", List.of(
                                new Finisher("1152-bokarra-0005", Duration.ofMinutes(80).plusSeconds(26), false),
                                new Finisher("1255-melody-0006", Duration.ofMinutes(77).plusSeconds(32), false),
                                new Finisher("6295-ratty-0007", Duration.ofMinutes(77).plusSeconds(59), false)
                        ))
                )
        );
    }
}
