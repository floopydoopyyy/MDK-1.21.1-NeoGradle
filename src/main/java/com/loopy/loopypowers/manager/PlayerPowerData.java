package com.loopy.loopypowers.manager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player power state stored as a NeoForge entity attachment.
 * Lives in the player's own NBT, so it is automatically scoped
 * per-world-save rather than being shared across saves via static maps.
 */
public class PlayerPowerData implements INBTSerializable<CompoundTag> {

    private String  powerName = null;  // null = no power assigned
    private int     level     = 1;
    private double  cdMult    = 1.0;
    private boolean passive   = true;

    // Cooldown end timestamps (epoch-ms). Persisted so cooldowns survive
    // world reloads. Key format: "powerName:ABILITY_TYPE" (matches abilityKey()).
    private final Map<String, Long> cooldownEndMs = new HashMap<>();

    /* ---- accessors ---- */

    public String  getPowerName() { return powerName; }
    public int     getLevel()     { return level; }
    public double  getCdMult()    { return cdMult; }
    public boolean isPassive()    { return passive; }

    public void setPowerName(String name) { this.powerName = name; }
    public void setLevel(int lvl)         { this.level = Math.max(1, Math.min(3, lvl)); }
    public void setCdMult(double m)       { this.cdMult = Math.max(0.0, m); }
    public void setPassive(boolean p)     { this.passive = p; }
    public void clearPower()              { this.powerName = null; }

    /* ---- cooldown map accessors ---- */

    public Map<String, Long> getCooldownEndMs() { return cooldownEndMs; }

    public void putCooldown(String key, long endMs) {
        cooldownEndMs.put(key, endMs);
    }

    public void removeCooldown(String key) {
        cooldownEndMs.remove(key);
    }

    public void clearCooldowns() {
        cooldownEndMs.clear();
    }

    /* ---- NBT serialization (called automatically by NeoForge) ---- */

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (powerName != null) tag.putString("lp_power", powerName);
        tag.putInt("lp_level", level);
        tag.putDouble("lp_cd_mult", cdMult);
        tag.putBoolean("lp_passive", passive);

        // Serialize cooldowns as a list of {key, endMs} compound tags.
        // We only write entries that haven't expired yet to keep the NBT clean.
        long now = System.currentTimeMillis();
        ListTag cdList = new ListTag();
        for (Map.Entry<String, Long> e : cooldownEndMs.entrySet()) {
            if (e.getValue() > now) {
                CompoundTag entry = new CompoundTag();
                entry.putString("k", e.getKey());
                entry.putLong("v", e.getValue());
                cdList.add(entry);
            }
        }
        if (!cdList.isEmpty()) tag.put("lp_cooldowns", cdList);

        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag tag) {
        powerName = tag.contains("lp_power")   ? tag.getString("lp_power")   : null;
        level     = tag.contains("lp_level")   ? tag.getInt("lp_level")      : 1;
        cdMult    = tag.contains("lp_cd_mult") ? tag.getDouble("lp_cd_mult") : 1.0;
        passive   = !tag.contains("lp_passive") || tag.getBoolean("lp_passive");

        cooldownEndMs.clear();
        if (tag.contains("lp_cooldowns", Tag.TAG_LIST)) {
            ListTag cdList = tag.getList("lp_cooldowns", Tag.TAG_COMPOUND);
            long now = System.currentTimeMillis();
            for (int i = 0; i < cdList.size(); i++) {
                CompoundTag entry = cdList.getCompound(i);
                String k  = entry.getString("k");
                long   v  = entry.getLong("v");
                // Skip already-expired cooldowns so they don't clog the map
                if (v > now) cooldownEndMs.put(k, v);
            }
        }
    }
}