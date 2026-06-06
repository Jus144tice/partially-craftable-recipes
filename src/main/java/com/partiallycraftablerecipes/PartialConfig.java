package com.partiallycraftablerecipes;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config for Partially Craftable Recipes.
 *
 * <p>Mirrors the project's established config pattern: the {@link ModConfigSpec} values are the
 * source of truth on disk ({@code config/partiallycraftablerecipes-client.toml}); on (re)load they
 * are copied into the plain {@code volatile} fields below, which is what the Mixins read on the
 * render / click hot-path. Reading the cached fields means the Mixins never have to touch the config
 * system before it is loaded.
 */
public final class PartialConfig {

    private PartialConfig() {}

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_PARTIAL_CRAFTABLE_FILTER;
    public static final ModConfigSpec.BooleanValue SHOW_MISSING_INGREDIENTS_TOOLTIP;
    public static final ModConfigSpec.BooleanValue SORT_PARTIAL_RECIPES_BY_CLOSEST;
    public static final ModConfigSpec.IntValue MIN_MATCHED_INGREDIENTS;

    // Cached copies read by the Mixins. Defaults mirror the spec defaults so behavior is correct even
    // in the (brief) window before the config file is read.
    public static volatile boolean enablePartialCraftableFilter = true;
    public static volatile boolean showMissingIngredientsTooltip = true;
    public static volatile boolean sortPartialRecipesByClosest = true;
    public static volatile int minMatchedIngredients = 1;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("partial");

        ENABLE_PARTIAL_CRAFTABLE_FILTER = b.comment(
                        "Master switch for the third recipe-book filter state. When false the filter button is a",
                        "plain vanilla two-state toggle (All <-> Craftable) and nothing else in this mod runs.")
                .define("enablePartialCraftableFilter", true);

        SHOW_MISSING_INGREDIENTS_TOOLTIP = b.comment(
                        "When hovering a partially craftable recipe, append a 'Partially craftable' line, a",
                        "2/4-style progress line, and a list of the ingredients you are still missing to the",
                        "recipe tooltip.")
                .define("showMissingIngredientsTooltip", true);

        SORT_PARTIAL_RECIPES_BY_CLOSEST = b.comment(
                        "In the partial filter, sort recipes closest-to-craftable first: more satisfied ingredient",
                        "slots first, then fewer missing slots. Disable to keep the vanilla recipe-book order.")
                .define("sortPartialRecipesByClosest", true);

        MIN_MATCHED_INGREDIENTS = b.comment(
                        "How many of a recipe's ingredient slots you must already have for it to count as",
                        "'partially craftable'. 1 means 'show anything you have at least one ingredient for'.")
                .defineInRange("minMatchedIngredients", 1, 1, 64);

        b.pop();
        SPEC = b.build();
    }

    /** Copy the on-disk spec values into the cached fields read by the Mixins. */
    public static void bake() {
        enablePartialCraftableFilter = ENABLE_PARTIAL_CRAFTABLE_FILTER.get();
        showMissingIngredientsTooltip = SHOW_MISSING_INGREDIENTS_TOOLTIP.get();
        sortPartialRecipesByClosest = SORT_PARTIAL_RECIPES_BY_CLOSEST.get();
        minMatchedIngredients = MIN_MATCHED_INGREDIENTS.get();
    }

    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            bake();
        }
    }
}
