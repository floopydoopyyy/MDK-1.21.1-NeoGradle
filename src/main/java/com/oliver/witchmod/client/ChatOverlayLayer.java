package com.oliver.witchmod.client;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import com.oliver.witchmod.data.WitchModAttachments;

/**
 * The Chat blessing's fake Twitch-chat overlay (Phase D / Section 13.4): a small scrolling panel of hype
 * messages, rendered while the auto-synced {@link WitchModAttachments#CHAT_OVERLAY} flag is active. Lines
 * are generated client-side on a timer (the panel is purely cosmetic, so it needs no server push beyond the
 * active flag).
 *
 * <p>PROTOTYPE: messages come from a hardcoded pool and there's no real Twitch-frame texture yet
 * (Human Action Items); the "surface real useful info" behaviour is deferred.
 */
public final class ChatOverlayLayer implements LayeredDraw.Layer {
    private static final int MAX_LINES = 5;
    private static final int NEW_LINE_INTERVAL = 40; // ticks between messages
    private static final int PANEL_WIDTH = 130;
    private static final String[] POOL = {
            "xX_gamer_Xx: W", "poggers123: no wayyy", "mod_steve: clean", "lurker99: KEKW",
            "hype_train: LETS GOOO", "chatter: GG ez", "sub_gifter: cracked", "quiet_andy: o7",
            "bot_helper: !uptime", "viewer42: actually insane",
    };

    private final Deque<String> lines = new ArrayDeque<>();
    private long lastLineTick = Long.MIN_VALUE;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player.getData(WitchModAttachments.CHAT_OVERLAY) < 0) {
            lines.clear();
            return; // Chat blessing not active
        }

        long now = mc.level.getGameTime();
        if (now - lastLineTick >= NEW_LINE_INTERVAL) {
            lastLineTick = now;
            lines.addLast(POOL[player.getRandom().nextInt(POOL.length)]);
            while (lines.size() > MAX_LINES) {
                lines.removeFirst();
            }
        }

        Font font = mc.font;
        int x = 4;
        int lineHeight = font.lineHeight + 2;
        int y = guiGraphics.guiHeight() / 2 - (lines.size() * lineHeight) / 2;

        guiGraphics.fill(x - 2, y - 2, x + PANEL_WIDTH, y + lines.size() * lineHeight, 0x88512D8A); // twitch-purple panel
        int i = 0;
        for (String line : lines) {
            guiGraphics.drawString(font, Component.literal(line), x, y + i * lineHeight, 0xFFE6DCF5);
            i++;
        }
    }
}
