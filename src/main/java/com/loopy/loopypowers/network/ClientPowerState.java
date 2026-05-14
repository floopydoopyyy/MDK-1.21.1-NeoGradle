package com.loopy.loopypowers.network;

// registers some powers client side so the client doesn't battle their passives.
public final class ClientPowerState {
    private ClientPowerState() {}

    private static volatile boolean hasStrengthPower = false;
    private static volatile boolean overdriveActive = false;

    public static boolean hasStrengthPower() {
        return hasStrengthPower;
    }

    public static void setStrengthPower(boolean v) {
        hasStrengthPower = v;
    }

    public static boolean isOverdriveActive() {
        return overdriveActive;
    }

    public static void setOverdriveActive(boolean v) {
        overdriveActive = v;
    }
}