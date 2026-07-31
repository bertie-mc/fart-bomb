package com.berlord.fartbomb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
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

/**
 * Fart Bomb — a Whoopee Cushion fart that goes off while the wearer is alight detonates.
 *
 * <p>Every fart counts, not just the ones from Artifacts' flatulence roll. See
 * {@link com.berlord.fartbomb.mixin.ServerLevelMixin} for why the hook is on the sound
 * rather than on the {@code artifacts:fart} game event.
 *
 * <p>When a fart detonates its ordinary sound is suppressed and {@link #BIG_FART} plays in
 * its place — the same audio an octave down, which is as deep as Minecraft's sound engine
 * allows.
 */
@Mod(FartBomb.MOD_ID)
public class FartBomb {
    public static final String MOD_ID = "fartbomb";

    /** Artifacts' Whoopee Cushion fart. Matched by location so the holder type does not matter. */
    private static final ResourceLocation ARTIFACTS_FART =
            ResourceLocation.fromNamespaceAndPath("artifacts", "item.whoopee_cushion.fart");

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    /**
     * The detonation fart. Defined in {@code assets/fartbomb/sounds.json} as Artifacts' own
     * fart samples at pitch 0.5, and registered as a variable-range event so the volume
     * passed at play time widens how far it carries.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> BIG_FART = SOUNDS.register(
            "item.whoopee_cushion.big_fart",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "item.whoopee_cushion.big_fart")));

    public FartBomb(IEventBus modBus, ModContainer container) {
        SOUNDS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, FartBombConfig.SPEC);
    }

    /**
     * A fart is being played by an entity. Returns {@code true} only when the ordinary fart
     * sound should be suppressed — that is, when it detonated <em>and</em> the big fart has
     * been played in its place. With {@code replaceSound} off it still detonates, and the
     * normal fart is left to play.
     */
    public static boolean onFart(ServerLevel level, Holder<SoundEvent> sound, Player player) {
        if (!isFartSound(sound)) {
            return false;
        }
        if (!FartBombConfig.ENABLED.getAsBoolean() || !isIgnited(player)) {
            return false;
        }
        detonate(player, level);
        return FartBombConfig.REPLACE_SOUND.getAsBoolean();
    }

    /**
     * A fart is being played at a position with no source entity attached — the equip case.
     * Artifacts plays it at the wearer's exact feet position, so the nearest player within a
     * block of it is the farter.
     */
    public static boolean onFartAt(ServerLevel level, Holder<SoundEvent> sound, double x, double y, double z) {
        if (!isFartSound(sound)) {
            return false;
        }
        Player player = level.getNearestPlayer(x, y, z, 1.0, false);
        return player != null && onFart(level, sound, player);
    }

    private static boolean isFartSound(Holder<SoundEvent> sound) {
        return sound.value().getLocation().equals(ARTIFACTS_FART);
    }

    /**
     * Whether the player is alight or standing somewhere hot enough to set a fart off.
     *
     * <p>Two positions matter and they are not interchangeable. A magma block is
     * <em>below</em> the feet because it is a full cube; a campfire or a torch is
     * <em>at</em> the feet, because the player stands inside that block position — on the
     * campfire's shortened collision box, or simply in the torch's empty one. Hence two tags.
     */
    private static boolean isIgnited(Player player) {
        if (FartBombConfig.TRIGGER_WHEN_BURNING.getAsBoolean() && player.isOnFire()) {
            return true;
        }
        // Covers standing in lava; standing on its surface is the tag check below.
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

    /**
     * A tagged block only counts when it is actually burning. Campfires carry the vanilla
     * {@code lit} property and an extinguished one is just a wooden step; blocks without the
     * property (torches, lava, magma) always count.
     */
    private static boolean isFiery(BlockState state, TagKey<Block> tag) {
        if (!state.is(tag)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
    }

    private static void detonate(Player player, ServerLevel level) {
        Vec3 midpoint = player.position().add(0.0, player.getBbHeight() / 2.0, 0.0);

        // Minecraft's forward vector for a yaw is (-sin, cos); the fart leaves from the
        // opposite side. getYRot() is where the player is looking — swap for yBodyRot if
        // the torso should win over the head.
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 behind = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw))
                .scale(FartBombConfig.OFFSET_BEHIND.getAsDouble());

        Vec3 at = midpoint.add(behind).subtract(0.0, FartBombConfig.OFFSET_BELOW.getAsDouble(), 0.0);

        // Plays before the explosion so it is not buried under the blast. A different sound
        // event, so it passes straight back through the mixin without recursing.
        if (FartBombConfig.REPLACE_SOUND.getAsBoolean()) {
            level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    BIG_FART.get(),
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
