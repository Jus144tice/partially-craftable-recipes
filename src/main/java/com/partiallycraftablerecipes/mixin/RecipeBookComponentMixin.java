package com.partiallycraftablerecipes.mixin;

import com.partiallycraftablerecipes.PartialConfig;
import com.partiallycraftablerecipes.PartialCraftingScore;
import com.partiallycraftablerecipes.PartialFilterState;
import com.partiallycraftablerecipes.PartialRecipeAnalyzer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the vanilla recipe book with a third filter state, "partially craftable", without
 * replacing any vanilla behavior.
 *
 * <p>Vanilla stores one boolean per book type ("filtering" = craftable-only). This mixin layers a
 * third state on top (tracked client-side in {@link PartialFilterState}) and hooks four points:
 *
 * <ol>
 *   <li><b>{@code toggleFiltering()}</b> — redirected so the filter button cycles
 *       All &rarr; Craftable &rarr; Partial &rarr; All instead of toggling two states.
 *   <li><b>{@code RecipeBookPage.updateCollections(list, reset)}</b> — redirected so that, in partial
 *       mode, the displayed collections are filtered to those holding a craftable <em>or</em>
 *       partially-craftable recipe and sorted closest-to-craftable first. Partial mode is a superset
 *       of craftable (craftable &sub; partial &sub; all). (In the other two modes the list is passed
 *       through untouched, so vanilla "all" / "craftable" filtering is unchanged.)
 *   <li><b>{@code getRecipeFilterName()}</b> — returns the "Partially craftable" toggle tooltip when
 *       partial mode is active.
 *   <li><b>{@code initVisuals()} / {@code render()}</b> — keep the filter button shown as "on" and draw
 *       a small marker so the partial state is visually distinct.
 * </ol>
 *
 * <p>Everything is gated by {@link PartialConfig#enablePartialCraftableFilter}; with it off the filter
 * button behaves exactly like vanilla's two-state toggle and none of the partial logic runs.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow
    protected RecipeBookMenu<?, ?> menu;

    @Shadow
    @SuppressWarnings("unused")
    private ClientRecipeBook book;

    @Shadow
    @SuppressWarnings("unused")
    private StackedContents stackedContents;

    @Shadow
    protected StateSwitchingButton filterButton;

    @Shadow
    public abstract boolean isVisible();

    @Shadow
    protected abstract void updateFilterButtonTooltip();

    /**
     * Replace the vanilla two-state filter toggle with the tri-state cycle. The boolean returned here
     * is what vanilla passes straight to {@code filterButton.setStateTriggered(...)}, so partial mode
     * shows the button as "on". Vanilla then refreshes the tooltip, sends the (vanilla) settings, and
     * re-runs {@code updateCollections}, which our other redirect turns into the partial view.
     */
    @Redirect(
            method = "mouseClicked",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;toggleFiltering()Z"))
    private boolean pcr$cycleFilter(RecipeBookComponent self) {
        RecipeBookType type = this.menu.getRecipeBookType();
        boolean currentFiltering = this.book.isFiltering(this.menu);
        boolean currentPartial = PartialFilterState.isPartial(type);
        PartialFilterState.CycleResult result =
                PartialFilterState.cycle(currentFiltering, currentPartial, PartialConfig.enablePartialCraftableFilter);
        this.book.setFiltering(type, result.vanillaFiltering());
        PartialFilterState.setPartial(type, result.partial());
        return result.buttonOn();
    }

    /**
     * In partial mode, transform the collection list before it reaches the page: keep only collections
     * that contain a partially-craftable recipe, ordered closest-to-craftable first. In the other two
     * modes the list is forwarded unchanged so vanilla behavior is preserved exactly.
     */
    @Redirect(
            method = "updateCollections",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void pcr$filterPartial(RecipeBookPage page, List<RecipeCollection> list, boolean resetPagePosition) {
        if (PartialConfig.enablePartialCraftableFilter && PartialFilterState.isPartial(this.menu.getRecipeBookType())) {
            list = pcr$collectPartial(list);
        }
        page.updateCollections(list, resetPagePosition);
    }

    /** Replace the toggle tooltip with "Partially craftable" while partial mode is active. */
    @Inject(method = "getRecipeFilterName", at = @At("HEAD"), cancellable = true)
    private void pcr$filterName(CallbackInfoReturnable<Component> cir) {
        if (PartialConfig.enablePartialCraftableFilter && PartialFilterState.isPartial(this.menu.getRecipeBookType())) {
            cir.setReturnValue(Component.translatable("gui.partiallycraftablerecipes.toggle.partial"));
        }
    }

    /** On (re)open, restore the button's "on" look and partial tooltip if this book is in partial mode. */
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void pcr$initVisuals(CallbackInfo ci) {
        if (PartialConfig.enablePartialCraftableFilter
                && this.filterButton != null
                && PartialFilterState.isPartial(this.menu.getRecipeBookType())) {
            this.filterButton.setStateTriggered(true);
            this.updateFilterButtonTooltip();
        }
    }

    /** Draw a small marker on the filter button so partial mode reads as visually distinct. */
    @Inject(method = "render", at = @At("TAIL"))
    private void pcr$renderMarker(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!PartialConfig.enablePartialCraftableFilter
                || this.filterButton == null
                || !this.filterButton.visible
                || !this.isVisible()
                || !PartialFilterState.isPartial(this.menu.getRecipeBookType())) {
            return;
        }
        int x = this.filterButton.getX();
        int y = this.filterButton.getY();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F); // sit above the recipe-book panel
        // small amber square in the top-left corner of the toggle
        graphics.fill(x + 1, y + 1, x + 5, y + 5, 0xFFFFC400);
        graphics.pose().popPose();
    }

    /**
     * Filter {@code collections} to those with at least one partially-craftable recipe, sorted (when
     * enabled) closest-to-craftable first using each collection's best partial score.
     */
    private List<RecipeCollection> pcr$collectPartial(List<RecipeCollection> collections) {
        Map<Integer, Integer> availability = PartialRecipeAnalyzer.availabilityOf(this.stackedContents);
        int minMatched = PartialConfig.minMatchedIngredients;

        record Scored(RecipeCollection collection, PartialCraftingScore score) {}
        List<Scored> scored = new ArrayList<>();
        for (RecipeCollection collection : collections) {
            PartialCraftingScore best = pcr$bestPartialScore(collection, availability, minMatched);
            if (best != null) {
                scored.add(new Scored(collection, best));
            }
        }

        if (PartialConfig.sortPartialRecipesByClosest) {
            scored.sort(Comparator.comparing(Scored::score, PartialCraftingScore.CLOSEST_FIRST));
        }

        List<RecipeCollection> result = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            result.add(s.collection());
        }
        return result;
    }

    /**
     * The best (closest-to-craftable) score among a collection's recipes, or {@code null} if none
     * qualify for the partial filter. The partial filter is a <em>superset</em> of craftable
     * (craftable &sub; partial &sub; all), so a recipe qualifies when it fits the grid and is known
     * (vanilla restricts {@code getRecipes(false)} to those) and either is fully craftable per the
     * game's own check <em>or</em> has at least {@code minMatched} ingredient slots the inventory can
     * cover. Fully craftable recipes score all-slots-satisfied, so they sort to the top.
     */
    private PartialCraftingScore pcr$bestPartialScore(
            RecipeCollection collection, Map<Integer, Integer> availability, int minMatched) {
        List<RecipeHolder<?>> fitting = collection.getRecipes(false); // fits dimensions + known (incl. craftable)
        if (fitting.isEmpty()) {
            return null;
        }
        Set<RecipeHolder<?>> craftable = new HashSet<>(collection.getRecipes(true)); // fully craftable subset
        int min = Math.max(1, minMatched);

        PartialCraftingScore best = null;
        for (RecipeHolder<?> recipe : fitting) {
            PartialCraftingScore score = PartialRecipeAnalyzer.score(recipe, availability);
            if (score == null) {
                continue;
            }
            // Always keep fully craftable recipes (subset rule); keep the rest once they reach the
            // matched-ingredient minimum.
            if (!craftable.contains(recipe) && score.satisfiedSlots() < min) {
                continue;
            }
            if (best == null || PartialCraftingScore.CLOSEST_FIRST.compare(score, best) < 0) {
                best = score;
            }
        }
        return best;
    }
}
