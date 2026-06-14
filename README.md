# pcmc-realms

**Player governance — laws and government** for the [Project Commonwealth](https://github.com/theasshats/project-commonwealth)
modpack (Minecraft 1.21.1 / NeoForge). Mod id: `pcmc_realms`.

This mod adds tiers, hierarchy, and a law engine on top of the pack's existing claims and colony
systems: settlements grow into cities, cities federate into kingdoms and empires, and every entity at
every tier can issue laws that cascade down the hierarchy. It introduces **no new claim system** — it
reads the ones the pack already runs and hangs *political* state on top of them.

> ## Status: early development
>
> **Nothing is built yet.** This repository currently holds the design and the license — there is no mod
> code, no Gradle project, and no released jar. `pcmc-realms` is **pre-alpha**: the architecture, law
> types, commands, and behavior described below are **planned, not implemented**, and will change as the
> design is finalized and spiked. Don't depend on anything here yet.
>
> The full design is mirrored here in [`docs/GOVERNANCE-REALMS-SCOPE.md`](docs/GOVERNANCE-REALMS-SCOPE.md)
> (this mod's scope) and [`docs/GOVERNANCE-MOD-SPEC.md`](docs/GOVERNANCE-MOD-SPEC.md) (the whole-trio plan);
> this README and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) summarize them. The **canonical, evolving
> source** lives in the pack repo (issue
> [#260](https://github.com/theasshats/project-commonwealth/issues/260)) — the mirrors note it at their top.

---

## Where this sits — the governance trio

`pcmc-realms` is **Part 2 of three** purpose-built mods that layer player governance onto the pack. Each
lives in its own repo and depends only *downward* — a lower mod never references an upper one.

```
  pcmc-territory (Part 1)          pcmc-realms (Part 2)              pcmc-mint (Part 3)
  ─────────────────────           ────────────────────             ──────────────────
  "who governs this chunk?"  ──►   tiers + hierarchy + LAWS    ──►  treasury + minting
  entities, members, claims        (this repo)                      (registers TAX/STIPEND
  resolver + reverse index         + a pluggable LawType registry    effects into Part 2)
```

| Part | Repo | Role | Depends on |
| --- | --- | --- | --- |
| 1 — Claims | [`theasshats/pcmc-territory`](https://github.com/theasshats/pcmc-territory) (`pcmc_territory`) | The claims/resolution substrate — entities, members, colony/claim binding, "who governs this position?" | — |
| **2 — Government** | **`theasshats/pcmc-realms`** (`pcmc_realms`) | **Tiers, federation hierarchy, the law engine, enforcement modes** | `pcmc_territory` (hard) |
| 3 — Economy | [`theasshats/pcmc-mint`](https://github.com/theasshats/pcmc-mint) (`pcmc_mint`) | Treasury + currency minting; supplies the coin for fiscal laws | `pcmc_realms` + Numismatics (hard) |

**What Part 2 owns:** the tier ladder and promotion; federation composition and the recursive sub-region
tree (parent/child links); member roles for governance acts; and the **law engine** — the typed-law
registry, the leaf→root precedence resolver, and the three enforcement modes. It adds no new claims and
no new entity identity: an entity's UUID, members, colonies, and claim keys all live in Part 1; Part 2
hangs political state off that same UUID in its own persistence.

## The law model — hard vs soft enforcement

The central design idea: **what the mod may automate and what it must leave to people are different
things.** A law carries an *enforcement mode*, and the mode — not the law type — decides whether the mod
acts or merely signals.

| Mode | Who acts | What the mod does | Example |
| --- | --- | --- | --- |
| **`AUTOMATIC`** (hard) | the mod | Applies a mechanical effect directly. Reserved for **money/state the government legitimately controls** — never blocking a player's physical action. | **Tax** skimmed from a transaction; **stipend** paid to citizens. |
| **`OFFICER`** (hybrid) | a human officer triggers; the mod settles | The judgment is a person's; the settlement is automatic. | **Fine**: an officer enacts it, the withdrawal is automatic. |
| **`SOCIAL`** (soft) | the player base / MineColonies guards | **Never blocks the action.** Records the violation and raises a consequence signal (guard aggression, "wanted" status, optional bounty). No enforcer present → nothing happens. | **PvP ban**: making PvP illegal doesn't stop the kill — guards attack the killer; if none are near, they get away with it. |

Rule of thumb for slotting any law: **does it move government money, or restrain a player's action?**
Money → `AUTOMATIC` (or `OFFICER` if a human verdict is needed first); action → `SOCIAL`.

Soft-law enforcement leans on **MineColonies guards** — a violation flips the offender to a "Fight
Guards" rank for a time-boxed *wanted* window, and the colony's guards aggro natively (no guards nearby →
no effect). A justification check runs first, so self-defense and hunting an already-wanted outlaw don't
flag you. This is the highest-risk dependency and is gated behind a spike. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full catalog, the guard mechanism, and the
addon-vs-fork decision.

## Dependencies

- **Hard:** `pcmc_territory` (Part 1) — compiled against its public `api` package only.
- **Soft:** MineColonies (`1.1.1327`, for guard-based soft enforcement) and Open Parties and Claims (`0.26.2`).
- **Later (Part 3):** Numismatics, via the `LawType` effect hook this mod publishes.

The pack runs MineColonies **snapshots**, so the rank API and metric lookups can shift between bumps —
dev coordinates are pinned and re-verified on each bump.

## Planned scope (slices)

- **2a — single-entity laws (MVP):** tier field + promotion; the law engine + leaf→root resolver; the
  three enforcement modes with **PvP (`SOCIAL`) live end to end** (guard integration); TAX/STIPEND/FINE
  types and commands *stubbed* into the registry (no coin until Part 3). No federation yet.
- **2b — hierarchy:** federation composition, recursive `carve`, parent/child links, governance roles;
  the full leaf→root cascade; tier-gated claim allowances and charters.

## How it reaches the pack

Project Commonwealth ships **manifests, not jars**. Custom mods follow the **mod-mirror** pattern (see the
pack's [`docs/CUSTOM-MODS.md`](https://github.com/theasshats/project-commonwealth/blob/main/docs/CUSTOM-MODS.md)):
each mod lives in its own repo, a `build.yml` workflow builds the jar and attaches it to a GitHub release
on a `v*` tag, and the pack references that release asset through a packwiz manifest
(`mods/pcmc-realms.pw.toml`). That manifest is **not** added to the pack until a real, hashed jar exists
for a tagged release — so this mod reaches players only once it's actually built and released.

## Building

There is no build yet. When the Gradle/NeoForge project lands, build and dev instructions will be added
here. The pack's web sandbox cannot compile or run NeoForge; this mod is built and playtested on a real
instance.

## Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — developer orientation: the Part 1 API contract this
  mod compiles against, the law catalog and enforcement mechanism, the slice plan, and the public surface
  for Part 3.
- [`docs/GOVERNANCE-REALMS-SCOPE.md`](docs/GOVERNANCE-REALMS-SCOPE.md) — this mod's full design scope (the
  detailed version of what `ARCHITECTURE.md` summarizes).
- [`docs/GOVERNANCE-MOD-SPEC.md`](docs/GOVERNANCE-MOD-SPEC.md) — the whole-trio technical spec / three-part
  implementation plan (`pcmc-territory` → `pcmc-realms` → `pcmc-mint`).

The two `GOVERNANCE-*` files are **mirrors** of the canonical copies in the pack repo, which stays the
source of truth (see each file's top banner). Not mirrored here:
[`GOVERNANCE.md`](https://github.com/theasshats/project-commonwealth/blob/main/docs/GOVERNANCE.md) (the
governance scoping + survey) and
[`CUSTOM-MODS.md`](https://github.com/theasshats/project-commonwealth/blob/main/docs/CUSTOM-MODS.md) (the
mod-mirror pattern).

## License

[MIT](LICENSE).
