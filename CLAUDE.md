# Loopypowers NeoForge 1.21.1 — Block Mining Fix

## The problem
Some block drops and mining attributes are currently broken due to 1.21.1 changes.

## The fix
JSON files will need to be amended to have the correct block drops and mining tags.
These may be partially complete, but ensure all requirements are met for each block.
Each block does have a corresponding class, but it is not relevant here, as all tasks are data-driven.

## Loot tables location
`src/main/resources/data/loopypowers/loot_table/blocks/`

## Tag files location
`src/main/resources/data/minecraft/tags/block/`

---

## Required fixes per block

### Celestial Ore
- Only mineable with a diamond-tier pickaxe or better
- Drops 1-2 celestial shards only when broken with the correct tool
- Celestial shard drops are affected by Fortune.
- Only drops the full block when broken using the correct tool with silk touch.
- Drops should only be silk touch and the ore or the shards - just like vanilla - it's one or the other and both pools should never be dropped. (This is currently an issue with the lootable)

---

### Deepslate Celestial Ore
Identical requirements to Celestial Ore - But silk touch instead drops the deepslate version.

---

### Casino Bars
- Only mineable with an iron-tier pickaxe or better
- Drops the block itself when broken with correct tool
- Wrong tool = no drop
- 
---

### Casino Floor
Identical requirements to Casino Bars 

---

### Thorn Vine
- Broken faster with a sword (sword is the preferred tool)
- Drops the item when broken with a sword
- No special tier requirement — any sword works

---

### Ice Spike
- Broken faster with a pickaxe
- Only drops with Silk Touch — no drop otherwise (like packed ice)
- No tier requirement

---

## Workflow

1. Read all required attributes of the block, including drops, requirements and special conditions
2. Establish all conditions using the 1.21.1 data-driven format of block attributes using JSONs in specific directories
3. Ensure JSON and directory names/locations are all correct for 1.21.1 Minecraft (important since many directory changes were made in this version.)
4. Confirm that all needs have been met and there are no logical issues like multiple loot pools being used with silk touch etc.

Do one block at a time. After each block confirm the properties, tags, and
loot table are all consistent before moving to the next.

## Checklist
- [ ] Celestial Ore
- [ ] Deepslate Celestial Ore
- [ ] Casino Bars
- [ ] Casino Floor
- [ ] Thorn Vine
- [ ] Ice Spike
