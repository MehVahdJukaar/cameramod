package net.mehvahdjukaar.vista.integration.create;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastLocationType;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.Supplier;

public class CreateCompat {

    // registered unconditionally (like VistaMod's other registry entries) so the type/packet exist regardless
    // of whether Create is actually installed; only CreateCompat#setup is gated behind CompatHandler.CREATE.
    public static final Supplier<BroadcastLocationType> CONTRAPTION_BROADCAST =
            RegHelper.register(VistaMod.res("contraption_location"),
                    () -> ContraptionBroadcastLocation.TYPE, VistaMod.BROADCAST_LOCATION_REGISTRY.key());

    public static void init() {
        NetworkHelper.addNetworkRegistration(
                event -> event.registerBidirectional(SyncContraptionViewFinderPacket.CODEC), 2);
    }

    public static void setup() {
        registerViewFinderBehaviours(VistaMod.VIEWFINDER.get());
    }

    // called from the contraption interaction behaviour; returns whether the interaction was handled
    public static boolean onContraptionInteractClient(@Nullable BlockEntity be, Entity contraption, BlockPos localPos) {
        if (be instanceof ViewFinderBlockEntity vf) {
            CreateClientCompat.startControlling(vf, contraption, localPos);
            return true;
        }
        return false;
    }

    // called from the movement behaviour every tick while the contraption moves
    public static void linkContraptionFeed(Level world, Entity contraption, BlockPos localPos,
                                           @Nullable CompoundTag blockEntityData) {
        if (world.isClientSide()) return;
        if (blockEntityData == null || !blockEntityData.hasUUID("UUID")) return;
        UUID feedId = blockEntityData.getUUID("UUID");
        // linkFeed is a no-op when unchanged, so calling it every tick is cheap
        BroadcastManager.getInstance(world).linkFeed(feedId,
                new ContraptionBroadcastLocation(world.dimension(), contraption.getUUID(), localPos));
    }

    // called from the movement behaviour when the contraption stops moving
    public static void unlinkContraptionFeed(Level world, Entity contraption, BlockPos localPos) {
        if (world.isClientSide()) return;
        // remove by value: if the block already re-registered a world location on disassembly, this is a no-op
        BroadcastManager.getInstance(world).unlinkFeed(
                new ContraptionBroadcastLocation(world.dimension(), contraption.getUUID(), localPos));
    }

    public static void registerViewFinderBehaviours(Block viewFinder) {
        MovingInteractionBehaviour.REGISTRY.register(viewFinder, new MovingInteractionBehaviour() {
            @Override
            public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
                                                   AbstractContraptionEntity contraptionEntity) {
                // Create fires this on both sides; camera control is entered on the client only
                if (contraptionEntity.level().isClientSide) {
                    BlockEntity be = contraptionEntity.getContraption().getBlockEntityClientSide(localPos);
                    return onContraptionInteractClient(be, contraptionEntity, localPos);
                }
                return true;
            }
        });
        MovementBehaviour.REGISTRY.register(viewFinder, new MovementBehaviour() {
            @Override
            public void startMoving(MovementContext ctx) {
                link(ctx);
            }

            @Override
            public void tick(MovementContext ctx) {
                // idempotent: also covers contraptions loaded from disk mid-journey
                link(ctx);
            }

            @Override
            public void stopMoving(MovementContext ctx) {
                AbstractContraptionEntity e = ctx.contraption.entity;
                if (e != null) unlinkContraptionFeed(ctx.world, e, ctx.localPos);
            }

            private void link(MovementContext ctx) {
                AbstractContraptionEntity e = ctx.contraption.entity;
                if (e != null) linkContraptionFeed(ctx.world, e, ctx.localPos, ctx.blockEntityData);
            }
        });
    }

    public static Vec3 contraptionPosToGlobalPos(Entity contraption, Vec3 localVec, float partialTicks) {
        return ((AbstractContraptionEntity) contraption).toGlobalVector(localVec, partialTicks);
    }

    public static Quaternionf getContraptionRotation(Entity contraption, float partialTicks) {
        AbstractContraptionEntity c = (AbstractContraptionEntity) contraption;
        // derive the contraption's world rotation by transforming the three basis vectors.
        // convention-agnostic: works for any contraption type without matching Create's euler order.
        Vec3 x = c.applyRotation(new Vec3(1, 0, 0), partialTicks);
        Vec3 y = c.applyRotation(new Vec3(0, 1, 0), partialTicks);
        Vec3 z = c.applyRotation(new Vec3(0, 0, 1), partialTicks);
        Matrix3f rot = new Matrix3f(
                new Vector3f((float) x.x, (float) x.y, (float) x.z),
                new Vector3f((float) y.x, (float) y.y, (float) y.z),
                new Vector3f((float) z.x, (float) z.y, (float) z.z));
        return new Quaternionf().setFromNormalized(rot);
    }

    public static Vec3 getContactPointMotion(Entity contraption, Vec3 worldPoint) {
        return ((AbstractContraptionEntity) contraption).getContactPointMotion(worldPoint);
    }

    @Nullable
    public static BlockEntity getClientBlockEntity(Entity contraption, BlockPos localPos) {
        return ((AbstractContraptionEntity) contraption).getContraption().getBlockEntityClientSide(localPos);
    }

    @Nullable
    public static Entity findContraption(Level level, UUID contraptionId) {
        if (level instanceof ServerLevel sl) {
            return sl.getEntity(contraptionId) instanceof AbstractContraptionEntity ce && !ce.isRemoved() ? ce : null;
        }
        if (level instanceof ClientLevel cl) {
            for (Entity e : cl.entitiesForRendering()) {
                if (e instanceof AbstractContraptionEntity ce && !ce.isRemoved() && ce.getUUID().equals(contraptionId)) {
                    return ce;
                }
            }
        }
        return null;
    }

    // bakes the aim into the contraption's stored block NBT so it outlives the live render
    public static void persistViewFinderAim(Entity contraption, BlockPos localPos, Quaternionf localRot,
                                            int zoom, boolean locked) {
        Contraption c = ((AbstractContraptionEntity) contraption).getContraption();
        StructureBlockInfo info = c.getBlocks().get(localPos);
        if (info == null) return;
        CompoundTag nbt = info.nbt() != null ? info.nbt().copy() : new CompoundTag();
        // common owns the NBT format; the stored block gets rebuilt into a render BE via its normal loadAdditional
        ViewFinderBlockEntity.buildAimNbt(nbt, info.state(), localRot, zoom, locked);
        c.getBlocks().put(localPos, new StructureBlockInfo(info.pos(), info.state(), nbt));
    }
}
