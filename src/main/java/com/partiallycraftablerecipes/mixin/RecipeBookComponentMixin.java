package com.partiallycraftablerecipes.mixin;

import com.partiallycraftablerecipes.PartialConfig;
import com.partiallycraftablerecipes.PartialCraftingScore;
import com.partiallycraftablerecipes.PartialFilterState;
import com.partiallycraftablerecipes.PartialKeyBindings;
import com.partiallycraftablerecipes.PartialRecipeAnalyzer;
import com.partiallycraftablerecipes.PartialUiState;
import com.partiallycraftablerecipes.RecipeBookSorter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the vanilla recipe book with a third filter state ("partially craftable") and adds
 * display-ordering controls (sort mode + craftability grouping), without replacing vanilla behavior.
 *
 * <p><b>Filter.</b> Vanilla stores one boolean per book type ("filtering" = craftable-only). We layer
 * a third state on top (tracked in {@link PartialFilterState}) by redirecting {@code toggleFiltering()}
 * into a tri-state cycle (All &rarr; Craftable &rarr; Partial), and by redirecting the
 * {@code RecipeBookPage.updateCollections(list, reset)} call to filter the collection list in partial
 * mode (craftable &sub; partial &sub; all).
 *
 * <p><b>Ordering.</b> That same redirect also applies the user's sort mode (default / alphabetical)
 * and optional craftability grouping to <em>every</em> mode, via the pure {@link RecipeBookSorter}.
 * Two small buttons drawn in the book footer (and two rebindable keybinds, see
 * {@link PartialKeyBindings}) toggle these; the state is remembered across sessions by
 * {@link PartialUiState}.
 *
 * <p>Everything is gated by {@link PartialConfig#enablePartialCraftableFilter}; with it off the book
 * behaves exactly like vanilla.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Unique
    private static final int PCR_BUTTON_SIZE = 14;

    @Shadow
    protected RecipeBookMenu<?, ?> menu;

    @Shadow
    private ClientRecipeBook book;

    @Shadow
    private StackedContents stackedContents;

    @Shadow
    protected StateSwitchingButton filterButton;

    @Shadow
    private EditBox searchBox;

    @Shadow
    public abstract boolean isVisible();

    @Shadow
    protected abstract void updateFilterButtonTooltip();

    @Shadow
    protected abstract void updateCollections(boolean resetPagePosition);

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
        PartialUiState.save(); // remember the filter mode across sessions
        return result.buttonOn();
    }

    /**
     * Transform the collection list before it reaches the page: in partial mode, filter to craftable +
     * partial collections; then, in every mode, apply the user's sort/grouping. With the feature off
     * the list is forwarded untouched so vanilla behavior is preserved exactly.
     */
    @Redirect(
            method = "updateCollections",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;Z)V"))
    private void pcr$filterAndSort(RecipeBookPage page, List<RecipeCollection> list, boolean resetPagePosition) {
        if (PartialConfig.enablePartialCraftableFilter) {
            if (PartialFilterState.isPartial(this.menu.getRecipeBookType())) {
                list = pcr$collectPartial(list);
            }
            list = pcr$applyView(list);
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

    /** Draw the partial-mode marker on the filter button and the two sort/group buttons in the footer. */
    @Inject(method = "render", at = @At("TAIL"))
    private void pcr$renderOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!PartialConfig.enablePartialCraftableFilter || this.filterButton == null || !this.isVisible()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F); // sit above the recipe-book panel

        if (PartialFilterState.isPartial(this.menu.getRecipeBookType())) {
            int fx = this.filterButton.getX();
            int fy = this.filterButton.getY();
            graphics.fill(fx + 1, fy + 1, fx + 5, fy + 5, 0xFFFFC400); // amber corner marker
        }

        boolean alphabetical = PartialFilterState.sortMode == RecipeBookSorter.SortMode.ALPHABETICAL;
        pcr$drawButton(graphics, 0, alphabetical ? "A" : "#", alphabetical, mouseX, mouseY);
        pcr$drawButton(graphics, 1, "G", PartialFilterState.groupByCraftability, mouseX, mouseY);

        graphics.pose().popPose();
    }

    /** Tooltips for the two footer buttons. */
    @Inject(method = "renderTooltip", at = @At("TAIL"))
    private void pcr$renderButtonTooltips(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        if (!PartialConfig.enablePartialCraftableFilter || this.filterButton == null || !this.isVisible()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        if (pcr$inButton(mouseX, mouseY, 0)) {
            graphics.renderTooltip(font, pcr$sortTooltip(), mouseX, mouseY);
        } else if (pcr$inButton(mouseX, mouseY, 1)) {
            graphics.renderTooltip(font, pcr$groupTooltip(), mouseX, mouseY);
        }
    }

    /** Handle clicks on the footer buttons before vanilla processes the click. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void pcr$buttonClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!PartialConfig.enablePartialCraftableFilter
                || this.filterButton == null
                || !this.isVisible()
                || button != 0) {
            return;
        }
        if (pcr$inButton(mouseX, mouseY, 0)) {
            PartialFilterState.cycleSort();
            pcr$onControlChanged();
            cir.setReturnValue(true);
        } else if (pcr$inButton(mouseX, mouseY, 1)) {
            PartialFilterState.toggleGroupByCraftability();
            pcr$onControlChanged();
            cir.setReturnValue(true);
        }
    }

    /** Handle the rebindable sort keybinds while the book is open (key mappings don't auto-fire here). */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void pcr$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!PartialConfig.enablePartialCraftableFilter || !this.isVisible()) {
            return;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return; // don't steal typing in the search box
        }
        if (PartialKeyBindings.cycleSort != null && PartialKeyBindings.cycleSort.matches(keyCode, scanCode)) {
            PartialFilterState.cycleSort();
            pcr$onControlChanged();
            cir.setReturnValue(true);
        } else if (PartialKeyBindings.toggleGrouping != null
                && PartialKeyBindings.toggleGrouping.matches(keyCode, scanCode)) {
            PartialFilterState.toggleGroupByCraftability();
            pcr$onControlChanged();
            cir.setReturnValue(true);
        }
    }

    /** Persist the changed control, refresh the displayed list, and play the vanilla button click. */
    @Unique
    private void pcr$onControlChanged() {
        PartialUiState.save();
        this.updateCollections(false);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // --- view ordering --------------------------------------------------------------------------

    /**
     * Apply the active sort mode and grouping to {@code collections}. Returns the list unchanged when
     * nothing would reorder it (default sort, grouping off), so the common case costs nothing.
     */
    @Unique
    private List<RecipeCollection> pcr$applyView(List<RecipeCollection> collections) {
        RecipeBookSorter.SortMode mode = PartialFilterState.sortMode;
        boolean group = PartialFilterState.groupByCraftability;
        boolean searchActive = pcr$searchActive();
        boolean alphabetical = mode == RecipeBookSorter.SortMode.ALPHABETICAL && !searchActive;
        if (!group && !alphabetical) {
            return collections;
        }

        Map<Integer, Integer> availability = group ? PartialRecipeAnalyzer.availabilityOf(this.stackedContents) : null;
        int min = Math.max(1, PartialConfig.minMatchedIngredients);

        List<RecipeBookSorter.Entry> entries = new ArrayList<>(collections.size());
        for (int idx = 0; idx < collections.size(); idx++) {
            RecipeCollection collection = collections.get(idx);
            String name = alphabetical ? pcr$sortName(collection) : "";
            int rank = group ? pcr$craftRank(collection, availability, min) : 0;
            entries.add(new RecipeBookSorter.Entry(idx, name, rank));
        }

        List<RecipeBookSorter.Entry> sorted = RecipeBookSorter.sort(entries, mode, group, searchActive);
        List<RecipeCollection> result = new ArrayList<>(collections.size());
        for (RecipeBookSorter.Entry entry : sorted) {
            result.add(collections.get(entry.index()));
        }
        return result;
    }

    @Unique
    private boolean pcr$searchActive() {
        return this.searchBox != null && !this.searchBox.getValue().isBlank();
    }

    /** The display name of a collection's output item, for alphabetical sorting. */
    @Unique
    private String pcr$sortName(RecipeCollection collection) {
        try {
            List<RecipeHolder<?>> recipes = collection.getRecipes();
            if (recipes.isEmpty()) {
                return "";
            }
            return recipes.get(0)
                    .value()
                    .getResultItem(collection.registryAccess())
                    .getHoverName()
                    .getString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** 0 = craftable, 1 = partially craftable, 2 = uncraftable. */
    @Unique
    private int pcr$craftRank(RecipeCollection collection, Map<Integer, Integer> availability, int min) {
        if (collection.hasCraftable()) {
            return 0;
        }
        for (RecipeHolder<?> recipe : collection.getRecipes(false)) {
            PartialCraftingScore score = PartialRecipeAnalyzer.score(recipe, availability);
            if (score != null && score.satisfiedSlots() >= min) {
                return 1;
            }
        }
        return 2;
    }

    // --- partial filtering ----------------------------------------------------------------------

    /**
     * Filter {@code collections} to those with a craftable or partially-craftable recipe, ordered
     * closest-to-craftable first (when {@code sortPartialRecipesByClosest}). The user's sort/grouping
     * is applied afterwards by {@link #pcr$applyView}.
     */
    @Unique
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
    @Unique
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

    // --- footer buttons -------------------------------------------------------------------------

    /** Left edge of footer button {@code which} (0 = sort, 1 = group), derived from the filter button. */
    @Unique
    private int pcr$buttonX(int which) {
        return (this.filterButton.getX() - 110) + 107 + which * (PCR_BUTTON_SIZE + 2);
    }

    @Unique
    private int pcr$buttonY() {
        return (this.filterButton.getY() - 12) + 137;
    }

    @Unique
    private boolean pcr$inButton(double mouseX, double mouseY, int which) {
        int x = pcr$buttonX(which);
        int y = pcr$buttonY();
        return mouseX >= x && mouseX < x + PCR_BUTTON_SIZE && mouseY >= y && mouseY < y + PCR_BUTTON_SIZE;
    }

    @Unique
    private void pcr$drawButton(GuiGraphics graphics, int which, String label, boolean active, int mouseX, int mouseY) {
        int x = pcr$buttonX(which);
        int y = pcr$buttonY();
        boolean hovered = pcr$inButton(mouseX, mouseY, which);

        graphics.fill(x, y, x + PCR_BUTTON_SIZE, y + PCR_BUTTON_SIZE, 0xFF8B8B8B); // border
        int inner = hovered ? 0xFF6A6A6A : 0xFF313131;
        graphics.fill(x + 1, y + 1, x + PCR_BUTTON_SIZE - 1, y + PCR_BUTTON_SIZE - 1, inner);
        if (active) {
            graphics.fill(x + 1, y + 1, x + PCR_BUTTON_SIZE - 1, y + 2, 0xFF55FF55); // green "on" bar
        }

        Font font = Minecraft.getInstance().font;
        int textColor = active ? 0xFF7CFF7C : 0xFFFFFFFF;
        graphics.drawString(font, label, x + (PCR_BUTTON_SIZE - font.width(label)) / 2 + 1, y + 3, textColor);
    }

    @Unique
    private Component pcr$sortTooltip() {
        String key = PartialFilterState.sortMode == RecipeBookSorter.SortMode.ALPHABETICAL
                ? "gui.partiallycraftablerecipes.sort.alphabetical"
                : "gui.partiallycraftablerecipes.sort.default";
        return Component.translatable(key);
    }

    @Unique
    private Component pcr$groupTooltip() {
        String key = PartialFilterState.groupByCraftability
                ? "gui.partiallycraftablerecipes.group.on"
                : "gui.partiallycraftablerecipes.group.off";
        return Component.translatable(key);
    }
}
