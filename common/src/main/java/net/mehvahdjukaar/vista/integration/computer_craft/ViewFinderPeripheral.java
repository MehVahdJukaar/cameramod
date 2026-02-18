package net.mehvahdjukaar.vista.integration.computer_craft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.mehvahdjukaar.ml_classes.TileOrEntityTarget;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderAccess;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ViewFinderPeripheral implements IPeripheral {
    private final ViewFinderBlockEntity tile;
    private final ViewFinderAccess acc;

    public ViewFinderPeripheral(ViewFinderBlockEntity tile) {
        this.tile = tile;
        this.acc = ViewFinderAccess.find(tile.getLevel(), TileOrEntityTarget.of(tile));
    }

    @LuaFunction
    public void setYaw(double value) {
        tile.setYaw(acc, (float) value);
        acc.updateClients();
    }

    @LuaFunction
    public float getYaw() {
        return tile.getYaw();
    }

    @LuaFunction
    public void setPitch(double value) {
        tile.setPitch(acc, (float) value);
        acc.updateClients();
    }

    @LuaFunction
    public float getPitch() {
        return tile.getPitch();
    }

    @LuaFunction
    public void setZoom(int zoom) {
        byte power = (byte) Math.min(Math.max(zoom, 1), ViewFinderBlockEntity.MAX_ZOOM);
        tile.setZoomLevel(power);
        acc.updateClients();
    }

    @LuaFunction
    public int getZoom() {
        return tile.getZoomLevel();
    }

    @Override
    public String getType() {
        return "view_finder";
    }

    @Override
    public boolean equals(@Nullable IPeripheral obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ViewFinderPeripheral) obj;
        return Objects.equals(this.tile, that.tile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tile);
    }

    @Override
    public String toString() {
        return "ViewFinderPeripheral[" +
                "tile=" + tile + ']';
    }

}
