# Partially Craftable Recipes

A client-side **NeoForge** quality-of-life mod for **Minecraft Java 1.21.1** that adds a third
filter state to the vanilla recipe book: **partially craftable** recipes.

Vanilla's recipe book lets you show *all* recipes or *only fully craftable* ones. This mod adds a
useful middle ground — recipes you can *almost* make — without replacing the vanilla recipe book.

## What it does

- The recipe-book filter button now **cycles through three states** instead of two:
  1. **All** unlocked recipes (vanilla)
  2. **Craftable** only (vanilla)
  3. **Partially craftable** (new)
- A recipe is **partially craftable** when you have **at least one** required ingredient but **not
  enough to fully craft it**. (Configurable minimum; default: at least one matching ingredient.)
- Partial recipes get a **distinct amber border**, and their tooltip shows:
  - **Partially craftable**
  - **2/4 ingredients available** (progress)
  - **Missing:** a list of the items you still need, e.g. `- Egg x1`, `- Milk Bucket x1`
- In partial mode, recipes are **sorted closest-to-craftable first** (most ingredients satisfied
  first; ties broken by fewest missing).

Fully craftable recipes never show up in the partial filter, and recipes you have *no* ingredients
for don't either. The vanilla "all" and "craftable" modes are completely unchanged.

## How it handles tricky recipes

- **Shaped and shapeless** crafting recipes are both supported.
- **Tags / alternatives** (e.g. "any plank") are matched against every accepted item.
- **Ingredient counts** are respected — a recipe needing two planks needs two in your inventory.
- **Container items** (milk bucket, etc.) need no special handling: checking only ever *reads* your
  inventory, it never consumes or moves anything.
- **Special / custom recipes** (fireworks, modded custom ingredients, empty tags) are skipped
  conservatively rather than guessed at, so the book never crashes on an exotic recipe.
- **Locked recipes** you haven't unlocked are never shown (the mod only considers recipes vanilla
  already counts as known and grid-fitting).

## Config

`config/partiallycraftablerecipes-client.toml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enablePartialCraftableFilter` | `true` | Master switch. Off → the filter button is a plain vanilla two-state toggle and nothing else runs. |
| `showMissingIngredientsTooltip` | `true` | Show the "Partially craftable" / progress / missing-ingredient lines on hover. |
| `sortPartialRecipesByClosest` | `true` | Sort the partial list closest-to-craftable first. |
| `minMatchedIngredients` | `1` | How many ingredient slots you must already have for a recipe to count as partial. |

## Build

Needs **JDK 21**. From the project root:

```bat
gradlew.bat build
```

This formats (Spotless / palantir-java-format), compiles, runs the JUnit suite, and produces
`build/libs/partiallycraftablerecipes-<version>.jar`. `gradlew.bat test` runs just the tests.

## In-game test checklist (not yet verified)

The pure logic is unit-tested, but the in-game mixin wiring still needs a manual pass:

1. Survival, open a **crafting table**. Put *some* ingredients for a recipe in your inventory (e.g.
   wheat + sugar but no egg/milk for a cake).
2. Click the recipe-book filter button and confirm it cycles **All → Craftable → Partial → All**,
   with the "Partially craftable" tooltip and the amber marker in partial mode.
3. In **Partial** mode: the cake shows up with an amber border; hovering it lists the missing egg and
   milk; fully craftable recipes are **absent**; recipes you have nothing for are **absent**.
4. Confirm the vanilla **All** and **Craftable** modes look and behave exactly as before.
5. Try it with tag recipes (planks/stairs) and a few modded recipes — no crashes, sensible results.

## License

Apache-2.0. See [LICENSE](LICENSE).
