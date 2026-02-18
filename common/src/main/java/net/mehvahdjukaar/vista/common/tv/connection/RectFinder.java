package net.mehvahdjukaar.vista.common.tv.connection;

import net.mehvahdjukaar.ml_classes.Direction2D;
import net.mehvahdjukaar.ml_classes.Rect2D;
import net.mehvahdjukaar.ml_classes.Vec2i;
import net.mehvahdjukaar.vista.common.tv.TVType;

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
            TVType t = grid.getAt(p).type();
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
        List<RectSelection> results = new ArrayList<>();

        Rect2D touched = state.touchedRect();

        var edge = nextRect.iterateEdge(d);

        while (edge.hasNext()) {
            Vec2i p = edge.next();
            GridTile at = grid.getAt(p);
            TVType t = at.type();

            if (t == null) {
                return List.of(); // no expansion possible
            }

            if (t != TVType.SINGLE || at.hasBe()) {

                // If we already chose a selection, it must match
                if (touched != null) {
                    if (!touched.contains(p)) {
                        return List.of(); // cannot expand this way
                    }
                } else {
                    //bug here, wont work if it has multiple owners
                    touched = findMaxRect(grid, p, false);
                }
            }
        }

        // No new selection touched OR it matches existing
        results.add(new RectSelection(nextRect, touched));
        return results;
    }

}
