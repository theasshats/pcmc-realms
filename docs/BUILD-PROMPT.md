# Build prompt — `pcmc-realms` (Part 2, Government)

> This file is a **kickoff prompt for a fresh Claude Code session** that will implement `pcmc-realms`.
> Paste it (or point the session at it) to start the build. It is self-contained: it names every repo
> the build needs, what's already verified, what to build first, and the hard rules. Keep it current as
> the build progresses — a later session should be able to resume from it.

---

## Your task

Implement **`pcmc-realms`** (`pcmc_realms`), **Part 2 (Government)** of the Project Commonwealth
governance trio: tiers, federation hierarchy, and the **law engine** with three enforcement modes, layered
on top of Part 1's claim-resolution substrate. It is a **NeoForge 1.21.1** mod. **Nothing is built yet** —
this repo currently holds only design docs and the license. You are scaffolding the Gradle/NeoForge project
and writing the first real code.

**Deliver slice 2a (the MVP) first.** Do not try to build the whole design at once. The design is large and
deliberately sliced; 2a is independently shippable and proves the whole model end-to-end.

### Slice 2a scope (build this)

- Project scaffold: Gradle multi-module (mirror Part 1's `:core` + `:mod` split — see below), NeoForge
  1.21.1, `mod_id = pcmc_realms`, hard-dep on `pcmc_territory`.
- `RealmGov` political state (tier, parent/children placeholders) in **this mod's own `SavedData`**, keyed
  by the territory entity UUID — hung off Part 1's entity by association, never by editing Part 1's records.
- Tier field + `/realm promote` validation (municipality ladder; promotion validated on the command, never
  per-tick; inputs are free MineColonies metrics).
- The **law engine**: a pluggable `LawType` registry, the **leaf→root precedence resolver** (Part 2 walks
  its own `parentId` map up from `TerritoryApi.resolve(...)[0]` — Part 1 only returns the leaf), and the
  three **enforcement modes** (`AUTOMATIC` / `OFFICER` / `SOCIAL`).
- **PVP (`SOCIAL`) live end-to-end** — the one law that proves the pipeline: detect candidate act →
  justification check (self-defense: first striker is the violator; + already-wanted-outlaw is open season)
  → raise the "wanted" signal → MineColonies guard aggro. **No guards nearby → nothing happens.**
- TAX / STIPEND / FINE **types + commands stubbed** into the registry (registered, no-op or "requires
  pcmc-mint" until Part 3 supplies coin) so the registry contract is exercised early.
- The **public `LawType` registry + effect hook** surface that Part 3 (`pcmc-mint`) will register money
  movement into — in a clean `api` package from day one, exactly as Part 1 did.

**Out of scope for 2a (later slices):** federation composition / `carve` / secession / war (2b), charters &
ship-realms (2b/later), TAX/STIPEND/FINE money movement (Part 3), GUI and `/realm map` render (polish).

### Spike BEFORE writing enforcement code

The whole `SOCIAL` model leans on MineColonies guard targeting — the **highest-risk dependency**. Confirm
the rank API on the pinned snapshot and (this needs a real instance) that setting a player to a Fight-Guards
rank actually makes guards aggro, and that the prior rank restores cleanly. Build the decoupled **"wanted"
signal** (event/tag/capability) as the canonical soft-law output with the guard rank-flip as one consumer —
it's robust to the guard API and later feeds bounties + killfeed. The MineColonies API for this is **already
located and confirmed to exist** (see "Verified facts" below); the spike is to confirm runtime behavior, not
to find the API.

---

## Read these first (the design is already written — do not re-derive it)

The full design is **canonical in the pack repo** and **mirrored into this repo**. Read them in this order:

1. `pcmc-realms/docs/ARCHITECTURE.md` — the condensed developer map (start here).
2. `pcmc-realms/docs/GOVERNANCE-REALMS-SCOPE.md` — the **authoritative Part 2 design**: hard/soft taxonomy,
   law catalog, exact Part 1 API contract, the MineColonies-guard mechanism, slices, spikes, open
   questions. This is the source of truth for *what* to build.
3. `pcmc-realms/docs/GOVERNANCE-MOD-SPEC.md` — the whole-trio (3-mod) plan for context.
4. `project-commonwealth/docs/CUSTOM-MODS.md` — the **mod-mirror** release pattern (how the jar reaches the
   pack) and prior-art mods (`pcmc-killfeed`, `pcmc-arcana`).

The canonical copies live at `theasshats/project-commonwealth/docs/GOVERNANCE-REALMS-SCOPE.md` and
`…/GOVERNANCE-MOD-SPEC.md`. If a design decision changes, update it **there**, not just in this repo's
mirror.

The scope doc's **§12 open questions** still stand. Most carry a recommendation — follow the recommendation
unless a maintainer has answered otherwise. If an open question genuinely blocks you and has no
recommendation (e.g. "secession war — whose officer decides"), ask the maintainer via `AskUserQuestion`
rather than guessing; but **none of the open questions block slice 2a** (they're mostly 2b/Part-3).

---

## Repositories — use all of them

All four repos are cloned into this sandbox and are in the session's GitHub scope. Use them:

| Repo | Use it for | Write? |
| --- | --- | --- |
| **`theasshats/pcmc-realms`** | **This is what you build.** | **Yes** — your branch + PR. |
| **`theasshats/pcmc-territory`** | **Part 1 — the API you compile against.** Read its `api` package source, README, and `docs/SPIKE-PART1.md` for the verified MineColonies/OPAC adapter notes. | No (read only) — but you may **file issues** there for cross-repo work (see below). |
| **`theasshats/minecolonies-data-repo`** | **MineColonies source, for lookup + integration reference only** — find the guard/permission API classes, exact signatures, and how guard targeting works. | **NEVER write code here.** It exists purely for faster code lookup and to help you integrate. Do not branch, commit, PR, or edit it. |
| **`theasshats/project-commonwealth`** | The pack: canonical design docs, the mod-mirror pattern, `docs/SYSTEMS.md` §3a (perf doctrine), and where the finished jar eventually plugs in via a packwiz manifest. | No (read only for this task) — the manifest is added **only once a real tagged jar exists**. |

⚠️ **`minecolonies-data-repo` is read-only reference.** It is the full MineColonies fork checked out so you
can grep its source instead of decompiling a jar. Look things up in it freely; never modify it.

If you need a repo outside this scope (e.g. `pcmc-killfeed` to copy its `build.yml` mod-mirror workflow, or
`pcmc-mint`), call `mcp__claude-code-remote__list_repos` and `add_repo` to bring it into scope — don't
assume it's inaccessible. `pcmc-killfeed` is the prior-art template for the release pipeline.

---

## Verified facts (gathered from the repos — trust these, but re-verify against current branch state)

### Part 1's actual API surface — and it is NOT on `main`

`pcmc-territory`'s `main` is **just an initial commit**. The real Part 1 code lives on branch
**`claude/part1-claims-2wek0q`** (PR **#1**, open). Compile against that. The public package is
`com.theasshats.pcmcterritory.api` and contains exactly three files (read verbatim from that branch):

- **`TerritoryApi`** (static facade): `resolve(ServerLevel, ChunkPos) -> List<UUID>` (leaf-first; **length
  0 or 1** in Part 1 — Part 2 builds the full chain by walking its own parent map), `getEntity(ServerLevel,
  UUID) -> Optional<EntitySnapshot>`, `getEntityByName(ServerLevel, String) -> Optional<EntitySnapshot>`,
  `toTerritoryChunk(...)`.
- **`EntitySnapshot`** (record): `(UUID id, String name, Map<UUID,Role> members, Set<Integer> colonyIds,
  Set<ClaimKey> claimKeys)`. Extend it **by association** (a parallel `Map<UUID,RealmGov>` in Part 2's
  `SavedData`), never by editing the record.
- **`TerritoryEvents`** (on `NeoForge.EVENT_BUS`): `Created` / `Removed` / `Changed`, each carrying an
  `EntitySnapshot`. **Listen, don't poll.** `Removed` must cascade-clean Part 2's tree.

⚠️ **`Role` leaks across the boundary.** `EntitySnapshot.members()` exposes `Role`, but `Role` lives in
Part 1's `core` package (`com.theasshats.pcmcterritory.core.Role`: `CITIZEN < OFFICER < LEADER`, with
`atLeast(...)`), not its `api` package — so reading a member's role forces importing a `core` type. The
scope doc recommends Part 1 promote `Role` into `api`. **File this as an issue on `pcmc-territory`**
(you can write issues there) so the contract becomes self-contained; until then, import the `core` type.

**Thread-safety invariant:** Part 1's resolver is **server-thread only**. Part 2's law handlers run on the
server thread (combat/interaction/command events) so they're in bounds — but **never call
`TerritoryApi.resolve` off-thread**.

### Part 1's Gradle layout to mirror

Root `settings.gradle` includes `:core` and `:mod`. `:core` is **pure Java** (no Minecraft/NeoForge/
MineColonies deps) so it builds and unit-tests offline; `:mod` is the NeoForge mod (ModDevGradle) that
wires `:core` to real Minecraft types and the soft-dep mods. Mirror this split — put the law engine,
resolver-walk, and registry in a pure-Java `:core` you can JUnit-test without the sandbox reaching the
modding mavens; keep Minecraft/MineColonies-touching code in `:mod`. Part 1's `gradle.properties` pins:
NeoForge `21.1.233`, MC `1.21.1`, Java 21, MineColonies CurseForge `project=245506 file=8186694`
(`[1.1.1327,)`), OPAC Modrinth version id `b16WHzyv` (`0.26.2`). Match these.

### MineColonies guard / permission API — confirmed present (read-only lookup in `minecolonies-data-repo`)

The soft-enforcement hypothesis is confirmed by the fork source:

- `com/minecolonies/api/colony/permissions/IPermissions.java` — **public** `boolean setPlayerRank(UUID id,
  Rank rank, Level world)`, plus `getRank(UUID player)` (read the prior rank to restore it), `getRankHostile()`,
  and rank-id constants (`HOSTILE_RANK_ID = 4`, `OFFICER_RANK_ID = 1`, …).
- `com/minecolonies/api/colony/permissions/Rank.java` — `isHostile()` / `setHostile(boolean)` is the modern
  "Fight Guards" mechanism. Note the comment in `Action.java`: `// GUARDS_ATTACK(1), replaced by hostile
  type` — so the **hostile rank flag**, not a `GUARDS_ATTACK` permission, is what makes guards attack.
- `com/minecolonies/core/entity/ai/workers/guard/AbstractEntityAIGuard.java` — guard target acquisition
  (read-only, to understand behavior; there's no public hook to inject a target, which is why an **addon**
  using the rank flip is the path, not a fork).

So the mechanism is: **on a violation in a colony-governed jurisdiction, flip the offender to a
Fight-Guards (Hostile) rank in that colony for a time-boxed "wanted" window via `setPlayerRank`, then
restore the prior rank when it expires.** Guards do the rest natively; "no guards → get away with it" is
free. Build this as an **addon** (MineColonies-API calls inside `pcmc-realms`), **not** a fork — a fork is a
later, separate decision justified only by guard *behavior* the binary rank can't express (see scope §5).
The pack runs MineColonies **snapshots**, so re-verify these signatures on the pinned coordinate before
relying on them.

### How the finished jar reaches the pack (mod-mirror)

Per `project-commonwealth/docs/CUSTOM-MODS.md`: each custom mod lives in its own repo with a `build.yml`
that, on a `v*` tag, builds the jar and attaches it to a GitHub release; the pack references that release
asset via a packwiz manifest (`mods/pcmc-realms.pw.toml`). **The manifest is not added to the pack until a
real, hashed jar exists for a tagged release.** `pcmc-territory` currently has **no `build.yml`** — you'll
need to add one to `pcmc-realms` (copy `pcmc-killfeed`'s as the template; `add_repo` it if needed).

---

## Hard rules

1. **`minecolonies-data-repo` is read-only.** Lookup and integration reference only. Never write to it.
2. **Lower mods never reference upper ones.** `pcmc_realms` compiles against `pcmc_territory`'s `api`
   package only — never `data`/`integration`/`core` (except the `Role` leak noted above, until Part 1 fixes
   it). `pcmc_territory` knows nothing of tiers/laws; `pcmc_realms` knows nothing of coins.
3. **No new claim system, no new entity identity.** Part 2 reads Part 1's claims and hangs *political*
   state off the same UUID in its own `SavedData`. Don't re-implement claim protection (OPAC/MineColonies
   already hard-protect claims) — unwelcome-but-permitted acts are the `SOCIAL` path.
4. **A law never cancels a physical action.** `SOCIAL` records + signals; `AUTOMATIC`/`OFFICER` move only
   government money. Match the taxonomy in scope §3 exactly.
5. **Event-driven, position-local, server-authoritative.** No global tick scans (STIPEND's coarse scheduler
   is the only time-driven piece, and it's later). Matches `SYSTEMS.md` §3a.
6. **Spike the guard integration before building enforcement.** Keep the wanted-signal fallback.
7. **You cannot run the game in this sandbox.** `:core` unit-tests and a `windows`/cross compile of `:mod`
   are the most you can verify here; runtime behavior (guard aggro especially) must be playtested on a real
   instance. Say so plainly — green CI ≠ it works.

---

## Workflow

- **Branch:** develop on `claude/bold-brahmagupta-2bf1cd` in `pcmc-realms` (create it if absent; never push
  to `main`). Fold related work into one PR; don't spin up parallel PRs.
- **This is a project, not a patch** — it gets one long-lived branch/PR that 2a (then 2b) fold into.
- **Cross-repo issues to file on `pcmc-territory`** (you can write issues there, not code): (a) promote
  `Role` into the `api` package; (b) resolve `aeroclaims` sub-level claims so ship-realms can resolve
  (scope §13, Issue 1); (c) embassy/extraterritorial precedence for claimed sub-levels (scope §13, Issue 2).
  None block 2a.
- **Open questions:** follow the scope §12 recommendations; escalate a genuine blocker via
  `AskUserQuestion`. None block 2a.
- When you open the PR, add a `## Playtest` checklist (client boots, the guard-aggro flow works on a real
  instance, restore-on-expiry survives a restart, server jar starts) and keep it a draft until a human
  ticks it. Auto-subscribe to the PR's activity.

Re-verify any "verified fact" above against current branch state before depending on it — Part 1's PR #1 may
have moved, and MineColonies snapshot signatures can drift.
