package dev.smpb.findmyitems.retrieval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CraftingExecutorContractTest {
    @Test
    void exposesTheUserVisibleExecutionStates() {
        assertEquals("calculating", ExecutionStatus.CALCULATING.statusKey());
        assertEquals("gather", ExecutionStatus.GATHER.statusKey());
        assertEquals("craft", ExecutionStatus.CRAFT.statusKey());
        assertEquals("missing", ExecutionStatus.MISSING.statusKey());
        assertEquals("no-table", ExecutionStatus.NO_TABLE.statusKey());
        assertEquals("full", ExecutionStatus.FULL.statusKey());
        assertEquals("busy", ExecutionStatus.BUSY.statusKey());
        assertEquals("failed", ExecutionStatus.FAILED.statusKey());
        assertEquals("complete", ExecutionStatus.COMPLETE.statusKey());
    }
}
