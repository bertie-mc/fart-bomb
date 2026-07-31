package com.berlord.fartbomb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.VanillaGameEvent;

/**
 * Fart Bomb — a Whoopee Cushion fart that goes off while the wearer is alight
 * detonates.
 *
 * <p>Artifacts fires an {@code artifacts:fart} game event from both of its
 * flatulence sites — {@code Entity#setShiftKeyDown} (the rising edge of crouching)
 * and its double-jump ability — immediately after the {@code artifacts:flatulence}
 * probability roll succeeds. {@link VanillaGameEvent} is posted unconditionally from
 * {@code ServerLevel#gameEvent} for every game event, modded ones included, so
 * listening for it catches exactly the moment a fart actually happens without a mixin
 * and without a compile-time dependency on Artifacts.
 *
 * <p>The explosion is placed behind and below the wearer's midpoint rather than at
 * their feet, so it reads as coming out of the player rather than off the floor.
 */
@Mod(FartBomb.MOD_ID)
public class FartBomb {
    public static final String MOD_ID = "fartbomb";

    /** The game event Artifacts fires when a Whoopee Cushion fart lands. */
    private static final ResourceKey<GameEvent> ARTIFACTS_FART = ResourceKey.create(
            Registries.GAME_EVENT,
            ResourceLocation.fromNamespaceAndPath("artifacts", "fart"));

    public FartBomb(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, FartBombConfig.SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onVanillaGameEvent(VanillaGameEvent event) {
        if (!event.getVanillaEvent().is(ARTIFACTS_FART)) {
            return;
        }
        if (!FartBombConfig.ENABLED.getAsBoolean()) {
            return;
        }
        if (!(event.getCause() instanceof Player player)) {
            return;
        }

        // VanillaGameEvent is server-only, but the explosion must not run client-side
        // under any circumstance.
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }
        if (!isIgnited(player)) {
            return;
        }

        detonate(player, level);
    }

    /**
     * Whether the player is alight or standing somewhere hot enough to set a fart off.
     *
     * <p>Two positions matter and they are not interchangeable. A magma block is
     * <em>below</em> the feet because it is a full cube; a campfire or a torch is
     * <em>at</em> the feet, because the player stands inside that block position —
     * on the campfire's shortened collision box, or simply in the torch's empty one.
     * Hence two tags rather than one.
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
     * A tagged block only counts when it is actually burning. Campfires carry the
     * vanilla {@code lit} property and an extinguished one is just a wooden step;
     * blocks without the property (torches, lava, magma) always count.
     */
    private static boolean isFiery(BlockState state, TagKey<Block> tag) {
        if (!state.is(tag)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
    }

    private static void detonate(Player player, Level level) {
        Vec3 midpoint = player.position().add(0.0, player.getBbHeight() / 2.0, 0.0);

        // Minecraft's forward vector for a yaw is (-sin, cos); the fart leaves from
        // the opposite side. getYRot() is where the player is looking — swap for
        // yBodyRot if the torso should win over the head.
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 behind = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw))
                .scale(FartBombConfig.OFFSET_BEHIND.getAsDouble());

        Vec3 at = midpoint.add(behind).subtract(0.0, FartBombConfig.OFFSET_BELOW.getAsDouble(), 0.0);

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
