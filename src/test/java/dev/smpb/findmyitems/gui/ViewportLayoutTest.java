package dev.smpb.findmyitems.gui;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

final class ViewportLayoutTest {
    @Test
    void topMiddleAndBottomRowsUseOneClippedViewport() {
        var top = ViewportLayout.layout(10, 110, 20, 10, 0, 1);
        var middle = ViewportLayout.layout(10, 110, 20, 10, 70, 1);
        var bottom = ViewportLayout.layout(10, 110, 20, 10, 200, 1);

        assertEquals(0, top.firstVisibleRow());
        assertEquals(4, top.lastVisibleRow());
        assertEquals(100, top.scrollMaximum());
        assertEquals(3, middle.firstVisibleRow());
        assertEquals(8, middle.lastVisibleRow());
        assertEquals(5, bottom.firstVisibleRow());
        assertEquals(9, bottom.lastVisibleRow());
        assertEquals(100, bottom.scroll());
        assertTrue(bottom.rowRect(9).bottom() <= 110);
    }

    @Test
    void overscanRowsRenderButDoNotChangeHitTestBounds() {
        var layout = ViewportLayout.layout(10, 70, 20, 10, 20, 1);

        assertEquals(1, layout.firstVisibleRow());
        assertEquals(3, layout.lastVisibleRow());
        assertEquals(OptionalInt.of(1), layout.hitTest(5, 25));
        assertEquals(OptionalInt.empty(), layout.hitTest(5, 9));
        assertEquals(OptionalInt.empty(), layout.hitTest(5, 70));
    }

    @Test
    void emptyAndInvalidDimensionsProduceEmptyLayout() {
        var layout = ViewportLayout.layout(10, 10, 20, 3, 5, 2);

        assertEquals(0, layout.firstVisibleRow());
        assertEquals(-1, layout.lastVisibleRow());
        assertEquals(0, layout.scrollMaximum());
        assertTrue(layout.rows().isEmpty());
    }
}
