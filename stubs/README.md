# `:stubs` — compile-only mirror of the pcmc-territory (Part 1) API

This module exists for **one reason**: `pcmc-realms` hard-depends on `pcmc-territory`, but
Part 1 does not yet publish a consumable Maven artifact (it has an open PR, no release). To
compile `:mod` in CI today, this module mirrors the exact public surface Part 2 compiles
against — read verbatim from Part 1's `claude/part1-claims-2wek0q` branch:

```
com.theasshats.pcmcterritory.api.TerritoryApi      (static facade: resolve / getEntity / getEntityByName / toTerritoryChunk)
com.theasshats.pcmcterritory.api.EntitySnapshot    (record: id, name, members, colonyIds, claimKeys)
com.theasshats.pcmcterritory.api.TerritoryEvents   (Created / Removed / Changed, extends NeoForge Event)
com.theasshats.pcmcterritory.core.Role             (CITIZEN < OFFICER < LEADER — the documented api->core leak)
com.theasshats.pcmcterritory.core.ClaimKey         (record: ownerId)
com.theasshats.pcmcterritory.core.TerritoryChunk   (record: levelId, packedChunkPos)
```

## Critical properties

- **Compile-only.** `:mod` declares `compileOnly project(':stubs')`. These classes are **never
  bundled into the mod jar** (CI asserts this). At runtime the real `pcmc_territory` mod —
  a `required` dependency in `neoforge.mods.toml` — supplies the real implementations.
- **The bodies throw.** If a stub method is ever actually called at runtime, the real mod is
  missing and you get a clear `IllegalStateException` rather than silent wrong behavior.
- **Drift risk.** This is a hand mirror. If Part 1 changes its API, this stub must be updated
  to match, or `:mod` compiles green against a contract the runtime mod no longer honors. Keep
  it minimal (only what `:mod` references) to keep the drift surface small.

## How to remove it (do this once Part 1 publishes)

1. Delete the `stubs/` directory.
2. Remove `include 'stubs'` from `settings.gradle`.
3. In `mod/build.gradle`, replace `compileOnly project(':stubs')` with the real dependency,
   e.g. `compileOnly "com.theasshats:pcmc_territory:<version>"` (plus its repository), matching
   whatever coordinate Part 1's release publishes.

Tracked alongside the cross-repo issue to promote `Role` into Part 1's `api` package
(scope §2 / §12), which would also let this stub drop the `core` types.
