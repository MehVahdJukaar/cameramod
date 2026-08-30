package net.mehvahdjukaar.vista.common.view_finder;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.Position;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class SubLevelReferenceFrame extends WorldReferenceFrame {

    public SubLevelReferenceFrame(BlockEntity be) {
        super(be);
    }

    @Override
    public Vec3 position(float partialTicks) {
        Vec3 plotPos = super.position(partialTicks);
        Pose3dc pose = shipPose(partialTicks);
        return pose == null ? plotPos : pose.transformPosition(plotPos);
    }

    @Override
    public Quaternionf getRotation(float partialTicks) {
        Pose3dc pose = shipPose(partialTicks);
        return pose == null ? new Quaternionf() : new Quaternionf(pose.orientation());
    }

    @Override
    public Vec3 velocity() {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(be);
        if (subLevel == null) return Vec3.ZERO;
        return SableCompanion.INSTANCE.getVelocity(be.getLevel(), subLevel, (Position) super.position(1));
    }

    @Nullable
    private Pose3dc shipPose(float partialTicks) {
        if (be.getLevel().isClientSide()) {
            ClientSubLevelAccess subLevel = SableCompanion.INSTANCE.getContainingClient(be);
            return subLevel == null ? null : subLevel.renderPose(partialTicks);
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(be);
        if (subLevel == null) return null;
        return subLevel.lastPose().lerp(subLevel.logicalPose(), partialTicks, new Pose3d());
    }
}
