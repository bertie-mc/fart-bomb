package com.berlord.fartbomb;

/** Computes the explosion center without depending on Minecraft classes. */
public final class FartPlacement {

    private FartPlacement() {
    }

    public static Position behindPlayer(
            double x, double y, double z, double height, float yawDegrees,
            double offsetBehind, double offsetBelow) {
        double yaw = Math.toRadians(yawDegrees);
        return new Position(
                x + Math.sin(yaw) * offsetBehind,
                y + height / 2.0 - offsetBelow,
                z - Math.cos(yaw) * offsetBehind);
    }

    public record Position(double x, double y, double z) {
    }
}
