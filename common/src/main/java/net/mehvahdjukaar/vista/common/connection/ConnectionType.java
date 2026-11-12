package net.mehvahdjukaar.vista.common.connection;

import net.mehvahdjukaar.moonlight.api.util.math.Direction2D;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Locale;

public enum ConnectionType implements StringRepresentable {
    SINGLE(0),
    CENTER(C.U | C.D | C.L | C.R),
    TOP(C.D | C.L | C.R),
    BOTTOM(C.U | C.L | C.R),
    LEFT(C.U | C.D | C.R),
    RIGHT(C.U | C.D | C.L),
    TOP_LEFT(C.D | C.R),
    TOP_RIGHT(C.D | C.L),
    BOTTOM_LEFT(C.U | C.R),
    BOTTOM_RIGHT(C.U | C.L),

    H_LEFT(C.R),         // left end of a horizontal strip
    H_RIGHT(C.L),        // right end of a horizontal strip
    H_MIDDLE(C.L | C.R), // interior cell of a horizontal strip
    V_BOTTOM(C.U),       // bottom end of a vertical strip
    V_TOP(C.D),          // top end of a vertical strip
    V_MIDDLE(C.U | C.D); // interior cell of a vertical strip

    private final int mask;

    ConnectionType(int mask) {
        this.mask = mask;
        C.LUT[mask] = this;
    }

    private static class C {
        private static final int U = 1, D = 2, L = 4, R = 8;
        private static final ConnectionType[] LUT = new ConnectionType[16];
    }

    public static ConnectionType fromConnections(boolean up, boolean down, boolean left, boolean right) {
        int mask = (up ? C.U : 0) | (down ? C.D : 0) | (left ? C.L : 0) | (right ? C.R : 0);
        ConnectionType c = C.LUT[mask];
        return c != null ? c : SINGLE;
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean hasEdge(Direction2D dir) {
        return (switch (dir) {
            case UP -> C.U;
            case DOWN -> C.D;
            case LEFT -> C.L;
            case RIGHT -> C.R;
        } & mask) == 0;
    }

    public boolean isSingle() {
        return this == SINGLE;
    }

    public boolean isConnected(Direction2D dir) {
        return !hasEdge(dir);
    }

    public boolean isConnected(Direction worldSide, Direction blockFacing) {
        Direction2D dir = Direction2D.from3D(worldSide, negativeHorizontalRot(blockFacing.getOpposite()));
        return isConnected(dir);
    }

    private static Rotation negativeHorizontalRot(Direction dir) {
        return switch (dir) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static final EnumProperty<ConnectionType> STATE_PROPERTY = EnumProperty .create("connection", ConnectionType.class);
}
