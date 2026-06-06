package com.partiallycraftablerecipes;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Rebindable key mappings for the recipe-book sort controls. They mirror the in-book buttons: one
 * cycles the sort mode, one toggles craftability grouping.
 *
 * <p>Both default to <em>unbound</em> ({@code GLFW_KEY_UNKNOWN}) so they can never clash with another
 * mod's keys out of the box — the user binds them in Options &rarr; Controls if they want them. They
 * use {@link KeyConflictContext#GUI} because they are only meaningful while the recipe book is open;
 * the actual handling lives in {@code RecipeBookComponentMixin#keyPressed}, which compares the pressed
 * key against these mappings (key mappings do not auto-fire while a screen is open).
 */
public final class PartialKeyBindings {

    private PartialKeyBindings() {}

    private static final String CATEGORY = "key.categories.partiallycraftablerecipes";

    public static KeyMapping cycleSort;
    public static KeyMapping toggleGrouping;

    public static void register(RegisterKeyMappingsEvent event) {
        cycleSort = new KeyMapping(
                "key.partiallycraftablerecipes.cycle_sort",
                KeyConflictContext.GUI,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY);
        toggleGrouping = new KeyMapping(
                "key.partiallycraftablerecipes.toggle_grouping",
                KeyConflictContext.GUI,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY);
        event.register(cycleSort);
        event.register(toggleGrouping);
    }
}
