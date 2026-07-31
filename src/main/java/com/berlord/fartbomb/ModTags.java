package com.berlord.fartbomb;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The two block tags that decide what counts as a fiery surface. Both are datapack
 * tags, so the pack can add modded fire blocks without a rebuild.
 */
public final class ModTags {
    /** Checked against the block the player's lower half occupies — torches, campfires. */
    public static final TagKey<Block> IGNITES_AT_FEET = create("ignites_at_feet");

    /** Checked against the block directly underfoot — magma, lava surface. */
    public static final TagKey<Block> IGNITES_BELOW = create("ignites_below");

    private static TagKey<Block> create(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FartBomb.MOD_ID, path));
    }

    private ModTags() {}
}
