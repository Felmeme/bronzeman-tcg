# Detecting an active Last Man Standing match (to bypass all Bronzeman TCG restrictions)

Research for `bronzeman-tcg`. Goal: a single boolean `isInLmsMatch()` that is **TRUE only during a live LMS round** and FALSE in the Ferox Enclave lobby, at the login screen, in normal PvM/PvP, and (ideally) promptly FALSE on death/exit. The plugin then early-returns from `onMenuOptionClicked` and returns `true` from `addEntity` while it is TRUE.

LMS was developed internally as **"Battle Royale"**, so every relevant client id is prefixed `BR_`. All ids below were read straight from RuneLite `master` source (not memory).

---

## Method 1 — Varbit `BR_INGAME` / `IN_LMS` (PRIMARY, recommended)

The client keeps an "am I in a battle-royale game" varbit. Core RuneLite exposes it under two names that resolve to the **same id, 5314**:

- `net.runelite.api.gameval.VarbitID.BR_INGAME = 5314`
- `net.runelite.api.Varbits.IN_LMS = 5314` (the deprecated hand-named constant — the RuneLite devs literally called this varbit "IN_LMS", which is as strong an authority signal as we get without in-game testing)

Sources (verified by direct file grep this session):
- VarbitID.java line 3772: `public static final int BR_INGAME = 5314;` — https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java
- Varbits.java: `public static final int IN_LMS = 5314;` — https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/Varbits.java

Neighbouring `BR_` varbits corroborate that 5314 is the live-match flag (they only make sense mid-round): `BR_KILLCOUNT = 5315`, `BR_SAFECOORD_X/Z = 5320/5316`, `BR_SAFERADIUS = 5317`, `BR_FORCEFOG = 5318`, `BR_FOG_DAMAGECOUNT = 5319` (the shrinking-fog mechanic). `BR_MODE_SELECTED = 5306` is set at lobby/loadout time, so do NOT use that one.

### Snippet (this plugin's style)
```java
import net.runelite.api.gameval.VarbitID;

/** True while the local player is inside a live Last Man Standing round. */
private boolean isInLmsMatch()
{
    // BR_INGAME (a.k.a. Varbits.IN_LMS) is the client's own "in a battle-royale game" flag.
    return client.getVarbitValue(VarbitID.BR_INGAME) == 1;
}
```

Wire it in as a single early-out at the top of the two enforcement paths:
```java
@Subscribe
public void onMenuOptionClicked(MenuOptionClicked event)
{
    if (isInLmsMatch())
    {
        return; // LMS hands out gear/supplies you don't "own" - never restrict inside a match.
    }
    ... existing body ...
}

@Override
public boolean addEntity(Renderable renderable, boolean ui)
{
    if (isInLmsMatch())
    {
        return true; // don't hide/grey opponents inside LMS
    }
    ... existing body ...
}
```
`client.getVarbitValue(int)` is the current API (reads the cached value on the client thread, which is where both hooks already run). No `VarbitChanged` subscription is needed for this pull-style check; add one only if you want to log/toggle overlays on transition.

### Behaviour by state
- **Lobby (Ferox Enclave):** expected FALSE. The lobby has its own overlay (`BR_LOBBYOVERLAY`, Method 2) and its own varbits (`BR_MODE_SELECTED`, `BR_COFFER`); `BR_INGAME` is the *in-game* flag. **Verify in-game** that it is 0 while waiting in Ferox.
- **Match start:** flips to 1 when the round begins / you are teleported to the island.
- **Death / win / leave:** expected to return to 0 when you leave the match. **Verify** it does so promptly (see Method 2 — the KDA overlay closing on match end is the corroborating signal core code trusts).
- **Spectating after death:** UNKNOWN without testing — the varbit may linger at 1 until you actually exit to the lobby. This is the **safe direction of error**: while spectating you cannot meaningfully loot/equip/attack for real, and staying permissive a few extra seconds never bricks anything. Bricking only happens if detection is FALSE during a real round, which the varbit does not do.

### Confidence: HIGH.
Officially named `IN_LMS` by RuneLite; it is the client's purpose-built flag; cheapest and most direct method. The only unverifiable-from-source detail is the exact death/spectate transition timing.

---

## Method 2 — Widget/interface `BR_OVERLAY` loaded (strong secondary)

LMS shows an in-game HUD (kills / players remaining). That interface group is the in-match overlay; the lobby uses a *different* group. Cross-validated three ways this session:

- `InterfaceID.BR_OVERLAY = 328` (in-game HUD) and `InterfaceID.BR_LOBBYOVERLAY = 333` (lobby HUD) — InterfaceID.java lines 335 / 340.
- Old `WidgetID.java`: `LMS_INGAME_GROUP_ID = InterfaceID.BR_OVERLAY;` and `LMS_GROUP_ID = InterfaceID.BR_LOBBYOVERLAY;` (lines 153-154) — i.e. RuneLite itself maps "LMS in-game" to 328 and "LMS lobby" to 333.
- Old `WidgetInfo.java`: `LMS_KDA(InterfaceID.BrOverlay.CONTENT)` (the kills/deaths widget) and `LMS_INFO(InterfaceID.BrLobbyoverlay.CONTENT)`.
- The popular **lms-start-notifier** plugin detects a live game exactly this way: `inGame = true` on `WidgetLoaded` for `WidgetInfo.LMS_KDA` (group 328), `inGame = false` on `WidgetClosed` for the same group. Source: https://github.com/pwatts6060/lms-start-notifier (`LMSPlugin.java`).

Because 328 (game) and 333 (lobby) are distinct groups, this method **cleanly separates match from lobby** — its main strength over region checks.

### Snippet — event-driven (mirrors the community plugin, most robust)
```java
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;

private boolean inLmsGame;

@Subscribe public void onWidgetLoaded(WidgetLoaded e)
{
    if (e.getGroupId() == InterfaceID.BR_OVERLAY) inLmsGame = true;
}
@Subscribe public void onWidgetClosed(WidgetClosed e)
{
    if (e.getGroupId() == InterfaceID.BR_OVERLAY) inLmsGame = false;
}
```

### Snippet — polling variant (if you prefer no extra state)
```java
private boolean isLmsOverlayUp()
{
    Widget w = client.getWidget(InterfaceID.BR_OVERLAY, 0); // 0 = root child, as done for GE_OFFERS elsewhere
    return w != null && !w.isHidden();
}
```
Note: the plugin already imports `net.runelite.api.gameval.InterfaceID` and uses `client.getWidget(InterfaceID.GE_OFFERS, 0)` (line 670), so the flat-group-id call style is a drop-in match.

### Behaviour by state
- **Lobby:** FALSE — lobby uses group 333, not 328. Good.
- **Match:** TRUE while the KDA HUD is up.
- **Death/exit:** goes FALSE when the overlay closes. In the community plugin this is the authoritative "game over" signal, so it is reliable at end-of-match.
- **Spectating:** UNKNOWN — the KDA overlay may stay loaded while spectating. Same benign direction of error as Method 1.
- **Caveat:** if you use the polling variant, a `WidgetLoaded` for 328 must have happened; the event-driven pair is safer across login-while-already-in-a-match edge cases (rare for LMS). Group ids are stable but a Jagex UI rework could renumber them.

### Confidence: HIGH.
Battle-tested by a live Plugin Hub plugin; distinguishes lobby from match by design.

---

## Method 3 — Map region check (good corroborating fallback, weakest alone)

Core RuneLite's **loot tracker** ignores LMS player-kill loot by testing the player's current map regions against a fixed set (LootTrackerPlugin.java lines 254-255, used at 793-795):

```java
private static final Set<Integer> LAST_MAN_STANDING_REGIONS = ImmutableSet.of(
    13658, 13659, 13660,
    13914, 13915, 13916, 13918, 13919, 13920,
    14174, 14175, 14176,
    14430, 14431, 14432);
```
Helper (line 1712):
```java
private boolean isPlayerWithinMapRegion(Set<Integer> regions)
{
    for (int region : client.getMapRegions())
        if (regions.contains(region)) return true;
    return false;
}
```
Source: https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java

### Behaviour by state
- **Lobby (Ferox Enclave):** FALSE — Ferox is a different region, not in this set. Good (this is why loot tracker is safe there too).
- **Match:** TRUE — these are the LMS island regions.
- **Spectating:** likely still TRUE (you're still on the island) — again the benign direction.
- **Weaknesses:** (1) a **static region list breaks when Jagex adds/rotates LMS maps** — this set is maintained by hand and has grown over the years; (2) it's TRUE for the *whole island* including any brief pre-round staging, which is actually fine for our lenient purpose but means it is NOT a precise "match is live" signal on its own.

### Confidence: MEDIUM.
Authoritative (core code uses it) but coarse and maintenance-bound. Best used to *harden* the varbit, not replace it.

---

## Recommendation

**Primary: Method 1, the `BR_INGAME` (a.k.a. `IN_LMS`) varbit, id 5314.** It is the client's own in-game flag, RuneLite literally named it `IN_LMS`, it's a one-line client-thread read that drops straight into both existing hooks, and it is FALSE in the lobby by construction. This alone is the cleanest reliable answer.

**Belt-and-braces (recommended for a challenge plugin where a false-negative bricks the minigame): OR it with the region check.** Because the only dangerous failure is detection going FALSE mid-match, combine the two so either signal keeps restrictions off:

```java
private boolean isInLmsMatch()
{
    if (client.getVarbitValue(VarbitID.BR_INGAME) == 1)
    {
        return true;
    }
    for (int region : client.getMapRegions())
    {
        if (LAST_MAN_STANDING_REGIONS.contains(region))
        {
            return true;
        }
    }
    return false;
}
```
This stays TRUE even if a future client tweak changes the varbit's death/spectate timing, and stays TRUE across the whole island. The cost is only that restrictions also lift anywhere on the LMS islands (harmless — you never legitimately train Bronzeman skills there). The `BR_OVERLAY` widget (Method 2) is an equally valid secondary if you'd rather not hard-code regions that Jagex may extend; it distinguishes lobby-vs-match more precisely but is one UI-rework away from renumbering. Either pairing is fine; varbit-primary is the non-negotiable part.

### What the owner MUST verify in an actual LMS match (cannot be confirmed from source)
1. In the **Ferox Enclave lobby** (mode selected, waiting): `BR_INGAME` reads **0** and `getWidget(BR_OVERLAY, 0)` is null. (Use RuneLite's Dev Tools > Varbit inspector on 5314, and Widget inspector on group 328.)
2. **At round start:** `BR_INGAME` flips to **1** and group 328 loads.
3. **On death, and while spectating afterwards:** note whether `BR_INGAME` returns to 0 immediately, stays 1 until you exit, and whether the 328 overlay closes. Any lingering-TRUE here is acceptable (safe direction), but confirm it does eventually clear on return to the lobby so restrictions resume.
4. **Normal world (non-LMS):** `BR_INGAME` is 0 everywhere — sanity check in Ferox proper and in the wilderness.

Everything above is sourced from RuneLite `master`; the only genuinely untestable-from-code points are the death/spectate transition timings in item 3.
