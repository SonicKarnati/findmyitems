package dev.smpb.findmyitems.gui;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/** Pure geometry for a scrollable row list. */
public final class ViewportLayout {
    private ViewportLayout() {
    }

    public static Layout layout(int top, int bottom, int rowHeight, int rowCount, double scroll, int overscan) {
        var height = Math.max(0, bottom - top);
        if (rowHeight <= 0 || rowCount <= 0 || height == 0) {
            return new Layout(top, bottom, rowHeight, 0, 0, 0, -1, List.of());
        }
        var maximum = Math.max(0, rowCount * (long) rowHeight - height);
        var clamped = Math.max(0, Math.min(maximum, scroll));
        var firstVisible = Math.max(0, (int) Math.floor(clamped / rowHeight));
        var lastVisible = Math.min(rowCount - 1, (int) Math.ceil((clamped + height) / rowHeight) - 1);
        var first = Math.max(0, firstVisible - Math.max(0, overscan));
        var last = Math.min(rowCount - 1, lastVisible + Math.max(0, overscan));
        var rows = IntStream.rangeClosed(first, last).mapToObj(index -> new Row(index,
                top + index * rowHeight - clamped, rowHeight)).toList();
        return new Layout(top, bottom, rowHeight, rowCount, clamped, firstVisible, lastVisible, rows);
    }

    public record Row(int index, double top, double height) {
        public double bottom() {
            return top + height;
        }
    }

    public record Layout(int top, int bottom, int rowHeight, int rowCount, double scroll,
                         int firstVisibleRow, int lastVisibleRow, List<Row> rows) {
        public Layout {
            rows = List.copyOf(rows);
        }

        public double scrollMaximum() {
            return Math.max(0, rowCount * (long) rowHeight - (bottom - top));
        }

        public Row rowRect(int index) {
            return rows.stream().filter(row -> row.index() == index).findFirst()
                    .orElseThrow(() -> new IndexOutOfBoundsException(index));
        }

        public OptionalInt hitTest(double x, double y) {
            if (y < top || y >= bottom) return OptionalInt.empty();
            var index = (int) Math.floor((y - top + scroll) / rowHeight);
            return index >= 0 && index < rowCount ? OptionalInt.of(index) : OptionalInt.empty();
        }
    }
}
