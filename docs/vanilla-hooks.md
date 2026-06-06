# Vanilla hook points — recipe book (Mojmaps 1.21.1)

Written in our own words from the decompiled **Minecraft + NeoForge** sources so you usually don't
need to re-decompile. Do **not** vendor the decompiled sources into this repo (proprietary, large,
reproducible). To grep them locally, the moddev cache holds a sources jar at:

```
~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*.jar
```

(Unzip the one whose listing contains `RecipeBookComponent.java`.) IDE "go to definition" also
resolves these after a Gradle sync.

## The vanilla two-state filter

The vanilla recipe book has exactly two filter states, stored as a single boolean per
`RecipeBookType` inside `RecipeBookSettings` (`RecipeBook#isFiltering` / `#setFiltering`):

- **filtering = false** → "show all" (every known recipe that fits the grid),
- **filtering = true** → "show only craftable".

This mod adds a third state ("partially craftable") on top, tracked client-side in
`PartialFilterState` (a small set of book types currently in partial mode). It is **not** persisted to
the server — `sendUpdateSettings` keeps sending the vanilla boolean (false while in partial mode).

## How vanilla decides what to display

| Vanilla symbol | Class | What it does / why we hook near it |
| --- | --- | --- |
| `updateCollections(boolean)` | `RecipeBookComponent` | Builds the displayed list: starts from `book.getCollection(tab)`, calls `RecipeCollection#canCraft` on each, drops not-known / not-fitting / not-search-matching collections, and if `isFiltering` drops not-craftable ones. Ends by calling `recipeBookPage.updateCollections(list, reset)`. **We `@Redirect` that final call** and, in partial mode, swap in our filtered+sorted list. |
| `RecipeBookPage#updateCollections(List, boolean)` | `RecipeBookPage` | Receives the final collection list and paginates it into 20 `RecipeButton`s. Our redirect target. |
| `toggleFiltering()` | `RecipeBookComponent` | Flips the vanilla boolean and returns the new value (used for `filterButton.setStateTriggered`). Called once from `mouseClicked` when the filter button is clicked. **We `@Redirect` that call** to run the tri-state cycle instead. |
| `getRecipeFilterName()` | `RecipeBookComponent` | The tooltip shown when the filter button is "on" (vanilla: "Only show craftable"). **We `@Inject` HEAD** to return "Partially craftable" in partial mode. |
| `initVisuals()` | `RecipeBookComponent` | Rebuilds widgets on open; constructs `filterButton` from `isFiltering`. **We `@Inject` TAIL** to re-assert the button's "on" look + tooltip when reopening in partial mode. |
| `render(GuiGraphics,int,int,float)` | `RecipeBookComponent` | Draws the book (inside a `z+100` pose that is popped before the method returns). **We `@Inject` TAIL** to draw a small marker over the filter button at `z+300` so it sits above the panel. |

## How a recipe's craftability is known

| Vanilla symbol | Class | Notes |
| --- | --- | --- |
| `RecipeCollection#canCraft(StackedContents, gridW, gridH, RecipeBook)` | `RecipeCollection` | Populates the collection's `fitsDimensions` (fits grid **and** `book.contains` = known) and `craftable` (fits **and** `StackedContents#canCraft`) sets. Called by `updateCollections` before our redirect, so both sets are fresh when we read them. |
| `RecipeCollection#getRecipes(false)` / `getRecipes(true)` | `RecipeCollection` | `false` → the `fitsDimensions` recipes (known + fit), `true` → the `craftable` recipes. We treat **fitting − craftable** as partial candidates. |
| `RecipeCollection#isCraftable(RecipeHolder)` | `RecipeCollection` | Authoritative "fully craftable" check used by `RecipeButtonMixin` to never tint/annotate a craftable recipe as partial. |
| `StackedContents.contents` (`Int2IntMap`) | `StackedContents` | `stackingId → count` of available items. We snapshot it (positive counts only) into the matcher's availability map. `stackingId = BuiltInRegistries.ITEM.getId(item)`. |
| `StackedContents#canCraft(Recipe, IntList)` | `StackedContents` | Vanilla's exact (bipartite-matching) craftability check — the source of truth for "fully craftable". Our matcher is only a *closeness* heuristic and never overrides this. |
| `Ingredient#getStackingIds()` | `Ingredient` | Sorted `IntList` of every item id the ingredient accepts (a tag/alternatives ingredient yields many). This is exactly the per-slot accept-set our matcher needs. `isEmpty()` cells are skipped; `isCustom()` / `hasNoItems()` recipes are skipped conservatively. |
| `StackedContents.fromStackingIndex(int)` | `StackedContents` | Turns a stacking id back into an `ItemStack` — used to render missing-ingredient names in the tooltip. |
| `RecipeBookMenu#fillCraftSlotsStackedContents(StackedContents)` | `RecipeBookMenu` | Adds the loaded crafting grid's items to a `StackedContents`, matching what the component itself counts (inventory + grid). |

## Conventions / gotchas

- **No refmap.** Dev and production both run Mojmaps, so `mixins.json` omits a refmap; mixin member
  names/descriptors must match Mojmaps exactly.
- **Read-only.** Nothing here consumes or moves items — we only read availability, so checking partial
  craftability can never dupe/lose items or desync the server. The mod is fully client-side.
- **Greedy vs. exact.** `RecipePartialMatcher` is a greedy heuristic for the closeness score and the
  missing list. "Fully craftable" is always decided by vanilla (`isCraftable` / `canCraft`), so the
  heuristic can never promote a craftable recipe into the partial filter or vice-versa.
- **Behavior is unverified in-game.** Unit tests cover the pure logic; the mixin wiring (button cycle,
  filtering, tint, tooltip) still needs the manual in-game pass in the README.
