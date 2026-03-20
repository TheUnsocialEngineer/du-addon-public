package io.jackywacky.addon.mixin;

import io.jackywacky.addon.ChannelKeeper;
import io.jackywacky.addon.VersionKeeper;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.client.network.ClientConfigurationNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.config.SelectKnownPacksS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onCustomPayload", at = @At("HEAD"))
    private void onCustomPayload(CustomPayload payload, CallbackInfo ci) {
        if(!ChannelKeeper.channels.contains(payload.getId().toString()))
            ChannelKeeper.channels.add(payload.getId().toString());
    }
}
