package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mehvahdjukaar.moonlight.api.misc.WeakHashSet;
import net.mehvahdjukaar.moonlight.api.util.math.EntityAngles;
import net.mehvahdjukaar.moonlight.core.client.DummyCamera;
import net.mehvahdjukaar.vista.VistaPlatStuff;
import net.mehvahdjukaar.vista.client.textures.MirrorReflectionTexture;
import net.mehvahdjukaar.vista.client.textures.PerspectiveTexture;
import net.mehvahdjukaar.vista.common.mirror.MirrorBlockEntity;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.integration.iris.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static net.minecraft.client.Minecraft.ON_OSX;

public class VistaLevelRenderer {

    private static final Set<LevelRendererFrustumState> MANAGED_STATES = new WeakHashSet<>();
    private static final Object STATES_LOCK = new Object();
    // MC's own occlusion graph, so async chunk/section callbacks can forward into it alongside the
    // feed graphs. Only the outermost render sets it: nested renders see a feed graph as "current".
    private static final AtomicReference<SectionOcclusionGraph> MC_OWN_GRAPH = new AtomicReference<>(null);

    // Re-entrancy stack for render(). Main render thread only, so no locking. Each depth gets its own
    // dummy camera because vanilla aliases mainCamera into caches the outer call still needs.
    private static final Deque<RenderFrame> RENDER_STACK = new ArrayDeque<>();
    private static final List<DummyCamera> DUMMY_CAMERA_POOL = new ArrayList<>();

    // textureRecursionDepth/textureParentChain describe the texture being rendered into, not the
    // stack. A PENDING flush pushes one frame no matter how deep the texture really is, so children
    // must derive their depth and chain from these or the recursion cap never fires.
    private record RenderFrame(
            Object token,
            boolean hasOffAxisFrustum,
            @Nullable Vec3 bfsStartOverride,
            @Nullable UUID mirrorUuid,
            int textureRecursionDepth,
            List<UUID> textureParentChain
    ) {}

    private static ResourceKey<Level> lastLevel = null;

    // World-space eye displacement caused by view bob this frame. Bob is folded into the projection
    // matrix here, so the modelview keeps the un-bobbed camera position. Mirrors have to reflect the
    // bobbed eye or the reflected scene wobbles against the (bobbed) quad, worse the deeper it goes.
    private static Vec3 mainBobEyeOffset = Vec3.ZERO;

    public static boolean isRenderingLiveFeed() {
        return !RENDER_STACK.isEmpty();
    }

    public static boolean isRenderingMirrorReflection() {
        RenderFrame top = RENDER_STACK.peek();
        return top != null && top.mirrorUuid != null;
    }

    public static boolean isRenderingCameraFeed() {
        RenderFrame top = RENDER_STACK.peek();
        return top != null && top.mirrorUuid == null;
    }

    // Polygon offset layering doesn't take inside nested level renders, and z-fights under FAST
    // graphics, so surface quads fall back to a manual forward offset in those cases.
    public static boolean needsManualSurfaceOffset() {
        if (isRenderingLiveFeed()) return true;
        return Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
    }

    /**
     * Records the eye displacement view bob introduced this pass, read off the bob pose matrix the
     * game already built, so mods that alter bob and the bob-disabled case work too.
     * Bob sits in view space between projection and modelview, so the effective eye solves
     * B * R_w2v * (eye - camPos) = 0, i.e. the offset is R_v2w * translation(B^-1), and
     * camera.rotation() is that R_v2w.
     *
     * @param bobPose pose after bobHurt + bobView, i.e. pure bob starting from identity
     */
    public static void captureMainBobEyeOffset(Camera camera, Matrix4f bobPose) {
        Vector3f off = new Matrix4f(bobPose).invert().getTranslation(new Vector3f());
        camera.rotation().transform(off);
        mainBobEyeOffset = new Vec3(off.x, off.y, off.z);
    }

    public static Vec3 getMainBobEyeOffset() {
        return mainBobEyeOffset;
    }

    public static boolean isViewFinderRenderingLiveFeed(ViewFinderBlockEntity vf) {
        for (RenderFrame f : RENDER_STACK) {
            if (f.token == vf) return true;
        }
        return false;
    }

    // Depth a child mirror found inside the current render should use, 0 if nothing is rendering.
    // Derived from the frame rather than the stack size, see RenderFrame.
    public static int getCurrentDepth() {
        RenderFrame top = RENDER_STACK.peek();
        if (top == null) return 0;
        if (top.mirrorUuid == null) return 1;
        return top.textureRecursionDepth + 1;
    }

    // Chain a child mirror found inside the current render should use. Empty outside a mirror render.
    public static List<UUID> getCurrentMirrorChain() {
        RenderFrame top = RENDER_STACK.peek();
        if (top == null || top.mirrorUuid == null) return List.of();
        List<UUID> chain = new ArrayList<>(top.textureParentChain.size() + 1);
        chain.addAll(top.textureParentChain);
        chain.add(top.mirrorUuid);
        return chain;
    }

    public static void clear() {
        DUMMY_CAMERA_POOL.clear();
        MC_OWN_GRAPH.set(null);
        synchronized (STATES_LOCK) {
            MANAGED_STATES.clear();
        }
        RENDER_STACK.clear();
    }

    // Forces every feed graph to redo its BFS. Call when zone data changes so freshly pinned
    // sections get picked up.
    public static void invalidateManagedGraphs() {
        synchronized (STATES_LOCK) {
            for (LevelRendererFrustumState state : MANAGED_STATES) {
                SectionOcclusionGraph graph = state.getOcclusionGraph();
                if (graph != null) graph.invalidate();
            }
        }
    }

    public static void registerManagedState(LevelRendererFrustumState state) {
        synchronized (STATES_LOCK) {
            MANAGED_STATES.add(state);
        }
    }

    // allChanged() frees every section's VertexBuffer and swaps in a new ViewArea, leaving the cached
    // feed states pointing at dead RenderSections. Wipe them and let the next feed render rebuild.
    public static void onLevelRendererAllChanged() {
        synchronized (STATES_LOCK) {
            for (LevelRendererFrustumState state : MANAGED_STATES) {
                state.resetForLevelRendererReload();
            }
        }
        MC_OWN_GRAPH.set(null);
    }

    public static DummyCamera getDummyCamera() {
        return acquireDummyCamera(0);
    }

    private static DummyCamera acquireDummyCamera(int depth) {
        while (DUMMY_CAMERA_POOL.size() <= depth) {
            DUMMY_CAMERA_POOL.add(new DummyCamera());
        }
        return DUMMY_CAMERA_POOL.get(depth);
    }

    public static void render(PerspectiveTexture text, ViewFinderBlockEntity tile) {
        render(text, tile, (camera, partialTicks) -> setupSceneCamera(tile, camera, partialTicks),
                tile.getFOV(), true, null, null, null);
    }

    /**
     * @param fov                    ignored when customProjection is given
     * @param customProjection       used as-is instead of a symmetric perspective. Mirrors pass an
     *                               off-axis frustum shaped to their frame, so the near plane is the
     *                               mirror itself
     * @param bfsStartOverride       world position the camera is moved to just for the occlusion BFS.
     *                               Mirrors on walls need it: the reflected eye sits inside the wall
     *                               block, so smart culling would propagate "blocked" everywhere
     * @param renderDistanceOverride per-pass chunk render distance, used to attenuate mirror nesting
     */
    public static void render(PerspectiveTexture text, Object renderingToken,
                              SceneCameraSetup cameraSetup, float fov,
                              boolean applyPostChain,
                              @Nullable Matrix4f customProjection,
                              @Nullable Vec3 bfsStartOverride,
                              @Nullable Integer renderDistanceOverride) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) return;
        //debounce dimension changing for some reason idk yet
        if (mc.level.dimension() != lastLevel) {
            lastLevel = mc.level.dimension();
            return;
        }

        // Every off-screen level render funnels through here, so compat wrappers go on this call and
        // nowhere else. Each one saves and restores what it stomps, so nesting is fine.
        CompatHandler.decorateRenderer(() -> doRender(mc, text, renderingToken, cameraSetup, fov,
                applyPostChain, customProjection, bfsStartOverride, renderDistanceOverride)).run();
    }

    private static void doRender(Minecraft mc, PerspectiveTexture text, Object renderingToken,
                                 SceneCameraSetup cameraSetup, float fov,
                                 boolean applyPostChain,
                                 @Nullable Matrix4f customProjection,
                                 @Nullable Vec3 bfsStartOverride,
                                 @Nullable Integer renderDistanceOverride) {
        int depth = RENDER_STACK.size();
        boolean isOutermost = depth == 0;

        RenderTarget mainTarget = mc.getMainRenderTarget();
        RenderTarget canvas = text.getRenderTarget();
        mc.mainRenderTarget = canvas;

        // A TV resize swaps in a whole new RenderTarget, which Iris's version-counter change detection
        // misses, leaving its gbuffers attached to the old freed canvas. Nudge it manually.
        if (CompatHandler.IRIS) {
            IrisCompat.onFeedCanvasBound(canvas);
        }

        Camera camera = acquireDummyCamera(depth);
        Camera mainCamera = mc.gameRenderer.mainCamera;
        mc.gameRenderer.mainCamera = camera;

        // save old state
        float oldRenderDistance = mc.gameRenderer.renderDistance;
        PostChain oldPostEffect = mc.gameRenderer.postEffect;
        boolean wasEffectActive = mc.gameRenderer.effectActive;

        // switch to feed camera state
        LevelRendererFrustumState oldCameraState = LevelRendererFrustumState.capture(mc.levelRenderer);
        LevelRendererFrustumState feedCameraState = text.getRendererState();

        RenderSystemState oldRenderState = RenderSystemState.capture();

        // Reproduce the Fabulous-off state so the world composes into our canvas instead of Fabulous's
        // deferred targets. Kept in a local so re-entrant renders each restore their own values.
        FabulousDeferredState fabulousState = FabulousDeferredState.captureAndDisable(mc.levelRenderer);

        UUID mirrorUuid = renderingToken instanceof MirrorBlockEntity m ? m.getId() : null;
        int textureRecursionDepth = 0;
        List<UUID> textureParentChain = List.of();
        if (text instanceof MirrorReflectionTexture mrt) {
            textureRecursionDepth = mrt.getRecursionDepth();
            textureParentChain = mrt.getParentChain();
        }
        RENDER_STACK.push(new RenderFrame(
                renderingToken, customProjection != null, bfsStartOverride,
                mirrorUuid, textureRecursionDepth, textureParentChain));

        try {
            float partialTicks = mc.getTimer().getGameTimeDeltaTicks();
            cameraSetup.setup(camera, partialTicks);

            canvas.bindWrite(true);
            RenderSystem.viewport(0, 0, canvas.width, canvas.height);

            if (applyPostChain) {
                text.applyPostChain();
            } else {
                mc.gameRenderer.postEffect = null;
                mc.gameRenderer.effectActive = false;
            }

            int requestedDist = renderDistanceOverride != null
                    ? renderDistanceOverride
                    : calculateRenderDistance(fov);
            mc.gameRenderer.renderDistance = Math.min(oldRenderDistance, requestedDist);
            RenderSystem.clear(16640, ON_OSX);
            FogRenderer.setupNoFog();
            RenderSystem.enableCull();

            feedCameraState.apply(mc.levelRenderer);

            if (isOutermost) {
                MC_OWN_GRAPH.set(oldCameraState.getOcclusionGraph());
            }

            // already wrapped outside; don't double-wrap this or it fucks everything over omg.
            renderLevel(mc, canvas, camera, fov, customProjection);

            // save updated feed camera state
            feedCameraState.copyFrom(mc.levelRenderer);

            if (mc.gameRenderer.postEffect != null && mc.gameRenderer.effectActive) {
                RenderSystem.disableBlend();
                RenderSystem.disableDepthTest();
                RenderSystem.resetTextureMatrix();
                DeltaTracker deltaTracker = mc.getTimer();
                mc.gameRenderer.postEffect.process(deltaTracker.getGameTimeDeltaTicks());
            }
        } finally {
            if (isOutermost) {
                MC_OWN_GRAPH.set(null);
            }

            fabulousState.restore(mc.levelRenderer);
            oldCameraState.apply(mc.levelRenderer);
            oldRenderState.apply();
            // clear depth only; clearing color here causes visible world/water popping
            RenderSystem.clear(GL11C.GL_DEPTH_BUFFER_BIT, ON_OSX);

            RENDER_STACK.pop();

            mc.mainRenderTarget = mainTarget;
            mc.gameRenderer.mainCamera = mainCamera;

            // nested renders have to re-bind the outer canvas, or the rest of the outer pass keeps
            // drawing into the inner one
            if (!isOutermost) {
                mainTarget.bindWrite(true);
                RenderSystem.viewport(0, 0, mainTarget.width, mainTarget.height);
            }

            mc.gameRenderer.postEffect = oldPostEffect;
            mc.gameRenderer.effectActive = wasEffectActive;
            mc.gameRenderer.renderDistance = oldRenderDistance;
        }
    }

    private static Integer calculateRenderDistance(float fov) {
        //TODO: improve
        return ClientConfigs.RENDER_DISTANCE.get();
    }


    //same as game renderer render level but simplified
    private static void renderLevel(Minecraft mc, RenderTarget target, Camera camera, float fov,
                                    @Nullable Matrix4f customProjection) {
        DeltaTracker deltaTracker = mc.getTimer();
        GameRenderer gr = mc.gameRenderer;
        LevelRenderer lr = mc.levelRenderer;
        Matrix4f oldProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());

        Matrix4f projMatrix = customProjection != null
                ? new Matrix4f(customProjection)
                : createProjectionMatrixForCamera(gr, target, fov);

        PoseStack poseStack = new PoseStack();

        // Don't bake bobView/bobHurt in here. The mirror quad already bobs through the main pass, so
        // bobbing the content too would double it up. Bob parallax is applied to the eye position
        // instead, in MirrorBlockEntityRenderer, which is all reflection depends on.
        Quaternionf cameraRotation = camera.rotation().conjugate(new Quaternionf());
        Matrix4f cameraMatrix = (new Matrix4f()).rotation(cameraRotation);
        Vec3 cameraPos = camera.getPosition();

        gr.resetProjectionMatrix(projMatrix);
        lr.prepareCullFrustum(cameraPos, cameraMatrix, projMatrix);

        // Iris tracks "inside renderLevel" with a plain boolean set on HEAD and cleared on RETURN, no
        // nesting counter, so our nested call would leave it false for the rest of the main pass.
        // Everything gated on it then takes the not-rendering path and block entities vanish.
        boolean irisWasRenderingLevel = CompatHandler.IRIS && IrisCompat.isIrisRenderingLevel();
        try {
            lr.renderLevel(deltaTracker, false, camera, gr,
                    gr.lightTexture(), cameraMatrix, projMatrix);
        } finally {
            if (irisWasRenderingLevel) IrisCompat.setIrisRenderingLevel(true);
        }

        Matrix4f modelViewMatrix = RenderSystem.getModelViewMatrix();

        VistaPlatStuff.dispatchRenderStageAfterLevel(mc, poseStack, camera, modelViewMatrix, projMatrix);
        gr.resetProjectionMatrix(oldProjectionMatrix);
    }

    @SuppressWarnings("ConstantConditions")
    private static void setupSceneCamera(ViewFinderBlockEntity tile, Camera camera, float partialTicks) {
        Level level = tile.getLevel();
        Quaternionf viewFinderRot = tile.getWorldOrientation(partialTicks);
        //TODO: add Z for when looking up
        EntityAngles entityAngles = EntityAngles.fromQuaternion(viewFinderRot);
        float yaw = entityAngles.yaw();
        float pitch = entityAngles.pitch();

        camera.initialized = true;
        camera.level = level;
        if (camera.entity == null) {
            camera.entity = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        }
        Entity dummyCameraEntity = camera.getEntity();
        Vec3 pos = tile.getGlobalPosition(partialTicks);
        dummyCameraEntity.setPos(pos);
        dummyCameraEntity.setXRot(pitch);
        dummyCameraEntity.setYRot(yaw + 180);

        camera.setPosition(pos);
        camera.setRotation(yaw, pitch);
    }

    //Same as GameRenderer getProjectionMatrix but with custom fov and aspect ratio based on target size, and no zoom support (for now)
    private static Matrix4f createProjectionMatrixForCamera(GameRenderer gr, RenderTarget target, float fov) {
        Matrix4f matrix4f = new Matrix4f();
        float zoom = 1;

        if (zoom != 1.0F) {
            float zoomX = 0;
            float zoomY = 0;
            matrix4f.translate(zoomX, -zoomY, 0.0F);
            matrix4f.scale(zoom, zoom, 1.0F);
        }
        float depthFar = gr.getDepthFar();

        return matrix4f.perspective(fov * Mth.DEG_TO_RAD,
                (float) target.width / (float) target.height,
                ViewFinderBlockEntity.NEAR_PLANE, depthFar);
    }


    //mixin called stuff

    public static boolean onSetupRenderer(LevelRenderer lr, Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator) {
        if (!isRenderingLiveFeed()) {
            return false;
        }

        if (CompatHandler.SODIUM) return false;

        Vec3 cameraPosition = camera.getPosition();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel clientLevel = minecraft.level;

        //TODO: change
        if (minecraft.options.getEffectiveRenderDistance() != lr.lastViewDistance) {
            viewAreaStuffChanged(lr); //this initializes stuff and is crucial but could be hooked up better
        }

        clientLevel.getProfiler().push("camera");

        SectionOcclusionGraph graph = lr.sectionOcclusionGraph;

        Entity cameraEntity = camera.entity;
        double playerX = cameraEntity.getX();
        double playerY = cameraEntity.getY();
        double playerZ = cameraEntity.getZ();

        int cameraSectionX = SectionPos.posToSectionCoord(playerX);
        int cameraSectionY = SectionPos.posToSectionCoord(playerY);
        int cameraSectionZ = SectionPos.posToSectionCoord(playerZ);

        if (lr.lastCameraSectionX != cameraSectionX ||
                lr.lastCameraSectionY != cameraSectionY ||
                lr.lastCameraSectionZ != cameraSectionZ) {

            lr.lastCameraSectionX = cameraSectionX;
            lr.lastCameraSectionY = cameraSectionY;
            lr.lastCameraSectionZ = cameraSectionZ;

            //lr.viewArea.repositionCamera(cameraX, cameraZ);
        }

        lr.sectionRenderDispatcher.setCamera(cameraPosition);

        clientLevel.getProfiler().popPush("cull");
        minecraft.getProfiler().popPush("culling");

        BlockPos cameraBlockPos = camera.getBlockPosition();

        // occlusion checks work in 8-block units, off the render camera, not the player
        double cameraUnitX = Math.floor(cameraPosition.x / 8.0);
        double cameraUnitY = Math.floor(cameraPosition.y / 8.0);
        double cameraUnitZ = Math.floor(cameraPosition.z / 8.0);

        if (cameraUnitX != lr.prevCamX ||
                cameraUnitY != lr.prevCamY ||
                cameraUnitZ != lr.prevCamZ) {
            graph.invalidate();
        }

        lr.prevCamX = cameraUnitX;
        lr.prevCamY = cameraUnitY;
        lr.prevCamZ = cameraUnitZ;

        minecraft.getProfiler().popPush("update");

        if (!hasCapturedFrustum) {
            boolean smartCulling = minecraft.smartCull;

            // vanilla disables smart culling for spectators inside solid blocks
            if (isSpectator && clientLevel.getBlockState(cameraBlockPos).isSolidRender(clientLevel, cameraBlockPos)) {
                //    smartCulling = false;
            }

            double entityViewScale = Mth.clamp( //TODO: change these
                    (double) minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5
            ) * minecraft.options.entityDistanceScaling().get();
            Entity.setViewScale(entityViewScale);

            minecraft.getProfiler().push("section_occlusion_graph");

            // Teleport the camera to the override point for the duration of graph.update only, so the
            // BFS seeds from a visible chunk. The frustum is untouched and still does the real
            // culling, we're only moving the seed.
            RenderFrame currentFrame = RENDER_STACK.peek();
            Vec3 bfsOverride = currentFrame != null ? currentFrame.bfsStartOverride : null;
            Vec3 actualCamPos = null;
            if (bfsOverride != null) {
                actualCamPos = camera.getPosition();
                camera.setPosition(bfsOverride);
            }
            try {
                graph.update(smartCulling, camera, frustum, lr.visibleSections);
            } finally {
                if (actualCamPos != null) camera.setPosition(actualCamPos);
            }

            minecraft.getProfiler().pop();

            double cameraRotXHalf = Math.floor(camera.getXRot() / 2.0);
            double cameraRotYHalf = Math.floor(camera.getYRot() / 2.0);

            // Off-axis frustums always need the update: their bounds shift as the viewer moves even
            // though the camera rotation stays pinned to the mirror normal.
            boolean hasOffAxis = currentFrame != null && currentFrame.hasOffAxisFrustum;
            if (graph.consumeFrustumUpdate() ||
                    cameraRotXHalf != lr.prevCamRotX ||
                    cameraRotYHalf != lr.prevCamRotY ||
                    hasOffAxis) {

                lr.applyFrustum(LevelRenderer.offsetFrustum(frustum));
                lr.prevCamRotX = cameraRotXHalf;
                lr.prevCamRotY = cameraRotYHalf;
            }
        }

        minecraft.getProfiler().pop();

        return true;
    }

    private static void viewAreaStuffChanged(LevelRenderer lr) {
        Level level = Minecraft.getInstance().level;
        Minecraft mc = Minecraft.getInstance();

        lr.lastViewDistance = mc.options.getEffectiveRenderDistance();
        if (lr.viewArea != null) {
            //    lr.viewArea.releaseAllBuffers();
        }

        //    lr.sectionRenderDispatcher.blockUntilClear();

        //lr.viewArea = new ViewArea(lr.sectionRenderDispatcher, level, mc.options.getEffectiveRenderDistance(), lr);
        lr.sectionOcclusionGraph.waitAndReset(lr.viewArea);
        lr.visibleSections.clear();
        Entity entity = mc.getCameraEntity();
        if (entity != null) {
            lr.viewArea.repositionCamera(entity.getX(), entity.getZ());
        }

    }

    //very ugly because these can be called on another thread

    public static void onChunkLoaded(ChunkPos chunkPos, SectionOcclusionGraph sectionOcclusionGraph) {
        if (CompatHandler.SODIUM) return;
        LevelRendererFrustumState[] snapshot;
        synchronized (STATES_LOCK) {
            snapshot = MANAGED_STATES.toArray(new LevelRendererFrustumState[0]);
        }
        for (LevelRendererFrustumState state : snapshot) {
            SectionOcclusionGraph graph = state.getOcclusionGraph();
            if (graph != null && graph != sectionOcclusionGraph) {
                graph.onChunkLoaded(chunkPos);
            }
        }
        SectionOcclusionGraph old = MC_OWN_GRAPH.get();
        if (old != null && old != sectionOcclusionGraph) {
            old.onChunkLoaded(chunkPos);
        }
    }

    public static void onRecentlyCompiledSection(SectionRenderDispatcher.RenderSection renderSection, SectionOcclusionGraph sectionOcclusionGraph) {
        if (CompatHandler.SODIUM) return;
        LevelRendererFrustumState[] snapshot;
        synchronized (STATES_LOCK) {
            snapshot = MANAGED_STATES.toArray(new LevelRendererFrustumState[0]);
        }
        for (LevelRendererFrustumState state : snapshot) {
            SectionOcclusionGraph graph = state.getOcclusionGraph();
            if (graph != null && graph != sectionOcclusionGraph) {
                graph.onSectionCompiled(renderSection);
            }
        }
        SectionOcclusionGraph old = MC_OWN_GRAPH.get();
        if (old != null && old != sectionOcclusionGraph) {
            old.onSectionCompiled(renderSection);
        }
    }
}
