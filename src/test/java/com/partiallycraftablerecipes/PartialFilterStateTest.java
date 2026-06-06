package com.partiallycraftablerecipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.partiallycraftablerecipes.PartialFilterState.CycleResult;
import com.partiallycraftablerecipes.PartialFilterState.Mode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for the pure tri-state cycle logic (no Minecraft state touched). */
class PartialFilterStateTest {

    @Nested
    class ModeResolution {
        @Test
        void resolvesAllThreeModes() {
            assertEquals(Mode.ALL, PartialFilterState.modeOf(false, false));
            assertEquals(Mode.CRAFTABLE, PartialFilterState.modeOf(true, false));
            assertEquals(Mode.PARTIAL, PartialFilterState.modeOf(false, true));
            assertEquals(Mode.PARTIAL, PartialFilterState.modeOf(true, true), "partial overrides filtering");
        }
    }

    @Nested
    class FeatureEnabled {
        @Test
        void all_to_craftable() {
            CycleResult r = PartialFilterState.cycle(false, false, true);
            assertTrue(r.vanillaFiltering());
            assertFalse(r.partial());
            assertTrue(r.buttonOn());
        }

        @Test
        void craftable_to_partial() {
            CycleResult r = PartialFilterState.cycle(true, false, true);
            assertFalse(r.vanillaFiltering());
            assertTrue(r.partial());
            assertTrue(r.buttonOn());
        }

        @Test
        void partial_to_all() {
            CycleResult r = PartialFilterState.cycle(false, true, true);
            assertFalse(r.vanillaFiltering());
            assertFalse(r.partial());
            assertFalse(r.buttonOn());
        }

        @Test
        void fullCycleReturnsToStart() {
            // ALL -> CRAFTABLE -> PARTIAL -> ALL
            CycleResult a = PartialFilterState.cycle(false, false, true);
            CycleResult b = PartialFilterState.cycle(a.vanillaFiltering(), a.partial(), true);
            CycleResult c = PartialFilterState.cycle(b.vanillaFiltering(), b.partial(), true);
            assertEquals(Mode.ALL, PartialFilterState.modeOf(c.vanillaFiltering(), c.partial()));
        }
    }

    @Nested
    class FeatureDisabled {
        @Test
        void degradesToVanillaToggle() {
            CycleResult on = PartialFilterState.cycle(false, false, false);
            assertTrue(on.vanillaFiltering());
            assertFalse(on.partial());
            assertTrue(on.buttonOn());

            CycleResult off = PartialFilterState.cycle(true, false, false);
            assertFalse(off.vanillaFiltering());
            assertFalse(off.partial());
            assertFalse(off.buttonOn());
        }

        @Test
        void neverProducesPartial() {
            // even if partial was somehow set, a disabled feature clears it
            CycleResult r = PartialFilterState.cycle(false, true, false);
            assertFalse(r.partial());
        }
    }
}
