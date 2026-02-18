package net.mehvahdjukaar.vista.common.tv;


import net.mehvahdjukaar.ml_classes.Direction2D;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Rotation;

import java.util.Locale;

public enum TVType implements StringRepresentable {
    SINGLE(0),
    CENTER(C.U | C.D | C.L | C.R),
    TOP(C.D | C.L | C.R),
    BOTTOM(C.U | C.L | C.R),
    LEFT(C.U | C.D | C.R),
    RIGHT(C.U | C.D | C.L),
    TOP_LEFT(C.D | C.R),
    TOP_RIGHT(C.D | C.L),
    BOTTOM_LEFT(C.U | C.R),
    BOTTOM_RIGHT(C.U | C.L);

    private final int mask;

    TVType(int mask) {
        this.mask = mask;
        C.LUT[mask] = this;
    }

    //just due to class loading
    private static class C {
        private static final int U = 1, D = 2, L = 4, R = 8;
        private static final TVType[] LUT = new TVType[16];
    }

    public static TVType fromConnections(boolean up, boolean down, boolean left, boolean right) {
        int mask = (up ? C.U : 0) | (down ? C.D : 0) | (left ? C.L : 0) | (right ? C.R : 0);
        TVType c = C.LUT[mask];
        if (c != null) return c;
        return SINGLE;
       // throw new IllegalArgumentException("Invalid pattern for square tiling (mask=" + mask + ")");
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public boolean isConnected(TVType other) {
        if (other == null) return false;
        return (mask & other.mask) != 0;
    }

    public boolean isConnected(Direction worldSide, Direction tvFacing){
        Direction flippedFacing = tvFacing.getOpposite(); //since our states are from the player view not our view
        Direction2D dir = Direction2D.from3D(worldSide, negativeHorizontalRot(flippedFacing));
        return isConnected(dir);
    }

    //this is a bug in Direction2d. it should take the facing rotation, not the rotation to go to facing
    private static Rotation negativeHorizontalRot(Direction dir){
        return switch (dir) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.COUNTERCLOCKWISE_90; //-90
            case SOUTH -> Rotation.CLOCKWISE_180; //-180
            case WEST -> Rotation.CLOCKWISE_90; //90
            default -> Rotation.NONE;
        };
    }

    public boolean isConnected(Direction2D dir){
        return !hasEdge(dir);
    }

    public boolean hasEdge(Direction2D dir){
        return (switch (dir) {
            case UP -> C.U;
            case DOWN -> C.D;
            case LEFT -> C.L;
            case RIGHT -> C.R;
        } & mask) == 0;
    }

}

