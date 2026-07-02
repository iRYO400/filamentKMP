# Product vision — case-opening simulator (tiered)

> The **end goal** the backlog (Phases B–E) climbs toward. Companion to
> [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) (how) and
> [`FILAMENT_CAPABILITIES.md`](FILAMENT_CAPABILITIES.md) (what the engine can do). This doc = **what
> we're building and why**. Product-level; implementation lives in the phase milestones.

## One-liner
A **free case-opening simulator** that delivers loot-box excitement without spending a cent. The
payoff — a cinematic **3D reveal** — runs on our Filament pipeline. The 3D object *is* the product.

## Why this exists
Phase A proved a reusable Filament ↔ Compose-Multiplatform interop pipeline (both platforms). That
pipeline needs a destination whose climax is literally a 3D object reveal, so the engine is the core
dopamine, not a bolted-on demo. The full app (collections, store, tabs) turned out to be mostly
*app architecture* — which we deliberately **defer**. Near-term intent: **exercise Filament (assets
+ animation)**, not drown in navigation. Hence the tiers below.

## Delivery tiers
| Tier | What it is | Loot | Focus |
|---|---|---|---|
| **preMVP** ✅ **DONE** | Splash → one hero button → **3D box-open reveal** | existing **holo card** | Filament **animation** on the current pipeline |
| **MVP** ← next | Import real **gun models + textures**; maybe RNG/rarity | glTF **guns** | Filament **asset pipeline** (gltfio) |
| **MVP+** | Full app: Cases + Inventory, 3 tabs, economy | guns/cards | app architecture + Phases C/D/E |

Design specifics below (choreography, rarity ladder, mood) are the current **POV** — solid
defaults, flippable as we iterate.

---

## preMVP — Filament reveal attraction ✅ DELIVERED (Android + iOS, owner-approved)
Minimal shell, maximum 3D. Proves box-open animation + reveal choreography on the pipeline we
**already have** — no asset import, no app architecture. Shipped exactly as scoped: procedural box
(two half-cubes + seam glow), card rises + one auto-spin, drag-to-inspect. Navigation landed on
**Navigation 3** (back-stack-as-state), the choreography is a pure shared state machine (`reveal/`),
and the reveal-only engine mount gives lazy start/teardown for free. Implementation detail lives in
[`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md).

**Flow**
- **Splash → Home:** one screen, mostly empty, with a single **large hero button** ("OPEN") in a
  bottom bar. No tabs, no lists.
- Tap OPEN → a **reveal screen** (full-bleed Filament): a **procedural 3D lootbox** plays an **open
  animation**, a **holo card rises out**, auto-spins once, then the user **drags to inspect**
  (reuses the A3 flick-to-spin inertia). "Open again" / back → Home.

**Scope (locked)**
- **Loot = the existing card** (reuse the `quad`/card renderer). **Box = procedural geometry** (we
  already build quads/primitives in `FilamentRenderer`; owner has loot models but no box model).
- **Pure showcase:** no randomness, no rarity, no economy, no persistence, no lists/tabs.

**Reveal choreography (POV — flippable)**
Idle bob → tap → shake/anticipation → seams crack with a light leak → scale-pop → box splits/fades →
card rises + one auto-spin → settle → drag to inspect. All transform/material-driven; particles
optional (deferred to Phase B2).

> The **CS2-style 2D ticker is dropped** — the reveal is a Filament-native **3D box-open**. (A
> ticker, if ever, is an MVP+ flourish.)

---

## MVP — real assets (interpolation, later)
Swap procedural for **imported glTF gun models with textures** (gltfio → **Phase B1**). Optionally
add randomness / a small **rarity flourish** and a minimal "last opened". The interpolation point
between preMVP and MVP+ — scoped when we get there.

Rarity ladder (POV, kicks in here — flippable): Common (steel gray) → Uncommon (blue) → Rare
(purple) → Epic (magenta) → Legendary (red) → **Mythic "Golden"** (animated gold, jackpot). Odds
descend steeply.

---

## MVP+ — the full vision (deferred north star)
The complete app:
- **3 bottom tabs**, center = elevated hero button: **Center = Open · Left = Cases · Right =
  Inventory.**
- **Cases:** catalog of case types (cover art + name + rarity-odds bar).
- **Inventory:** grid of collected loot, filter/sort by rarity, per-rarity counters; tap → 3D
  viewer.
- Economy (soft currency / keys / cooldowns), trade-up / recycle, profile/stats, sound, social.

**Visual mood (POV — flippable):** dark, premium, high-contrast; neon rarity accents,
glassmorphism, motion-rich; matches the dark-navy Filament stage.

---

## Backlog ↔ product (re-sequenced)
Why each phase now has a purpose, and *when* it pays off:
- **preMVP** builds on **Phase A (done)** + a small **procedural animation** layer (box open +
  reveal choreography) — light, no glTF. Exercises Filament *animation*, not asset import.
- **Phase B1 (glTF pipeline)** → **MVP** (import gun models with textures).
- **Phase B2 (holographic material / particles)** → makes loot pop; preMVP nice-to-have, MVP polish.
- **Phases C / D / E** (module split, lazy-load/warm-up, device tiers) → **MVP+**, where
  collections/grids and multiple engines make them matter.

## Screens for design (when needed)
- **preMVP:** (1) Home with hero button, (2) reveal screen across its beats, (3) post-reveal /
  "open again" state.
- **MVP+ (later):** Cases catalog, case detail, Inventory grid, single-item 3D viewer.
The Claude Design prompt for the full vision lives in the plan file history; regenerate from the
sections above per tier as needed.
