package dev.smpb.findmyitems.model;

public record BlockPosition(int x, int y, int z) implements Comparable<BlockPosition> {
    @Override
    public int compareTo(BlockPosition other) {
        int byX = Integer.compare(x, other.x);
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(y, other.y);
        return byY != 0 ? byY : Integer.compare(z, other.z);
    }
}

