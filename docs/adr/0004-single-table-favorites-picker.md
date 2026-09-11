# ADR-0004: Single-Table Favorites Picker

**Status**: Accepted  
**Date**: 2026-09-11  

## Context

The Favorite Models settings page was a two-table picker (available ↔ favorites)
with Add / Add All / Remove / Clear All buttons between the tables, three rows of
filter widgets (two combo boxes, four capability checkboxes, six "Quick" preset
buttons), a modal "Variants…" dialog and an eleven-component chip legend. At the
default Settings width it overflowed horizontally, it advertised drag-and-drop
that was never implemented, and the "Variants…" dialog round-tripped the
favorites through a `Set`, destroying their order — which is load-bearing,
because `ModelsServlet` emits favorites in stored order and that becomes the
AI Assistant dropdown order.

The panel was 948 lines with effectively no coverage: its only test was
headless-disabled and re-implemented a listener locally, contradicting the goal
of ADR-0003.

JetBrains UI Guidelines favour selecting a subset of a large set with checkboxes
in one list or table (Plugins, Database Schemas, Notifications), filters as
toolbar drop-downs beside a full-width search field (Git log), toolbar actions
instead of button rows, and `StatusText` empty states with one action link.

## Decision

- Present the catalog as **one table** whose first column is an editable
  checkbox bound to the ordered favorites list. Ticking appends to the end;
  unticking removes positionally; rows never move on toggle.
- **Favorites only** (a toolbar toggle) is the only place order is shown or
  edited: it lists favorites in stored order, disables sorting, search and
  filters, and enables Move Up / Move Down and drag-and-drop row reorder.
- Filters are **toolbar drop-downs** (Provider, Context, Variant single-select;
  Capabilities multi-select) plus a Presets drop-down and Refresh. A
  text-only "Clear filters" action is visible only while a filter or search
  text is active. The Variant drop-down lists only the suffixes the loaded
  catalog carries: `:free` and `:batch` are catalog entries, while `:nitro`,
  `:floor` and `:exacto` are request-time routing shortcuts that never appear
  in `/models`, so offering them would guarantee an empty table.
- Variants are surfaced through the chip renderer, the Variant filter and a
  context-help icon; there is no dedicated variants dialog.
- All page logic lives in `settings/favorites/FavoriteModelsPageState`, a
  platform-free view-model tested in the unit tier. The Swing panel is a thin
  adapter with injectable seams, covered by one `*PlatformTest`.
- The variant legend is a `ContextHelpLabel` built from an explicit
  `HelpTooltip` anchored **below** the icon. The `ContextHelpLabel.create`
  default hangs the popup `-popupHeight` above the icon; a legend-sized popup
  is clamped back over the icon and the owner then alternates
  mouseExited / mouseEntered forever (IDEA-330235).
- Only public platform API is used (ADR-0002): `TableView` + `ListTableModel`,
  `BooleanTableCellRenderer`/`BooleanTableCellEditor`, `RowsDnDSupport`,
  `ActionToolbar` with `ComboBoxAction`/`ToggleAction`, `KeepPopupOnPerform`,
  `StatusText`, `ContextHelpLabel` and `HelpTooltip`. `ToolbarDecorator` was
  rejected because mixing custom actions into it requires deprecated members.
  One knowing exception: `HelpTooltip.setTitle`/`setDescription` are current
  API on the 2025.3 platform the plugin compiles against, and 2026.2
  deprecates every overload of both with no replacement reachable from 2025.3.

## Consequences

- Fewer widgets to keep consistent: one table model, one set of columns, one
  toolbar. Presets and empty states report through the status line instead of
  modal dialogs.
- Reordering is unavailable while filters or search are active; the trade-off
  is that row indices always equal stored positions.
- Unavailable favorites (ids missing from the catalog) remain visible and
  removable in Favorites only mode, greyed with a tooltip.
- `FavoriteModelsTableManager`, `ModelsFilterManager`,
  `FavoriteModelsTableModels`, `VariantAwareFavoritesPickerDialog` and
  `VariantPickerLogic` are deleted. `ModelFilterCriteria` becomes set-based
  with a `matches(model)` predicate.
- The separate `/models/count` request is dropped; "of N" uses the loaded
  catalog size so the numbers are reachable.
- The table claims the page's spare height, so the group row and the table row
  are both resizable; the page no longer leaves the lower half empty.
- Note: `gradle.properties` builds against platform 2025.3 (`pluginSinceBuild
  = 253`) while ADR-0001 records 2024.2. Nothing in this design needs a newer
  API than 2024.2, but the drift should be reconciled in a follow-up ADR.

## Related

- ADR-0002 (no non-public platform API), ADR-0003 (test taxonomy)
- `src/main/kotlin/org/zhavoronkov/openrouter/settings/FavoriteModelsSettingsPanel.kt`
- `src/main/kotlin/org/zhavoronkov/openrouter/settings/favorites/`
- `src/test/kotlin/org/zhavoronkov/openrouter/settings/favorites/FavoriteModelsPageStateTest.kt`
- `src/main/kotlin/org/zhavoronkov/openrouter/settings/favorites/VariantLegend.kt` (tooltip placement)
- `src/test/kotlin/org/zhavoronkov/openrouter/settings/FavoriteModelsSettingsPanelPlatformTest.kt`
- `docs/MODEL_VARIANTS_AND_ROUTING.md`
