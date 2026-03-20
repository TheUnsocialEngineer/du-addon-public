/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.jackywacky.addon.modules.dupes;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import io.jackywacky.addon.DupersUnitedPublicAddon;

public class ZAH214 extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> runCommand = sgGeneral.add(new BoolSetting.Builder()
        .name("run-command")
        .description("Runs the /ah list 100 command before disconnecting.")
        .defaultValue(true)
        .build()
    );

    public ZAH214() {
        super(DupersUnitedPublicAddon.CATEGORY_DUPES, "ZAH214", "Disconnects the user while running the command /ah list 100.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (runCommand.get()) {
            mc.player.networkHandler.sendChatMessage("/ah list 100");
        }
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("Disconnected via ZAH214 module")));
        toggle();
    }
}
