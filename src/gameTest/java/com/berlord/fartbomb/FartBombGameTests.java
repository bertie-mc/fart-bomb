package com.berlord.fartbomb;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FartBombGameTests {
    private static final String EMPTY_TEMPLATE = "bastion/mobs/empty";
    private static final Holder<SoundEvent> FART = Holder.direct(SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("artifacts", "item.whoopee_cushion.fart")));

    private FartBombGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void interceptsBothServerSoundPaths(GameTestHelper helper) {
        Set<String> methods = Arrays.stream(helper.getLevel().getClass().getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        helper.assertTrue(methods.stream().anyMatch(name -> name.contains("fartbomb$onSoundAtEntity")),
                "entity sound hook is missing");
        helper.assertTrue(methods.stream().anyMatch(name -> name.contains("fartbomb$onSoundAtPosition")),
                "positional sound hook is missing");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void fieryTagsRespectLitState(GameTestHelper helper) {
        helper.assertTrue(FartBomb.isFiery(Blocks.MAGMA_BLOCK.defaultBlockState(), ModTags.IGNITES_BELOW),
                "magma should ignite from below");
        helper.assertTrue(FartBomb.isFiery(Blocks.CAMPFIRE.defaultBlockState(), ModTags.IGNITES_AT_FEET),
                "lit campfire should ignite at the feet");
        helper.assertFalse(FartBomb.isFiery(
                Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false),
                ModTags.IGNITES_AT_FEET), "extinguished campfire should not ignite");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void burningPlayerDetonatesFart(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.igniteForSeconds(5);
        helper.assertTrue(FartBomb.onFart(helper.getLevel(), FART, player),
                "burning player's fart should detonate and replace the sound");
        helper.succeed();
    }
}
