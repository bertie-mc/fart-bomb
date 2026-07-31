package com.berlord.fartbomb;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config for the fart explosion, written to {@code config/fartbomb-common.toml}.
 *
 * <p>The fart's own probability is not here — that is Artifacts' own
 * {@code config/artifacts/items.toml}, under {@code [whoopee_cushion] fartChance}.
 * This mod only decides what happens once a fart has already gone off.
 */
public final class FartBombConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue POWER;
    public static final ModConfigSpec.BooleanValue BREAK_BLOCKS;
    public static final ModConfigSpec.BooleanValue START_FIRES;
    public static final ModConfigSpec.BooleanValue TRIGGER_WHEN_BURNING;
    public static final ModConfigSpec.DoubleValue OFFSET_BEHIND;
    public static final ModConfigSpec.DoubleValue OFFSET_BELOW;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Whoopee Cushion farts detonate when the wearer is on fire or standing on something hot.")
                .push("explosion");

        ENABLED = builder
                .comment("Whether a fart can detonate at all.")
                .define("enabled", true);

        POWER = builder
                .comment("Explosion radius. A creeper is 3.0, TNT is 4.0. Damage reaches 2x this many blocks.")
                .defineInRange("power", 3.0, 0.0, 32.0);

        BREAK_BLOCKS = builder
                .comment("Whether the explosion destroys terrain. False still damages entities.")
                .define("breakBlocks", true);

        START_FIRES = builder
                .comment("Whether the explosion leaves fire behind. Compounds, since fire is itself a trigger.")
                .define("startFires", false);

        TRIGGER_WHEN_BURNING = builder
                .comment("Whether being on fire is enough on its own, with no hot block underfoot.")
                .define("triggerWhenBurning", true);

        builder.pop();

        builder.comment("Where the explosion is centred, relative to the midpoint of the player's hitbox.")
                .push("placement");

        OFFSET_BEHIND = builder
                .comment("Blocks behind the player, along the direction they face.")
                .defineInRange("offsetBehind", 0.3, 0.0, 4.0);

        OFFSET_BELOW = builder
                .comment("Blocks below the midpoint of the player's hitbox.")
                .defineInRange("offsetBelow", 0.3, 0.0, 4.0);

        builder.pop();

        SPEC = builder.build();
    }

    private FartBombConfig() {}
}
