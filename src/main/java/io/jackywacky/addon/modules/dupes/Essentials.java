/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.jackywacky.addon.modules.dupes;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import io.jackywacky.addon.DupersUnitedPublicAddon;

public class Essentials extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgConfig = settings.createGroup("Command");

    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
        .name("item")
        .description("Item for /recipe {item name}. Uses registry path (e.g. netherite_block, mojang_banner_pattern). Any MC item.")
        .defaultValue(Items.DIAMOND)
        .build()
    );

    private final Setting<Boolean> recipe = sgConfig.add(new BoolSetting.Builder()
        .name("/recipe")
        .description("Uses /recipe")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> recipes = sgConfig.add(new BoolSetting.Builder()
        .name("/recipes")
        .description("Uses /recipes")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> formula = sgConfig.add(new BoolSetting.Builder()
        .name("/formula")
        .description("Uses /formula")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> eformula = sgConfig.add(new BoolSetting.Builder()
        .name("/eformula")
        .description("Uses /eformula")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> method = sgConfig.add(new BoolSetting.Builder()
        .name("/method")
        .description("Uses /method")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> emethod = sgConfig.add(new BoolSetting.Builder()
        .name("/emethod")
        .description("Uses /emethod")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The delay between specified messages in ticks.")
        .defaultValue(20)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> cancelInventoryOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("cancel-inventory-open")
        .description("No-bed method: replicate sleeping state so items appear in inv. See EssentialsX #6450.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> closeDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("close-delay-ticks")
        .description("Ticks to wait before sending close (simulates wake-up). Only for cancel-inventory-open.")
        .defaultValue(20)
        .min(1)
        .max(100)
        .sliderRange(5, 60)
        .visible(() -> cancelInventoryOpen.get())
        .build()
    );

    private final Setting<Boolean> onlyWhenSleeping = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-sleeping")
        .description("Only run the command when the player is in a bed and sleeping.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoBedDupe = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-bed-dupe")
        .description("Automatically get in and out of beds to run the dupe repeatedly without it becoming day.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> bedRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("bed-range")
        .description("Range to find beds for auto-bed-dupe.")
        .defaultValue(4)
        .min(1)
        .max(6)
        .sliderRange(2, 6)
        .visible(() -> autoBedDupe.get())
        .build()
    );

    private final Setting<Integer> ticksInBedBeforeWake = sgGeneral.add(new IntSetting.Builder()
        .name("ticks-in-bed-before-wake")
        .description("Ticks to stay in bed after sending command before leaving (auto-bed-dupe).")
        .defaultValue(15)
        .min(5)
        .max(60)
        .sliderRange(5, 40)
        .visible(() -> autoBedDupe.get())
        .build()
    );

    private int messageI, timer;
    /** When cancel-inventory-open: syncId we cancelled (recipe screen); send close after closeDelayTicks. */
    private int pendingCloseSyncId = -1;
    private int pendingCloseTicks = -1;
    /** Auto bed dupe: countdown before waking; when 0 we call wakeUp(). */
    private int wakeCountdown = -1;
    /** Cooldown before trying to enter a bed again after waking. */
    private int enterBedCooldown = 0;

    public Essentials() {
        super(DupersUnitedPublicAddon.CATEGORY_DUPES, "Essentials", "Essentials /recipe dupe. Bed method (sleep + /recipe) or cancel-inventory-open (no bed).");
    }

    @Override
    public void onActivate() {
        timer = delay.get();
        messageI = 0;
        pendingCloseSyncId = -1;
        pendingCloseTicks = -1;
        wakeCountdown = -1;
        enterBedCooldown = 0;
    }

    @Override
    public void onDeactivate() {
        pendingCloseSyncId = -1;
        pendingCloseTicks = -1;
        wakeCountdown = -1;
        enterBedCooldown = 0;
    }

    /**
     * Recreate sleeping state: only cancel the GUI open (no screen). Slot updates are still applied.
     * After close-delay-ticks we send close so the server can push items into our inventory.
     */
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!cancelInventoryOpen.get()) return;
        if (!(event.packet instanceof OpenScreenS2CPacket packet)) return;
        int syncId = packet.getSyncId();
        event.cancel();
        pendingCloseSyncId = syncId;
        pendingCloseTicks = closeDelayTicks.get();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!cancelInventoryOpen.get()) return;
        if (event.screen instanceof HandledScreen<?>) {
            event.cancel();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Auto bed dupe: get in/out of beds
        if (autoBedDupe.get()) {
            if (mc.player.isSleeping()) {
                if (wakeCountdown > 0) {
                    wakeCountdown--;
                    if (wakeCountdown <= 0) {
                        mc.player.wakeUp(true, false);
                        enterBedCooldown = 20;
                    }
                }
            } else {
                if (enterBedCooldown > 0) {
                    enterBedCooldown--;
                } else {
                    BlockPos bed = findBedInRange();
                    if (bed != null && mc.interactionManager != null) {
                        BlockHitResult hit = new BlockHitResult(
                            Vec3d.ofCenter(bed),
                            Direction.UP,
                            bed,
                            false
                        );
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                        enterBedCooldown = 15;
                    }
                }
            }
        }

        // Delayed close (simulate wake-up) so server pushes items to our inventory
        if (pendingCloseTicks > 0 && pendingCloseSyncId >= 0) {
            pendingCloseTicks--;
            if (pendingCloseTicks <= 0 && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(pendingCloseSyncId));
                pendingCloseSyncId = -1;
                pendingCloseTicks = -1;
            }
        }

        if (timer > 0) {
            timer--;
            return;
        }

        if ((onlyWhenSleeping.get() || autoBedDupe.get()) && !mc.player.isSleeping()) {
            return;
        }

        Item chosen = item.get();
        if (chosen == null || chosen == Items.AIR) return;

        String itemName = Registries.ITEM.getId(chosen).getPath();
        String command = getCommandPrefix() + itemName;
        ChatUtils.sendPlayerMsg(command);

        if (autoBedDupe.get()) {
            wakeCountdown = ticksInBedBeforeWake.get();
        }

        timer = delay.get();
    }

    private String getCommandPrefix() {
        if (recipe.get()) return "/recipe ";
        if (recipes.get()) return "/recipes ";
        if (formula.get()) return "/formula ";
        if (eformula.get()) return "/eformula ";
        if (method.get()) return "/method ";
        if (emethod.get()) return "/emethod ";
        return "";
    }

    /** Returns a bed block position within bedRange, or null. */
    private BlockPos findBedInRange() {
        double range = bedRange.get();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(range);
        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (playerPos.getSquaredDistance(pos) > range * range) continue;
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
