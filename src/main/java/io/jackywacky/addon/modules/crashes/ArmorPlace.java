/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.jackywacky.addon.modules.crashes;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import io.jackywacky.addon.DupersUnitedPublicAddon;

/** Placement target: block we click and which face. Stand spawns adjacent to that face. */
record PlaceTarget(BlockPos blockPos, Direction face) {}

public class ArmorPlace extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between placement bursts (in ticks).")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> packetsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("packets-per-tick")
        .description("Placement packets to send per tick. Higher = faster (144 packets fills 16x9 in &lt;1 sec at 50+).")
        .defaultValue(50)
        .min(1)
        .max(150)
        .sliderRange(10, 150)
        .build()
    );

    private final Setting<Integer> length = sgGeneral.add(new IntSetting.Builder()
        .name("length")
        .description("Length of the line (blocks forward).")
        .defaultValue(5)
        .min(1)
        .max(6)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Integer> verticality = sgGeneral.add(new IntSetting.Builder()
        .name("verticality")
        .description("Blocks up - places stands on floors and on the side of walls.")
        .defaultValue(3)
        .min(1)
        .max(6)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Boolean> disableOnEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-empty")
        .description("Disables the module when no armor stands are found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> raycastRange = sgRender.add(new BoolSetting.Builder()
        .name("raycast-range")
        .description("Highlight all placeable spots within block interaction range.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> highlightColor = sgRender.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Color for placeable spot highlights.")
        .defaultValue(new SettingColor(0, 255, 0, 60))
        .visible(raycastRange::get)
        .build()
    );

    private final Setting<SettingColor> highlightLineColor = sgRender.add(new ColorSetting.Builder()
        .name("highlight-line-color")
        .description("Outline color for placeable spot highlights.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .visible(raycastRange::get)
        .build()
    );

    private int currentDelay = 0;

    public ArmorPlace() {
        super(DupersUnitedPublicAddon.CATEGORY_CRASHES, "armor-stand-placer", "Places armor stands in front. Packet-based for speed.");
    }

    @Override
    public void onDeactivate() {
        InvUtils.swapBack();
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (!raycastRange.get() || mc.player == null || mc.world == null) return;
        for (BlockPos standPos : getPlaceableSpotsInReach()) {
            event.renderer.box(standPos, highlightColor.get(), highlightLineColor.get(), ShapeMode.Both, 0);
        }
    }

    private List<BlockPos> getPlaceableSpotsInReach() {
        List<BlockPos> out = new ArrayList<>();
        for (PlaceTarget t : collectPlaceTargets()) {
            out.add(t.blockPos().offset(t.face()));
        }
        return out;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        if (currentDelay < delay.get()) {
            currentDelay++;
            return;
        }

        currentDelay = 0;
        placeArmorStands();
    }

    private void placeArmorStands() {
        int slot = ensureArmorStandInHotbar();
        if (slot == -1) {
            if (disableOnEmpty.get()) toggle();
            info("No armor stands in inventory.");
            return;
        }

        List<PlaceTarget> targets = collectPlaceTargets();
        if (targets.isEmpty()) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        var networkHandler = mc.getNetworkHandler();

        InvUtils.swap(slot, false);
        networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        for (PlaceTarget target : targets) {
            sendPlacePacket(target);
        }

        InvUtils.swap(prevSlot, true);
        networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
    }

    /** Find armor stand in hotbar, or move from inventory to hotbar. Returns slot or -1. */
    private int ensureArmorStandInHotbar() {
        Predicate<ItemStack> isStand = s -> s.getItem() == Items.ARMOR_STAND;

        // Check hotbar first
        int slot = findSlot(isStand, 0, 8);
        if (slot != -1) return slot;

        // Find empty hotbar slot
        int hotbarSlot = findEmptyHotbarSlot();
        if (hotbarSlot == -1) return -1;

        // Find in main inventory
        slot = findSlot(isStand, 9, 35);
        if (slot == -1) return -1;

        // Move from inv to hotbar
        InvUtils.move().from(slot).toHotbar(hotbarSlot);
        InvUtils.dropHand();

        return hotbarSlot;
    }

    private int findSlot(Predicate<ItemStack> predicate, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i <= 8; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Line in front: floors and walls. Full length at each height, then next height. */
    private List<PlaceTarget> collectPlaceTargets() {
        BlockPos playerPos = mc.player.getBlockPos();
        Direction dir = mc.player.getHorizontalFacing();
        int len = length.get();
        int v = verticality.get();
        List<PlaceTarget> out = new ArrayList<>();
        for (int h = 0; h < v; h++) {
            for (int i = 1; i <= len; i++) {
                BlockPos at = playerPos.offset(dir, i).up(h);
                out.add(new PlaceTarget(at.down(), Direction.UP));       // floor
                out.add(new PlaceTarget(at, dir.getOpposite()));         // wall face toward us
            }
        }
        return out;
    }

    private void sendPlacePacket(PlaceTarget target) {
        Direction face = target.face();
        Vec3d hitPos = Vec3d.ofCenter(target.blockPos())
            .add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, face, target.blockPos(), false);
        mc.getNetworkHandler().sendPacket(
            new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0)
        );
    }
}
