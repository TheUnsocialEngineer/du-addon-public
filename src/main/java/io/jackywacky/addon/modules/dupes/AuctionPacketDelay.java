/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.jackywacky.addon.modules.dupes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import io.jackywacky.addon.DupersUnitedPublicAddon;

/**
 * Delays sending the close-window packet when closing auction/shop GUIs.
 * Works with DonutAuction (Shulker) and AxShulkers auction GUIs (DupeDB: packet delay dupe).
 * When you close the GUI, the close packet is held for a set delay then sent, which can cause
 * the server to process the transaction twice (e.g. claim items/money twice).
 */
public class AuctionPacketDelay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to delay the close-window packet (20 ticks = 1 second).")
        .defaultValue(40)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private int pendingSyncId = -1;
    private int ticksLeft;

    public AuctionPacketDelay() {
        super(DupersUnitedPublicAddon.CATEGORY_DUPES, "Auction-Packet-Delay", "Delays close-window packet for DonutAuction / AxShulkers auction GUIs (packet delay dupe).");
    }

    @Override
    public void onActivate() {
        pendingSyncId = -1;
        ticksLeft = 0;
    }

    @Override
    public void onDeactivate() {
        if (pendingSyncId >= 0 && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(pendingSyncId));
        }
        pendingSyncId = -1;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof CloseHandledScreenC2SPacket packet)) return;
        if (mc.currentScreen == null || !(mc.currentScreen instanceof HandledScreen<?>)) return;
        // Don't delay closing the player inventory screen
        if (mc.currentScreen instanceof InventoryScreen) return;
        event.cancel();
        pendingSyncId = packet.getSyncId();
        ticksLeft = delay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pendingSyncId < 0 || mc.getNetworkHandler() == null) return;
        ticksLeft--;
        if (ticksLeft <= 0) {
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(pendingSyncId));
            pendingSyncId = -1;
        }
    }
}
