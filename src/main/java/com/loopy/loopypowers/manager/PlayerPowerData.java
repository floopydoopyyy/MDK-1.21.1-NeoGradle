package com.loopy.loopypowers.manager;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * Per-player power state stored as a NeoForge entity attachment.
 * Lives in the player's own NBT, so it is automatically scoped
 * per-world-save rather than being shared across saves via static maps.
 */
public class PlayerPowerData implements INBTSerializable<CompoundTag> {

    private String powerName = null;   // null  = no power assigned
    private int    level     = 1;
    private double cdMult    = 1.0;
    private boolean passive  = true;

    /* ---- accessors ---- */

    public String  getPowerName() { return powerName; }
    public int     getLevel()     { return level; }
    public double  getCdMult()    { return cdMult; }
    public boolean isPassive()    { return passive; }

    public void setPowerName(String name)  { this.powerName = name; }
    public void setLevel(int lvl)          { this.level = Math.max(1, Math.min(3, lvl)); }
    public void setCdMult(double m)        { this.cdMult = Math.max(0.0, m); }
    public void setPassive(boolean p)      { this.passive = p; }
    public void clearPower()               { this.powerName = null; }

    /* ---- NBT serialization (called automatically by NeoForge) ---- */

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (powerName != null) tag.putString("lp_power", powerName);
        tag.putInt("lp_level", level);
        tag.putDouble("lp_cd_mult", cdMult);
        tag.putBoolean("lp_passive", passive);
        return tag;
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag tag) {
        powerName = tag.contains("lp_power")   ? tag.getString("lp_power")   : null;
        level     = tag.contains("lp_level")   ? tag.getInt("lp_level")      : 1;
        cdMult    = tag.contains("lp_cd_mult") ? tag.getDouble("lp_cd_mult") : 1.0;
        passive   = !tag.contains("lp_passive") || tag.getBoolean("lp_passive");
    }
}