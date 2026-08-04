package dev.smpb.findmyitems.craft;

public record PlanningPolicy(boolean allowCraftingTable, boolean allowInventoryCrafting, int candidateCap) {
    public static final PlanningPolicy DEFAULT = new PlanningPolicy(true, true, 64);

    public PlanningPolicy {
        if (candidateCap <= 0) throw new IllegalArgumentException("candidateCap must be positive");
    }
}
