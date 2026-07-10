# Bronzeman TCG

A bronzeman-style restriction plugin for RuneLite, driven by the
[OSRS TCG](https://github.com/Azderi/osrs-tcg) plugin's card collection:
**you may only attack NPCs whose card you have already pulled.**

## How it works

- The OSRS TCG plugin persists its collection in RuneLite's per-account
  (RSProfile-scoped) configuration under group `osrstcg`, key `state`,
  encoded as gzip → XOR → Base64 with an `RLTCG_v2:` prefix.
- This plugin reads and decodes that state directly (read-only, cached for
  5 seconds) — no compile-time dependency, so both plugins install
  independently from the Plugin Hub.
- A bundled snapshot (`tracked_monster_names.json`, 1,227 names) lists every
  card in the TCG catalog tagged `Monster`. Only NPCs on this list are ever
  restricted; anything without a card can always be attacked, since it could
  never be unlocked.
- Enforcement consumes `MenuOptionClicked` events client-side (the same
  pattern Bronzeman-mode plugins use): direct `Attack` options on NPCs, and
  optionally spells/items used on NPCs (`WIDGET_TARGET_ON_NPC`).

## Config

| Option | Default | Effect |
|---|---|---|
| Restrict attacks | on | Block `Attack` on uncollected NPCs |
| Restrict spells/items on NPCs | on | Also block spell casts / item-on-NPC |
| Chat feedback | on | Explain blocks in game chat (throttled) |

## Known limitations / maintenance notes

- **Name matching**: cards are matched to NPCs by exact (case-insensitive)
  name. Multi-form NPCs use their transformed composition's name.
- **Catalog drift**: if osrs-tcg updates its `Card.json`, regenerate
  `src/main/resources/tracked_monster_names.json` (filter for category
  containing `Monster`, dedupe names). The file records its own `count`.
- **Format drift**: if osrs-tcg changes its storage encoding
  (`RLTCG_v2:` prefix) or JSON shape (`collectionState.instances[].cardName`),
  update `TcgStateDecoder` / `TcgStateDto`. On any decode failure the plugin
  fails safe: it treats the collection as empty and logs at debug level —
  meaning all tracked NPCs become blocked, which makes breakage obvious
  rather than silently disabling the challenge.
- **Foils**: owning either the normal or foil version counts as collected.

## Dev

```
./gradlew build          # requires network for RuneLite deps
./gradlew run            # launches RuneLite in developer mode with the plugin loaded
```
