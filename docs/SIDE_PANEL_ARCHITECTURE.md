# Bronzeman TCG side-panel code tour

This guide explains how the RuneLite side panel is assembled, where its data
comes from, and which file to change for each kind of contribution. It assumes
basic Java knowledge but no previous Swing or RuneLite plugin experience.

## The short version

The panel follows this flow:

```text
RuneLite/TCG event
    -> BronzemanTcgPlugin requests a refresh
    -> background thread reads immutable catalogues and collection state
    -> PanelSnapshot is handed to Swing
    -> only the selected tab is rebuilt
    -> clicks either expand a row, select a tab, or open a card detail page
```

`BronzemanTcgPanel` coordinates that flow. It should not become the home for
every new visual component or data relationship.

## Main classes

| Class | Responsibility |
|---|---|
| `BronzemanTcgPanel` | Coordinates refreshes, tabs, filters, card navigation and scroll restoration. |
| `PanelComponents` | Stateless Swing rows, progress bars, dividers, borders and click binding. |
| `PanelCardDeck` | CardLayout wrapper that displays and sizes the currently selected tab. |
| `CardDetailPanel` | Renders resource, monster, quest and cross-linked card information. |
| `SidePanelSettings` | Builds the compact in-panel settings view from `BronzemanTcgConfig`. |
| `SidePanelModels` | Immutable prepared data and player-state snapshots used by rendering. |

The catalogue classes load data. They do not normally build Swing components:

| Catalogue | Panel information |
|---|---|
| `ImportantUnlocksCatalog` | Collection categories and subcategories |
| `CardKnowledgeCatalog` | Card descriptions, IDs, drops, sources and production links |
| `QuestCatalog` | Quest requirements and reverse card-to-quest relationships |
| `ContentCatalog` | Raids and other PvM content groups |
| `MonsterAreaCatalog` | Monster cards grouped by area |
| `ResourceNodeCatalog` | Rumour masters and resource-rule information |
| `TrackedMonsterCatalog` / `TrackedItemCatalog` | Canonical card names and progress totals |

## Creation and ownership

`BronzemanTcgPlugin` creates the panel during plugin startup:

```text
BronzemanTcgPlugin
    -> new BronzemanTcgPanel(...)
    -> NavigationButton points at that panel
    -> panel.requestRefresh()
```

The plugin owns the panel's lifetime. Gameplay event handlers do not directly
edit Swing rows. They request a refresh and let the panel obtain a consistent
snapshot.

## Refresh lifecycle

### 1. A refresh is requested

`BronzemanTcgPanel.requestRefresh()` is called when relevant state changes,
including TCG collection updates and RuneScape profile/quest changes.

Two atomic flags prevent refresh storms:

- `refreshRunning` means a background refresh is already active.
- `refreshAgain` remembers that another refresh was requested while it ran.

This avoids launching many overlapping catalogue/collection reads.

### 2. Data is prepared away from Swing

Catalogue-derived information is prepared on RuneLite's executor. This work
must not create or mutate Swing components.

`SidePanelModels.PreparedData` contains stable data such as:

- sorted quest entries;
- PvM and area entries;
- Slayer masters/tasks;
- rumour masters;
- searchable item and NPC entries.

### 3. Player state is captured

`SidePanelModels.PanelSnapshot` combines prepared data with current state:

- owned cards;
- shared cards;
- recent and shared unlock history;
- completed quests;
- progress totals;
- whether Slayer superiors are included.

The snapshot is treated as immutable after it is constructed.

### 4. Swing receives the snapshot

`finishRefresh()` returns to Swing's Event Dispatch Thread. `applySnapshot()`
compares old and new state, marks affected tabs dirty, and calls
`renderSelectedTab()`.

Only the visible tab is rebuilt. Other tabs remain dirty until selected. This
is important because rebuilding every list after every card unlock would be
wasteful and could cause visible layout jitter.

## Tabs and navigation

`PanelTab` identifies:

- Quests
- Slayer
- PvM
- Rumours
- Recent
- Collection
- Shared Cards
- Settings

`PanelCardDeck<PanelTab>` keeps every tab attached to one `CardLayout` and
shows the requested component. Its preferred height follows the selected tab,
which lets the outer scroll pane behave correctly.

`selectTab()`:

1. records the selected content tab;
2. updates the bronze navigation-button styling;
3. renders the tab if it is dirty;
4. resets the scroll position appropriately.

The Shared Cards button is added only while TCG Locked party sharing is
enabled. The Recent Unlocks “Show shared” option is independent and does not
control whether the Shared Cards tab exists.

## Expandable rows

Swing mouse clicks do not automatically bubble from a child label or progress
bar to its parent panel. `PanelComponents.makeClickable()` therefore attaches
the same action to the row and all of its children.

This is why the whole bordered row responds to a click rather than only its
progress bar.

Expansion state is stored as sets of stable string keys, for example:

```text
expandedQuests
expandedSlayerTasks
expandedImportantCategories
expandedImportantSubcategories
```

Rebuilding a tab consults those sets, so expanded rows remain open after a
collection refresh.

## Collection tab

Collection structure comes from:

```text
important_unlocks.json
    -> ImportantUnlocksCatalog
    -> BronzemanTcgPanel.refreshImportantUnlocks()
```

The JSON controls:

- category order;
- subcategory order;
- which exact card names appear in each group.

The Java panel controls:

- locked/unlocked filtering;
- counts and progress bars;
- expansion state;
- card-link behaviour.

Do not hard-code category membership in `BronzemanTcgPanel`. Edit the JSON or
its reviewed generation script instead.

## Card detail pages

The parent panel owns navigation:

```text
openCollectionCard()
    -> records the originating tab and scroll position
    -> adds the card to navigation history
    -> renderCollectionCard()
```

`CardDetailPanel` owns presentation:

- card sprite and heading;
- locked/unlocked status;
- type and categories;
- combat/Slayer levels;
- examine text;
- “Used in” relationships;
- monster drops;
- production and gathering information;
- ground spawns, shops and clues;
- quest relationships.

Linked cards call back into `openCollectionCard()`. The detail renderer does
not select tabs or modify the outer scroll pane itself.

Back navigation has two cases:

1. More than one card in history: return to the previous card.
2. First card: return to the originating tab and restore its scroll position.

Opening a new information page snaps to the top. Expanding a section while
already on that page preserves the current scroll position.

## Quests, Slayer, PvM and Rumours

These tabs use `QuestCatalog.Requirement` as their common requirement shape:

- cards inside one requirement are alternatives: **OR**;
- separate requirements must all be met: **AND**.

Example:

```text
Any pickaxe: Bronze pickaxe / Iron pickaxe / ...
Rope
```

Owning one pickaxe satisfies the first group. Rope satisfies the second.

Single-card requirements can link directly to their card page. Alternative
groups deliberately do not choose an arbitrary card when clicked.

Slayer has additional view models in `SidePanelModels` because its hierarchy
is deeper:

```text
Master
    -> task or location group
        -> card requirements
    -> superior monsters
```

The remaining tab-specific renderers stay in `BronzemanTcgPanel` because they
share filter, expansion and navigation state. Moving them without changing
that design would only relocate code rather than simplify it.

## Recent and Shared Cards

`RecentUnlocksTracker` supplies chronological unlock records. The panel must
not alphabetically reorder these records; newest unlock time is primary.

`SharedUnlockStore` supplies the cards shared through TCG Locked. Shared Cards
uses the same category order as Collection, filtering those categories down to
the shared card set.

Both lists make exact card rows clickable when card knowledge is available.

## Settings view

`SidePanelSettings` reads `@ConfigItem` metadata from `BronzemanTcgConfig`.
Reflection metadata is built once and cached. Each refresh reads the current
setting values and creates the appropriate Swing control:

- boolean -> checkbox;
- enum -> dropdown;
- integer -> spinner;
- colour -> colour chooser;
- other value -> text field.

Settings are grouped into Gathering, Production and Other, then by the
existing RuneLite config section.

Display names and descriptions can be changed safely. Existing `keyName`
values are user-data contracts and must not be renamed.

## Shared visual components

Use `PanelComponents` for common styling:

- `row()` – non-stretching list row;
- `sectionBody()` – vertical dark panel;
- `statusRow()` – card name and locked/unlocked mark;
- `progressRow()` – overall progress display;
- `hierarchyProgressRow()` – bordered expandable category;
- `styleHierarchyRow()` – main/subcategory border treatment;
- `listDivider()` and `addSpacedDivider()` – consistent separation;
- `makeClickable()` – whole-row click handling.

If several tabs need the same visual adjustment, change it here. If only one
tab or card-information section needs the change, keep it local.

## Threading rule

Swing components must be created or changed on Swing's Event Dispatch Thread.
Catalogue and collection preparation may happen on the executor.

The safe boundary is:

```text
executor/background: build immutable data
SwingUtilities.invokeLater: apply snapshot and render components
```

Do not read live game objects from a Swing renderer. Capture the required
state in the plugin or refresh preparation first.

## Where a change belongs

| Requested change | File/location |
|---|---|
| Border, progress-bar colour or common spacing | `PanelComponents` |
| Navigation button layout or tab switching | `BronzemanTcgPanel` |
| Card information layout | `CardDetailPanel` |
| Compact settings categories/controls | `SidePanelSettings` |
| Snapshot or Slayer view-model structure | `SidePanelModels` |
| Scrollable selected-tab behaviour | `PanelCardDeck` |
| Collection category membership | `important_unlocks.json` |
| Card facts or relationships | relevant catalogue/generated resource |
| New RuneLite setting | `BronzemanTcgConfig`, then automatically shown by settings view |
| TCG collection/shared-state behaviour | reader/store/plugin integration, not panel rendering |

## Contributor safety checklist

Before changing the panel:

1. Branch from the latest `0.3.0`.
2. Decide whether the change is presentation, navigation, data or state.
3. Avoid editing generated resources by hand unless they are explicitly
   owner-curated.
4. Preserve config key names.
5. Do not move background work onto Swing or Swing work onto the executor.
6. Test narrow panel width, expansion, Back navigation and scroll restoration.
7. Test Shared Cards both in and out of a party-sharing configuration.
8. Run the full Gradle build before proposing the change.

## Useful explanation for another developer

The simplest accurate description is:

> The plugin owns the gameplay and TCG state. The side panel takes immutable
> snapshots of that state and renders the selected tab. JSON/catalogues decide
> what information exists; the panel decides how it is filtered, expanded and
> navigated. Shared components handle consistent styling, while the card and
> settings views are isolated from the main tab coordinator.

Config key names, catalogue formats and TCG Locked communication are outside
the visual architecture and must not be changed during a presentation-only
refactor.
