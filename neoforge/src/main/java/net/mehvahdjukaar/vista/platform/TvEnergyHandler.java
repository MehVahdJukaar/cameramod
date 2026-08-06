package net.mehvahdjukaar.vista.platform;

import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.common.cassette.ITvCassette;
import net.mehvahdjukaar.vista.common.tv.TVBlock;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class TvEnergyHandler implements IEnergyStorage {

    private static final int CAPACITY = 1600;

    private final TVBlockEntity tv;
    private int stored = 0;

    public static TvEnergyHandler getOrCreate(TVBlockEntity tv) {
        if (tv.energyCap == null){
            tv.energyCap = new TvEnergyHandler(tv);
        }
        return (TvEnergyHandler) tv.energyCap;
    }

    public TvEnergyHandler(TVBlockEntity tv) {
        this.tv = tv;
    }

    public void tick() {
        Vec2i v = tv.getConnectedCount();
        int cost = CommonConfigs.TV_ENERGY_CONSUMPTION_RATE.get() * v.x() * v.y();
        // draining below one tick's worth counts as unpowered, so a tv fed exactly at its
        // consumption rate stays on instead of flickering between the two states
        boolean canRun = stored >= cost;

        boolean hasCassette = !tv.getDisplayedItem().isEmpty() &&
                tv.getDisplayedItem().getItem() instanceof ITvCassette;
        boolean isPowered = tv.getBlockState().getValue(TVBlock.POWER_STATE).isOn();
        if (canRun && hasCassette && isPowered) stored -= cost;

        tv.setHasEnergy(canRun);
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int accepted = Math.min(maxReceive, CAPACITY - stored);
        if (!simulate) stored += accepted;
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return stored;
    }

    @Override
    public int getMaxEnergyStored() {
        return CAPACITY;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return stored < CAPACITY;
    }
}
