package net.mehvahdjukaar.vista.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.common.chunk_tracking.ILevelRendererExt;
import net.mehvahdjukaar.vista.common.chunk_tracking.IViewAreaExt;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.supernatural.SupernaturalCompat;
import net.mehvahdjukaar.vista.integration.vampirism.VampirismCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin implements ILevelRendererExt {

    @Shadow
    public SectionOcclusionGraph sectionOcclusionGraph;

    @Shadow @Nullable
    public ViewArea viewArea;

    // Rebuilds just the pinned section slots, keeping compiled chunk geometry. Stands in for
    // allChanged() on zone data changes, which would be far heavier.
    @Unique
    @Override
    public void vista$refreshPinnedSections() {
        if (viewArea instanceof IViewAreaExt va) {
            va.vista$rebuildPinnedSections();
        }
        if (sectionOcclusionGraph != null) {
            sectionOcclusionGraph.invalidate();
        }
        VistaLevelRenderer.invalidateManagedGraphs();
    }

    @ModifyReturnValue(method = "shouldShowEntityOutlines", at = @At(value = "RETURN"))
    public boolean vista$disableEntityOutlines(boolean original) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) {
            return false;
        }
        return original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"),
            require = 1)
    public boolean vista$isCameraDetached(boolean original) {
        if (VistaLevelRenderer.isRenderingLiveFeed()) {
            return true;
        }
        return original;
    }

    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getEntity()Lnet/minecraft/world/entity/Entity;",
            ordinal = 3), require = 1)
    public Entity vista$getActualPlayer(Entity original, @Local(ordinal = 0) Entity entity) {
        if (VistaLevelRenderer.isRenderingLiveFeed() && entity instanceof LocalPlayer) {
            return entity;
        }
        return original;
    }

    // Keeps vampires and friends off reflective and recorded surfaces. Main view is never affected.
    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void vista$hideMirrorInvisibleEntities(Entity entity, double camX, double camY, double camZ,
                                                   float partialTick, PoseStack poseStack,
                                                   MultiBufferSource bufferSource, CallbackInfo ci) {
        boolean mirror = VistaLevelRenderer.isRenderingMirrorReflection();
        boolean tv = VistaLevelRenderer.isRenderingCameraFeed();
        if (!mirror && !tv) return;

        boolean hidden = (mirror && entity.getType().is(VistaMod.CANT_SEE_THROUGH_MIRROR))
                || (tv && entity.getType().is(VistaMod.CANT_SEE_THROUGH_TV));

        // Vampire players are still minecraft:player and some mods flag mobs through an API instead of
        // a tag, so entity type tags catch neither. The mod-loaded flags keep the compat impls from
        // being classloaded when the mod is absent.
        if (!hidden && entity instanceof LivingEntity living) {
            hidden = (CompatHandler.SUPERNATURAL && SupernaturalCompat.isVampire(living))
                    || (CompatHandler.VAMPIRISM && living instanceof Player player && VampirismCompat.isVampire(player));
        }

        if (hidden) ci.cancel();
    }

    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    public void vista$alterSetupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator, CallbackInfo ci) {
        if (VistaLevelRenderer.onSetupRenderer((LevelRenderer) (Object) this, camera, frustum, hasCapturedFrustum, isSpectator)) {
            ci.cancel();
        }
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    public void vista$onChunkLoaded(ChunkPos chunkPos, CallbackInfo ci) {
        VistaLevelRenderer.onChunkLoaded(chunkPos, this.sectionOcclusionGraph);
    }

    @Inject(method = "addRecentlyCompiledSection", at = @At("HEAD"))
    public void vista$onRecentlyCompiledSection(SectionRenderDispatcher.RenderSection renderSection, CallbackInfo ci) {
        VistaLevelRenderer.onRecentlyCompiledSection(renderSection, this.sectionOcclusionGraph);
    }

    // ViewArea.setDirty is floorMod-indexed into the normal torus and can never reach the appended
    // pinned sections, so far zone chunks compile once and then their mesh freezes. Dirty the pinned
    // section at the exact coordinates too.
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void vista$dirtyPinnedSection(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread, CallbackInfo ci) {
        if (viewArea instanceof IViewAreaExt va
                && VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.containsChunk(sectionX, sectionZ)) {
            va.vista$setPinnedSectionDirty(sectionX, sectionY, sectionZ, reRenderOnMainThread);
        }
    }

    // Same torus problem as above: vanilla skips entities whose section "isn't compiled", so far zone
    // entities never draw despite existing on the client. Answer the gate from the pinned section.
    @ModifyReturnValue(method = "isSectionCompiled", at = @At("RETURN"))
    private boolean vista$pinnedSectionCompiled(boolean original, BlockPos pos) {
        if (original) return true;
        int secX = SectionPos.blockToSectionCoord(pos.getX());
        int secZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (viewArea instanceof IViewAreaExt va
                && VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.containsChunk(secX, secZ)) {
            return va.vista$isPinnedSectionCompiled(secX, SectionPos.blockToSectionCoord(pos.getY()), secZ);
        }
        return original;
    }

    // F3+A and friends release every section VertexBuffer, so cached feed states have to be reset or
    // they draw closed buffers.
    @Inject(method = "allChanged", at = @At("TAIL"))
    public void vista$onAllChanged(CallbackInfo ci) {
        VistaLevelRenderer.onLevelRendererAllChanged();
    }
}
