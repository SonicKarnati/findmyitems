package dev.smpb.findmyitems.craft;

/** Lower is better; fields are deliberately ordered to make the policy auditable. */
public record PlanScore(long missingQuantity, long missingKinds, long unreachableStock,
                        long sourceOpenings, long transfers, long craftOperations,
                        long stationChanges, long reversibleConversions, long treeDepth)
        implements Comparable<PlanScore> {
    public PlanScore {
        if (missingQuantity < 0 || missingKinds < 0 || unreachableStock < 0 || sourceOpenings < 0
                || transfers < 0 || craftOperations < 0 || stationChanges < 0
                || reversibleConversions < 0 || treeDepth < 0) {
            throw new IllegalArgumentException("score fields must be non-negative");
        }
    }

    public PlanScore(long missingQuantity, long missingKinds, long unreachableStock,
                     long sourceOpenings, long transfers, long craftOperations,
                     long stationChanges, long reversibleConversions) {
        this(missingQuantity, missingKinds, unreachableStock, sourceOpenings, transfers,
                craftOperations, stationChanges, reversibleConversions, 0);
    }

    @Override
    public int compareTo(PlanScore other) {
        int result;
        if ((result = Long.compare(missingQuantity, other.missingQuantity)) != 0) return result;
        if ((result = Long.compare(missingKinds, other.missingKinds)) != 0) return result;
        if ((result = Long.compare(unreachableStock, other.unreachableStock)) != 0) return result;
        if ((result = Long.compare(sourceOpenings, other.sourceOpenings)) != 0) return result;
        if ((result = Long.compare(transfers, other.transfers)) != 0) return result;
        if ((result = Long.compare(craftOperations, other.craftOperations)) != 0) return result;
        if ((result = Long.compare(stationChanges, other.stationChanges)) != 0) return result;
        if ((result = Long.compare(reversibleConversions, other.reversibleConversions)) != 0) return result;
        return Long.compare(treeDepth, other.treeDepth);
    }
}
