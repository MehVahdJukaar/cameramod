package net.mehvahdjukaar.vista.common.tv.connection;


import net.mehvahdjukaar.ml_classes.Rect2D;
import net.mehvahdjukaar.ml_classes.Vec2i;
import net.mehvahdjukaar.vista.common.tv.TVType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GridAccessor {
    @NotNull
    GridTile getAt(Vec2i pos);

    void setAt(Vec2i pos, @Nullable TVType type, boolean setPower);

    default void setAt(Vec2i pos, @Nullable TVType type) {
        this.setAt(pos, type, false);
    }

    default void transform(Rect2D from, Rect2D to, @Nullable Rect2D originBe) {
      //  if (from.equals(to)) return;

        int left = to.left();
        int bottom = to.y();
        int top = to.y() + to.height() - 1;
        int right = to.right();

        boolean hasStrongPowerAnywhere = false;
        var iter = to.iteratePoints();
        while (iter.hasNext()) {
            Vec2i p = iter.next();
            GridTile t = this.getAt(p);
            if (t.powerState().isStrong()) {
                hasStrongPowerAnywhere = true;
                break;
            }
        }
        this.planBeMove(originBe, to);

        for (int y = bottom; y <= top; y++) {
            for (int x = left; x <= right; x++) {
                boolean edgeUp = (y == top);
                boolean edgeDown = (y == bottom);
                boolean edgeLeft = (x == left);
                boolean edgeRight = (x == right);

                TVType conn = TVType.fromConnections(!edgeUp, !edgeDown, !edgeLeft, !edgeRight);
                // GridAccess is (left, top) → pass (col, row)
                this.setAt(new Vec2i(x, y), conn, hasStrongPowerAnywhere);
            }
        }

        for (var p : from.subtract(to)) {
            this.setAt(p, TVType.SINGLE, false);
        }
    }

    void planBeMove(@Nullable Rect2D from, Rect2D to);

}

