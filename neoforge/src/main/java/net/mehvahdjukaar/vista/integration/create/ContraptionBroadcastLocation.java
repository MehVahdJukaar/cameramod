package net.mehvahdjukaar.vista.integration.create;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastLocationType;
import net.mehvahdjukaar.vista.common.broadcast.IBroadcastLocation;
import net.mehvahdjukaar.vista.common.broadcast.TriResult;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.mehvahdjukaar.vista.common.view_finder.ReferenceFrame;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Points a broadcast feed at a view finder riding inside a Create contraption.
// Client side get() returns the render-level block entity and installs the moving reference frame so the
// feed tracks the structure. Server side there is no live block entity, so the link exists only for chunk
// tracking + sync. Contraption access goes through CreateCompat to keep this file Create-free.
public record ContraptionBroadcastLocation(ResourceKey<Level> dimension, UUID contraptionId, BlockPos localPos)
        implements IBroadcastLocation {

    private static final Codec<ResourceKey<Level>> DIMENSION_CODEC =
            ResourceLocation.CODEC.xmap(rl -> ResourceKey.create(Registries.DIMENSION, rl), ResourceKey::location);
    private static final StreamCodec<ByteBuf, ResourceKey<Level>> DIMENSION_STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(rl -> ResourceKey.create(Registries.DIMENSION, rl), ResourceKey::location);

    public static final BroadcastLocationType TYPE = new BroadcastLocationType(
            RecordCodecBuilder.<ContraptionBroadcastLocation>mapCodec(i -> i.group(
                    DIMENSION_CODEC.fieldOf("dimension").forGetter(ContraptionBroadcastLocation::dimension),
                    UUIDUtil.CODEC.fieldOf("contraption").forGetter(ContraptionBroadcastLocation::contraptionId),
                    BlockPos.CODEC.fieldOf("local_pos").forGetter(ContraptionBroadcastLocation::localPos)
            ).apply(i, ContraptionBroadcastLocation::new)),
            StreamCodec.composite(
                    DIMENSION_STREAM_CODEC, ContraptionBroadcastLocation::dimension,
                    UUIDUtil.STREAM_CODEC, ContraptionBroadcastLocation::contraptionId,
                    BlockPos.STREAM_CODEC, ContraptionBroadcastLocation::localPos,
                    ContraptionBroadcastLocation::new)
    );

    @Override
    public TriResult<IBroadcastSource> get(boolean isClient) {
        Level level = isClient
                ? VistaModClient.getLocalLevelByDimension(dimension)
                : serverLevel();
        if (level == null) return TriResult.empty();

        Entity contraption = CreateCompat.findContraption(level, contraptionId);
        if (contraption == null) return TriResult.empty();

        if (!isClient) {
            // server keeps blocks as NBT with no live view finder BE; nothing to hand back
            return TriResult.empty();
        }

        BlockEntity be = CreateCompat.getClientBlockEntity(contraption, localPos);
        if (be instanceof ViewFinderBlockEntity vf) {
            ensureFrame(vf, contraption);
            return TriResult.valid(vf);
        }
        return TriResult.empty();
    }

    @Override
    public BroadcastLocationType type() {
        return TYPE;
    }

    @Override
    public MutableComponent getTooltipComponent(Level level) {
        return Component.translatable("tooltip.vista.hollow_cassette.linked_contraption");
    }

    @Override
    public @Nullable GlobalPos getChunkSendPosition() {
        ServerLevel level = serverLevel();
        if (level == null) return null;
        Entity contraption = CreateCompat.findContraption(level, contraptionId);
        if (contraption == null) return null;
        return GlobalPos.of(dimension, contraption.blockPosition());
    }

    // keep the view finder bound to the contraption so its global position/orientation track the movement
    private void ensureFrame(ViewFinderBlockEntity vf, Entity contraption) {
        ReferenceFrame current = vf.getReferenceFrame();
        if (!(current instanceof ContraptionReferenceFrame(Entity contraption1, BlockPos pos))
                || contraption1 != contraption
                || !pos.equals(localPos)) {
            vf.setReferenceFrame(new ContraptionReferenceFrame(contraption, localPos));
        }
    }

    @Nullable
    private ServerLevel serverLevel() {
        MinecraftServer server = PlatHelper.getCurrentServer();
        return server == null ? null : server.getLevel(dimension);
    }
}
