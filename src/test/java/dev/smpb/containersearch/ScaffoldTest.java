package dev.smpb.containersearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ScaffoldTest {
    @Test
    void modIdentityIsStable() {
        assertEquals("container-search", ContainerSearchMod.MOD_ID);
    }
}
