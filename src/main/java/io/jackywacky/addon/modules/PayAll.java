/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.jackywacky.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.StringHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import io.jackywacky.addon.DupersUnitedPublicAddon;

public class PayAll extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("EokaAddon");

    public PayAll() {
        super(DupersUnitedPublicAddon.CATEGORY_UTILITIES, "Pay-All", "Pays all players in the server.");
    }

    private int currentDelay = 0;
    private List<String> players = new ArrayList<>();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("Defines the command to use.")
        .defaultValue("/pay {player} {amount}")
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Defines the delay between sending commands (in ticks).")
        .defaultValue(20)
        .sliderRange(0, 200)
        .min(0)
        .build()
    );

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("amount")
        .description("Defines the amount to pay to every player.")
        .defaultValue(1000)
        .sliderRange(1, 200000)
        .min(1)
        .build()
    );

    private final Setting<Boolean> disableOnLeave = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-leave")
        .description("Disables the module when you leave a server.")
        .defaultValue(true)
        .build()
    );

        private final Setting<Boolean> disableOnDisconnect = sgGeneral.add(new BoolSetting.Builder()
            .name("disable-on-disconnect")
            .description("Disables the module when you are disconnected from a server.")
            .defaultValue(true)
            .build()
        );

    @Override
    public void onActivate() {
        fetchPlayers();
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (disableOnDisconnect.get() && event.screen instanceof DisconnectedScreen) {
            toggle();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (disableOnLeave.get()) toggle();
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (players.isEmpty()) fetchPlayers();

        if (currentDelay < delay.get()) {
            currentDelay++;
        } else {
            currentDelay = 0;
            if (!players.isEmpty()) {
                String cmd = command.get().replace("{amount}", String.valueOf(amount.get())).replace("{player}", players.get(0));
                mc.getNetworkHandler().sendChatCommand(cmd);
                players.remove(0);
            }
        }
    }

    private void fetchPlayers() {
        players.clear();
        String playerName = mc.getSession().getUsername();

        for (PlayerListEntry info : mc.player.networkHandler.getPlayerList()) {
            String name = StringHelper.stripTextFormat(info.getProfile().name());

            if (!name.equalsIgnoreCase(playerName)) {
                players.add(name);
            }
        }
    }
}
