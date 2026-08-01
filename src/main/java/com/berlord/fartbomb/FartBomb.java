package com.berlord.fartbomb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Turns an ignited Artifacts Whoopee Cushion fart into an explosion. */
@Mod(FartBomb.MOD_ID)
public class FartBomb {
    public static final String MOD_ID = "fartbomb";

    private static final ResourceLocation ARTIFACTS_FART =
            ResourceLocation.fromNamespaceAndPath("artifacts", "item.whoopee_cushion.fart");

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GOD_VOICE_FART = SOUNDS.register(
            "god_voice_fart",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "god_voice_fart")));

    public FartBomb(IEventBus modBus, ModContainer container) {
        SOUNDS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, FartBombConfig.SPEC);
    }

    public static boolean onFart(ServerLevel level, Holder<SoundEvent> sound, Player player) {
        if (!FartPolicy.isFartSound(sound.value().getLocation().getNamespace(),
                sound.value().getLocation().getPath())) {
            return false;
        }
        boolean enabled = FartBombConfig.ENABLED.getAsBoolean();
        if (!FartPolicy.shouldDetonate(enabled, enabled && isIgnited(player))) {
            return false;
        }
        detonate(player, level);
        return FartPolicy.suppressOriginalSound(true, FartBombConfig.REPLACE_SOUND.getAsBoolean());
    }

    public static boolean onFartAt(ServerLevel level, Holder<SoundEvent> sound, double x, double y, double z) {
        if (!sound.value().getLocation().equals(ARTIFACTS_FART)) {
            return false;
        }
        Player player = level.getNearestPlayer(x, y, z, 1.0, false);
        return player != null && onFart(level, sound, player);
    }

    private static boolean isIgnited(Player player) {
        if (FartBombConfig.TRIGGER_WHEN_BURNING.getAsBoolean() && player.isOnFire()) {
            return true;
        }
        if (player.isInLava()) {
            return true;
        }

        Level level = player.level();
        BlockPos feet = player.blockPosition();
        if (isFiery(level.getBlockState(feet), ModTags.IGNITES_AT_FEET)) {
            return true;
        }
        return isFiery(level.getBlockState(feet.below()), ModTags.IGNITES_BELOW);
    }

    static boolean isFiery(BlockState state, TagKey<Block> tag) {
        if (!state.is(tag)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
    }

    private static void detonate(Player player, ServerLevel level) {
        FartPlacement.Position position = FartPlacement.behindPlayer(
                player.getX(), player.getY(), player.getZ(), player.getBbHeight(), player.getYRot(),
                FartBombConfig.OFFSET_BEHIND.getAsDouble(), FartBombConfig.OFFSET_BELOW.getAsDouble());
        Vec3 at = new Vec3(position.x(), position.y(), position.z());

        if (FartBombConfig.REPLACE_SOUND.getAsBoolean()) {
            level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    GOD_VOICE_FART.get(),
                    SoundSource.PLAYERS,
                    (float) FartBombConfig.SOUND_VOLUME.getAsDouble(),
                    1.0F);
        }

        level.explode(
                player,
                at.x, at.y, at.z,
                (float) FartBombConfig.POWER.getAsDouble(),
                FartBombConfig.START_FIRES.getAsBoolean(),
                FartBombConfig.BREAK_BLOCKS.getAsBoolean()
                        ? Level.ExplosionInteraction.TNT
                        : Level.ExplosionInteraction.NONE);
    }
}
