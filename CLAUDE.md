# CLAUDE.md — Partially Craftable Recipes

Navigation map for AI code-assist tools. Anchors are **symbol names** (classes, methods, fields,
config keys, Gradle task names), not line numbers — search for the anchor to jump to the code.

## ⚠️ Mandate: keep this file self-updating

**This file MUST be updated in the same session as any change that affects it — never deferred to a
later session.** If, during a task, you do any of the following, update the relevant section of this
CLAUDE.md before finishing the task (treat it as part of the change, not optional follow-up):

- add / rename / move / delete a file, class, method, config key, or Gradle task referenced here;
- change a vanilla hook point, Mixin target, or the partial-detection / sort assumptions;
- bump a platform version (MC, NeoForge, Parchment, Gradle) or change the build flow;
- change the mod's behavior, scope, or conventions/gotchas.

Keep edits surgical: fix the affected table row / anchor, don't rewrite the whole file.

## Purpose

Client-only NeoForge QoL mod for **Minecraft Java 1.21.1** that adds a third recipe-book filter
state, **partially craftable**, alongside vanilla's "all" and "craftable" modes. A recipe is partial
when the player has at least `minMatchedIngredients` of its ingredient slots but the game cannot fully
craft it. Partial recipes are tinted, get a missing-ingredients tooltip, and are sorted
closest-to-craftable first. No server install; works on vanilla servers; never moves/consumes items.

## How the feature works (read before touching the Mixins)

Vanilla stores **one boolean per book type** ("filtering" = craftable-only). The mod layers a third
state on top, tracked client-side in `PartialFilterState` (a set of book types currently in partial
mode; never sent to the server). The combination yields three modes — see `PartialFilterState.modeOf`
and the `cycle` truth table.

Two Mixins do the work, both gated by `PartialConfig.enablePartialCraftableFilter`:

- **`RecipeBookComponentMixin`** — the filter integration.
  - `pcr$cycleFilter` `@Redirect`s the `toggleFiltering()` call inside `mouseClicked` so the button
    cycles **All → Craftable → Partial → All** (`PartialFilterState.cycle`). The returned boolean is
    what vanilla feeds to `filterButton.setStateTriggered`, so partial mode shows the button "on".
  - `pcr$filterPartial` `@Redirect`s the `RecipeBookPage.updateCollections(list, reset)` call inside
    `updateCollections`. In partial mode it swaps in `pcr$collectPartial(list)` — only collections
    with a partial recipe (`pcr$bestPartialScore`), sorted `CLOSEST_FIRST` when
    `sortPartialRecipesByClosest`. In the other two modes the list is forwarded unchanged (vanilla
    behavior preserved).
  - `pcr$filterName` (`getRecipeFilterName` `@Inject`) → "Partially craftable" toggle tooltip;
    `pcr$initVisuals` re-asserts the button look on reopen; `pcr$renderMarker` draws the amber marker.
- **`RecipeButtonMixin`** — per-recipe visuals.
  - `pcr$tint` (`renderWidget` TAIL) draws an amber border when the shown recipe is partial.
  - `pcr$tooltip` (`getTooltipText` RETURN) appends "Partially craftable", the `N/M` progress line,
    and the missing list. Both call `pcr$partialScore` / `pcr$partialActive`.

**Partial decision (authoritative).** A recipe counts as partial iff it is **not** fully craftable per
the game's own check (`RecipeCollection#isCraftable` / vanilla `StackedContents#canCraft`) **and**
`RecipePartialMatcher` reports `satisfiedSlots >= minMatchedIngredients`. The matcher is only a
*closeness* heuristic (greedy, most-constrained-slot first); it never overrides vanilla's
craftability, so a craftable recipe can never be shown as partial and vice-versa.

## Feature → file/symbol map

| Need to work on... | File | Symbol / anchor |
| --- | --- | --- |
| **Filter button tri-state cycle** | `src/main/java/com/partiallycraftablerecipes/mixin/RecipeBookComponentMixin.java` | `pcr$cycleFilter` (`@Redirect` of `toggleFiltering()` in `mouseClicked`) |
| **Which collections show in partial mode + sorting** | same file | `pcr$filterPartial` (`@Redirect` of `RecipeBookPage.updateCollections`), `pcr$collectPartial`, `pcr$bestPartialScore` |
| **Toggle tooltip / button look / marker** | same file | `pcr$filterName`, `pcr$initVisuals`, `pcr$renderMarker`; `@Shadow` `menu`/`book`/`stackedContents`/`filterButton` |
| **Per-recipe tint + tooltip** | `src/main/java/com/partiallycraftablerecipes/mixin/RecipeButtonMixin.java` | `pcr$tint` (`renderWidget`), `pcr$tooltip` (`getTooltipText`), `pcr$partialScore`, `pcr$partialActive` |
| **The tri-state logic (pure, testable)** | `src/main/java/com/partiallycraftablerecipes/PartialFilterState.java` | `cycle`, `modeOf`, `Mode`, `CycleResult`; `isPartial`/`setPartial` (client-side `RecipeBookType` set) |
| **Ingredient matching (pure, testable)** | `src/main/java/com/partiallycraftablerecipes/RecipePartialMatcher.java` | `match(List<int[]> slots, Map<Integer,Integer> available)` (greedy, most-constrained first) |
| **Score data + ordering (pure, testable)** | `src/main/java/com/partiallycraftablerecipes/PartialCraftingScore.java` | `satisfiedSlots`/`totalSlots`/`missingSlots`/`fraction`/`isPartial`; `CLOSEST_FIRST` comparator |
| **A missing item (pure)** | `src/main/java/com/partiallycraftablerecipes/MissingIngredient.java` | record `(int itemId, int count)`, `plus` |
| **MC ↔ matcher bridge** | `src/main/java/com/partiallycraftablerecipes/PartialRecipeAnalyzer.java` | `availabilityOf(StackedContents)`, `score(RecipeHolder, availability)` (skips special/custom/empty-tag, catches all) |
| Mod entry / lifecycle | `src/main/java/com/partiallycraftablerecipes/PartiallyCraftableRecipes.java` | ctor `(IEventBus, ModContainer)`; `MOD_ID`; `DEBUG`; `LOGGER` |
| Config schema (on-disk spec) | `src/main/java/com/partiallycraftablerecipes/PartialConfig.java` | `SPEC`; `ENABLE_PARTIAL_CRAFTABLE_FILTER`, `SHOW_MISSING_INGREDIENTS_TOOLTIP`, `SORT_PARTIAL_RECIPES_BY_CLOSEST`, `MIN_MATCHED_INGREDIENTS` |
| Config values read by the Mixins | `PartialConfig.java` | cached `volatile` fields `enablePartialCraftableFilter`, `showMissingIngredientsTooltip`, `sortPartialRecipesByClosest`, `minMatchedIngredients`; populated by `bake()` |
| Config (re)load wiring | `PartialConfig.java` + `PartiallyCraftableRecipes.java` | `PartialConfig.onConfigEvent`; `modBus.addListener(... ModConfigEvent.Loading / .Reloading ...)` |
| Register Mixins with the loader | `src/main/resources/partiallycraftablerecipes.mixins.json` | `"client": ["RecipeBookComponentMixin", "RecipeButtonMixin"]`; `"package"` |
| Tooltip / toggle strings | `src/main/resources/assets/partiallycraftablerecipes/lang/en_us.json` | `gui.partiallycraftablerecipes.{toggle.partial,partially_craftable,progress,missing}` |
| Mod metadata (loaded by NeoForge) | `src/main/templates/META-INF/neoforge.mods.toml` | `displayName`/`authors`/`license`/`version` (all `${...}` from `gradle.properties`); `[[mixins]]`; `side = "CLIENT"` |
| Resource-pack stub | `src/main/resources/pack.mcmeta` | `pack_format` (34 for 1.21.1) |

## Tests

JUnit 5, run via the moddev `unitTest` harness (NeoForge runtime on the test classpath). The Mixins
can't be unit-tested without a live client, so all testable logic lives in the pure classes.
Run with `.\gradlew.bat test`.

| Need to work on... | File | Symbol / anchor |
| --- | --- | --- |
| Matcher tests (cake example, counts, tags, no-mutation) | `src/test/java/com/partiallycraftablerecipes/RecipePartialMatcherTest.java` | `CakeExample`, `CountsAndDuplicates`, `TagsAndAlternatives`, `doesNotConsumeCallersMap` |
| Score / ordering tests | `src/test/java/com/partiallycraftablerecipes/PartialCraftingScoreTest.java` | `isPartial_respectsMinMatchedAndNotFull`, `closestFirst_ordersBySatisfiedThenMissing` |
| Tri-state cycle tests | `src/test/java/com/partiallycraftablerecipes/PartialFilterStateTest.java` | `FeatureEnabled`, `FeatureDisabled`, `ModeResolution` |
| Config schema/defaults tests | `src/test/java/com/partiallycraftablerecipes/PartialConfigTest.java` | `valuePaths`, `specDefaults`, `cachedDefaultsMirrorSpec` |
| Test harness + JUnit deps | `build.gradle` | `neoForge { unitTest { enable(); testedMod } }`; `testImplementation`/`testRuntimeOnly`; `tasks.named('test')` |

## Build / version map

| Need to change... | File | Anchor |
| --- | --- | --- |
| Platform versions (MC / NeoForge / Parchment / ranges) | `gradle.properties` | `minecraft_version`, `neo_version`, `parchment_version`, `*_version_range` |
| Mod id / name / version / group / license | `gradle.properties` | `mod_id`, `mod_version`, `mod_group_id`, `mod_license` (single source of truth) |
| Build logic, toolchain (Java 21), runs | `build.gradle` | `neoForge { version / parchment / runs.client / mods }`; `java.toolchain` |
| Formatting (palantir-java-format) | `build.gradle` | `spotless { java { ... } }`; **auto-runs**: `compileJava` `dependsOn 'spotlessApply'`. `spotlessCheck` is a standalone dry-run gate |
| How `${...}` in the toml gets filled | `build.gradle` | `generateModMetadata` task (expands `gradle.properties` into the toml template) |
| Gradle version | `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl` (8.12.1) |
| CI (build + test on push/PR) | `.github/workflows/build.yml` | JDK 21, `./gradlew build`, uploads the jar artifact |

> Note: `neoforge.mods.toml` lives in `src/main/templates/` (not `resources/`) because it uses
> `${...}` placeholders expanded by `generateModMetadata`. The built copy lands in the jar under
> `META-INF/`.

## Reading vanilla source (don't commit it)

The vanilla recipe-book classes, filter model, and craftability checks this mod hooks are written up
in our own words in [`docs/vanilla-hooks.md`](docs/vanilla-hooks.md) — read that first.

The decompiled **Minecraft + NeoForge** sources are produced locally by the moddev plugin — do **not**
vendor them. To grep them, the moddev cache holds a sources jar at
`~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*.jar` (unzip the
one whose listing contains `RecipeBookComponent.java`). IDE "go to definition" resolves these after a
Gradle sync.

## Conventions / gotchas

- **Client-only.** `@Mod(dist = Dist.CLIENT)`; both Mixins are in the `client` list. No server code.
- **Shadowing inherited members needs `extends`.** `RecipeButtonMixin` uses `getX()`/`getY()`, which
  `RecipeButton` only inherits from `AbstractWidget`. You cannot `@Shadow` a method the target merely
  inherits — the mixin must `extends AbstractWidget` and call them directly (with a dummy constructor
  that Mixin discards). Doing it via `@Shadow` crashes at apply time with `InvalidMixinException`
  (this was the 1.0.0 → 1.0.1 fix).
- **No refmap.** Dev and production both run Mojmaps, so `mixins.json` omits a refmap. Mixin member
  names/descriptors must match Mojmaps exactly.
- **Read-only.** Nothing consumes/moves items — only availability is read — so the partial check can
  never dupe/lose items or desync.
- **Vanilla decides craftability.** The greedy matcher is only for the closeness score and the missing
  list. Always gate "is partial?" on `isCraftable` (component side) / vanilla craftability so a
  craftable recipe is never shown as partial. Mirror any rule change across both Mixins.
- **Conservative on weird recipes.** `PartialRecipeAnalyzer.score` returns `null` (→ skipped) for
  special/custom/empty-tag recipes and catches everything, so the book never crashes.
- **Don't break the other modes.** `pcr$filterPartial` only transforms the list in partial mode; "all"
  and "craftable" pass through untouched. Keep it that way.
- **Behavior is unverified in-game.** Unit tests cover the pure logic only; the mixin wiring still
  needs the manual pass in `README.md`. Don't claim in-game verification until then.
- **Build = format + compile + test, one command.** `.\gradlew.bat build` (needs JDK 21) auto-runs
  `spotlessApply`, compiles, runs the JUnit suite, and jars → `build/libs/partiallycraftablerecipes-<version>.jar`.
