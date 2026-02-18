package net.mehvahdjukaar.ml_classes;

import net.minecraft.world.level.block.state.BlockBehaviour;

public interface IOptionalEntityBlock {
    boolean shouldHaveBlockEntity(BlockBehaviour.BlockStateBase state);
}
