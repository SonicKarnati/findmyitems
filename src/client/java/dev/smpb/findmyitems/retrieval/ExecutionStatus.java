package dev.smpb.findmyitems.retrieval;

import net.minecraft.network.chat.Component;

/** The small set of states exposed by the gather/craft action. */
public enum ExecutionStatus {
    CALCULATING("calculating"),
    GATHER("gather"),
    CRAFT("craft"),
    MISSING("missing"),
    NO_TABLE("no-table"),
    FULL("full"),
    BUSY("busy"),
    FAILED("failed"),
    COMPLETE("complete"),
    CANCELLED("cancelled");

    private final String statusKey;

    ExecutionStatus(String statusKey) {
        this.statusKey = statusKey;
    }

    public String statusKey() {
        return statusKey;
    }

    public Component component() {
        return Component.translatable("screen.findmyitems.craft." + statusKey);
    }
}
