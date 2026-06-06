package com.partiallycraftablerecipes;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.slf4j.Logger;

/**
 * Entry point for Partially Craftable Recipes.
 *
 * <p>A <strong>client-only</strong> mod ({@code dist = Dist.CLIENT}). It registers a single client
 * config and bakes its values into plain fields on {@link PartialConfig} whenever the config loads or
 * reloads. All of the actual behavior lives in the client Mixins
 * {@code com.partiallycraftablerecipes.mixin.RecipeBookComponentMixin} and {@code RecipeButtonMixin}.
 */
@Mod(value = PartiallyCraftableRecipes.MOD_ID, dist = Dist.CLIENT)
public final class PartiallyCraftableRecipes {

    public static final String MOD_ID = "partiallycraftablerecipes";

    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Flip to {@code true} (and run with debug logging enabled) for verbose traces around the
     * partial-filter computation. Kept {@code false} so runtime logging stays quiet by default.
     */
    public static final boolean DEBUG = false;

    public PartiallyCraftableRecipes(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, PartialConfig.SPEC);

        // ModConfigEvent is abstract and never posted directly, so we listen for both concrete
        // subtypes. Each fires once the config file is read so we can cache its values.
        modBus.addListener((ModConfigEvent.Loading event) -> PartialConfig.onConfigEvent(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> PartialConfig.onConfigEvent(event));

        // Rebindable sort keybinds, registered on the mod event bus.
        modBus.addListener((RegisterKeyMappingsEvent event) -> PartialKeyBindings.register(event));

        // Restore the remembered recipe-book view state (sort mode, grouping, partial mode).
        PartialUiState.load();

        LOGGER.info("Partially Craftable Recipes loaded (client-only).");
    }
}
