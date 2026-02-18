package net.mehvahdjukaar.vista.common.tv.connection;


import net.mehvahdjukaar.ml_classes.Rect2D;

public record RectSelection(Rect2D selection, Rect2D touchedRect) {
    public static final RectSelection SINGLE = new RectSelection(new Rect2D(0, 0, 1, 1), null);

    public static RectSelection shrinking(Rect2D newRec) {
        return new RectSelection(newRec, newRec);
    }

}