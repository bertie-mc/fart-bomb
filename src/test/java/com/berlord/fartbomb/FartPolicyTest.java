package com.berlord.fartbomb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FartPolicyTest {

    @Test
    void recognizesOnlyTheArtifactsWhoopeeCushionSound() {
        assertTrue(FartPolicy.isFartSound("artifacts", "item.whoopee_cushion.fart"));
        assertFalse(FartPolicy.isFartSound("artifacts", "other"));
        assertFalse(FartPolicy.isFartSound("fartbomb", "item.whoopee_cushion.fart"));
    }

    @Test
    void requiresBothAnEnabledModAndIgnition() {
        assertTrue(FartPolicy.shouldDetonate(true, true));
        assertFalse(FartPolicy.shouldDetonate(true, false));
        assertFalse(FartPolicy.shouldDetonate(false, true));
    }

    @Test
    void suppressesTheOriginalOnlyAfterAReplacement() {
        assertTrue(FartPolicy.suppressOriginalSound(true, true));
        assertFalse(FartPolicy.suppressOriginalSound(true, false));
        assertFalse(FartPolicy.suppressOriginalSound(false, true));
    }
}
