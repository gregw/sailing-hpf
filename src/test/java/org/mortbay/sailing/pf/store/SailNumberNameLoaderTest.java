package org.mortbay.sailing.pf.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SailNumberNameLoaderTest
{
    @Test
    void loadsSeedFromClasspath()
    {
        // aliases.yaml is on the classpath under src/main/resources
        Aliases.Loaded seed = Aliases.load(Path.of("nonexistent"));
        assertNotNull(seed);
    }

    @Test
    void designAliasResolvesToCanonicalId()
    {
        Aliases.Loaded seed = Aliases.load(Path.of("nonexistent"));
        // "Sydney 36 OD" normalises to "sydney36od" → should resolve to "sydney36cr"
        assertEquals("sydney36cr", seed.resolveDesignAlias("sydney36od"));
    }

    @Test
    void unknownDesignAliasReturnsInput()
    {
        Aliases.Loaded seed = Aliases.load(Path.of("nonexistent"));
        // Unknown aliases pass through unchanged
        assertEquals("unknowndesign99", seed.resolveDesignAlias("unknowndesign99"));
    }

    @Test
    void designCanonicalNameReturned()
    {
        Aliases.Loaded seed = Aliases.load(Path.of("nonexistent"));
        assertEquals("Sydney 36 CR", seed.designCanonicalName("sydney36cr"));
    }

    @Test
    void unknownDesignCanonicalNameReturnsNull()
    {
        Aliases.Loaded seed = Aliases.load(Path.of("nonexistent"));
        assertNull(seed.designCanonicalName("notinseednever"));
    }

    @Test
    void lookupBoatByAlternateName()
    {
        Aliases.Loaded seed = Aliases.load(Path.of("nonexistent"));
        // MYC7-daydreaming has aliases "1060" and "TenSixty"
        // lookupBoat returns the canonical sail/name pair plus display name
        Optional<Aliases.BoatMatch> result = seed.lookupBoat("MYC7", "1060");
        assertTrue(result.isPresent());
        assertEquals("MYC7", result.get().normSailNumber());
        assertEquals("daydreaming", result.get().normName());
        assertEquals("Day Dreaming", result.get().canonicalDisplayName());
    }

    @Test
    void lookupBoatByTenSixtyAlias()
    {
        Aliases.Loaded seed = Aliases.load(Path.of("nonexistent"));
        Optional<Aliases.BoatMatch> result = seed.lookupBoat("MYC7", "tensixty");
        assertTrue(result.isPresent());
        assertEquals("MYC7", result.get().normSailNumber());
        assertEquals("daydreaming", result.get().normName());
    }

    @Test
    void unknownBoatReturnsEmpty()
    {
        Aliases.Loaded seed = Aliases.load(Path.of("nonexistent"));
        assertTrue(seed.lookupBoat("NOSUCHSAIL999", "noname").isEmpty());
    }

    @Test
    void missingFileReturnsEmptySeedWithoutThrowing()
    {
        // The EMPTY sentinel should return nulls/empty lists without throwing
        Aliases.Loaded empty = Aliases.Loaded.EMPTY;
        // EMPTY resolveDesignAlias passes through the input
        assertEquals("anything", empty.resolveDesignAlias("anything"));
        assertNull(empty.designCanonicalName("anything"));
        assertTrue(empty.lookupBoat("anything", "anything").isEmpty());
    }

    @Test
    void appendDesignMergeAliasesSetsCanonicalName(@TempDir Path tempDir) throws Exception
    {
        Aliases.appendDesignMergeAliases(tempDir, "mydesign", "My Design Name", List.of("Alt Name"));

        Aliases.Loaded seed = Aliases.load(tempDir);
        assertEquals("My Design Name", seed.designCanonicalName("mydesign"));
        assertEquals("mydesign", seed.resolveDesignAlias("altname"));
    }

    @Test
    void appendDesignMergeAliasesDoesNotOverwriteExistingCanonicalName(@TempDir Path tempDir) throws Exception
    {
        Aliases.appendDesignMergeAliases(tempDir, "mydesign", "First Name", List.of());
        Aliases.appendDesignMergeAliases(tempDir, "mydesign", "Second Name", List.of());

        Aliases.Loaded seed = Aliases.load(tempDir);
        assertEquals("First Name", seed.designCanonicalName("mydesign"));
    }

    // --- Implicit AUS prefix alias tests ---

    @Test
    void lookupBoatStripsAusPrefixWithNoYamlEntry()
    {
        Aliases.Loaded empty = Aliases.Loaded.EMPTY;
        Optional<Aliases.BoatMatch> result = empty.lookupBoat("AUS1234", "someboat");
        assertTrue(result.isPresent());
        assertEquals("1234", result.get().normSailNumber());
        assertEquals("someboat", result.get().normName());
        assertNull(result.get().canonicalDisplayName());
    }

    @Test
    void lookupBoatStripsJausPrefixWithNoYamlEntry()
    {
        Aliases.Loaded empty = Aliases.Loaded.EMPTY;
        Optional<Aliases.BoatMatch> result = empty.lookupBoat("JAUS103", "myyacht");
        assertTrue(result.isPresent());
        assertEquals("103", result.get().normSailNumber());
        assertEquals("myyacht", result.get().normName());

        result = empty.lookupBoat("0103", "myyacht");
        assertTrue(result.isPresent());
        assertEquals("103", result.get().normSailNumber());
        assertEquals("myyacht", result.get().normName());

        result = empty.lookupBoat("AUS00103", "myyacht");
        assertTrue(result.isPresent());
        assertEquals("103", result.get().normSailNumber());
        assertEquals("myyacht", result.get().normName());
    }

    @Test
    void lookupBoatNoPrefixAndNoYamlEntryReturnsEmpty()
    {
        Aliases.Loaded empty = Aliases.Loaded.EMPTY;
        assertTrue(empty.lookupBoat("1234", "someboat").isEmpty());
    }

    /**
     * Regression: when the alias entry shares its canonical sail number with the alias's
     * own sail number (e.g. canonical (10001, wildoatsxi) with alias (10001, hamiltonislandwildoats)),
     * the sail-index skips that entry — so the only lookup path is the name branch. The
     * name branch must still honour AUS-prefix equivalence on the sail number, otherwise
     * an input of "AUS10001" falls through to the implicit prefix-strip with the raw name
     * and a fresh duplicate boat record gets created.
     */
    @Test
    void lookupBoatMatchesNameAliasAcrossAusPrefix(@TempDir Path tempDir) throws Exception
    {
        Aliases.addAliases(tempDir, "10001", "Wild Oats XI",
            List.of(new Aliases.SailNumberName("10001", "hamiltonislandwildoats")));

        Aliases.Loaded seed = Aliases.load(tempDir);

        // Without AUS prefix — this worked before too.
        Optional<Aliases.BoatMatch> plain = seed.lookupBoat("10001", "hamiltonislandwildoats");
        assertTrue(plain.isPresent());
        assertEquals("10001", plain.get().normSailNumber());
        assertEquals("wildoatsxi", plain.get().normName());
        assertEquals("Wild Oats XI", plain.get().canonicalDisplayName());

        // With AUS prefix — this was the broken case.
        Optional<Aliases.BoatMatch> prefixed = seed.lookupBoat("AUS10001", "hamiltonislandwildoats");
        assertTrue(prefixed.isPresent(), "AUS-prefixed sail should still resolve via the alias");
        assertEquals("10001", prefixed.get().normSailNumber());
        assertEquals("wildoatsxi", prefixed.get().normName(),
            "name must be canonicalised, not left as hamiltonislandwildoats");
        assertEquals("Wild Oats XI", prefixed.get().canonicalDisplayName());
    }

    /**
     * Symmetric case: alias stored with AUS prefix, input without it. Fix should handle
     * both directions since stripPrefix is applied to both sides of the comparison.
     */
    @Test
    void lookupBoatMatchesNameAliasAcrossAusPrefixInverted(@TempDir Path tempDir) throws Exception
    {
        Aliases.addAliases(tempDir, "10001", "Wild Oats XI",
            List.of(new Aliases.SailNumberName("AUS10001", "hamiltonislandwildoats")));

        Aliases.Loaded seed = Aliases.load(tempDir);

        Optional<Aliases.BoatMatch> plain = seed.lookupBoat("10001", "hamiltonislandwildoats");
        assertTrue(plain.isPresent());
        assertEquals("wildoatsxi", plain.get().normName());
    }

    @Test
    void lookupBoatPrefixOnlyNoDigitsDoesNotStrip()
    {
        // "AUS" alone (no trailing digit) should not be stripped
        Aliases.Loaded empty = Aliases.Loaded.EMPTY;
        assertTrue(empty.lookupBoat("AUS", "someboat").isEmpty());
    }
}
