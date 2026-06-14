# Architecture — `pcmc-realms`

Developer orientation for building Part 2 (Government) of the governance trio. This is a condensed,
stable map of the design so a developer can start without cross-referencing another repo for every fact.

> **Early development — nothing here is built.** Everything below is planned. The full design (with all
> the open questions and detail this doc omits) is in [`GOVERNANCE-REALMS-SCOPE.md`](GOVERNANCE-REALMS-SCOPE.md)
> and [`GOVERNANCE-MOD-SPEC.md`](GOVERNANCE-MOD-SPEC.md), mirrored alongside this file. Those mirror the
> **canonical, evolving copies in the pack repo** (issue
> [#260](https://github.com/theasshats/project-commonwealth/issues/260)); where this doc and the scope doc
> disagree, the scope doc wins — update it at the canonical source, not just here.

## 1. Layering and the dependency rule

Three mods, depending only downward. A lower mod never references an upper one.

```
  pcmc-territory (Part 1)  ──►  pcmc-realms (Part 2)  ──►  pcmc-mint (Part 3)
  claims + resolution           tiers + hierarchy + laws    treasury + minting
```

Part 2 hard-depends on Part 1 and compiles against its **public `api` package only**
(`com.theasshats.pcmcterritory.api`) — never reaching into `data`/`integration`/`core`. Part 2 introduces
no claims and no entity identity; it hangs *political* state off Part 1's entity UUID in its **own**
`SavedData` (persistence partitions per mod).

## 2. The Part 1 contract Part 2 compiles against

Part 1 exposes one public package. The surface Part 2 uses:

- **`TerritoryApi`** (static facade):
  - `resolve(ServerLevel, ChunkPos) -> List<UUID>` — the governing chain, **leaf first**. Part 1 returns
    length 0 (wilderness) or 1 (the single leaf entity; colony borders shield their chunks, so at most one
    leaf). **Part 2 builds the full `leaf → … → root` chain itself** by walking its own `parentId` map
    from `resolve()[0]`. Part 1 has no concept of parents — do not push hierarchy down into territory.
  - `getEntity(ServerLevel, UUID) -> Optional<EntitySnapshot>` and `getEntityByName(...)` — entity lookups.
- **`EntitySnapshot`** (immutable record): `id`, `name`, `members: Map<UUID,Role>`, `colonyIds: Set<Integer>`,
  `claimKeys: Set<ClaimKey>`. Part 2 extends it **by association** (a parallel `Map<UUID,RealmGov>`), not
  by editing the record.
- **`TerritoryEvents`** (on `NeoForge.EVENT_BUS`): `Created` / `Removed` / `Changed`, each carrying an
  `EntitySnapshot`. **Listen, don't poll.** `Removed` must cascade-clean Part 2's tree (drop `RealmGov`,
  detach from parent's `childIds`, re-evaluate or dissolve a federation that drops below its floor) or the
  tree leaks dangling ids. `Changed` invalidates cached jurisdiction footprints.
- **`Role`** enum: `CITIZEN < OFFICER < LEADER`, with `atLeast(Role)`. Part 2's command permissions gate
  on this.

**Thread-safety invariant:** Part 1's resolver is **server-thread only**. Part 2's law handlers run on the
server thread (combat/interaction/command events), so they're in bounds — but **never call
`TerritoryApi.resolve` off-thread** (no async, no render-thread queries).

## 3. The law engine

A law is a typed rule resolved **leaf→root by tier** (highest tier rank wins; a subordinate fills gaps its
parent leaves unset; same-rank ties get a documented tie-break). Each law carries an **enforcement mode**.

### Enforcement modes

| Mode | Acts | Behavior |
| --- | --- | --- |
| `AUTOMATIC` (hard) | the mod | Direct mechanical effect; reserved for government money/state (tax, stipend). Never blocks a physical action. |
| `OFFICER` (hybrid) | human triggers, mod settles | The verdict is a person's; the settlement (coin movement) is automatic (fine). |
| `SOCIAL` (soft) | players / MineColonies guards | Never cancels the action; records the violation and raises a consequence signal. No enforcer present → no effect. |

### Law catalog (MVP marked)

| Law / act | Mode | Notes | MVP |
| --- | --- | --- | --- |
| **PVP** (`ALLOW`/`DENY`) | `SOCIAL` | Unprovoked attacker in a `DENY` jurisdiction → wanted + guard aggro. The one law that proves the whole pipeline. | ✅ |
| TRESPASS / non-member entry | `SOCIAL` | Guard aggro / wanted. Don't duplicate claim-mod build/use protection. | optional |
| CONTRABAND / ITEM_BAN | `SOCIAL` | Banned item in-region → violation. | later |
| TAX (rate) | `AUTOMATIC` | Skim into treasury; Part 2 defines the type + trigger, **Part 3 moves the coin**. | type stubbed in 2a |
| STIPEND (amount, period) | `AUTOMATIC` | Scheduled payout to citizens; Part 2 owns the scheduler + member iteration, Part 3 the credit. | type stubbed in 2a |
| FINE (amount, target) | `OFFICER` | `/realm fine` auto-withdraws via Part 3. | command stubbed in 2a |
| Tier-gated claim allowance / charters | `AUTOMATIC` | Governance grants scaling OPAC claim pools; depends on an OPAC capability spike. | 2b/later |

**Don't re-implement claim protection.** OPAC and MineColonies already hard-protect their claims. Part 2
ships no hard build/break law; unwelcome-but-permitted acts are the `SOCIAL` path instead. This keeps
Part 2 a governance layer, not a third protection system.

## 4. Soft-law enforcement (the lynchpin) and the exception check

The mechanism that makes `SOCIAL` laws real — and the **highest-risk dependency**.

- **Justification check first.** The SOCIAL pipeline is *detect candidate act → justification check →
  only an unjustified act raises the signal*. Model it as a pluggable `Justification` step usable by any
  law. The built-in MVP justification is **self-defense: the first striker is the violator** (track a
  short-lived per-pair aggression record; a retaliating hit inside the combat window is suppressed).
  Strong second MVP justification: **attacking an already-wanted outlaw is open season.**
- **Guard mechanism (leading hypothesis).** MineColonies guards natively attack players ranked into a
  "Fight Guards" rank (the **Hostile** rank by default) via the colony permission system. On a violation
  in a colony-governed jurisdiction, **set the offender's MineColonies rank to a Fight-Guards rank for a
  time-boxed wanted window** (`IPermissions#setPlayerRank(UUID, Rank, Level)` — public API); restore the
  prior rank when it expires. Guards do the rest; "no guards nearby → get away with it" falls out for
  free.
- **Spike before building enforcement:** confirm the rank API on the pinned snapshot, confirm setting it
  actually aggros guards in-game (needs a real instance), and confirm restore semantics (don't strand a
  player as Hostile across a restart).
- **Robust fallback / canonical output:** maintain a decoupled **"wanted" signal** (event / tag /
  capability) that guard aggro, bounties, and a kill-feed overlay all subscribe to, with the rank-flip as
  one consumer. Worth building anyway.
- **Delivery: addon-first.** The rank/permission path covers the core mechanism through MineColonies'
  *public API*, so a **MineColonies addon** is sufficient (prior art: *MineColonies: War 'N Taxes*). A
  **fork** is only justified by guard *behavior the binary rank can't express* (graduated response,
  jailing, pursuit past the border) and costs a pack-wide replacement of a snapshot dependency — treat it
  as a separate, later decision.
- **Edge cases:** OPAC-only jurisdictions and airship (`aeroclaims` sub-level) realms have **no guards** —
  soft enforcement there is purely player/bounty-driven. Hard laws and the wanted signal still apply.

## 5. Tiers, hierarchy, scheduler

- **Tiers:** municipality `SETTLEMENT(0) → VILLAGE(1) → TOWN(2) → CITY(3)`; federation
  `COUNTY(4) → KINGDOM(5) → EMPIRE(6)`. Stored in `RealmGov`, keyed by the territory entity UUID.
- **Promotion** is player-initiated and validated on the command (no per-tick scanning); inputs are free
  MineColonies metrics (citizen count, claimed chunks, Town Hall level). **Federations are composed**, not
  promoted (`/realm federate`, ≥ N consenting members). **`carve`** spawns a recursive child (KINGDOM+).
- **Secession is at-will** — a federation can't hard-block leaving even with a law (mirrors "a law never
  cancels an action"); the only hard hold is an **admin lock** (an IRL wall-clock timer). A `SECESSION =
  DENY` law instead lets the abandoned faction's officer declare the seceder hostile (the entity-scale
  analog of player "wanted", reusing the same machinery).
- **Scheduler:** STIPEND is the one *time*-driven law. Fire on a coarse period (config; never per-tick),
  iterate citizens, pay from the treasury (Part 3), skip when underfunded (no debt), persist "last paid"
  so a restart doesn't double-pay. Everything else is event-driven and position-local.

## 6. Commands

Part 1 already ships `/realm found | info | whogoverns`. Part 2 adds (all `Role`-gated): `promote`,
`federate`, `carve`, `secede`, `member`, `law set|unset|list`, `fine`, `wanted`, `hostility
decide|list`, `charter grant|list|revoke`, and the OP `admin confederate|unlock`. GUI screens and
`/realm map` border rendering are post-MVP polish — commands are the MVP.

## 7. The public surface for Part 3

Part 2 publishes a **pluggable `LawType` registry** plus an **effect hook** so Part 3 registers the
TAX/STIPEND/FINE *money movement* without Part 2 knowing anything about coins. Part 2 owns the *rule,
scope, precedence, schedule, and trigger*; Part 3 owns the *credit/debit*. Version it deliberately
(breaking the registry API = major bump; Part 3 pins a range).

## 8. Risks (ranked)

1. **MineColonies guard targeting** — the whole `SOCIAL` model leans on it; spike first, keep the
   wanted-signal fallback ready.
2. **TAX transaction hook** (shared with Part 3) — no public sale event on Create: Trading Floor was
   found; assume a mixin. Define the TAX *type* now; the *trigger* is a Part 3 spike.
3. **OPAC claim-limit capability** — gates *hard* tier-gated allowances; if absent, degrade to a `SOCIAL`
   over-claim law.
4. **MineColonies snapshot drift** — pin dev coordinates; re-verify the rank API and metric lookups on
   each bump.

---

_Full design, mirrored in this repo: [`GOVERNANCE-REALMS-SCOPE.md`](GOVERNANCE-REALMS-SCOPE.md) ·
[`GOVERNANCE-MOD-SPEC.md`](GOVERNANCE-MOD-SPEC.md). Canonical source: the same files in
`theasshats/project-commonwealth/docs/`._
