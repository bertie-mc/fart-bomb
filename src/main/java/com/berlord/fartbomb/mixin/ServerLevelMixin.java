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

/**
 * Catches every fart, whatever played it.
 *
 * <p>Artifacts farts on three occasions and they do not share a code path: crouching and
 * double-jumping go through its flatulence roll, while equipping the Whoopee Cushion plays
 * the same sound from the accessory framework's equip hook. Only the first two fire the
 * {@code artifacts:fart} game event, which is why listening for that event missed the
 * equip fart entirely.
 *
 * <p>What all three do share is the sound. {@code ServerLevel} overrides exactly two
 * {@code playSeededSound} methods — one entity-relative, one positional — and every
 * server-side sound in the game funnels through them, so intercepting both catches any
 * fart, including any Artifacts might add later.
 *
 * <p>Mixing into {@code ServerLevel} rather than {@code Level} is deliberate: it is the
 * server half of the abstract pair, so the client's copy is untouched and the handler
 * cannot fire twice in single-player.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    /**
     * The flatulence path — {@code Level#playSound(Player, Entity, ...)} lands here, and the
     * farting entity is passed straight in.
     */
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

    /**
     * The equip path — Artifacts plays that one positionally with a {@code null} excluded
     * player, so the wearer has to be recovered from the position rather than read off a
     * parameter.
     */
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
