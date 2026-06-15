# Implementation status — slice 2a

What is actually built in this repo, what's verified, and what still needs a real instance. Read
[`ARCHITECTURE.md`](ARCHITECTURE.md) and [`GOVERNANCE-REALMS-SCOPE.md`](GOVERNANCE-REALMS-SCOPE.md) for
the design; this doc is the build's ground truth so a later session can resume.

## Module layout (mirrors pcmc-territory)

```
:core   pure Java government engine — NO Minecraft/NeoForge/MineColonies/territory deps.
        Builds + unit-tests offline. The verifiable heart of 2a.
:stubs  compile-only mirror of pcmc-territory's public api (TerritoryApi/EntitySnapshot/
        TerritoryEvents + leaked core Role/ClaimKey/TerritoryChunk). Lets :mod compile before
        Part 1 publishes. NOT bundled into the jar — the real pcmc_territory provides it at runtime.
        Delete + swap for the real artifact when Part 1 releases (stubs/README.md).
:mod    the NeoForge mod (pcmc_realms). Thin adapter wiring :core to Minecraft, NBT persistence,
        the combat/tick/territory events, the /realm commands, and the MineColonies guard consumer.
```

## What slice 2a delivers (built)

- **Tiers + promotion** — `Tier` ladder (SETTLEMENT..EMPIRE); `PromotionRules.validate` checks free
  MineColonies metrics against `PromotionThresholds` (config). `/realm promote`. Municipality ladder only
  (federations are composed, not promoted — 2b). Validated on the command, never per-tick.
- **The law engine** — `LawType<V>` (typed, pluggable) + `LawRegistry`; built-ins `pvp` (SOCIAL),
  `tax`/`stipend` (AUTOMATIC), `fine` (OFFICER) in `LawTypes`. `RealmGov` stores law values per entity;
  `LawResolver` resolves leaf→root (highest tier wins, subordinate gap-fill, same-rank tie → leaf-closer).
- **Three enforcement modes** — `EnforcementMode`; PVP is live end-to-end, the fiscal types are
  registered but inert (no coin) until Part 3 registers a `FiscalEffect`.
- **SOCIAL pipeline (PVP)** — combat event → `SocialLawEvaluator.evaluatePvp` → `JustificationPipeline`
  (`SelfDefenseJustification` = first-striker-is-violator, bounded combat window; `OutlawOpenSeasonJustification`
  = attacking an already-wanted player is open season) → on an unjustified violation, raise the wanted
  signal. The hit is **never cancelled** (scope §3).
- **The wanted signal (canonical)** — `WantedTable` (per-jurisdiction, expiry-tracked, persisted) is the
  source of truth; `WantedEvent.Marked/Cleared` posted on the bus; the MineColonies guard rank-flip is one
  *consumer* (`GuardEnforcer`). Robust to the guard API and ready for bounties/killfeed (scope §5, §12).
- **Public surface for Part 3** — `api.RealmsApi` (global `LawRegistry` + `registerFiscalEffect`),
  `api.FiscalEffect` (collect/payout, NOOP default), `api.WantedEvent`. Clean `api` package from day one.
- **Persistence** — `GovSavedData` (overworld) serializes the gov tree (tier, parent, laws; children
  rebuilt from parents) and the wanted table. `AggressionTracker` is intentionally transient.
- **Cascade cleanup** — listens to `TerritoryEvents.Removed`, calls `GovStore.onEntityRemoved` (detaches
  from parent, orphans children) so the tree leaks no dangling ids (scope §2).
- **Config** — `realms-server.toml`: combat window, wanted window, purge interval, promotion thresholds.
- **Debug command (OP)** — `/realm debug wanted <player> [seconds]` / `/realm debug pardon <player>` flag
  or clear a player wanted in the current jurisdiction through the exact same signal + guard path. This
  is how the §5 guard spike is tested **without a second player**: standing in your own colony,
  `/realm debug wanted <you>` turns its guards on you (mirrors Part 1's `debug bindclaim` precedent).
  Real player-vs-player PVP still needs two players (Open to LAN, or a server).
- **CI** — `.github/workflows/build.yml`: full build, `:core` tests, packaged-metadata validation
  (incl. asserting the :stubs mirror did NOT leak into the jar), and v*-tag release (mod-mirror).

## Verified vs not

- **Verified here:** `:core` compiles and all unit tests pass (`./gradlew :core:test`,
  `--configure-on-demand`). Coverage: resolver precedence, hierarchy/cascade, self-defense +
  outlaw justifications, wanted expiry/purge, law (de)serialization + registry, promotion validation.
- **Compiles only in CI:** `:mod` and `:stubs` need the NeoForge/CurseForge maven repos, which are gated
  in the pack's web sandbox. The MineColonies API calls were checked against the fork source
  (`minecolonies-data-repo`) but not compiled here.
- **NOT verified — needs a real instance (playtest):**
  - MineColonies guard aggro actually fires when a player is flipped to the Hostile rank (the §5 spike).
  - Restore-on-expiry returns the prior rank and survives a restart without stranding a player Hostile.
  - The combat event (`LivingIncomingDamageEvent`) fires for the PvP cases we expect (melee + projectile).
  - The claimed-chunk metric: we approximate with `getLoadedChunkCount()` (a SPIKE — see
    `MineColoniesMetricsLookup`); tune the `minClaimedChunks` thresholds or firm up the API.

## Known limitations / follow-ups

- **Territory dependency is stubbed.** Swap `:stubs` for the real pcmc-territory artifact once Part 1
  releases (stubs/README.md). Until then `:mod` can't run against a real territory locally.
- **`Role` import leak.** `:mod` imports `pcmc-territory`'s `core.Role` via `EntitySnapshot.members()`.
  Filed back to Part 1 to promote `Role` into its `api` package (scope §2/§12).
- **Guard prior-rank store is in-memory.** Across a restart, an expiring wanted entry restores to the
  colony's Neutral rank rather than the exact prior rank. The persisted `WantedTable` is the truth;
  persisting prior ranks (and re-deriving the flip on load) is a follow-up the spike informs.
- **No GUI / `/realm map`** — post-MVP polish (scope §9).
- **2b not started** — federation composition/carve/secede/war, charters, ship-realms.
