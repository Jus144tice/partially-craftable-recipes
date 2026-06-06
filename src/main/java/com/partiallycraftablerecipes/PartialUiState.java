package com.partiallycraftablerecipes;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.StringJoiner;
import net.minecraft.world.inventory.RecipeBookType;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Persists the recipe-book view state that the user toggles in-game — the sort mode, the
 * group-by-craftability flag, and which book types are in partial mode — so they are restored the
 * next time the book is opened, even across game restarts.
 *
 * <p>This is deliberately a tiny self-managed {@code .properties} file in the config directory rather
 * than a {@link PartialConfig} entry: these values change on a button click / keypress (not by hand
 * editing), and writing our own file gives reliable, immediate persistence under our control. The
 * feature flags that the user <em>does</em> edit by hand stay in {@link PartialConfig}.
 *
 * <p>All I/O is best-effort and fully guarded — a missing or corrupt file just means "use defaults",
 * and nothing here can throw into the game.
 */
public final class PartialUiState {

    private PartialUiState() {}

    private static final String FILE_NAME = "partiallycraftablerecipes-view.properties";
    private static final String KEY_SORT = "sortMode";
    private static final String KEY_GROUP = "groupByCraftability";
    private static final String KEY_PARTIAL = "partialBookTypes";

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /** Load saved view state into {@link PartialFilterState}. No-op if the file is missing/unreadable. */
    public static void load() {
        Path path = file();
        if (!Files.isReadable(path)) {
            return;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            props.load(reader);
        } catch (IOException | RuntimeException e) {
            PartiallyCraftableRecipes.LOGGER.debug("Could not read view state; using defaults", e);
            return;
        }

        PartialFilterState.sortMode = parseSortMode(props.getProperty(KEY_SORT));
        PartialFilterState.groupByCraftability = Boolean.parseBoolean(props.getProperty(KEY_GROUP, "false"));

        String partial = props.getProperty(KEY_PARTIAL, "");
        for (String token : partial.split(",")) {
            RecipeBookType type = parseBookType(token.trim());
            if (type != null) {
                PartialFilterState.setPartial(type, true);
            }
        }
    }

    /** Write the current {@link PartialFilterState} to disk. Best-effort; failures are logged at debug. */
    public static void save() {
        Properties props = new Properties();
        props.setProperty(KEY_SORT, PartialFilterState.sortMode.name());
        props.setProperty(KEY_GROUP, Boolean.toString(PartialFilterState.groupByCraftability));

        StringJoiner joiner = new StringJoiner(",");
        for (RecipeBookType type : PartialFilterState.partialTypes()) {
            joiner.add(type.name());
        }
        props.setProperty(KEY_PARTIAL, joiner.toString());

        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                props.store(writer, "Partially Craftable Recipes — remembered recipe-book view state");
            }
        } catch (IOException | RuntimeException e) {
            PartiallyCraftableRecipes.LOGGER.debug("Could not save view state", e);
        }
    }

    private static RecipeBookSorter.SortMode parseSortMode(String raw) {
        if (raw != null) {
            try {
                return RecipeBookSorter.SortMode.valueOf(raw);
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return RecipeBookSorter.SortMode.DEFAULT;
    }

    private static RecipeBookType parseBookType(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return RecipeBookType.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
