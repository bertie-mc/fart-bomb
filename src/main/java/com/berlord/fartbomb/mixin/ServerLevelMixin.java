package com.berlord.fartbomb.mixin;

import com.berlord.fartbomb.FartBomb;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercepts both server sound paths used by Artifacts' Whoopee Cushion. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"),
            cancellable = true)
    private void fartbomb$onSoundAtEntity(
            Player excluded,
            Entity source,
            Holder<SoundEvent> sound,
            SoundSource category,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci) {
        if (source instanceof Player player
                && FartBomb.onFart((ServerLevel)(Object)this, sound, player)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"),
            cancellable = true)
    private void fartbomb$onSoundAtPosition(
            Player excluded,
            double x,
            double y,
            double z,
            Holder<SoundEvent> sound,
            SoundSource category,
            float volume,
            float pitch,
            long seed,
            CallbackInfo ci) {
        if (FartBomb.onFartAt((ServerLevel)(Object)this, sound, x, y, z)) {
            ci.cancel();
        }
    }
}
