package net.mehvahdjukaar.vista.common.connection;

import net.mehvahdjukaar.moonlight.api.util.math.Direction2D;
import net.mehvahdjukaar.moonlight.api.util.math.Rect2D;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;

import java.util.*;

public final class RectFinder {

    public static Rect2D findMaxRect(
            GridAccessor grid,
            Vec2i from,
            boolean squareOnly
    ) {
        return findMaxRects(grid, from, squareOnly)
                .stream()
                .max(Comparator.comparingInt(Rect2D::getArea))
                .orElseThrow();
    }

    private static Set<Rect2D> findMaxRects(
            GridAccessor grid,
            Vec2i from,
            boolean squareOnly
    ) {

        Rect2D start = new Rect2D(from.x(), from.y(), 1, 1);

        Set<Rect2D> visited = new HashSet<>();
        Set<Rect2D> maximalRects = new HashSet<>();
        Deque<Rect2D> stack = new ArrayDeque<>();

        stack.push(start);
        maximalRects.add(start);

        while (!stack.isEmpty()) {
            Rect2D r = stack.pop();
            if (!visited.add(r)) continue;

            for (Direction2D d : Direction2D.values()) {

                if (!couldBeConnectedToward(grid, r, d, d)) continue;
                Rect2D expandedRect = r.expandToward(d);

                if (couldBeConnectedToward(grid, expandedRect, d, d.getOpposite())) {
                    stack.push(expandedRect);
                }
            }

            // maximal = no further expansion possible
            if ((!squareOnly || r.isSquare())) {
                maximalRects.add(r);
            }
        }

        return maximalRects;
    }

    private static boolean couldBeConnectedToward(GridAccessor grid, Rect2D currentRect, Direction2D d, Direction2D d2) {
        var edgeLocs = currentRect.iterateEdge(d);

        while (edgeLocs.hasNext()) {
            Vec2i p = edgeLocs.next();
            ConnectionType t = grid.getAt(p).type();
            if (t == null) return false;

            if (t.hasEdge(d2)) {
                return false;
            }
        }
        return true;
    }

    public static RectSelection findMaxExpandedRect(GridAccessor grid, Vec2i from, int maxSize, boolean squareOnly) {
        return findMaxExpandedRects(grid, from, maxSize, squareOnly)
                .stream()
                .max(Comparator.comparingInt(r->r.selection().getArea()))
                .orElse(RectSelection.SINGLE);
    }

    private static Set<RectSelection> findMaxExpandedRects(
            GridAccessor grid,
            Vec2i from,
            int maxSize,
            boolean squareOnly
    ) {
        Rect2D start = new Rect2D(from.x(), from.y(), 1, 1);

        Set<RectSelection> visited = new HashSet<>();
        Set<RectSelection> maximalRects = new HashSet<>();
        Deque<RectSelection> stack = new ArrayDeque<>();

        stack.push(new RectSelection(start, null));

        while (!stack.isEmpty()) {
            RectSelection s = stack.pop();
            if (!visited.add(s)) continue;

            for (Direction2D d : Direction2D.values()) {
                for (RectSelection next : expand(grid, s, d)) {
                    stack.push(next);
                }
            }

            //validate solution
            if ((s.selection().width() <= maxSize && s.selection().height() <= maxSize) &&
                    (!squareOnly || s.selection().isSquare())
                    && s.selection().contains(s.touchedRect())) {

                maximalRects.add(s);
            }
        }

        return maximalRects;
    }

    private static List<RectSelection> expand(
            GridAccessor grid,
            RectSelection state,
            Direction2D d
    ) {
        Rect2D nextRect = state.selection().expandToward(d);
        Rect2D touched = state.touchedRect();

        var edge = nextRect.iterateEdge(d);

        while (edge.hasNext()) {
            Vec2i p = edge.next();
            GridTile at = grid.getAt(p);
            ConnectionType t = at.type();

            if (t == null) {
                return List.of(); // cell is empty or foreign, can't expand into it
            }

            if (!t.isSingle() || at.hasBe()) {
                // Cell belongs to an existing connected group (non-SINGLE) or is a 1x1
                // group of its own (SINGLE+BE). Accumulate every absorbed owner into
                // `touched`, since the final `selection.contains(touched)` check at the
                // validation step decides whether the selection has grown large enough
                // to fully cover all absorbed groups. The old code bailed here on the
                // second distinct owner, which blocked the 4-mirror 2x2 case (three
                // separate SINGLE+BE neighbors collapsing into one group) while keeping
                // the existing single-owner cases working.
                Rect2D owner = findMaxRect(grid, p, false);
                touched = touched == null ? owner : touched.containing(owner);
            }
        }

        return List.of(new RectSelection(nextRect, touched));
    }

}
