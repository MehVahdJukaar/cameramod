package net.mehvahdjukaar.vista.mixins;

import com.mrcrayfish.furniture.refurbished.electricity.Connection;
import com.mrcrayfish.furniture.refurbished.electricity.IModuleNode;
import net.mehvahdjukaar.moonlight.api.misc.OptionalMixin;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

// Turns TVs into Refurbished Furniture electricity modules, wirable with the wrench.
// RF finds nodes purely through getBlockEntity(pos) instanceof IElectricityNode, so implementing the
// interface is all that's needed: its own BlockEntity mixin registers us with the electricity ticker,
// and its LevelChunk mixin drops our links when the tv breaks.
@OptionalMixin(value = "com.mrcrayfish.furniture.refurbished.electricity.IModuleNode")
@Mixin(TVBlockEntity.class)
public abstract class CompatRefurbishedFurnitureSelfTvBlockEntityMixin implements IModuleNode {

    @Unique
    private final Set<Connection> vista$connections = new HashSet<>();
    @Unique
    private final Set<BlockPos> vista$powerSources = new HashSet<>();
    @Unique
    private boolean vista$receivingPower;

    @Unique
    private TVBlockEntity vista$self() {
        return (TVBlockEntity) (Object) this;
    }

    @Override
    public BlockPos getNodePosition() {
        return this.vista$self().getBlockPos();
    }

    @Override
    public Level getNodeLevel() {
        return this.vista$self().getLevel();
    }

    @Override
    public BlockEntity getNodeOwner() {
        return this.vista$self();
    }

    @Override
    public Set<Connection> getNodeConnections() {
        return this.vista$connections;
    }

    @Override
    public Set<BlockPos> getPowerSources() {
        return this.vista$powerSources;
    }

    @Override
    public void setNodeReceivingPower(boolean state) {
        this.vista$receivingPower = state;
    }

    @Override
    public boolean isNodeReceivingPower() {
        return this.vista$receivingPower;
    }

    @Override
    public boolean isNodePowered() {
        return this.vista$self().isHasEnergy();
    }

    @Override
    public void setNodePowered(boolean powered) {
        this.vista$self().setHasEnergy(powered);
    }

    // With the integration off the tv is still technically a node (the interface can't be removed at
    // runtime), so make it uninteresting to the rest of the system: no links can be made to it and
    // the "no power" hud indicator stays hidden.
    @Override
    public int getNodeMaximumConnections() {
        return CommonConfigs.isTvElectricityEnabled() ? IModuleNode.super.getNodeMaximumConnections() : 0;
    }

    @Override
    public boolean isNodeInPowerableNetwork() {
        return !CommonConfigs.isTvElectricityEnabled() || IModuleNode.super.isNodeInPowerableNetwork();
    }

    @Override
    public void updateNodePoweredState() {
        if (CommonConfigs.isTvElectricityEnabled()) {
            IModuleNode.super.updateNodePoweredState();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void vista$saveNode(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.writeNodeNbt(tag);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void vista$loadNode(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.readNodeNbt(tag);
    }
}
