package dev.smpb.findmyitems.craft;

/** Lexicographic score for the dimensions computed by the current pure planner. */
public record PlanScore(long missingQuantity, long missingKinds, long craftOperations,
                        long reversibleConversions, long treeDepth)
        implements Comparable<PlanScore> {
    public PlanScore {
        if (missingQuantity < 0 || missingKinds < 0 || craftOperations < 0
                || reversibleConversions < 0 || treeDepth < 0) {
            throw new IllegalArgumentException("score fields must be non-negative");
        }
    }

    @Override
    public int compareTo(PlanScore other) {
        int result;
        if ((result = Long.compare(missingQuantity, other.missingQuantity)) != 0) return result;
        if ((result = Long.compare(missingKinds, other.missingKinds)) != 0) return result;
        if ((result = Long.compare(craftOperations, other.craftOperations)) != 0) return result;
        if ((result = Long.compare(reversibleConversions, other.reversibleConversions)) != 0) return result;
        return Long.compare(treeDepth, other.treeDepth);
    }
}
