package com.partiallycraftablerecipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the config schema. These don't load a config file from disk; they assert the spec's
 * declared structure and defaults, plus that the cached fields the Mixins read start out matching
 * those defaults (so behavior is correct even before the file is first read). The NeoForge runtime is
 * on the test classpath via the moddev {@code unitTest} harness, which is what lets us touch
 * {@code ModConfigSpec} here.
 */
class PartialConfigTest {

    @Test
    @DisplayName("the spec builds")
    void specBuilds() {
        assertNotNull(PartialConfig.SPEC, "PartialConfig.SPEC should be built by the static initializer");
    }

    @Test
    @DisplayName("every config value is declared under the 'partial' section with the expected key")
    void valuePaths() {
        assertEquals(
                List.of("partial", "enablePartialCraftableFilter"),
                PartialConfig.ENABLE_PARTIAL_CRAFTABLE_FILTER.getPath());
        assertEquals(
                List.of("partial", "showMissingIngredientsTooltip"),
                PartialConfig.SHOW_MISSING_INGREDIENTS_TOOLTIP.getPath());
        assertEquals(
                List.of("partial", "sortPartialRecipesByClosest"),
                PartialConfig.SORT_PARTIAL_RECIPES_BY_CLOSEST.getPath());
        assertEquals(List.of("partial", "minMatchedIngredients"), PartialConfig.MIN_MATCHED_INGREDIENTS.getPath());
    }

    @Test
    @DisplayName("spec defaults match the documented behavior")
    void specDefaults() {
        assertTrue(PartialConfig.ENABLE_PARTIAL_CRAFTABLE_FILTER.getDefault());
        assertTrue(PartialConfig.SHOW_MISSING_INGREDIENTS_TOOLTIP.getDefault());
        assertTrue(PartialConfig.SORT_PARTIAL_RECIPES_BY_CLOSEST.getDefault());
        assertEquals(1, PartialConfig.MIN_MATCHED_INGREDIENTS.getDefault());
    }

    @Test
    @DisplayName("cached fields start at the spec defaults, so the Mixins read correct values pre-load")
    void cachedDefaultsMirrorSpec() {
        assertEquals(
                PartialConfig.ENABLE_PARTIAL_CRAFTABLE_FILTER.getDefault(), PartialConfig.enablePartialCraftableFilter);
        assertEquals(
                PartialConfig.SHOW_MISSING_INGREDIENTS_TOOLTIP.getDefault(),
                PartialConfig.showMissingIngredientsTooltip);
        assertEquals(
                PartialConfig.SORT_PARTIAL_RECIPES_BY_CLOSEST.getDefault(), PartialConfig.sortPartialRecipesByClosest);
        assertEquals(
                PartialConfig.MIN_MATCHED_INGREDIENTS.getDefault().intValue(), PartialConfig.minMatchedIngredients);
    }
}
