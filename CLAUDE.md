# Loopypowers NeoForge 1.21.1 — Block Mining Fix

## The problem
All block mining attributes are broken. The old approach used resource pack
tags (e.g. `minecraft:mineable/pickaxe`, `minecraft:needs_diamond_tool`) in
`src/main/resources/data/minecraft/tags/blocks/` but these no longer work
correctly in NeoForge 1.21.1.

## The fix
In NeoForge 1.21.1, mining properties must be set directly on the block's
`BlockBehaviour.Properties` in the block class itself, and drop behaviour
must be handled via loot tables in
`src/main/resources/data/loopypowers/loot_table/blocks/`.

Do NOT rely on tag JSON files alone for tool requirements or drop behaviour —
set them in code and back them up with correct loot tables.

## Block classes location
`src/main/java/com/loopy/loopypowers/block/`

## Loot tables location
`src/main/resources/data/loopypowers/loot_table/blocks/`

## Tag files location (still needed for some things, see per-block notes)
`src/main/resources/data/minecraft/tags/blocks/`

---

## Required fixes per block

### Celestial Ore
- Only mineable with a diamond-tier pickaxe or better
- Drops the ore item when broken with correct tool
- Fortune enchantment affects drop count
- Wrong tool = no drop (like vanilla diamond ore behaviour)

**Code fix** — in the block properties:
```java
BlockBehaviour.Properties.of()
    .requiresCorrectToolForDrops()
    .strength(3.0f, 3.0f)
```

**Tag files needed:**
- `data/minecraft/tags/blocks/mineable/pickaxe.json` — add celestial ore
- `data/minecraft/tags/blocks/needs_diamond_tool.json` — add celestial ore

**Loot table** — use fortune-sensitive ore drop pattern:
```json
{
  "type": "minecraft:block",
  "pools": [{
    "rolls": 1,
    "entries": [{
      "type": "minecraft:alternatives",
      "children": [
        {
          "type": "minecraft:item",
          "conditions": [{ "condition": "minecraft:match_tool",
            "predicate": { "enchantments": [{ "enchantment": "minecraft:silk_touch", "levels": { "min": 1 } }] }
          }],
          "name": "loopypowers:celestial_ore"
        },
        {
          "type": "minecraft:item",
          "name": "loopypowers:celestial_ore_drop",
          "functions": [
            { "function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops" },
            { "function": "minecraft:explosion_decay" }
          ]
        }
      ]
    }]
  }]
}
```
(Replace `celestial_ore_drop` with whatever the actual drop item is registered as.)

---

### Deepslate Celestial Ore
Identical requirements to Celestial Ore — diamond+ pickaxe, Fortune-sensitive
drops, no drop with wrong tool.

Apply the same code fix, tag entries, and loot table pattern as Celestial Ore.
Use the deepslate variant's registered name throughout.

---

### Casino Bars
- Only mineable with an iron-tier pickaxe or better
- Drops the block itself when broken with correct tool
- Wrong tool = no drop

**Code fix:**
```java
BlockBehaviour.Properties.of()
    .requiresCorrectToolForDrops()
    .strength(2.0f, 2.0f)
```

**Tag files needed:**
- `data/minecraft/tags/blocks/mineable/pickaxe.json` — add casino bars
- `data/minecraft/tags/blocks/needs_stone_tool.json` — add casino bars
  (NeoForge 1.21.1: iron tier = needs_stone_tool tag, which gates iron+)

**Loot table** — simple self-drop:
```json
{
  "type": "minecraft:block",
  "pools": [{
    "rolls": 1,
    "bonus_rolls": 0,
    "conditions": [{ "condition": "minecraft:survives_explosion" }],
    "entries": [{ "type": "minecraft:item", "name": "loopypowers:casino_bars" }]
  }]
}
```

---

### Casino Floor
Identical requirements to Casino Bars — iron+ pickaxe, drops itself, no drop
with wrong tool.

Apply same code fix, tag entries (`needs_stone_tool`), and loot table.

---

### Thorn Vine
- Broken faster with a sword (sword is the preferred tool)
- Drops the item when broken with a sword
- No special tier requirement — any sword works

**Code fix** — NeoForge 1.21.1 uses `ToolActions` for sword speed bonus.
In the block class, override `getDestroySpeed` or use the correct property.
The cleanest approach is to register it in the sword-mineable tag:

**Tag files needed:**
- `data/minecraft/tags/blocks/mineable/sword.json` — add thorn vine
  (create this file if it doesn't exist — NeoForge respects it)

**Code fix for drop on sword only:**
```java
BlockBehaviour.Properties.of()
    .requiresCorrectToolForDrops()
    .strength(0.4f, 0.4f)
```

**Loot table** — drop item when broken with sword:
```json
{
  "type": "minecraft:block",
  "pools": [{
    "rolls": 1,
    "entries": [{
      "type": "minecraft:item",
      "conditions": [{
        "condition": "minecraft:match_tool",
        "predicate": { "tag": "minecraft:swords" }
      }],
      "name": "loopypowers:thorn_vine"
    }]
  }]
}
```

---

### Ice Spike
- Broken faster with a pickaxe
- Only drops with Silk Touch — no drop otherwise (like packed ice)
- No tier requirement

**Code fix:**
```java
BlockBehaviour.Properties.of()
    .requiresCorrectToolForDrops()
    .strength(0.5f, 0.5f)
```

**Tag files needed:**
- `data/minecraft/tags/blocks/mineable/pickaxe.json` — add ice spike

**Loot table** — silk touch only drop:
```json
{
  "type": "minecraft:block",
  "pools": [{
    "rolls": 1,
    "entries": [{
      "type": "minecraft:item",
      "conditions": [{
        "condition": "minecraft:match_tool",
        "predicate": {
          "enchantments": [{ "enchantment": "minecraft:silk_touch", "levels": { "min": 1 } }]
        }
      }],
      "name": "loopypowers:ice_spike"
    }]
  }]
}
```

---

## Workflow

1. Read each block class in `block/` to find its current `BlockBehaviour.Properties`
2. Add `requiresCorrectToolForDrops()` and appropriate strength values
3. Check and update the tag JSON files for each block
4. Create or update the loot table JSON for each block
5. Verify registered names match exactly between the block class, tags, and loot tables

Do one block at a time. After each block confirm the properties, tags, and
loot table are all consistent before moving to the next.

## Checklist
- [ ] Celestial Ore
- [ ] Deepslate Celestial Ore
- [ ] Casino Bars
- [ ] Casino Floor
- [ ] Thorn Vine
- [ ] Ice Spike
