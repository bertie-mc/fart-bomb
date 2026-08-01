package com.berlord.fartbomb;

/** Dependency-free decisions made when a sound is intercepted. */
public final class FartPolicy {
    private static final String ARTIFACTS_NAMESPACE = "artifacts";
    private static final String FART_PATH = "item.whoopee_cushion.fart";

    private FartPolicy() {
    }

    public static boolean isFartSound(String namespace, String path) {
        return ARTIFACTS_NAMESPACE.equals(namespace) && FART_PATH.equals(path);
    }

    public static boolean shouldDetonate(boolean enabled, boolean ignited) {
        return enabled && ignited;
    }

    public static boolean suppressOriginalSound(boolean detonated, boolean replaceSound) {
        return detonated && replaceSound;
    }
}
