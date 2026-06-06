package com.partiallycraftablerecipes.mixin;

import com.partiallycraftablerecipes.MissingIngredient;
import com.partiallycraftablerecipes.PartialConfig;
import com.partiallycraftablerecipes.PartialCraftingScore;
import com.partiallycraftablerecipes.PartialFilterState;
import com.partiallycraftablerecipes.PartialRecipeAnalyzer;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-recipe visual treatment for the partial filter: a distinct tint on partially-craftable recipe
 * buttons and extra tooltip lines naming the ingredients still needed.
 *
 * <p>Both hooks only do anything while the book is in partial mode (and the feature is enabled), so
 * vanilla rendering and tooltips are untouched in the normal "all" / "craftable" modes. A recipe is
 * treated as partial here using the same rule as the collection filter: it must be the currently
 * displayed recipe, <em>not</em> fully craftable per the game's own check
 * ({@link RecipeCollection#isCraftable}), and have at least {@code minMatchedIngredients} ingredient
 * slots covered by the inventory.
 */
@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {

    @Shadow
    private RecipeCollection collection;

    @Shadow
    private RecipeBookMenu<?, ?> menu;

    @Shadow
    public abstract RecipeHolder<?> getRecipe();

    // Inherited from AbstractWidget (RecipeButton's superclass); shadowed so we can position the tint.
    @Shadow
    public abstract int getX();

    @Shadow
    public abstract int getY();

    /** Tint the button when it is showing a partially-craftable recipe. */
    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void pcr$tint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!pcr$partialActive()) {
            return;
        }
        if (pcr$partialScore() == null) {
            return;
        }
        int x = this.getX();
        int y = this.getY();
        int color = 0xFFFFC400; // amber
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 250.0F); // above the rendered item
        // 1px border around the 25x25 slot — distinct, without hiding the item.
        graphics.fill(x, y, x + 25, y + 1, color);
        graphics.fill(x, y + 24, x + 25, y + 25, color);
        graphics.fill(x, y, x + 1, y + 25, color);
        graphics.fill(x + 24, y, x + 25, y + 25, color);
        graphics.pose().popPose();
    }

    /** Append "Partially craftable", a progress line, and the missing-ingredient list to the tooltip. */
    @Inject(method = "getTooltipText", at = @At("RETURN"))
    private void pcr$tooltip(CallbackInfoReturnable<List<Component>> cir) {
        if (!pcr$partialActive()) {
            return;
        }
        PartialCraftingScore score = pcr$partialScore();
        if (score == null) {
            return;
        }
        List<Component> lines = cir.getReturnValue();
        lines.add(Component.translatable("gui.partiallycraftablerecipes.partially_craftable")
                .withStyle(ChatFormatting.GOLD));
        if (PartialConfig.showMissingIngredientsTooltip) {
            lines.add(Component.translatable(
                            "gui.partiallycraftablerecipes.progress", score.satisfiedSlots(), score.totalSlots())
                    .withStyle(ChatFormatting.GRAY));
            if (!score.missing().isEmpty()) {
                lines.add(Component.translatable("gui.partiallycraftablerecipes.missing")
                        .withStyle(ChatFormatting.GRAY));
                for (MissingIngredient missing : score.missing()) {
                    ItemStack stack = StackedContents.fromStackingIndex(missing.itemId());
                    if (stack.isEmpty()) {
                        continue;
                    }
                    lines.add(Component.literal(" - ")
                            .append(stack.getHoverName())
                            .append(Component.literal(" x" + missing.count()))
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    /** Whether partial mode is enabled and active for this button's book type. */
    @Unique
    private boolean pcr$partialActive() {
        return PartialConfig.enablePartialCraftableFilter
                && this.menu != null
                && PartialFilterState.isPartial(this.menu.getRecipeBookType());
    }

    /**
     * The partial score for the recipe this button currently shows, or {@code null} if it is not
     * partially craftable (fully craftable, special/skipped, or below the matched-ingredient minimum).
     */
    @Unique
    private PartialCraftingScore pcr$partialScore() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || this.collection == null) {
            return null;
        }
        RecipeHolder<?> recipe = this.getRecipe();
        if (recipe == null || this.collection.isCraftable(recipe)) {
            return null; // fully craftable -> not partial
        }
        StackedContents contents = new StackedContents();
        minecraft.player.getInventory().fillStackedContents(contents);
        this.menu.fillCraftSlotsStackedContents(contents);
        Map<Integer, Integer> availability = PartialRecipeAnalyzer.availabilityOf(contents);
        PartialCraftingScore score = PartialRecipeAnalyzer.score(recipe, availability);
        if (score == null || score.satisfiedSlots() < Math.max(1, PartialConfig.minMatchedIngredients)) {
            return null;
        }
        return score;
    }
}
