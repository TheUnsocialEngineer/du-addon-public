package io.jackywacky.addon.mixin;

import io.jackywacky.addon.modules.settingsmodules.GuiSlotNbt;
import io.jackywacky.addon.ui.EnterableTextField;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(HandledScreen.class)
public abstract class HandledScreenScreenMixin<T extends ScreenHandler> extends Screen {
    @Final
    @Shadow
    protected T handler;

    protected HandledScreenScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at= @At("TAIL"))
    public void init(CallbackInfo ci) {
        if(!Modules.get().isActive(GuiSlotNbt.class)) return;
        if(handler instanceof GenericContainerScreenHandler screenHandler) {
            addDrawableChild(EnterableTextField.builder(textRenderer)
                .dimensions(50, 20, 10, 10)
                .placeholder(Text.literal("Slot ID"))
                .onEnter(txt -> {
                    try {
                        int x = Integer.parseInt(txt.getText());
                        if (screenHandler.getInventory().getStack(x) == null || screenHandler.getInventory().getStack(x).getRegistryEntry().getIdAsString().equals("minecraft:air")) {
                            ChatUtils.error("Slot doesnt contain anything!");
                            return;
                        }
                        NbtElement element = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, screenHandler.getInventory().getStack(x)).result().get();
                        ChatUtils.sendMsg(NbtHelper.toPrettyPrintedText(element));
                        mc.keyboard.setClipboard(NbtHelper.toPrettyPrintedText(element).getString());
                    } catch (NumberFormatException e) {
                        ChatUtils.error("Invalid slot ID: " + txt.getText());
                    }
                }).build());
        }

    }
}
