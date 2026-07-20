# Bewitchment (placeholder name) — CLAUDE.MD (Post-Prototype / Main Stage)

STATUS: Prototype build COMPLETE. All attachments are loosely functional UNLESS listed in the
NOT PROTOTYPED master list below. This document merges the concrete attachment specs with the full
system framework (costs, formulas, items, blocks, UI, workflow) and is the single source of truth.
Where older notes conflict with this file, this file wins.
Totals: **50 curses, 45 blessings, 11 neutrals, 15 globals, 15 modifiers.**

---

## 0. MASTER LISTS — read before touching code

### 0.1 NOT PROTOTYPED (⛔ NO CLASS EXISTS YET — these must be built from scratch)
Everything NOT on this list already has a working class from the prototype.

**Curses (8):**
Super Explosive, Claustrophobia, Moonwalker, Siren's Call, Loading Screen, Pacing, Trumpet,
Uncareful (restored — see its entry).

**Blessings (15):**
Thick Skinned, Farmer's Spirit, Brute, Blacksmith, Unseen, Silver Tongue, Hot Stuff, Bouncy,
Excavation, Angler, Laugh Track, Chat, Civilisation, Low Gravity — plus Last Stand's
blessing-consumption behavior if the prototype lacks it.

### 0.2 CUSTOM SOUND attachments (need modded SoundEvents; OGG list in Section 12)
**Curses (7):** Delusions, Gassy, Slippery Feet, Aura, Loading Screen, Pacing, Trumpet.
**Blessings (5):** Main Character, Last Stand, Brute, Bouncy, Laugh Track.
**System (2):** discovery chime, global warning sting.

### 0.3 CUSTOM UI attachments (need HUD/overlay work beyond the 3 main screens)
Gluttony (second hunger bar), Thirst Meter (droplet bar — FULLY FUNCTIONAL required),
Loading Screen (fullscreen fake-load overlay — FULLY FUNCTIONAL required),
Organised (extra inventory row), Chat (twitch overlay).

### 0.4 CUSTOM ENTITIES
Bodyguard (armoured sunglasses-skeleton, 3-state AI, dialogue),
Tax Man (playermodel entity, custom skin/nametag, loot + delivery AI),
Delusion fake players (client-side, real-skin mirroring, state machine).

---

## 1. What This Mod Is

Bewitchment is a comedic multiplayer Minecraft mod (Fabric/NeoForge 1.21.1) built around a
curse/blessing economy. Players use a **Bewitching Table** to spend **Cursed Essence** and a
**Sacrificial Item** (which selects the specific effect) on a target identified by **Player
Essence**. Effects are timed, invisible in detail to the victim, and discoverable — each unlocks
a **Compendium** page on first occurrence. **Neutrals** are small flavour events (also the landing
pad for ritual failures), **Globals** are rare server-wide events funded from a shared bank, and
**Backfires** punish failed rituals. Counterplay: **Ward** (personal reflect), **Warding Totem**
(area block), **Jar** (capture), **Ledger** (public who-cursed-whom log), **Purifying Water**
(timer burn-down).

Foundational vanilla change: **budding amethyst is collectable via Silk Touch.**

---

## 2. Core Rules (Main Foundations)

1. **NO STACKING.** The same attachment can never be active twice on one player. Reapplying an
   active attachment keeps whichever duration is LONGER (`ATTACHMENT_REAPPLY_POLICY = KEEP_LONGER`).
   Never additive, never refresh-to-shorter.
2. Discovery: an attachment is **discovered** the first time its effect occurs. Victim discovers on
   trigger; caster discovers the instant they successfully send it. On discovery the player is
   **alerted in chat** and the Compendium updates from rumour → discovered instantly.
3. **Limit: 3** active curses/blessings per player (config: `Limit`). Exceeding risks backfire.
   Casting on an already-affected target costs more each time (escalating cost).
4. Durations: random **30–60 min** unless an attachment states an override. Effects are curable/
   forwardable but **do NOT expire on death** (exception: Recovery Compass modifier).
5. Globals/Neutrals have **no item-based counterplay** by design — counter them through play.
6. Victims cannot see WHICH attachments they have, but carry a modded status effect
   (**Cursed** / **Blessed** / **Afflicted** for globals) showing particles ONLY on initial onset
   and expiry — never in between.
7. All numeric values are **named balancing constants** in per-attachment config
   (`config/attachments/<name>.json` or per-attachment record) — tunable without logic changes.
8. `(CUSTOM SOUND)` = at least one modded SoundEvent required. Register all SoundEvents at init
   with placeholder audio; OGGs supplied later (master list, Section 12).
9. Sacrificial item matching is **EXACT-ITEM**, never tag-based, except two explicit exceptions:
   Hype Man (`minecraft:music_discs` tag) and Party Time ("any candle" tag). (Main Character was formerly
   a third, type-matching "any Firework Star"; it moved to Fire Charge — a plain exact-item — when the
   Section 11 collision was resolved, so that exception is retired.)

---

## 3. Blocks

### Bewitching Table / Altar — Recipe: Diamond, Cursed Essence, Wood
Slots: **Player Essence** (target; empty = self), **Sacrificial Item** (selects effect),
**Cursed Essence** (currency/success), **Modifier** (optional).
Failures → Neutral event, curse onto caster (Mirror backfire), or (rare, high-risk) the table
exploding.
**Special: Redstone Dust in the Sacrificial slot** = random attachment applied to the target,
weighted toward lower-cost attachments (`REDSTONE_RANDOM_LOW_BIAS = 1.5` inverse-cost weighting).

### Block of Cursed Essence — Recipe: 9 Cursed Essence
Storage block; currency unit for the server-wide **Global bank**.

### Ledger — Recipe: Wood, Compendium
Lectern-style, read-only. Logs all nearby hex attempts sent/received, including blocked ones and
whether they landed.

### Warding Totem
Radius shield: players inside repel all curses/blessings but receive no feedback on blocks.

### Purifying Water — Recipe: Water Bucket, Amethyst, Diamond, Cursed Essence (+more TBD)
Unique fluid; bathing rapidly burns down attachment timers
(`PURIFY_TIMER_BURN_MULT = 20.0` — 1 real second removes 20 seconds of effect time).

---

## 4. Items

| Item | Function | Recipe / Source |
|---|---|---|
| Cursed Essence | Currency | Smelt cursed items / Amethyst |
| Player Essence | Targeting | Bottle on a player or their current bed |
| Compendium | Discovery/rumour/tutorial/item-guide book UI, stored per world | Book + Cursed Essence |
| Voodoo Doll | Bound to a named player; forwards curses cast while it's in inventory; fails if target dead/offline; has durability; still Ledger-logged | Wool, Wood, Player Essence |
| Needle | Right-click on a Doll in inventory → direct damage to bound target; consumes Doll durability | Iron Ingot, Iron Nugget |
| Ward | Necklace; deflects spells back to sender, particle-points toward attacker; durability | Emerald, Cursed Essence, String |
| Scrying Mirror | Reveals your own active attachments | Glass, Cursed Essence, Diamond |
| Effigy | Forwards your curses to another player on click | Totem of Undying, Cursed Essence |
| Cursed Coin | Gambles a random curse OR blessing | Gold, Cursed Essence |
| Blessed Coin | Random blessing | Loot tables only |
| Executioner's Coin | Revives, inflicts random curse | Loot tables only |
| Jar | Right-click another player to capture an active curse; expensive; breaks on use; intentionally 2-player | Netherite Ingot, Cursed Essence, Glass |
| Cursed Jar | Pre-filled Jar variant, flavourtext-named | — |
| Amethyst Bell | Bell retexture; flips a random active effect, cooldown | — |

---

## 5. CURSES (50)

Format: Name — Sacrificial Item, then behavior, then constants.
All previously-specced constants carry over; entries below reflect the CURRENT item mappings and
any spec changes. `(NOT PROTOTYPED)` = still to build.

### Violence — Iron Sword
Chance to auto-swing at nearest entity (players prioritized); real camera/animation/enchantments;
intentionally does NOT consume attack cooldown; swing timing biases toward ledge/hazard shoves.
```
VIOLENCE_CHECK_INTERVAL=40  VIOLENCE_TRIGGER_CHANCE=0.15  VIOLENCE_TARGET_RANGE=3.5
VIOLENCE_PLAYER_PRIORITY_MULT=3.0  VIOLENCE_HAZARD_BIAS_RANGE=4  VIOLENCE_HAZARD_BIAS_MULT=4.0
```

### Butterfingers — Milk Bucket
Rare passive item drops on shared internal cooldown; boosted on damage taken / tool swing.
```
BUTTERFINGERS_COOLDOWN=600  BUTTERFINGERS_PASSIVE_CHANCE=0.03  BUTTERFINGERS_PASSIVE_INTERVAL=100
BUTTERFINGERS_ON_DAMAGE_CHANCE=0.35  BUTTERFINGERS_ON_SWING_CHANCE=0.15  BUTTERFINGERS_DROP_FROM_HOTBAR=true
```

### Explosive — Gunpowder
Explodes on death; world damage gated by mobGriefing.
```
EXPLOSIVE_POWER=4.0  EXPLOSIVE_CREATES_FIRE=false
```

### Super Explosive — TNT (NOT PROTOTYPED)
Small chance to explode on TAKING damage; world damage gated by mobGriefing.
```
SUPER_EXPLOSIVE_ON_HIT_CHANCE=0.08  SUPER_EXPLOSIVE_POWER=2.5
SUPER_EXPLOSIVE_SELF_DAMAGE=true  SUPER_EXPLOSIVE_COOLDOWN=200
```

### Popularity — Bell
Hostile spawns up, detection radius up, hostiles ALWAYS re-prioritize the victim.
```
POPULARITY_SPAWN_RATE_MULT=3.0  POPULARITY_SPAWN_RADIUS=48
POPULARITY_DETECTION_RADIUS=48  POPULARITY_RETARGET_INTERVAL=40
```

### Yap — Paper
Uncontrollable chat from writable list; occasional player-info-templated specials; prefers unsaid
entries.
```
YAP_INTERVAL_MIN=1200  YAP_INTERVAL_MAX=4800  YAP_SPECIAL_ENTRY_CHANCE=0.10  YAP_NO_REPEAT_WINDOW=10
List: data/bewitchment/text/yap.json
```

### Green Aura — Rotten Flesh
Green gas + tiny-fly particles; non-undead mobs flee; nearby players auto-drift away.
```
GREEN_AURA_MOB_FLEE_RADIUS=8  GREEN_AURA_PLAYER_DRIFT_RADIUS=3
GREEN_AURA_PLAYER_DRIFT_FORCE=0.04  GREEN_AURA_PARTICLE_INTERVAL=10
```

### Repel — Water Bucket
Ground items + XP slide away from victim, will roll off ledges.
```
REPEL_RADIUS=6  REPEL_FORCE=0.05  REPEL_TICK_RATE=5
```

### Echoes — Echo Shard
Client-only hallucinated game sounds + fake chat messages from list.
```
ECHOES_SOUND_INTERVAL_MIN=600  ECHOES_SOUND_INTERVAL_MAX=2400  ECHOES_CHAT_CHANCE=0.25
Pool: creeper_primed, footsteps, zombie_ambient, door_open, chest_open, arrow_hit, tnt_primed, enderman_stare
List: data/bewitchment/text/echoes_chat.json
```

### Delusions — Ender Pearl (CUSTOM SOUND)
Client-side fake players using real server skins/nametags (incl. victim's own). Vanish in smoke on
hit/collision. States: wandering, sprinting, teleporting, mining, flying, punching, sneaking,
waving, idle, dancing, twerking, jumping, spinning, realisation (turn + stare + vanish). Can charge
the victim when spotted.
```
DELUSIONS_MAX_CONCURRENT=2  DELUSIONS_SPAWN_INTERVAL_MIN=1200  DELUSIONS_SPAWN_INTERVAL_MAX=6000
DELUSIONS_SPAWN_RANGE_MIN=12  DELUSIONS_SPAWN_RANGE_MAX=32  DELUSIONS_LIFETIME_MAX=1200
DELUSIONS_STATE_SWAP_INTERVAL=200  DELUSIONS_REALISATION_CHANCE=0.15  DELUSIONS_CHARGE_CHANCE=0.08
Sounds: bewitchment:curse.delusions.vanish, bewitchment:curse.delusions.whisper
```

### Gluttony — Cake (CUSTOM UI)
Second hunger bar (extra UI row), bigger model, sprint cutoff at double vanilla threshold.
```
GLUTTONY_MODEL_SCALE=1.35  GLUTTONY_EXTRA_HUNGER_MAX=20  GLUTTONY_EXTRA_DRAIN_MULT=1.5
GLUTTONY_SPRINT_CUTOFF=12
```

### Gassy — Pufferfish (CUSTOM SOUND)
Random farts launch in random directions/velocities, biased toward ledges/hazards, can be upward;
nearby explosions/damage also trigger at higher velocity.
```
GASSY_INTERVAL_MIN=1200  GASSY_INTERVAL_MAX=3600  GASSY_VELOCITY_MIN=0.6  GASSY_VELOCITY_MAX=1.4
GASSY_HAZARD_BIAS_MULT=3.0  GASSY_ON_DAMAGE_CHANCE=0.25  GASSY_ON_EXPLOSION_CHANCE=0.60
GASSY_EVENT_VELOCITY_MULT=1.5
Sounds: bewitchment:curse.gassy.fart_small, bewitchment:curse.gassy.fart_large
```

### Farmhand — Wheat
Passive animals' AI overridden to stand in your way; only panic/flee-on-hurt outranks it (they
return after).
```
FARMHAND_RADIUS=16  FARMHAND_MAX_RECRUITS=8  FARMHAND_REPATH_INTERVAL=20  FARMHAND_BLOCKING_DISTANCE=1.0
```

### Heavy — Iron Ingot
Fall speed + damage up; long falls crater (damage/knockback/particles to victim + nearby; block
damage per mobGriefing).
```
HEAVY_FALL_SPEED_MULT=1.8  HEAVY_FALL_DAMAGE_MULT=1.75  HEAVY_CRATER_MIN_FALL=8
HEAVY_CRATER_RADIUS=3  HEAVY_CRATER_DAMAGE=6.0  HEAVY_CRATER_KNOCKBACK=1.2
```

### Slippery Feet — Ice (CUSTOM SOUND)
Standing near ledges: chance of slide-whistle + shove off.
```
SLIPPERY_CHECK_INTERVAL=60  SLIPPERY_TRIGGER_CHANCE=0.10  SLIPPERY_LEDGE_DROP_MIN=2  SLIPPERY_PUSH_FORCE=0.35
Sound: bewitchment:curse.slippery.slide_whistle
```

### Magnet — Lodestone
Nearby projectiles' velocity spoofed toward the victim.
```
MAGNET_RADIUS=16  MAGNET_STEER_STRENGTH=0.15  MAGNET_AFFECTS_OWN=false
```

### Neutral Aggression — Spider Eye
All neutral mobs auto-aggro the victim.
```
NEUTRAL_AGGRO_RADIUS=24  NEUTRAL_AGGRO_CHECK_INTERVAL=40
```

### Dwarfism — Turtle Egg
Half-size model; right-click entities to ride; reduced walk distance.
```
DWARFISM_MODEL_SCALE=0.5  DWARFISM_SPEED_MULT=0.75  DWARFISM_CAN_RIDE_HOSTILES=true  DWARFISM_RIDE_CONTROL=false
```

### Screensaver — Painting
Window bounces DVD-style; occasional, slow, large; slow ease in/out. Client-only; no-op if window
manipulation unavailable.
```
SCREENSAVER_TRIGGER_INTERVAL_MIN=4800  SCREENSAVER_TRIGGER_INTERVAL_MAX=12000
SCREENSAVER_EPISODE_DURATION=400  SCREENSAVER_WINDOW_SPEED_PX=2  SCREENSAVER_TRANSITION_TICKS=60
```

### Minor Inconvenience — Cobweb
Fullscreen blocked; window renamed to dumb titles from a list. Client-only.
```
MINOR_INCONV_RENAME_INTERVAL_MIN=2400  MINOR_INCONV_RENAME_INTERVAL_MAX=9600
List: data/bewitchment/text/window_titles.json
```

### Social Outcast — Wither Rose
Other players invisible on victim's client unless very close OR recently damaged the victim
(re-hidden after no damage for a period).
```
OUTCAST_REVEAL_DISTANCE=4  OUTCAST_DAMAGE_REVEAL_TIME=400
```

### Floor Is Lava — Magma Block
Constant damage while stationary; grace period for crafting etc.
```
FIL_STATIONARY_GRACE=100  FIL_DAMAGE=1.0  FIL_DAMAGE_INTERVAL=20  FIL_MOVEMENT_RESET_DIST=0.5
FIL_PAUSED_IN_GUIS=false
```

### Heavyweight — Iron Block
Blocks with air beneath break under you; time scales with hardness.
```
HEAVYWEIGHT_BASE_BREAK_TICKS=60  HEAVYWEIGHT_HARDNESS_MULT=60
HEAVYWEIGHT_UNBREAKABLE_SKIP=true  HEAVYWEIGHT_RESPECTS_GRIEFING=true
```

### Thirst Meter — Water Bottle (CUSTOM UI — FULLY FUNCTIONAL UI REQUIRED)
Replica hunger bar with droplet icons; drains slower than hunger; refill via water bottles or
right-clicking water (raw water risks hunger/poison).
```
THIRST_MAX=20  THIRST_DRAIN_MULT=0.75  THIRST_BOTTLE_RESTORE=6  THIRST_RAW_WATER_RESTORE=4
THIRST_RAW_WATER_DEBUFF_CHANCE=0.25  THIRST_EMPTY_EFFECTS=[slowness_1,weakness_1]  THIRST_EMPTY_DAMAGE=0.0
```

### Bad Swimmer — Copper Ingot
Liquids act as air for buoyancy: instant sink, walk along the bottom, still can't breathe.
```
BAD_SWIMMER_SINK_SPEED_MULT=1.0  BAD_SWIMMER_ALLOW_SWIM_INPUT=false
```

### Pests — Cobblestone
Chance to spawn silverfish on mining.
```
PESTS_CHANCE=0.06  PESTS_MAX_PER_TRIGGER=1  PESTS_ORE_BONUS_CHANCE=0.12
```

### Allergic — Sweet Berries
One category rolled at application. Forbidden food = big damage + half a hunger point.
**Categories use FOOD-PROPERTY CATEGORIES, not hardcoded lists, for modded-food compatibility**
(classify by item food properties: cooked flag/smelting result, meat tag, effect-bearing = magic,
plant-derived = natural; fall back to tags for edge cases).
Naturalist (no cooked) | Vegetarian (no meat) | Anti-magic (no magic foods) | Carnivore (no plants).
```
ALLERGIC_DAMAGE=6.0  ALLERGIC_HUNGER_RESTORED=1  ALLERGIC_CATEGORY_ROLL=uniform(4)
Category tags (fallback): data/bewitchment/tags/foods/{cooked,meat,magic,natural}.json
```

### Comic Relief — Lightning Rod
Low HP → chance of comedically-timed instakill lightning; item piles nearby may be struck and
destroyed; rare posthumous strike (intentional).
```
COMIC_LOW_HP_THRESHOLD=6.0  COMIC_CHECK_INTERVAL=100  COMIC_STRIKE_CHANCE=0.05
COMIC_ITEM_PILE_MIN=5  COMIC_ITEM_STRIKE_CHANCE=0.10  COMIC_POSTHUMOUS_CHANCE=0.02
```

### Ugly — Carved Pumpkin
Skin swapped for all clients to a random ugly skin from mod files.
**Skin location: `assets/bewitchment/textures/entity/skins/ugly/ugly_0.png, ugly_1.png, ...`**
(64x64 standard player skin format; one selected at random on application; ship >= 3.)

### Taxes — Emerald
Chest-scanner near victim; enough valuables → Tax Man entity (playermodel, custom skin/nametag)
loots valuables into world bank; hard cap; **curse consumed after collection**.
```
TAXES_SCAN_RADIUS=12  TAXES_MIN_VALUE_TRIGGER=16  TAXES_HAUL_CAP=128
TAXES_TAXMAN_LOOT_SPEED=20  TAXES_TAXMAN_HEALTH=100.0  TAXES_BANK_STORAGE_CAP=512
Value points: iron=1, gold=2, emerald=3, diamond=4, netherite=16; blocks = 9x
```

### Sticky — Honey Bottle
No armour removal, no drop/throw; containers still work; death drops unaffected.
```
STICKY_BLOCKS_Q_DROP=true  STICKY_BLOCKS_ARMOR_SLOTS=true  STICKY_BLOCKS_CONTAINERS=false
STICKY_BLOCKS_DEATH_DROPS=false
```

### Backseat Driver — Saddle
While riding, AI can seize full control; prioritizes stupid actions (ledges/lava/water) else
wanders; ends on dismount; early dismount shortens NEXT cooldown.
```
BACKSEAT_TRIGGER_CHANCE=0.20  BACKSEAT_CHECK_INTERVAL=200  BACKSEAT_EPISODE_DURATION=200
BACKSEAT_COOLDOWN=1200  BACKSEAT_EARLY_EXIT_COOLDOWN=600  BACKSEAT_HAZARD_SEEK_RANGE=12
```

### Clumsy — Egg
Occasional misplaced blocks (wrong adjacent position or orientation).
```
CLUMSY_CHANCE=0.10  CLUMSY_WRONG_POS_WEIGHT=0.5  CLUMSY_MAX_OFFSET=1
```

### Oversharer — Empty Map
Periodic server-wide broadcasts of personal info (coords, biome, Y, spawn, held item, armour) in
goofy templated lines.
```
OVERSHARER_INTERVAL_MIN=2400  OVERSHARER_INTERVAL_MAX=7200  Scope: server chat
List: data/bewitchment/text/oversharer.json (templates: {coords},{biome},{y},{spawn},{held},{armour})
```

### Aura — Note Block (CUSTOM SOUND)
Long-form music follows the victim, audible to all nearby. Long tracks require a positional-audio
re-anchor loop (move the playing sound instance to the victim), NOT re-firing short loops.
```
AURA_AUDIBLE_RADIUS=24  AURA_REANCHOR_INTERVAL=10  AURA_TRACK_GAP=100
Sounds: bewitchment:curse.aura.track_1 / track_2 / track_3
```

### Broken Bonds — Lead
Nearby tamed mobs may untame and pathfind away, preserving other NBT (names, dyes) where possible.
```
BROKEN_BONDS_RADIUS=16  BROKEN_BONDS_CHECK_INTERVAL=1200  BROKEN_BONDS_CHANCE=0.15
BROKEN_BONDS_FLEE_DISTANCE=48
```

### Insomniac — Phantom Membrane
Bed blocked; silly rejection message from list. Vanilla insomnia timer still rises.
```
List: data/bewitchment/text/insomniac.json  INSOMNIAC_COUNTS_FOR_PHANTOMS=true
```

### Flat Footed — Goat Horn
Footsteps heavily amplified + slight camera shake for nearby players.
```
FLATFOOT_AUDIBLE_RADIUS=24  FLATFOOT_VOLUME_MULT=3.0  FLATFOOT_SHAKE_RADIUS=8  FLATFOOT_SHAKE_STRENGTH=0.15
```

### Wonky — Feather
Subtle movement-direction noise while moving; amplified sprinting.
```
WONKY_WALK_DRIFT_STRENGTH=0.015  WONKY_SPRINT_MULT=2.5  WONKY_NOISE_PERIOD=40
```

### Stick Drift — Fishing Rod
Direction + mode (movement/camera) rolled once at start; random episodes; strength inversely
proportional to episode duration; direction always consistent.
```
STICKDRIFT_MODE_CAMERA_CHANCE=0.5  STICKDRIFT_EPISODE_INTERVAL_MIN=600  STICKDRIFT_EPISODE_INTERVAL_MAX=2400
STICKDRIFT_STRENGTH_MIN=0.02  STICKDRIFT_STRENGTH_MAX=0.12
STICKDRIFT_DURATION_AT_MIN=300  STICKDRIFT_DURATION_AT_MAX=60
```

### Basement Dweller — Grass Block
Direct sunlight damages; any helmet reduces (not removes).
```
BASEMENT_DAMAGE=1.0  BASEMENT_DAMAGE_INTERVAL=40  BASEMENT_HELMET_MULT=0.5  BASEMENT_REQUIRES_SKY=true
```

### Claustrophobia — Cobbled Deepslate (NOT PROTOTYPED)
Being indoors (no sky access) hurts; milder than Basement Dweller.
```
CLAUSTRO_DAMAGE=0.5  CLAUSTRO_DAMAGE_INTERVAL=40  CLAUSTRO_GRACE=200
```

### Glass Cannon — Glass Block
```
GLASS_CANNON_DAMAGE_TAKEN_MULT=2.0  GLASS_CANNON_DAMAGE_DEALT_MULT=1.5
```

### Mansplainer — Written Book (a signed book)
States the obvious in chat per action category: mining, building, chests, fighting, idling,
boating, crafting.
```
MANSPLAINER_TRIGGER_COOLDOWN=1200  MANSPLAINER_TRIGGER_CHANCE=0.30
List: data/bewitchment/text/mansplainer.json (per-category sub-lists)
```

### Moonwalker — End Stone (NOT PROTOTYPED)
Cannot walk forward normally (forward input reversed). Shorter duration override.
```
MOONWALKER_DURATION_OVERRIDE_MIN=6000  MOONWALKER_DURATION_OVERRIDE_MAX=12000  MOONWALKER_MODE=REVERSED
```

### Siren's Call — Heart of the Sea (NOT PROTOTYPED)
"Longing" stat builds while out of water: fatigue/yearning → physical pull toward nearest water.
Drowned are passive/protective toward the victim.
```
SIREN_LONGING_MAX=100  SIREN_LONGING_GAIN_PER_SEC=0.5
SIREN_STAGE1_THRESHOLD=40 (mining fatigue I + yearning actionbar)
SIREN_STAGE2_THRESHOLD=70 (slowness I on land)
SIREN_STAGE3_THRESHOLD=90 (pull active)
SIREN_PULL_FORCE=0.06  SIREN_WATER_SEARCH_RADIUS=48  SIREN_CLEAR_RATE_IN_WATER=5.0  SIREN_DROWNED_PASSIVE=true
```

### Loading Screen — Glistering Melon (NOT PROTOTYPED) (CUSTOM UI — FULLY FUNCTIONAL) (CUSTOM SOUND)
Bethesda joke: doors/trapdoors/fence gates trigger a fullscreen fake loading screen + random
useless tip.
```
LOADING_SCREEN_DURATION=60  LOADING_SCREEN_CHANCE=0.5  LOADING_SCREEN_COOLDOWN=400
List: data/bewitchment/text/loading_tips.json
Sound: bewitchment:curse.loading.ambience
UI: black overlay + spinner + tip text, client render layer
```

### Pacing — Tropical Fish (NOT PROTOTYPED) (CUSTOM SOUND)
One Piece joke: epic actions (landing a hit, sprint-jumping) freeze victim + involved enemy and
hijack the camera for dramatic cuts/closeups of nearby entities, then release.
```
PACING_TRIGGER_CHANCE=0.10  PACING_COOLDOWN=1200  PACING_FREEZE_DURATION=80
PACING_CAMERA_CUTS=3  PACING_AFFECTS_ENEMY=true
Sound: bewitchment:curse.pacing.sting
```

### Trumpet — Cookie (NOT PROTOTYPED) (CUSTOM SOUND)
Cartoonish fat-trumpet MUSIC plays while moving (looping track that starts/stops with movement,
not per-step toots).
```
TRUMPET_START_DELAY=5      // ticks of movement before music starts
TRUMPET_STOP_GRACE=20      // ticks of stillness before it cuts
TRUMPET_AUDIBLE_RADIUS=16
Sounds: bewitchment:curse.trumpet.walk_loop, bewitchment:curse.trumpet.stop_sting
```

### Uncareful — Flint (NOT PROTOTYPED — restored per decision)
Durability damage to tools/armour is greatly increased.
```
UNCAREFUL_DURABILITY_MULT=3.0
```

---

## 6. BLESSINGS (45)

### Fortune — Diamond
```
FORTUNE_BONUS_DROPS_MIN=1  FORTUNE_BONUS_DROPS_MAX=2  (additive AFTER enchant Fortune, never multiplicative)
Applies to ore-tag blocks only
```

### Peace — Poppy
```
PEACE_SPAWN_RATE_MULT=0.3  PEACE_RADIUS=48  PEACE_DETECTION_MULT=0.4
```

### Luck — Rabbit's Foot
```
LUCK_ATTRIBUTE_BONUS=5.0
```

### Full — Bread
```
FULL_HUNGER_FROZEN=true  FULL_SATURATION_FROZEN=true
```

### Army — Shield
Hostiles neutral to you; attack different-type entities that damage you.
```
ARMY_RADIUS=24  ARMY_DEFEND_DURATION=600  ARMY_SAME_TYPE_EXCLUDED=true
```

### Reflect — Turtle Shell
```
REFLECT_RETURN_VELOCITY_MULT=1.5  REFLECT_DAMAGE_NEGATED=true  REFLECT_ACCURACY=0.85
```

### Soul Bond — Totem of Undying
Nearest player becomes the bond victim (takes half your damage); treated as a CURSE on them
(counts toward their limit, shows Cursed status); continuously rebinds to nearest.
```
SOULBOND_DAMAGE_SHARE=0.5  SOULBOND_REBIND_INTERVAL=100  SOULBOND_MAX_RANGE=32  SOULBOND_VICTIM_IS_CURSED=true
```

### Bodyguard — Bone (CUSTOM ENTITY)
Armoured sunglasses-skeleton. WARNING (chat dialogue trees) → AGGRESSION (warning hits) →
ATTACKING (pursue until death or leash). Chats between all stages. Its death breaks the blessing
instantly.
```
BODYGUARD_HEALTH=60.0  BODYGUARD_DAMAGE=6.0  BODYGUARD_WARNING_RADIUS=8  BODYGUARD_AGGRESSION_RADIUS=4
BODYGUARD_WARNING_HIT_DAMAGE=1.0  BODYGUARD_LEASH_RANGE=32  BODYGUARD_DIALOGUE_COOLDOWN=200
List: data/bewitchment/text/bodyguard.json
```

### Tax Man — Gold Ingot
Delivers your banked taxed valuables. Waits for safety (no hostiles) + slight idle; Tax Man walks
up, drops haul, despawns; blessing breaks.
```
TAXMAN_SAFE_RADIUS=16  TAXMAN_IDLE_REQUIRED=100  TAXMAN_DELIVERY_CAP=512  TAXMAN_WALK_IN_DISTANCE=12
```

### Hype Man — Any Music Disc (tag: minecraft:music_discs — EXPLICIT TAG EXCEPTION)
Actions trigger nearby-player praise: combat, pickups, chest looting, existing nearby.
```
HYPEMAN_TRIGGER_COOLDOWN=900  HYPEMAN_TRIGGER_CHANCE=0.35  HYPEMAN_RADIUS=16
List: data/bewitchment/text/hypeman.json
```

### Workman — Netherite Scrap
```
WORKMAN_TOOLS_NO_DURABILITY=true  WORKMAN_ARMOR_NO_DURABILITY=true
```

### Pickpocket — String
```
PICKPOCKET_RADIUS=1.5  PICKPOCKET_CHECK_INTERVAL=100  PICKPOCKET_BASE_CHANCE=0.05
PICKPOCKET_BEHIND_MULT=4.0  PICKPOCKET_HOTBAR_WEIGHT=0.1
```

### Windfall — Wind Charge
```
WINDFALL_INTERVAL_MIN=2400  WINDFALL_INTERVAL_MAX=7200  WINDFALL_BENEFICIAL_WEIGHT=0.85
Loot: data/bewitchment/loot/windfall.json  (drifts by with wind particles; despawns after 600t if ignored)
```

### Immortality — Ghast Tear
Death → recovery state: items drop, immobile, slow rebuild, respawn at death spot. Respawn time
grows per use; breaks after third.
```
IMMORTALITY_RECOVERY_BASE=600  IMMORTALITY_RECOVERY_GROWTH=2.0  IMMORTALITY_MAX_USES=3
IMMORTALITY_VULNERABLE=true  IMMORTALITY_DROPS_ITEMS=true
```

### Sixth Sense — Compass
```
SIXTHSENSE_INTERVAL=1200  SIXTHSENSE_STRUCTURE_RANGE=128  SIXTHSENSE_PLAYER_RANGE=64
Vagueness: cardinal direction only, actionbar
```

### Iron Stomach — Raw Chicken
```
IRONSTOMACH_NEGATIVE_FOOD_EFFECTS=cancelled  IRONSTOMACH_HUNGER_MULT=1.25  IRONSTOMACH_SATURATION_MULT=1.5
```

### Iron Lung — Kelp
```
IRONLUNG_BREATHE_IN_BLOCKS=true  IRONLUNG_BREATHE_IN_WATER=true  (wall-clip damage still blocked)
```

### Anchor — Chain
```
ANCHOR_KNOCKBACK_IMMUNE=true (all sources incl. explosions)
```

### Twinkletoes — Hay Bale
```
TWINKLETOES_FALL_DAMAGE_IMMUNE=true
```

### Personal Trainer — XP Bottle
```
TRAINER_VILLAGER_XP_MULT=3.0
```

### Studious — Book
```
STUDIOUS_XP_MULT=2.5
```

### Twist of Fate — Nether Star
```
TWIST_NEGATE_CHANCE=0.12 (per incoming damage event, particle burst)
```

### Company — Bone Meal
```
COMPANY_ACQUIRE_RADIUS=16  COMPANY_DROP_INTERVAL_MIN=2400  COMPANY_DROP_INTERVAL_MAX=7200
COMPANY_REACQUIRE=true
Loot: data/bewitchment/loot/company_gifts.json
```

### Organised — Shulker Shell (CUSTOM UI)
```
ORGANISED_EXTRA_SLOTS=9  ORGANISED_DROP_ON_EXPIRE=true
```

### Nightowl — Glow Berries
```
NIGHTOWL_NO_FOG=true (incl. water/lava)  NIGHTOWL_DARKNESS_IMMUNE=true (blindness+darkness nullified client-side)
NIGHTOWL_GAMMA_OVERRIDE=full-bright equivalent
```

### Steady Hands — Spectral Arrow
```
STEADYHANDS_SPREAD_MULT=0.2  STEADYHANDS_CHARGE_TIME_MULT=0.6  STEADYHANDS_MULTISHOT_SPREAD=0.3
(multishot pellets can hit one target multiple times)
```

### Hawk Guy — Target Block
```
HAWKGUY_HOMING_STRENGTH=0.08  HAWKGUY_ACQUIRE_CONE=20  HAWKGUY_ACQUIRE_RANGE=24
```

### Main Character — Fire Charge (CUSTOM SOUND)  [Section 11 collision RESOLVED — moved off Firework Star]
```
MAINCHAR_BUFF_PER_ENEMY={strength:+0.5dmg, speed:+2%}  MAINCHAR_MAX_STACKS=8  MAINCHAR_COMBAT_TIMEOUT=200
Sounds: bewitchment:blessing.mainchar.theme, bewitchment:blessing.mainchar.theme_intense (>=4 stacks)
```

### Last Stand — Enchanted Golden Apple (CUSTOM SOUND)
Fatal damage → revive with huge brief buffs in a knockback explosion. **Consumes the blessing.**
```
LASTSTAND_REVIVE_HEALTH=6.0  LASTSTAND_BUFFS={strength_2,speed_2,resistance_2}  LASTSTAND_BUFF_DURATION=300
LASTSTAND_EXPLOSION_KNOCK=2.0  LASTSTAND_EXPLOSION_RADIUS=6 (knockback only, no damage)  LASTSTAND_USES=1
Sound: bewitchment:blessing.laststand.rise
```

### Jesus — Lily Pad
```
JESUS_WALK_ON_WATER=true  JESUS_CROUCH_SUBMERGES=true  JESUS_WORKS_ON_LAVA=false
```

### Thick Skinned — Armadillo Scute (NOT PROTOTYPED)
Small tick damage ignored; damage must exceed 2 to apply.
```
THICKSKIN_DAMAGE_FLOOR=2.0   // single events <= 2.0 fully negated (per spec: "must exceed more than 2")
```

### Farmer's Spirit — Carrot (NOT PROTOTYPED)
```
FARMSPIRIT_RADIUS=12  FARMSPIRIT_GROWTH_TICK_MULT=5.0
Applies: crops, saplings, nether wart, sweet berries, cocoa
```

### Brute — Iron Helmet (NOT PROTOTYPED) (CUSTOM SOUND)
Straight-line sprint ramps speed to a cap; at cap, collisions launch entities and you smash
through blocks at HP cost.
```
BRUTE_RAMP_TIME=60  BRUTE_MAX_SPEED_MULT=2.2  BRUTE_TURN_TOLERANCE=15
BRUTE_LAUNCH_FORCE=2.0  BRUTE_LAUNCH_DAMAGE=4.0  BRUTE_BLOCK_BREAK_HARDNESS_MAX=3.0
BRUTE_HP_COST_PER_BLOCK=1.0  BRUTE_RESPECTS_GRIEFING=true
Sounds: bewitchment:blessing.brute.ramp, bewitchment:blessing.brute.smash
```

### Blacksmith — Copper Block (NOT PROTOTYPED)
```
BLACKSMITH_ANVIL_COST_MULT=0.4  BLACKSMITH_TOO_EXPENSIVE=disabled
```

### Unseen — Ink Sac (NOT PROTOTYPED)
Fully invisible (armour/held included) to clients beyond proximity radius; fades, no hard pop.
```
UNSEEN_REVEAL_DISTANCE=8  UNSEEN_FADE_BAND=4  UNSEEN_BREAKS_ON_ATTACK=false
```

### Silver Tongue — Emerald Block (NOT PROTOTYPED)
```
SILVERTONGUE_TRADE_COST=minimum (all trades floor to vanilla minimum)
```

### Hot Stuff — Coal (NOT PROTOTYPED)
```
HOTSTUFF_SPEED_MULT=5.0  HOTSTUFF_LOOK_RANGE=8
Applies: furnace, blast_furnace, smoker, campfire
```

### Bouncy — Slime Ball (NOT PROTOTYPED) (CUSTOM SOUND)
```
BOUNCY_RESTITUTION=0.7  BOUNCY_FALL_DAMAGE_IMMUNE=true  BOUNCY_ENTITY_BOUNCE_FORCE=1.2
Sounds: bewitchment:blessing.bouncy.boing_small, bewitchment:blessing.bouncy.boing_large
```

### Excavation — Iron Pickaxe (NOT PROTOTYPED)
```
EXCAVATION_HASTE_STACK_TIME=60  EXCAVATION_MAX_HASTE=4  EXCAVATION_DECAY_GRACE=40
```

### Angler — Salmon (NOT PROTOTYPED)
```
ANGLER_BITE_TIME_MULT=0.25  ANGLER_TREASURE_BONUS=+0.05
```

### Laugh Track — Cocoa Beans (NOT PROTOTYPED) (CUSTOM SOUND)
Chat messages trigger a GLOBAL laugh track.
```
LAUGHTRACK_SCOPE=server-wide  LAUGHTRACK_COOLDOWN=200  LAUGHTRACK_VARIANTS=3
Sounds: bewitchment:blessing.laughtrack.laugh_1 / laugh_2 / laugh_3 (variant 3 = single sad "ha")
```

### Chat — Purple Wool (NOT PROTOTYPED) (CUSTOM UI)
Personal fake Twitch chat overlay reacting to gameplay; can surface REAL useful info (nearby
structures, chests, players/entities, low durability).
```
CHAT_MESSAGE_INTERVAL_MIN=100  CHAT_MESSAGE_INTERVAL_MAX=600  CHAT_USEFUL_INFO_CHANCE=0.10
CHAT_OVERLAY_LINES=5
List: data/bewitchment/text/twitch_chat.json
```

### Civilisation — Raw Beef (NOT PROTOTYPED)
Coyote time + edge bias for parkour.
```
CIVILISATION_COYOTE_TICKS=5  CIVILISATION_EDGE_MAGNETISM=0.05
```

### Low Gravity — Eye of Ender (NOT PROTOTYPED)
```
LOWGRAV_JUMP_MULT=1.6  LOWGRAV_FALL_SPEED_MULT=0.6  LOWGRAV_FALL_DAMAGE_MULT=0.4
```

---

## 7. NEUTRALS (11)

High chance of landing on the caster when a hex fails; also directly forcible like curses.

| Neutral | Item | Spec | Constants |
|---|---|---|---|
| Wooliam | White Wool | Sheep named Woolliam poofs into existence nearby | WOOLIAM_SPAWN_RANGE=6, persistent name tag |
| Disguise | Leather | You appear as a cow to all clients | DISGUISE_DURATION=6000 |
| Anvil | Anvil | Highly damaged anvil spawns well above the victim's head | ANVIL_SPAWN_HEIGHT=12, ANVIL_DAMAGE_STATE=very_damaged |
| Letter | Book and Quill | Note with random text appears | List: data/bewitchment/text/letters.json |
| Creeper | Dark Green Dye | Creeper (or charged) spawns behind player | CREEPER_CHARGED_CHANCE=0.15, CREEPER_SPAWN_DIST=3 |
| Useless Trade | Stick | Wandering trader with terrible trades spawns | Trade pool: data/bewitchment/trades/useless.json |
| Moovin | Cooked Beef | Gravity-free cow spawns nearby | MOOVIN_RISE_SPEED=0.02, despawns at Y+64 |
| Celebration | Firework Star | Firework burst around the player | CELEBRATION_ROCKETS=8, CELEBRATION_DURATION=100 |
| Mansplaining | Iron Nugget | One kind-of-useless tip in chat | List shares mansplainer.json tip pool |
| Damage | Diamond Sword | Damage chunk scaled by essence spent; never kills if targeted | DAMAGE_PER_ESSENCE=0.2, DAMAGE_FLOOR_HP=1.0 |
| Mirror | (BACKFIRE ONLY — no item) | The curse applies to the caster instead | system-triggered |

---

## 8. GLOBALS (15)

Funded from the shared **Global bank** (Blocks of Cursed Essence). Very low base odds. Every global
gets a chat build-up/warning naming the caster; time-based globals apply the **Afflicted** status.
"Who?" has been CUT from the roster.

| Global | Item | Spec | Constants |
|---|---|---|---|
| Inventory Shuffle | Potato | All players' inventories scrambled | shuffle within each player (not across) |
| Russian Roulette | Dragon's Breath | Everyone hears a charged-creeper hiss behind them; ONE is real | ROULETTE_FUSE=30 |
| Player Shuffle | Popped Chorus Fruit | Nametags + skins swapped per-client | PLAYERSHUFFLE_DURATION=12000 (~10 min) |
| Hot Potato | Baked Potato | Modded status effect ticks down; at 0 the carrier explodes and instantly dies (world dmg per mobGriefing); passed ONLY by hitting other players | HOTPOTATO_TIMER=3600, HOTPOTATO_PASS_COOLDOWN=40 |
| Spot Shuffle | Chorus Fruit | All player positions swapped after a brief warning | SPOTSHUFFLE_WARNING=100 |
| Gravity Flip | Purpur Block | Warning, then gravity inverts 10–20s, then reverts; Y-height cap on lift | GRAVFLIP_DURATION_MIN=200, MAX=400, GRAVFLIP_Y_CAP=+32 above start |
| Party Time | Any Candle (TAG EXCEPTION) | All non-boss mobs jump happily, no attacking, confetti, ~1 min | PARTY_DURATION=1200 |
| Auction | Gold Block | A valuable item is taken from someone's inventory; players bid XP levels in chat; hard cap | AUCTION_LEVEL_CAP=40, AUCTION_BID_WINDOW=600 |
| Apocalypse | Zombie Head | Hostile spawn rates greatly increased | APOC_SPAWN_MULT=5.0, APOC_DURATION=6000 |
| Aporkalypse | Porkchop | All spawns replaced with pigs; pigs can duplicate; hard cap | APORK_DUPE_CHANCE=0.05/min, APORK_PIG_CAP=200 |
| TNT Rain | Creeper Head | TNT rains around all players; NO world damage unless config enables | TNTRAIN_RATE=1 per 60t per player, TNTRAIN_DURATION=1200, config: tntRainWorldDamage=false |
| Silence | Dandelion | Hostile spawn rates greatly reduced | SILENCE_SPAWN_MULT=0.1, SILENCE_DURATION=12000 |
| Firework Show | Firework Rocket | Fireworks + confetti around all players ~3–4 min | SHOW_DURATION=4200 |
| Floor Is Lava (global) | Lava Bucket | Standing on grass-type blocks deals armour-ignoring damage of a special damage type | GFIL_DAMAGE=1.0/20t, damage type: bewitchment:lava_floor (bypasses armour), GFIL_DURATION=2400 |
| Gamble | Redstone Block | Every player receives a random blessing/curse/neutral | uses standard pools |

---

## 9. MODIFIERS (15)

Applied in the Table's Modifier slot. Deltas multiply onto base cost/duration/success/backfire.

| Modifier | Cost | Duration | Success | Backfire | Notes |
|---|---|---|---|---|---|
| Clock | +15% | +2 to +10 min (flat, random) | — | — | IGNORED by duration-override curses (e.g. Moonwalker) |
| Netherstar | +50% | — | forced 100% (removes failure entirely) | — | |
| Prismarine Shard | -15% | — | — | — | |
| Dragon's Breath | +30% | splash copies at 1/4 duration | — | +10% | spreads to players near the CASTER |
| Netherite Ingot | +40% | — | — | +10% | bypasses Ward/Jar; NOT Warding Totem |
| Ink Sac | +35% | — | — | +5% | effect hidden until its discovery event fires; then shows with timer |
| Glow Ink Sac | -10% | — | — | — | target told in chat exactly what they got |
| Rabbit's Foot | +10% | — | — | -15% | |
| Echo Shard | +10% | — | — | — | delays ONSET by 5–10 min (changed from delay-tell) |
| Goat Horn | -5% | — | — | — | horn sound when the curse lands |
| Sugar | -10% | -50% | +10% | — | |
| Honeycomb | +20% | 25% of rolled duration | 100% IF attachment is not Major-tier+ | 0% (forced) | no effect on non-time-based events |
| Quartz | -20% | — | — | — | only for Compendium-discovered spells |
| Compass | +5% | fixed 40 min | — | — | overrides the random roll |
| Recovery Compass | +15% | — | — | — | attachment does NOT persist after death (the exception to Rule 4) |

REMOVED as modifiers (now sacrificial items / table mechanics): Gunpowder (→ Explosive curse),
Milk Bucket (→ Butterfingers curse), Redstone Dust (→ random-attachment table mechanic).

---

## 10. COSTS, WEIGHTS & FORMULAS

### 10.1 Formulas
```
successChance  = min(95%, 30% + 65% * (essenceSpent / baseCost))     // Netherstar forces 100%
backfireChance = max(0%, 25% - (essenceSpent / baseCost) * 25%)      // modifiers add deltas after
```

### 10.2 Curse costs (updated names; NEW entries marked)
```
Violence - Moderate - 45
Butterfingers - Moderate - 30
Explosive - Major - 65
Super Explosive - Major - 60          (NEW)
Popularity - Moderate - 32
Yap - Minor - 20
Green Aura - Minor - 20
Repel - Minor - 25
Echoes - Minor - 15
Delusions - Minor - 17
Gluttony - Minor - 25
Gassy - Minor - 20
Farmhand - Minor - 17
Heavy - Minor - 25
Slippery Feet - Minor - 20
Magnet - Minor - 25
Neutral Aggression - Minor - 25
Dwarfism - Minor - 20
Screensaver - Minor - 15
Minor Inconvenience - Minor - 15
Thirst Meter - Minor - 17
Social Outcast - Moderate - 35
Floor Is Lava - Major - 55
Heavyweight - Minor - 25
Bad Swimmer - Minor - 20
Pests - Minor - 17
Allergic - Minor - 20
Comic Relief - Minor - 25
Ugly - Minor - 20
Taxes - Major - 80
Sticky - Moderate - 30
Backseat Driver - Minor - 17
Clumsy - Minor - 20
Oversharer - Moderate - 32
Aura - Minor - 20
Broken Bonds - Minor - 20
Insomniac - Minor - 17
Flat Footed - Minor - 25
Wonky - Minor - 15
Stick Drift - Minor - 17
Basement Dweller - Moderate - 40
Claustrophobia - Moderate - 30        (NEW)
Glass Cannon - Moderate - 30
Mansplainer - Minor - 17
Moonwalker - Minor - 20               (NEW)
Siren's Call - Moderate - 35          (NEW)
Loading Screen - Minor - 15           (NEW)
Pacing - Minor - 17                   (NEW)
Trumpet - Minor - 15                  (NEW)
Uncareful - Moderate - 40             (RESTORED; NOT PROTOTYPED)
```

### 10.3 Blessing costs (x1.4 flavor multiplier baked in; NEW entries marked)
```
Fortune - Minor - 35
Peace - Minor - 21
Luck - Minor - 28
Full - Minor - 24
Army - Minor - 35
Reflect - Minor - 28
Soul Bond - Major - 100 (manual surcharge applied)
Bodyguard - Moderate - 42
Tax Man - Minor - 35
Hype Man - Minor - 28
Workman - Minor - 28
Pickpocket - Minor - 35
Windfall - Minor - 24
Immortality - Major - 100 (manual surcharge applied)
Sixth Sense - Minor - 24
Iron Stomach - Minor - 24
Iron Lung - Minor - 24
Anchor - Minor - 24
Twinkletoes - Minor - 24
Personal Trainer - Minor - 24
Studious - Minor - 28
Twist Of Fate - Minor - 28
Company - Minor - 24
Organised - Minor - 24
Nightowl - Minor - 21
Steady Hands - Minor - 28
Hawk Guy - Moderate - 42
Main Character - Minor - 35   (Fire Charge — Section 11 collision resolved)
Last Stand - Moderate - 50            (NEW; single-use)
Jesus - Minor - 28
Thick Skinned - Minor - 24            (NEW)
Farmer's Spirit - Minor - 24          (NEW)
Brute - Moderate - 42                 (NEW)
Blacksmith - Minor - 24               (NEW)
Unseen - Moderate - 42                (NEW)
Silver Tongue - Minor - 35            (NEW)
Hot Stuff - Minor - 21                (NEW)
Bouncy - Minor - 28                   (NEW)
Excavation - Minor - 28               (NEW)
Angler - Minor - 21                   (NEW)
Laugh Track - Minor - 21              (NEW)
Chat - Minor - 28                     (NEW)
Civilisation - Minor - 24             (NEW)
Low Gravity - Minor - 24              (NEW)
```

### 10.4 Neutral weights (failure-pool selection)
```
Wooliam - 1 | Disguise - 2 | Anvil - 2 | Letter - 1 | Creeper - 2 | Useless Trade - 1
Moovin - 1 | Celebration - 1 | Mansplaining - 1 | Damage - 3
```

### 10.5 Backfire weights
```
Mirror (curse redirected to caster) - 3
Essence Backlash (damage + essence loss) - 2
Table Tantrum (personal Table cooldown) - 1
Essence Leak (essence drops for others to grab) - 2
Marked (temporarily cheaper for others to curse you) - 2
```

### 10.6 Global bank costs (Blocks of Cursed Essence)
```
Party Time - 2 | Firework Show - 2 | Silence - 4 | Floor Is Lava - 4 | Gravity Flip - 6
Inventory Shuffle - 8 | Player Shuffle - 8 | Spot Shuffle - 8
Apocalypse - 10 | Aporkalypse - 10 | TNT Rain - 10
Hot Potato - 12 | Russian Roulette - 12 | Auction - 12 | Gamble - 15
("Who?" removed from roster)
```

---

## 11. ⚠ COLLISION & GAP REPORT (report only — NEVER fix alone)

Sacrificial matching is exact-item (Rule 9), so collisions only matter within/between pools that
share the Sacrificial Item slot (curses + blessings certainly; neutrals and globals are also
table-forcible, so treat all four pools as one selection space until ruled otherwise).

**HARD — RESOLVED:**
1. ~~**Firework Star** — Main Character (blessing) vs Celebration (neutral).~~ RESOLVED (Oliver's call):
   **Main Character → Fire Charge**; Celebration keeps the Firework Star (it's inherently about fireworks).
   Fire Charge is used by nothing else, and dropping Main Character off any firework item also retires the
   "match any Firework Star regardless of components" type-match exception. Implemented in
   `BlessingMainCharacter`.

**RESOLVED this revision:** Book clash (Mansplainer → Written Book; Studious keeps Book);
Firework Rocket clash (Main Character → Firework Star; Firework Show keeps Rocket); Brute item
assigned (Iron Helmet — nothing else uses it); Uncareful restored (Flint — nothing else uses it).

**RESOLVED in earlier revisions (no action needed):** Heavy/Heavyweight Iron Block clash (Heavy →
Iron Ingot); Gunpowder Explosive/Creeper-neutral clash (Creeper → Dark Green Dye); Milk Bucket
Butterfingers/Moovin clash (Moovin → Cooked Beef); Copper Ingot Bad Swimmer/Useless-Trade clash
(Useless Trade → Stick); Heart of the Sea Siren's-Call/Gamble clash (Gamble → Redstone Block);
Eye of Ender Low-Gravity/Spot-Shuffle clash (Spot Shuffle → Chorus Fruit); Recovery Compass double
definition (settled: does-not-persist-after-death version).

**Cross-slot notes (Modifier slot is separate — informational only):**
Echo Shard (Echoes / modifier), Compass (Sixth Sense / modifier), Goat Horn (Flat Footed /
modifier), Rabbit's Foot (Luck / modifier), Nether Star (Twist of Fate / Netherstar modifier),
Ink Sac (Unseen / modifier), Dragon's Breath (Russian Roulette global / modifier), Honey-family
items are distinct (Honey Bottle=Sticky vs Honeycomb=modifier).

**Soft flags:**
- Written Book (Mansplainer) vs Book and Quill (Letter neutral): distinct items (signed vs
  unsigned), close enough to confuse players; Compendium should render both icons clearly.
- Purple Wool (Chat) vs White Wool (Wooliam): safe under exact-item matching only.
- Water Bottle (Thirst Meter) vs Glass Bottle (Player Essence collection): distinct items, adjacent
  enough to confuse; Compendium should render both icons clearly.
- Raw Beef (Civilisation) vs Cooked Beef (Moovin): distinct, fine, listed for awareness.
- Redstone Block (Gamble) vs Redstone Dust (random-attachment mechanic): distinct, adjacent naming.
- Tag exceptions (music discs tag, candles tag) must never be widened to other entries. (The former
  "any firework star" type-match is retired — Main Character is Fire Charge now, exact-item.)

---

## 12. CUSTOM SOUND EVENTS — MASTER OGG TODO (24)

```
CURSES
bewitchment:curse.delusions.vanish        smoke-puff disappear
bewitchment:curse.delusions.whisper       faint ambient near a delusion
bewitchment:curse.gassy.fart_small        standard fart
bewitchment:curse.gassy.fart_large        event-triggered fart
bewitchment:curse.slippery.slide_whistle  descending slide whistle
bewitchment:curse.aura.track_1            long follow-music track 1
bewitchment:curse.aura.track_2            long follow-music track 2
bewitchment:curse.aura.track_3            long follow-music track 3
bewitchment:curse.loading.ambience        fake loading screen hum
bewitchment:curse.pacing.sting            dramatic orchestral hit
bewitchment:curse.trumpet.walk_loop       fat-trumpet walking music loop
bewitchment:curse.trumpet.stop_sting      short stop flourish

BLESSINGS
bewitchment:blessing.mainchar.theme          looping battle theme
bewitchment:blessing.mainchar.theme_intense  high-stack variant
bewitchment:blessing.laststand.rise          revive fanfare/shockwave
bewitchment:blessing.brute.ramp              rising rumble while accelerating
bewitchment:blessing.brute.smash             impact hit
bewitchment:blessing.bouncy.boing_small      small bounce
bewitchment:blessing.bouncy.boing_large      big bounce
bewitchment:blessing.laughtrack.laugh_1      laugh track 1
bewitchment:blessing.laughtrack.laugh_2      laugh track 2
bewitchment:blessing.laughtrack.laugh_3      single sad "ha"

GLOBALS (no custom sounds currently required — Russian Roulette reuses vanilla charged-creeper hiss)

STATUS/SYSTEM
bewitchment:system.discovery              chat-alert chime on discovery
bewitchment:system.global_warning         global event build-up warning sting
```

---

## 13. UI SPECIFICATION

### 13.1 Bewitching Table screen
Crafting-table-shaped GUI: triangle of 3 slots (top = Player Essence, lower-left = Cursed Essence,
lower-right = Sacrificial Item); Modifier slot left of the triangle; purple probability bar under
the triangle scaling with essence inserted.

### 13.2 Compendium
Book UI with a side selection ("bookmarks") jumping to: Tutorial | Items/Blocks | Rumours
(Curses/Blessings) | Rumours (Events) | Rumours (Backfires) | Modifiers.
**Every attachment/event has its own RUMOUR page (placeholder entries until discovered) and a
DISCOVERED page with its own image inside the book.** Discovery flips rumour → discovered
instantly, with the chat alert (Rule 2).
Page images: `assets/bewitchment/textures/gui/compendium/pages/<attachment_id>.png`

### 13.3 Ledger
Read-only book-via-lectern UI; entries appended automatically from the hex log.

### 13.4 Attachment UI (fully functional required)
- **Thirst Meter**: droplet-icon replica hunger bar.
- **Loading Screen**: fullscreen fake-load overlay (black + spinner + tip).
- Also: Gluttony second hunger row, Organised extra inventory row, Chat twitch overlay.

### 13.5 GUIDE: Custom art, colours & textures for the Bewitching Table UI

How Minecraft container GUIs get their look, and where YOUR art goes:

1. **The background texture.** The whole Table screen is one PNG:
   `assets/bewitchment/textures/gui/container/bewitching_table.png`
   Convention: a 256x256 image where the actual GUI panel occupies the top-left region
   (typically 176 wide x 166+ tall — ours will be ~176x180 to fit the bar). Everything —
   panel background, slot outlines, decorative art — is painted INTO this PNG. The screen class
   just blits it: `guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight)`.
   To restyle the whole table: repaint this one file. Purple wood? Bone trim? Runes around the
   triangle? All just pixels here.

2. **Slot positions are code, slot LOOKS are texture.** Each slot is registered in the menu class
   at an (x,y) — e.g. Player Essence at (80, 20), Cursed Essence at (56, 62), Sacrificial at
   (104, 62), Modifier at (26, 41). The 18x18 slot frames you see are drawn in the PNG at those
   same coordinates. Want hexagonal or rune-ring slot art instead of vanilla squares? Draw it in
   the PNG around those coordinates — the code doesn't care what the frame looks like, only where
   items render.

3. **The probability bar is a two-state sprite.** Standard technique: draw the EMPTY bar into the
   background PNG, and draw the FULL (purple) bar elsewhere in the same 256x256 sheet (unused
   space, e.g. at (0, 180)). At render time, blit a partial-width slice of the full bar over the
   empty one: `blit(TEXTURE, x, y, 0, 180, (int)(barWidth * successChance), barHeight)`.
   Recolouring the bar = repainting that strip. Gradient, bubbling essence animation (multiple
   frames side-by-side + frame cycling), anything.

4. **Text colours.** Titles/labels are drawn in code with `drawString(font, text, x, y, 0xRRGGBB)`.
   Keep a small palette constants class (`BewitchmentColors`) so the purple used in the bar, the
   title text, and particle tints all reference one place.

5. **Recommended workflow for first-time GUI art:**
   a. Export vanilla's `crafting_table.png` GUI from the jar as a size/layout reference.
   b. Lay out your panel at 176-wide in any pixel editor (Aseprite/GIMP); keep vanilla's grey
      panel first, get slots aligned with code coordinates, THEN restyle.
   c. Test with `F3+T` (resource reload) — no rebuild needed for texture-only changes.
   d. Only after layout is stable, do the decorative pass (runes, wood grain, glow).

6. **Where everything lives:**
```
assets/bewitchment/textures/gui/container/bewitching_table.png   (table screen)
assets/bewitchment/textures/gui/compendium/book.png              (compendium base)
assets/bewitchment/textures/gui/compendium/pages/*.png           (per-attachment images)
assets/bewitchment/textures/gui/hud/thirst_icons.png             (droplet bar sprites)
assets/bewitchment/textures/gui/hud/gluttony_icons.png           (second hunger row)
assets/bewitchment/textures/gui/hud/loading_screen.png           (spinner + frame)
assets/bewitchment/textures/gui/hud/twitch_overlay.png           (chat blessing frame)
```

---

## 14. Gamerules / Config
```
Grace Period      - new players cannot be targeted for a customisable window
Curses            - on/off
Blessings         - on/off
Globals           - on/off
Ward Durability   - wards can be made non-decaying so players can 'opt out'
Backfires         - on/off
Limit             - max simultaneous attachments per player (default 3)
tntRainWorldDamage- false by default (TNT Rain world damage opt-in)
```

## 15. Command Tree
```
/bewitch apply {attachment} {selector}         (default selector = caster; supports duration override for testing)
/bewitch remove {selector} {attachment}        (default selector = caster)
/bewitch clear [targets] [curses|blessings]    (added: strips ALL attachments, or just one category; default target = caster)
/bewitch event {neutral|global} {eventname} {selector}
        (neutral default selector = caster; global needs none, but a given selector marks that
         player as initiator for Ledger attribution; supports duration override where time-based)
/bewitch forcestop {neutral|global} {selector} (unstoppable-by-design events silently no-op)
/bewitch organised                             (added, Phase D: opens the Organised blessing's 9-slot stash)
```
Note: `/bewitch clear` is backed by `EffectManager.removeAll(target, @Nullable category)` (fires each
effect's onRemove for clean teardown). The Thirst + Gluttony HUD bars now hide in creative/spectator
(gated on `gameMode.canHurtPlayer()`, matching vanilla's own survival-HUD gate).

---

## 16. REMAINING WORKFLOW (post-prototype)

Prototype is DONE — all attachments loosely functional except `(NOT PROTOTYPED)`. Remaining phases:

### Phase A — Build all NOT PROTOTYPED attachments (⛔ Section 0.1 — these have NO class yet)
Curses (8): Super Explosive, Claustrophobia, Moonwalker, Siren's Call, Loading Screen, Pacing,
Trumpet, Uncareful.
Blessings (14 + 1 behavior): Thick Skinned, Farmer's Spirit, Brute, Blacksmith, Unseen, Silver
Tongue, Hot Stuff, Bouncy, Excavation, Angler, Laugh Track, Chat, Civilisation, Low Gravity;
plus Last Stand blessing-consumption behavior if the prototype lacks it.
All must be command-startable with duration override before moving on.

### Phase B — Status effect layer hardening
Cursed/Blessed/Afflicted wrappers: dynamic duration tracking across add/cure/extend/shorten;
particles ONLY on first-onset and last-expiry; enforce NO-STACKING (KEEP_LONGER) at the single
apply entry-point so table, commands, coins, jars, and Gamble all inherit it.

### Phase C — Items & blocks completion
All Section 4 items + Section 3 blocks wired to the real ritual pipeline (formulas of 10.1,
failure → neutral/backfire/table-explosion, Redstone Dust random mechanic, global bank draw for
globals, escalating-cost rule).

### Phase D — UI build (Section 13)
Order of attack for a first-time UI dev: Ledger (read-only, simplest) → Thirst/Gluttony HUD bars →
Loading Screen overlay → Bewitching Table screen → Compendium (most complex: bookmarks, rumour/
discovered page flip, per-page images, chat-alert hook). Confirm every attachment's effect code
calls the discovery hook (victim on trigger, caster on successful send).

### Phase E — Balance & config extraction
All Section 10 numbers and every per-attachment constant exposed as real config (not hardcoded).
Full playtest of Grace Period, Ward opt-out, Backfires toggle, Limit.

### Phase F — Resolve Section 11 hard flag
Firework Star collision (Main Character vs Celebration). Do not ship before it's resolved.

---

## 17. HUMAN ACTION ITEMS (living list — Claude Code appends during work)

**Art — Items:** Cursed Essence, Player Essence, Compendium, Voodoo Doll, Needle, Ward, Scrying
Mirror, Effigy, Cursed/Blessed/Executioner's Coins, Jar, Cursed Jar, Amethyst Bell retexture.

**Art — Blocks:** Bewitching Table, Block of Cursed Essence, Ledger, Warding Totem,
Purifying Water (fluid texture + animation).

**Art — GUI (Section 13.5 guide).** Status as of Phase D — every HUD/overlay is BUILT and FULLY FUNCTIONAL
with placeholder visuals drawn in code; each is a drop-in swap once the PNG exists (no code/JSON change
needed beyond pointing the layer at the texture). Namespace note: paths below are written with the
`bewitchment` placeholder namespace, but the actual mod namespace is `witchmod` — real assets go under
`assets/witchmod/textures/...`.
- `bewitching_table.png` — Table screen background. STILL PLACEHOLDER: `BewitchingTableScreen.renderBg`
  draws the panel/slots/bar/button as `GuiGraphics.fill` rectangles. Swap = one `blit` in `renderBg`.
- `compendium` book + per-attachment page images — the Compendium is a written-book (no custom page art
  yet); per-attachment page images are a later enhancement, not blocking.
- `thirst_icons.png` — 10-icon droplet set (full/half/empty). Currently `ThirstHudLayer` draws blue
  filled rectangles. Swap = `blitSprite`/`blit` in `ThirstHudLayer`.
- `gluttony_icons.png` — NOT NEEDED as custom art: `GluttonyHudLayer` reuses the vanilla `hud/food_*`
  sprites (per Oliver's call, it's literally a second hunger bar). Only make a custom sheet if a distinct
  look is wanted later.
- `loading_screen.png` — spinner + frame. Currently `LoadingScreenOverlay` draws a black fill + a text
  spinner + tip. Swap the spinner/frame in that layer.
- `twitch_overlay.png` — Chat panel frame. Currently `ChatOverlayLayer` draws a translucent purple panel +
  text lines. Swap the panel frame in that layer.
- (No sprite needed for the Organised extra slots — it opens as a vanilla 1-row chest.)

**Art — Skins/Entities:** Ugly skin pool (>=3, at
`assets/bewitchment/textures/entity/skins/ugly/`), Tax Man skin, Bodyguard sunglasses-skeleton
texture, Woolliam (vanilla sheep + name tag — no art), Delusion fake players (reuse real skins —
no art).

**Audio:** 24 OGGs per Section 12.

**Writable text lists (data/bewitchment/text/):** yap, echoes_chat, window_titles, oversharer,
mansplainer (+ Mansplaining tips), insomniac, loading_tips, bodyguard, hypeman, twitch_chat,
letters. Loot: windfall.json, company_gifts.json. Trades: useless.json.

**Decisions owed (Section 11):** ~~Firework Star collision (Main Character vs Celebration).~~ RESOLVED —
Main Character moved to Fire Charge; Celebration keeps the Firework Star. (Nothing owed here now.)

---

### Phase A build log (Claude Code — post-prototype transition)

**Done:** all 22 previously NOT-PROTOTYPED attachments now have classes, are registered, and are
command-startable (`/bewitch apply witchmod:<id> <selector>`). Built to the same "loosely-functional,
vanilla-backed" bar as the rest of the prototype — NOT the full hyper-specific specs. Curse count is now
43+7 = **50** (matches the Section 5 header). Blessing count is 29+15 = **44**; the Section 6 header says
45 but only 44 are actually listed in Section 6, so 44 is almost certainly correct (same header-vs-list
miscount this doc already had for "Curses (41)" vs 43). Flagging for a reconcile — if a 45th blessing was
intended, it's missing from the Section 6 list and needs naming.

**New curse classes (7):** super_explosive, claustrophobia, moonwalker, sirens_call, loading_screen,
pacing, trumpet. **New blessing classes (15):** thick_skinned, farmers_spirit, brute, blacksmith, unseen,
silver_tongue, hot_stuff, bouncy, excavation, angler, laugh_track, chat, civilisation, low_gravity,
last_stand. (Registry ids use the spec's current names; note the older prototype ids kept for the renamed
ones — `neutral_mobs_attack_instantly`=Neutral Aggression, `loud`=Flat Footed, `pidgeon_toed`=Wonky,
`sick_of_you`=Broken Bonds, `locked_in`=Hawk Guy, `tools_dont_use_durability`=Workman, `trainer`=Personal
Trainer — kept stable to avoid breaking saved data; display names are a lang concern.)

**Prototype stand-ins (real behavior deferred to later phases — flagged in each class's javadoc):**
- Moonwalker → Slowness (real: reversed forward input, client-side, Phase D).
- Loading Screen → brief Blindness on a timer (real: fullscreen fake-load overlay on door use — Phase D UI
  + custom sound).
- Pacing → brief hard Slowness "freeze" (real: camera-hijack cutaways — Phase D + custom sound).
- Trumpet → periodic vanilla flute note (real: movement-gated fat-trumpet loop — custom OGG).
- Siren's Call → mining fatigue/slowness while dry, cleared in water (real: staged longing stat + physical
  water-pull + passive Drowned).
- Brute → Strength+Speed while active (real: sprint ramp → launch/smash + custom sounds).
- Unseen → vanilla Invisibility (real: proximity-based full-render fade incl. armour/held).
- Silver Tongue → Hero of the Village (real: floor all trade prices to vanilla minimum).
- Hot Stuff → Fire Resistance placeholder (real: nearby-furnace smelting-speed boost — needs a
  cook-progress accessor).
- Angler → Luck (real: shorter fishing bite time + treasure bonus).
- Excavation → flat Haste (real: mining-driven Haste stacks with decay grace).
- Civilisation → Jump Boost (real: parkour coyote-time + edge magnetism).
- Chat → periodic canned action-bar line (real: client Twitch-chat overlay surfacing real info — Phase D).
- Laugh Track → server-wide vanilla `villager.yes` on chat, no cooldown (real: 3 custom laugh OGGs +
  200t cooldown).
- Bouncy → fall-damage immunity only (real: restitution/bounce physics + custom boing sounds).

**Event-driven attachments** got real hooks (not just tick/attribute): Super Explosive
(`LivingIncomingDamageEvent`, guarded against explosion-recursion) in a new `effects/CurseEventHandler`;
Thick Skinned (damage floor ≤2 negated), Bouncy (`LivingFallEvent`), Blacksmith (`AnvilUpdateEvent` cost
×0.4), Laugh Track (`ServerChatEvent`), and Last Stand (`LivingDeathEvent`, single-use revive + buffs,
consumes the blessing) all in `effects/BlessingEventHandler` alongside the existing Immortality hook.

**⚠ Collisions / spec-deviations noticed while building (report-only per Section 11 — need Oliver's call):**
1. **Explosive was on the wrong sacrificial item.** The prototype `CurseExplosive` used TNT, but Section 5
   assigns Explosive→Gunpowder and Super Explosive→TNT. Since two effects on one item makes one uncastable
   at the table, I corrected Explosive→Gunpowder (its spec-correct item). This is the one spec-alignment
   change I made to existing code, not just new classes — flag if that wasn't wanted.
2. **Modifier list still contains three items Section 9 says were REMOVED as modifiers:** Gunpowder
   (→ Explosive curse), Milk Bucket (→ Butterfingers curse), Redstone Dust (→ random-attachment table
   mechanic). `Modifier.java`/`ModifierItems.java` still register all three as modifiers. Because the
   modifier slot and sacrificial slot are separate, this isn't a hard runtime conflict yet, but it
   contradicts the master spec. NOT fixed in Phase A (it's Phase C/E items-and-modifiers cleanup) — logged
   here so it isn't lost.
3. **Firework Star (Main Character vs Celebration)** remains the unresolved Section 11 HARD flag — untouched
   (Phase F), still needs Oliver's decision. Section 17 suggests "celebration uses red wool."

**Verified:** compiles clean; dedicated server boots to Done with all 50 curses + 44 blessings registered,
zero duplicate-id or registration errors. Interactive `/bewitch apply` spot-check of the new attachments is
the next human test step (headless server can't easily self-issue the command).

---

### Phase B build log (Claude Code — status-effect layer hardening)

All three Phase B requirements done, all landing on existing single chokepoints (no new scattering):

1. **NO-STACKING / KEEP_LONGER** — implemented in `EffectManager.apply()`, the one method every application
   path already routes through (verified: table, `/bewitch`, Effigy, Voodoo Doll, Amethyst Bell, Jar,
   Gamble Coin, Executioner's Coin, Global Gamble, Mirror backfire — and it's the only caller of
   `ActiveEffects.put`). A reapply now keeps `max(existingRemaining, newDuration)` — never additive, never
   refreshed to a shorter time. The effect's own vanilla sub-effects are re-sized to the kept duration, and
   caster attribution follows the winning duration (a longer/equal reapply takes ownership; otherwise the
   original caster stands).
2. **Particles ONLY on onset/expiry** — the Cursed/Blessed/Afflicted wrappers were previously applied with
   `visible=true`, which made vanilla render their swirl particles *every tick* (violating Rule 6). Now
   applied `visible=false` (particles off) + `showIcon=true` (the victim still sees they're Cursed/Blessed/
   Afflicted, just not which specific attachments). `StatusEffectSync` bursts the witch-particle cloud
   manually, now **per-category** on the absent→present onset and present→absent expiry only — so curing
   all curses while keeping a blessing bursts once (curse expiry) and correctly leaves the Blessed wrapper
   untouched.
3. **Dynamic duration tracking (add/cure/extend/shorten)** — `StatusEffectSync.sync()` already recomputed
   each category's max-remaining on every mutation; the gap was that vanilla's `addEffect` keep-longer
   merge refuses to *shorten* an existing effect, so a burn-down (Purifying Water) wouldn't shrink the
   wrapper. Fixed by removing-then-re-adding the wrapper each sync so its duration matches the real
   max-remaining exactly, in both directions.

**Verified:** compiles clean. Client boot check + in-world spot-test (reapply an active curse with a
shorter duration → it doesn't shrink; watch the wrapper particles fire only at onset and expiry, not in
between) is the human test step.

---

### Phase C build log (Claude Code — items & blocks / ritual pipeline) — PARTIAL

Done this pass (all compile clean):
- **Creative tab** (user-requested, not in spec): replaced the MDK example tab with a real `witchmod` tab
  iconed by Cursed Essence, listing every item (Section 4) + block (Section 3) + the Purifying Water bucket.
  (The MDK `example_item`/`example_block` are still registered but no longer in any WitchMod tab — a later
  cleanup could delete them; they're the source of the two remaining `example_*` missing-model warnings.)
- **Redstone Dust random-attachment mechanic** (Section 3): Redstone in the Sacrificial slot now casts a
  RANDOM curse/blessing weighted by inverse cost (`weight = 1/cost^1.5`, `REDSTONE_RANDOM_LOW_BIAS`), so
  cheaper attachments are favoured. `RitualSlot` accepts Redstone in the sacrificial slot;
  `BewitchingTableRitual.pickRandomLowBiased` does the weighted roll. (The Table screen's probability-bar
  preview shows nothing for Redstone since the effect is random until cast — acceptable, minor.)
- **Modifier-list cleanup** (Section 9 — resolves the Phase A flag): removed Gunpowder, Redstone Dust, and
  Milk Bucket as modifiers from `Modifier.java` + `ModifierItems.java`. They're sacrificial items / the
  Redstone table mechanic now, not modifiers.
- **"Who?" global removed** (Section 8 — cut from roster): deleted `GlobalWho` + its registration. Global
  count is now 15, matching the Section 8 header.

**Global "bank" — clarified by Oliver and built.** The "global bank" was NOT a currency reservoir; it means
all globals share ONE server-wide cooldown whose success chance ramps from ~0 up to a base ceiling over a
few hours, identical for every player, resetting when a global fires. Cursed Essence stays a normal item
(no deposit/withdraw, no `GlobalBankCosts` — Section 10.6's block costs are obsolete under this model).
Implemented as `data/GlobalCharge` (a `SavedData` on the overworld's `DimensionDataStorage`, so it's one
value server-wide and persists): `currentSuccessChance = globalBaseChancePercent% * chargeFactor`, where
`chargeFactor` is 0→1 across `globalRechargeHours` of overworld game time. Both are new config values.
- **Trigger wiring:** `/bewitch event global <name>` (bare, no selector) is now a "natural attempt" — it
  rolls the current shared chance and only fires on success, then `reset()`s the charge for everyone. The
  form WITH a selector (`/bewitch event global <name> <player>`) is an operator force-fire (bypasses the
  roll, still resets) for attribution/testing.
- **Remaining Section 11 HARD flag** (Firework Star, Main Character vs Celebration) is Phase F, still owed.
- Note: this makes Section 8/10.6's "funded from the shared Global bank / bank costs in Blocks of Cursed
  Essence" wording stale — the doc text should be reconciled to the shared-cooldown model when convenient.
- **Not yet wired:** triggering globals from the Bewitching Table via their sacrificial items (Section 11
  says globals are table-forcible). The `GlobalCharge` roll is reusable for that when the Table-global path
  is built; right now the command is the trigger surface.

---

### Phase D build log (Claude Code — UI/HUD) — IN PROGRESS

The three main *screens* (Ledger, Table, Compendium) were already built in Phase 5. Phase D is the HUD/
overlay layer (Section 0.3/13.4) + the client-side curses — all of which need a server→client sync path
the mod didn't have.

**Key infrastructure win:** NeoForge 21.1 attachments support **auto-sync** (`AttachmentType.Builder.sync(
StreamCodec)`), so per-player HUD state can be synced to the owning client with ZERO hand-rolled payload/
networking code — the client just reads the attachment. This is the reusable pattern for every Phase D bar.

**Thirst Meter (first slice — spec-marked FULLY FUNCTIONAL) — DONE:**
- `WitchModAttachments.THIRST` — an auto-synced `int` attachment. `-1` = curse inactive (bar hidden),
  `0..20` = active thirst.
- `CurseThirstMeter` rewritten from its placeholder to drive it: onApply → full; onTick drains 1/10s while
  dry, sips +1/2s while standing in water (25% chance of a Hunger debuff — the "raw water risk"), empties to
  Slowness+Weakness (no damage); onRemove → `-1` to hide the bar. Self-heals the value on relog.
- Refill: finishing a **Water Bottle** (+6) via `LivingEntityUseItemEvent.Finish` in `CurseEventHandler`.
- `client/ThirstHudLayer` (a `LayeredDraw.Layer` registered above `FOOD_LEVEL` via `RegisterGuiLayersEvent`)
  reads the synced attribute and draws a 10-droplet bar above hunger. No `thirst_icons.png` yet, so droplets
  are placeholder filled rectangles (blue = filled) — a drop-in `blit` swap once art lands; the value/
  behaviour are fully functional.
- Simplifications flagged in-class: the spec's "right-click a water source" is realized as the passive
  in-water sip (raw water isn't a right-clickable block); sacrificial item stays Glass Bottle (the spec's
  "Water Bottle" can't be isolated under exact-item matching — Section 11 soft flag).
- **Verified:** compiles clean. Client boot + in-world test (`/bewitch apply witchmod:thirst_meter @s` →
  droplet bar appears above hunger, drains over time, refills from water bottles / standing in water,
  empties to Slowness+Weakness; wears off → bar disappears) is the human test step.

**Gluttony (second slice — CUSTOM UI) — DONE:**
- `WitchModAttachments.GLUTTONY_HUNGER` — auto-synced `int`, same `-1`/`0..20` convention as Thirst.
- `CurseGluttony` rewritten from its placeholder: onApply → full extra bar + a `Attributes.SCALE` +0.35
  modifier (the "bigger model", 1.35x); onTick drains the extra bar (~1.5x a normal bar), gives periodic
  Hunger when empty, and cuts sprinting off while normal food ≤ 12 (double the vanilla threshold);
  onRemove → `-1` + removes the scale modifier. Eating any food tops up the extra bar (via the same
  `LivingEntityUseItemEvent.Finish` hook, using the food component's nutrition).
- `client/GluttonyHudLayer` — a second hunger row (brown placeholder rectangles, distinct from Thirst blue).
- **Bar stacking (per Oliver's note about both being active at once):** new `client/HudBars` owns the
  right-side stack layout — bars stack upward from just above hunger, each bar's row = count of active bars
  below it. Order bottom→top: Gluttony (row 0, it's a hunger row) then Thirst (row 1 when Gluttony is also
  active, row 0 otherwise). `ThirstHudLayer` now asks `HudBars` for its row instead of a hardcoded y, so the
  two never overlap regardless of which are active. Future right-side bars extend `HudBars` in one place.
- **Verified:** compiles clean; client boot check + in-world test (`/bewitch apply witchmod:gluttony @s` →
  second hunger row appears + model grows; drains over time; eat to refill; sprint cuts out at low food;
  apply thirst_meter too → the two bars stack, no overlap) is the human test step.

**Gluttony bar now uses real vanilla hunger sprites.** Per Oliver, `GluttonyHudLayer` blits the actual
`hud/food_empty|half|full` sprites with the same logic as `Gui#renderFood`, so it's literally a second
hunger bar visually (not the earlier placeholder rectangles). Thirst stays placeholder blue (no vanilla
"thirst" sprite exists).

**Loading Screen (third slice — FULLY FUNCTIONAL) — DONE:**
- Establishes the **one-shot trigger-sync** pattern: an auto-synced `LOADING_SCREEN_END_TICK` (long)
  attachment. The server sets it to `gameTime + 60` when the trigger fires; the client renders the overlay
  while the synced world game time is below it — no hand-rolled payload, reusing attachment auto-sync +
  the fact that world game time is itself synced to the client.
- **Trigger:** `PlayerInteractEvent.RightClickBlock` in `CurseEventHandler` — opening a block tagged
  `DOORS`/`TRAPDOORS`/`FENCE_GATES` while cursed rolls a 50% chance on a 400-tick per-player cooldown
  (transient UUID-keyed map) to flash the screen for 60 ticks.
- **Overlay:** `client/LoadingScreenOverlay` (registered `registerAboveAll` so it covers the whole HUD) —
  black fullscreen + a text spinner + a random useless tip (stable for the flash, seeded off the end tick).
- `CurseLoadingScreen` rewritten from its blindness placeholder to just declare cost/item + dismiss any
  overlay on cure/expiry.
- PROTOTYPE gaps flagged: custom ambience sound (Section 12) + the writable `loading_tips.json` (small
  hardcoded tip pool for now) + a real spinner texture are deferred.
- **Verified:** compiles clean; client boot check + in-world test (`/bewitch apply witchmod:loading_screen
  @s`, then open doors repeatedly → ~50% of the time a black fake-loading overlay flashes for ~3s with a
  spinner and a tip; respects the cooldown) is the human test step.

**Chat (Twitch overlay) — DONE:** `CHAT_OVERLAY` synced flag (set by `BlessingChat` onApply/onRemove);
`client/ChatOverlayLayer` renders a translucent purple side-panel of fake Twitch messages, generated
client-side from a hardcoded pool on a timer (purely cosmetic, so the active flag is all that's synced).
Real-info surfacing + a frame texture + the writable `twitch_chat.json` are deferred.

**Organised (extra inventory row) — DONE (with a documented shape choice):** the spec is "9 extra inventory
slots, drop on expire." Rather than inject a literal extra row into the vanilla inventory screen (which
needs a mixin), the 9 slots are a per-player stash: a serialized `ORGANISED_ITEMS` attachment (copied on
death since blessings persist), opened as a **plain vanilla 1-row chest** via `data/OrganisedStash` (a
`SimpleContainer` that saves back to the attachment on close — no custom Menu/Screen needed) through a new
`/bewitch organised` command. `BlessingOrganised.onRemove` drops all 9 (ORGANISED_DROP_ON_EXPIRE); it also
keeps the prototype's bonus auto-consolidate-stacks tick. A survival **keybind** to open the stash is the
intended follow-up (currently command-only) — logged in Human Action Items.

**Client-side curses — DONE.** All four now run genuinely client-side, driven off auto-synced flags on the
local player (`MOONWALKER_ACTIVE`, `SCREENSAVER_ACTIVE`, `MINOR_INCONVENIENCE_ACTIVE`, and a
`PACING_END_TICK` for the triggered one) — the server just flips the flags; the effects live in
`client/ClientCurseHandler` (`@EventBusSubscriber(Dist.CLIENT)`, game bus).
- **Moonwalker** — `MovementInputUpdateEvent`: negates `forwardImpulse` and swaps up/down, so forward/back
  are reversed. Genuinely functional (not the Phase A Slowness placeholder anymore).
- **Screensaver** — `ClientTickEvent.Post`: bounces the actual OS window around the monitor DVD-logo style
  via GLFW (`glfwGetWindowPos`/`glfwSetWindowPos` + primary-monitor video mode), in episodes (~200t bounce
  per ~600t cycle). No-ops when fullscreen or the monitor can't be queried (per spec).
- **Minor Inconvenience** — `ClientTickEvent.Post`: renames the OS window title to silly strings
  (`Window.setTitle`) on an interval; restores "Minecraft" when the curse ends. PROTOTYPE gaps: the
  fullscreen-block half of the spec + the writable `window_titles.json` are deferred (hardcoded pool).
- **Pacing** — server trigger in `CurseEventHandler` (the cursed player landing a hit rolls a chance on a
  cooldown → sets `PACING_END_TICK` + a brief Slowness "freeze"); client `ViewportEvent.ComputeCameraAngles`
  rolls the camera for a dramatic wobble during that window. PROTOTYPE: the full multi-shot "cut between
  nearby entities" camera + freezing the enemy too are deferred; custom sting sound deferred.

**Phase D is now functionally complete** — all Section 0.3 CUSTOM UI overlays (Thirst, Gluttony, Loading
Screen, Organised, Chat) and all the client-side curses are built. Remaining Phase D polish is art (the
placeholder→PNG swaps catalogued in Section 17) and the deferred bits noted per-effect above.