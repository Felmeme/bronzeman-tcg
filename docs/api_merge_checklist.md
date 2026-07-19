# Merging the osrs-tcg API branch (when Az's update is live)

Self-contained checklist — written 2026-07-18, when `feature/osrstcg-api`
(2 commits: `4b720d5` API integration, `a3bf3f2` warning fix) was pushed to
GitHub and waiting on Az's hub release.

All commands go in the IntelliJ terminal (it already sits in the repo folder).

## 0. Confirm it's actually live
Az says so, or the Plugin Hub shows OSRS TCG at a version newer than 0.16.1.
No rush — nothing breaks in any ordering. Merging early or late is always safe:
the config-decode fallback covers every combination of who has updated.

## 1. Check where you are
```
git status
git branch --show-current
```
- Uncommitted files (e.g. the held-back resource_nodes.json) are fine —
  they carry over between branches untouched.
- Note which branch you're on; step 2 moves you to main.

## 2. Switch to main and make sure it's current
```
git checkout main
git pull
```

## 3. Merge the branch
```
git merge feature/osrstcg-api
```
- Expected output: `Fast-forward` followed by a file list. That means main
  simply caught up to the branch — no new commit created, nothing to write.
- If it says `Already up to date`: the merge already happened previously. Done.
- If it reports CONFLICTS: main and the branch both changed the same lines
  since 2026-07-18. Don't guess — resolve in IntelliJ (Git menu > Resolve
  Conflicts) or bring the assistant in. Nothing is broken; the merge just
  pauses until resolved.

## 4. Build and sanity-check
Build in IntelliJ as usual (or `./gradlew build` in Git Bash). Must be green.
Optional but worth it: run the dev client, log in, and look for this line in
the log — it confirms the API path is live against the hub build:
```
osrs-tcg PluginMessage API active; collection now push-updated.
```

## 5. Push
```
git push
```

## 6. Release to the Plugin Hub (same flow as every release)
1. Get the new commit hash: `git log -1 --format=%H` (or copy it from GitHub).
2. In your fork of runelite/plugin-hub, edit `plugins/bronzeman-tcg` and set
   the `commit=` line to that hash.
3. Open the PR. Once it's merged, users receive the update automatically.
4. Post the update in Discord as usual.

## 7. Cleanup (optional, after the hub PR merges)
```
git branch -d feature/osrstcg-api
git push origin --delete feature/osrstcg-api
```
`-d` refuses to delete anything unmerged, so this can't lose work.

## If something looks wrong
- Pushed main before Az shipped? Harmless — the fallback carries everyone;
  the API code sits inert until his side answers.
- Merged the wrong direction / unsure what state you're in? Stop, run
  `git log --oneline -5` and `git status`, and bring the assistant in with
  that output. Don't run reset/revert commands from memory.
