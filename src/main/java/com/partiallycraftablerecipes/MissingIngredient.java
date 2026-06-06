package com.partiallycraftablerecipes;

/**
 * A simple (item, count) tally expressed purely as a stacking item id and a count. Its primary use is
 * the <em>missing</em> ingredient list (hence the name), but {@link PartialCraftingScore} reuses the
 * same shape for the <em>present</em> list — the items the player already has that cover satisfied
 * slots. "Stacking id" is the integer the game uses to bucket items in {@code StackedContents}
 * ({@code BuiltInRegistries.ITEM.getId(item)}); keeping the type primitive here is what lets the
 * matching logic stay free of Minecraft classes and therefore unit-testable.
 *
 * <p>The id is a <em>representative</em>: an ingredient slot may accept several items (e.g. any
 * plank), so {@link #itemId()} is the slot's first acceptable item — enough to render a "Missing:
 * Oak Planks x2"-style line. The UI layer ({@code PartialRecipeAnalyzer} / the Mixins) is what turns
 * the id back into an {@code ItemStack} and a display name; this class never touches the registry.
 *
 * @param itemId stacking id of a representative missing item ({@code 0} = unknown / empty)
 * @param count how many of that item are still required
 */
public record MissingIngredient(int itemId, int count) {

    public MissingIngredient {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, was " + count);
        }
    }

    /** A copy of this entry with {@code extra} more of the same item required. */
    public MissingIngredient plus(int extra) {
        return new MissingIngredient(this.itemId, this.count + extra);
    }
}
