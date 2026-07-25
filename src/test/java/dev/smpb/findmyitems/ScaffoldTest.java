package dev.smpb.findmyitems;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ScaffoldTest {
    @Test
    void modIdentityIsStable() {
        assertEquals("findmyitems", FindMyItemsMod.MOD_ID);
    }
}
