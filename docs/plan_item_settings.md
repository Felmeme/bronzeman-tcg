# Plan: Item settings pass (General section, consolidation round 2)

Agreed 2026-07-19. Theme: every item control is a dropdown with the standard
Locked/Unlocked pair; option-hiding is inherent to every Locked state (no
hiding toggle anywhere); shops block unconditionally. Two defaults get
HARSHER than live (owner's explicit choice - loud Discord note required).

## New controls (General section)

| Setting | keyName | Values (default bold) | Meaning |
|---|---|---|---|
| Ground Items | groundItemsMode | **Locked** / Unlocked | Locked: Take/telegrab blocked+hidden on locked drops (old restrictLoot). |
| Item Usage | itemUsageMode | **Locked** / Unlocked | Locked: locked inventory items keep ONLY Drop/Destroy/Examine (old forced-drop behaviour, now the definition of locked usage; equip/drink/use all inside it). Unlocked: everything allowed. |
| Banking | bankingMode | Off / **Deposit Only** / Full Banking | For locked items: Off = can't be banked at all; Deposit Only = holding pen (no withdraw); Full = both directions. Only meaningful while Item Usage is Locked. |
| Grand Exchange | grandExchangeMode | **Locked** / Unlocked | Locked: GE search/selection of locked items blocked (old restrictBuying's GE half). |
| Coin Settings | coinMode | Locked / **Unlocked** | Unlocked: Coins always exempt (old exemptCoins=true). Locked: even Coins need the card. |

- Shared two-value enum `LockState { LOCKED("Locked"), UNLOCKED("Unlocked") }`
  reused by the four binary dropdowns. `BankingMode` gets its own enum.
- **Shops: blocked unconditionally, no setting.** Exempt list is the escape.
- **Removed entirely**: restrictLoot, restrictItemUsage (this release's
  intermediate), restrictBuying, forcedDropMode (+ ForcedDropMode.java),
  exemptCoins, hideLockedOptions (+ its four non-NPC gates - stripping is
  now unconditional).

## Behaviour changes vs live (Discord note material)
1. DEFAULT gets harsher: locked inventory items become Drop/Destroy-only
   (forced drop was opt-in OFF before) and banking of locked items becomes
   deposit-only (was unrestricted). Owner's call: concise default experience.
2. Shop blocking always on - users who had buying off get shops re-blocked.
3. Option hiding always on - users who had hideLockedOptions off see options
   vanish instead of click-cancelling.

## Migration (inside the existing npcVisibilityMigrated one-shot - unreleased)
RuneLite auto-unsets stored values equal to defaults, so only explicit
non-default choices exist in storage. Rule: an explicit "off/false" maps to
the Unlocked/lenient value; everything else follows the new defaults.
- restrictLoot stored false        -> groundItemsMode UNLOCKED
- restrictEquipping/PotionDrinking stored false (pre-merge keys)
                                   -> itemUsageMode UNLOCKED
- restrictBuying stored false      -> grandExchangeMode UNLOCKED
- exemptCoins stored false         -> coinMode LOCKED
- forcedDropMode stored DROP       -> bankingMode OFF (usage already Locked)
- forcedDropMode stored ALLOW_BANKING -> bankingMode DEPOSIT_ONLY (no write; new default)
- Unset ALL old keys afterwards (incl. hideLockedOptions, restrictItemUsage).
- The restrictItemUsage write added earlier this release is REPLACED by the
  itemUsageMode mapping (none of it has shipped).

## Code touchpoints
- NEW LockState.java, BankingMode.java; DELETE ForcedDropMode.java.
- Config: 5 new dropdowns replace 6 items (net -1 control, -1 hidden later).
- Plugin:
  - restrictLoot() sites (ground-item hide + block) -> groundItemsMode.
  - restrictItemUsage() sites (equip/drink) -> subsumed by the forced-drop
    path: itemUsageMode == LOCKED drives the inventory option strip/block
    (today's FORCED_DROP_ALLOWED logic, incl. WIDGET_TARGET "Use" + CC_OP).
  - forcedDropMode() sites -> itemUsageMode == LOCKED; banking checks
    (deposit/withdraw paths) -> bankingMode.
  - restrictBuying() shop site -> unconditional; GE site -> grandExchangeMode.
  - exemptCoins() sites (isLootExempt, effectiveOwnedCards, migration)
    -> coinMode == UNLOCKED.
  - hideLockedOptions() gates (4) removed.
- Withdraw blocking for Off/Deposit Only: reuse the existing allow-banking
  withdraw path; Off additionally blocks deposits (new check, same site).

## Test plan
1. Defaults: locked inventory item shows only Drop/Destroy/Examine; deposit
   works, withdraw blocked; shops refuse locked purchase; GE search blocked;
   Coins lootable; locked ground item loses Take.
2. Each dropdown's Unlocked value restores the behaviour individually.
3. Banking Off: deposit of locked item blocked too. Full: both directions.
4. Coin Settings Locked: Coins need the card even for pickup.
5. Migration combos: each stored-false old key lands on the lenient value.
6. Exempt-list items behave as Unlocked everywhere (list still wins).

## Out of scope
- Exempt list mechanics (recently reworked, unchanged).
- Chat feedback / LMS / CotS / conflict message (CotS dies in phase 2).
- Per-skill sections.
