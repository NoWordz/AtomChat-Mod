package com.atom.chat.ui;

import java.util.ArrayList;
import java.util.List;

/** Last painted item geometry, in page coordinates before the navigation transform. */
public final class StaggerHitMap {
    public record Point(float x, float y) { }
    private record Item(UiLayout.Rect rect, float dx, float dy, float scale, float cx, float cy) {
        Point inverse(float x, float y) {
            return new Point(cx + (x - cx - dx) / scale, cy + (y - cy - dy) / scale);
        }
    }
    private final List<Item> items = new ArrayList<>();
    public void clear() { items.clear(); }
    public void add(UiLayout.Rect rect, float dx, float dy, float scale, float cx, float cy) {
        items.add(new Item(rect, dx, dy, scale, cx, cy));
    }
    public void add(UiLayout.Rect rect, float dx, float dy, float scale) {
        add(rect, dx, dy, scale, rect.x() + rect.w() / 2, rect.y() + rect.h() / 2);
    }
    public UiLayout.Rect paintedBounds(UiLayout.Rect rect) {
        for(int i=items.size()-1;i>=0;i--) {
            Item item=items.get(i);
            if(item.rect.equals(rect))return new UiLayout.Rect(item.cx+(rect.x()-item.cx)*item.scale+item.dx,
                item.cy+(rect.y()-item.cy)*item.scale+item.dy,rect.w()*item.scale,rect.h()*item.scale);
        }
        return rect;
    }
    public Point inverse(float x, float y) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item item = items.get(i);
            Point point = item.inverse(x, y);
            if (item.rect.contains(point.x, point.y)) return point;
        }
        // A vacated logical slot cannot answer the pointer beside its moving card.
        for (Item item : items) if (item.rect.contains(x, y)) return new Point(-1_000_000, -1_000_000);
        return new Point(x, y);
    }
}
