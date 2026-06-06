# Changelog

All notable changes to Partially Craftable Recipes are documented here.

The format is loosely based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - 2026-06-06

### Added

- Third recipe-book filter state, **Partially craftable**, cycled from the existing filter button
  (All → Craftable → Partial → All).
- Partial detection: a recipe is partial when at least `minMatchedIngredients` ingredient slots are
  covered by the inventory but the game cannot fully craft it. Supports shaped/shapeless recipes,
  tag/alternative ingredients, and ingredient counts; skips special/custom recipes conservatively.
- Distinct amber border on partially-craftable recipe buttons and a tooltip showing
  "Partially craftable", an `N/M ingredients available` progress line, and the missing-ingredient
  list. A small amber marker is drawn on the filter button while in partial mode.
- Closest-to-craftable sorting of the partial list (more satisfied slots first, then fewer missing).
- Client config (`enablePartialCraftableFilter`, `showMissingIngredientsTooltip`,
  `sortPartialRecipesByClosest`, `minMatchedIngredients`).
- Unit tests for the matcher, score/ordering, tri-state cycle, and config schema.

### Notes

- Client-only; works on vanilla servers. In-game behavior is not yet manually verified (see the
  README checklist) — the unit tests cover the pure logic only.
