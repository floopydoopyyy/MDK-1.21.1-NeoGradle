package com.loopy.loopypowers.client.fx;

public final class StunAudioClient { // handles client checks so see if player is stunned
    private static int stunTicks = 0;

    private StunAudioClient() {}

    public static void setStun(int ticks) {
        stunTicks = Math.max(stunTicks, ticks);
    }

    public static void tick() {
        if (stunTicks > 0) stunTicks--;
    }

    public static float volumeMultiplier() {
        if (stunTicks <= 0) return 1.0f;
        return 0.15f; // 15% volume while stunned
    }

    public static boolean isStunned() {
        return stunTicks > 0;
    }
}