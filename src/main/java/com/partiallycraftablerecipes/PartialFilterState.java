package com.partiallycraftablerecipes;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.inventory.RecipeBookType;

/**
 * Tracks the mod's extra "partially craftable" filter state and owns the tri-state cycle logic.
 *
 * <p>Vanilla's recipe book stores a single boolean per {@link RecipeBookType} ("filtering" =
 * craftable-only). This mod layers a third state on top without persisting it server-side: a small
 * client-only set of the book types currently in <em>partial</em> mode. The combination of vanilla's
 * filtering boolean and this set yields three user-visible states:
 *
 * <pre>
 *   filtering=false, partial=false  -&gt; ALL         (vanilla "show all")
 *   filtering=true,  partial=false  -&gt; CRAFTABLE    (vanilla "only craftable")
 *   filtering=false, partial=true   -&gt; PARTIAL      (this mod)
 * </pre>
 *
 * <p>The {@link #cycle} method that advances those states is pure (no Minecraft types), so it is unit
 * tested directly; the {@link RecipeBookType} set is just client-side bookkeeping touched only on the
 * render thread.
 */
public final class PartialFilterState {

    private PartialFilterState() {}

    /** The three user-visible filter states, in cycle order. */
    public enum Mode {
        ALL,
        CRAFTABLE,
        PARTIAL
    }

    /**
     * The outcome of one filter-button click: the vanilla filtering flag to store, whether partial
     * mode is now active, and whether the button should render as "on" (state-triggered).
     */
    public record CycleResult(boolean vanillaFiltering, boolean partial, boolean buttonOn) {}

    private static final Set<RecipeBookType> PARTIAL_TYPES = EnumSet.noneOf(RecipeBookType.class);

    /**
     * Display ordering controls, applied to every filter mode. These are remembered across sessions by
     * {@link PartialUiState}; the buttons / keybinds in the recipe book mutate them via
     * {@link #cycleSort()} / {@link #toggleGroupByCraftability()}.
     */
    public static volatile RecipeBookSorter.SortMode sortMode = RecipeBookSorter.SortMode.DEFAULT;

    public static volatile boolean groupByCraftability = false;

    public static boolean isPartial(RecipeBookType type) {
        return PARTIAL_TYPES.contains(type);
    }

    public static void setPartial(RecipeBookType type, boolean partial) {
        if (partial) {
            PARTIAL_TYPES.add(type);
        } else {
            PARTIAL_TYPES.remove(type);
        }
    }

    /** Read-only view of the book types currently in partial mode (used by {@link PartialUiState}). */
    public static Set<RecipeBookType> partialTypes() {
        return java.util.Collections.unmodifiableSet(PARTIAL_TYPES);
    }

    /** Advance the sort mode (DEFAULT &harr; ALPHABETICAL). */
    public static void cycleSort() {
        sortMode = RecipeBookSorter.next(sortMode);
    }

    /** Toggle craftability grouping on/off. */
    public static void toggleGroupByCraftability() {
        groupByCraftability = !groupByCraftability;
    }

    /** Resolve the current {@link Mode} from vanilla's filtering flag and our partial bookkeeping. */
    public static Mode modeOf(boolean vanillaFiltering, boolean partial) {
        if (partial) {
            return Mode.PARTIAL;
        }
        return vanillaFiltering ? Mode.CRAFTABLE : Mode.ALL;
    }

    /**
     * Advance the filter one step on a button click. Pure function of the current state and whether
     * the partial feature is enabled — the Mixin feeds in vanilla's current filtering flag and our
     * stored partial flag, applies the result, and uses {@link CycleResult#buttonOn()} to drive the
     * button visual.
     *
     * <p>With the partial feature enabled the cycle is {@code ALL -> CRAFTABLE -> PARTIAL -> ALL}.
     * With it disabled this degrades to the vanilla two-state toggle ({@code ALL <-> CRAFTABLE}),
     * leaving partial off.
     */
    public static CycleResult cycle(
            boolean currentVanillaFiltering, boolean currentPartial, boolean partialFeatureEnabled) {
        if (!partialFeatureEnabled) {
            boolean next = !currentVanillaFiltering;
            return new CycleResult(next, false, next);
        }
        Mode current = modeOf(currentVanillaFiltering, currentPartial);
        return switch (current) {
            case ALL -> new CycleResult(true, false, true); // -> CRAFTABLE
            case CRAFTABLE -> new CycleResult(false, true, true); // -> PARTIAL
            case PARTIAL -> new CycleResult(false, false, false); // -> ALL
        };
    }
}
