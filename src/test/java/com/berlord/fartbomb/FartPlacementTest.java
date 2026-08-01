package com.berlord.fartbomb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FartPlacementTest {

    @Test
    void placesTheExplosionBehindAndBelowThePlayerMidpoint() {
        FartPlacement.Position north = FartPlacement.behindPlayer(10, 20, 30, 1.8, 0, 0.3, 0.3);
        assertEquals(10.0, north.x(), 1.0e-9);
        assertEquals(20.6, north.y(), 1.0e-9);
        assertEquals(29.7, north.z(), 1.0e-9);

        FartPlacement.Position west = FartPlacement.behindPlayer(10, 20, 30, 1.8, 90, 0.3, 0.3);
        assertEquals(10.3, west.x(), 1.0e-9);
        assertEquals(20.6, west.y(), 1.0e-9);
        assertEquals(30.0, west.z(), 1.0e-9);
    }
}
