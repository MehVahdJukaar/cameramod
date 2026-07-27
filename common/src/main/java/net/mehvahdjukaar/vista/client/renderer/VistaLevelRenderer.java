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
    // Tracks MC's main occlusion graph so async chunk/section callbacks can forward into it
    // alongside the feed graphs. Only the outermost render writes this — nested renders see
    // a feed graph as the "current" graph, not MC's, so writing it on every entry would lose
    // the real reference.
    private static final AtomicReference<SectionOcclusionGraph> MC_OWN_GRAPH = new AtomicReference<>(null);

    // Re-entrancy stack: each entry to render() pushes a frame; finally pops. Single-thread
    // (main render thread), no locking. Per-depth dummy cameras live in a parallel pool so
    // nested renders don't trash the outer camera's state when both share the same instance.
    private static final Deque<RenderFrame> RENDER_STACK = new ArrayDeque<>();
    private static final List<DummyCamera> DUMMY_CAMERA_POOL = new ArrayList<>();

    /**
     * @param textureRecursionDepth the recursion depth of the texture this frame is rendering
     *                              into (read from {@link MirrorReflectionTexture#getRecursionDepth()}).
     *                              Nested BE-renderer calls inside this frame use
     *                              {@code textureRecursionDepth + 1} as their own depth.
     * @param textureParentChain    the parent chain of the texture this frame is rendering into.
     *                              Nested BE-renderer calls extend it with {@code mirrorUuid} to
     *                              form their own chain. Must be propagated through PENDING flushes —
     *                              otherwise nested chain contexts alias to the same texture and
     *                              the depth cap stops firing.
     */
    private record RenderFrame(
            Object token,
            boolean hasOffAxisFrustum,
            @Nullable Vec3 bfsStartOverride,
            @Nullable UUID mirrorUuid,
            int textureRecursionDepth,
            List<UUID> textureParentChain
    ) {}

    private static ResourceKey<Level> lastLevel = null;

    // World-space displacement of the main view's eye caused by view bob this frame. In this MC
    // version bob is folded into the projection matrix (GameRenderer.renderLevel), so the modelview
    // keeps the raw, un-bobbed camera position. Mirrors must reflect the *bobbed* eye, otherwise the
    // reflected scene's parallax tracks the un-bobbed POV while the displayed quad bobs — a mismatch
    // that's invisible at the mirror surface but grows with reflected depth (the far scene wobbles).
    // Captured every main pass by GameRendererMixin via captureMainBobEyeOffset; stays ZERO when bob
    // is disabled or the eye isn't moving. Main-thread render only, so a plain static is safe.
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

    /**
     * Whether the mirror/TV surface quads need their legacy manual forward z-offset instead of
     * relying on POLYGON_OFFSET_LAYERING. True inside nested level renders (the polygon-offset
     * state doesn't take there) and always under FAST graphics (where it z-fights otherwise).
     */
    public static boolean needsManualSurfaceOffset() {
        if (isRenderingLiveFeed()) return true;
        return Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
    }

    /**
     * Records the world-space eye displacement that view bob introduced this main pass. Reads it
     * straight off the bob pose matrix the game already built (so any mod that alters bob, plus
     * bob-hurt and the bob-disabled case, are all handled with no duplicated bob math).
     *
     * <p>The bob transform {@code B} sits in view space between projection and modelview, so the
     * effective eye solves {@code B · R_w2v · (eye - camPos) = 0}, giving
     * {@code eye = camPos + R_v2w · translation(B⁻¹)}. We only need the offset
     * {@code R_v2w · translation(B⁻¹)} — {@code camera.rotation()} is exactly {@code R_v2w}
     * (view→world). Only the eye *position* matters for reflection correctness; bob's rotation is
     * already carried by the mirror quad in the main pass.
     *
     * @param bobPose {@code poseStack.last().pose()} after bobHurt + bobView, i.e. the pure bob
     *                transform starting from identity.
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

    /**
     * Recursion depth that a child mirror encountered inside the current render should use.
     * Reads the innermost frame's {@code textureRecursionDepth} and adds 1 — using the raw
     * stack size would collapse to 1 for every PENDING-flushed nested render (each flush
     * pushes a single frame regardless of the texture's true depth), breaking the depth cap.
     * Returns 0 if no render is in progress.
     */
    public static int getCurrentDepth() {
        RenderFrame top = RENDER_STACK.peek();
        if (top == null) return 0;
        if (top.mirrorUuid == null) return 1;
        return top.textureRecursionDepth + 1;
    }

    /**
     * Chain of mirror UUIDs for a child mirror encountered inside the current render —
     * {@code parentChain + mirrorUuid} of the innermost mirror frame. Empty when no mirror is
     * currently being rendered (main pass, view finder, etc.).
     */
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

    /**
     * Invalidates all managed (feed) occlusion graphs so they redo their full BFS.
     * Call whenever zone data changes so newly-created pinned sections are picked up.
     */
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

    /**
     * Called at the tail of {@link LevelRenderer#allChanged()}. That method releases
     * every section's VertexBuffer (setting their mode to null and deleting their VAO)
     * and swaps in a fresh ViewArea. Every cached feed state still references the now
     * dead RenderSections, so we wipe them — the next feed render will rebuild a fresh
     * occlusion graph against the new ViewArea.
     */
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

    /**
     * Returns the dummy camera reserved for the given recursion depth. Each nesting level needs
     * its own camera instance — vanilla code aliases {@code mc.gameRenderer.mainCamera} into
     * many caches, so a nested render reusing the outer dummy would mutate state the outer call
     * still needs on resume.
     */
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
     * @param fov                     ignored when {@code customProjection} is non-null.
     * @param customProjection        if non-null, used as-is instead of building a symmetric
     *                                perspective from {@code fov}. Mirrors use this to bake an
     *                                off-axis frustum shaped to the mirror's frame, so the near
     *                                plane *is* the mirror.
     * @param bfsStartOverride        if non-null, temporarily moves the camera to this world
     *                                position around the section-occlusion-graph BFS so the walk
     *                                starts from a chunk that's actually visible. Mirrors against
     *                                walls pass a point in front of the mirror — otherwise smart
     *                                culling stalls because the reflected eye lands inside the
     *                                wall block, blocking BFS propagation.
     * @param renderDistanceOverride  if non-null, overrides the chunk render distance for this
     *                                pass (used by recursive mirror nesting to attenuate per
     *                                depth). Otherwise falls back to {@link #calculateRenderDistance}.
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

        // Every off-screen level render goes through here — TV feeds, mirrors, and their nested
        // recursions alike — so the mod compat wrappers belong on this call and nowhere else.
        // Each one saves and restores whatever global it stomps, so nesting is fine.
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

        // Tell Iris-side compat that the main render target may have changed identity
        // (TV resize swaps in a fresh PerspectiveTexture → fresh RenderTarget). Iris's
        // own change detection relies on a version counter that doesn't increment on
        // a brand-new RenderTarget, so without this nudge the iris pipeline keeps
        // its gbuffers attached to the old (smaller / freed) canvas.
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

        // Force the genuine FANCY path for this nested render by reproducing the Fabulous-off state
        // (null transparency chain + deferred targets) so the world composes into our off-screen
        // canvas instead of Fabulous's deferred targets. See FabulousDeferredState for the why.
        // Held in a local so re-entrant nested renders each restore their own values.
        FabulousDeferredState fabulousState = FabulousDeferredState.captureAndDisable(mc.levelRenderer);

        UUID mirrorUuid = renderingToken instanceof MirrorBlockEntity m ? m.getId() : null;
        // Pull the texture's logical recursion depth + parent chain so nested BE-renderer calls
        // can correctly derive THEIR depth/chain from this frame. The PENDING flush only knows
        // about textures, not the call site that scheduled them, so without this propagation a
        // depth-1 PENDING entry would look identical to a depth-0 one to its children.
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

            // Only the outermost render captures MC's real main graph. Nested renders see a
            // feed graph as "current", which isn't useful for the async section callbacks.
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

            // restore the fabulous deferred state we disabled for this nested render
            fabulousState.restore(mc.levelRenderer);

            // restore old camera state
            oldCameraState.apply(mc.levelRenderer);

            // restore old render state
            oldRenderState.apply();
            // clear depth only; clearing color here causes visible world/water popping
            RenderSystem.clear(GL11C.GL_DEPTH_BUFFER_BIT, ON_OSX);

            RENDER_STACK.pop();

            mc.mainRenderTarget = mainTarget;
            mc.gameRenderer.mainCamera = mainCamera;

            // Nested renders must re-bind the outer canvas they were drawing into — otherwise
            // subsequent draw calls in the outer pass would still be hitting the inner canvas.
            if (!isOutermost) {
                mainTarget.bindWrite(true);
                RenderSystem.viewport(0, 0, mainTarget.width, mainTarget.height);
            }

            // restore post process
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

        // NOTE: don't bake bobView/bobHurt into the reflection modelview here. The displayed mirror
        // surface already bobs (the quad goes through the main pass's bobbed projection), so baking
        // bob into the framebuffer content too would double-bob the surface. Parallax against the
        // bobbed POV is instead handled where it belongs — at the *eye position*: the mirror eye is
        // offset by VistaLevelRenderer.getMainBobEyeOffset() before reflection (see
        // MirrorBlockEntityRenderer). That's sufficient because reflecting a ray from the bobbed eye
        // through a mirror-plane point only depends on the eye position, not the bob rotation, which
        // the quad already carries. So the camera here stays pinned to the mirror normal.
        Quaternionf cameraRotation = camera.rotation().conjugate(new Quaternionf());
        Matrix4f cameraMatrix = (new Matrix4f()).rotation(cameraRotation);
        Vec3 cameraPos = camera.getPosition();

        gr.resetProjectionMatrix(projMatrix);
        lr.prepareCullFrustum(cameraPos, cameraMatrix, projMatrix);

        // Iris tracks "are we inside LevelRenderer#renderLevel" with a plain static boolean that it
        // sets on HEAD and clears on RETURN — no nesting counter. Our nested call therefore leaves it
        // false for the whole remainder of the main pass, and everything Iris gates on it silently
        // goes down the not-rendering-the-level path: new BufferBuilders skip Iris's extended vertex
        // format, and block entities stop getting wrapped with the block-entity render state (so they
        // lose their gbuffers_block program and just vanish). Save and restore it around the call.
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
        Vec3 pos = tile.getBlockPos().getCenter();
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

        // Check if the effective render distance has changed; if so, mark all chunks as needing update
        //TODO: change
        if (minecraft.options.getEffectiveRenderDistance() != lr.lastViewDistance) {
            viewAreaStuffChanged(lr); //this initializes stuff and is crucial but could be hooked up better
        }

        clientLevel.getProfiler().push("camera");

        SectionOcclusionGraph graph = lr.sectionOcclusionGraph;


        // Get player's exact coordinates
        Entity cameraEntity = camera.entity; //this.minecraft.player
        double playerX = cameraEntity.getX();
        double playerY = cameraEntity.getY();
        double playerZ = cameraEntity.getZ();

        // Convert world coordinates to section (chunk) coordinates
        int cameraSectionX = SectionPos.posToSectionCoord(playerX);
        int cameraSectionY = SectionPos.posToSectionCoord(playerY);
        int cameraSectionZ = SectionPos.posToSectionCoord(playerZ);

        // If the camera has moved to a new section, update the renderer's tracking and reposition the view area
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

        // Camera's block position (rounded to nearest block)
        BlockPos cameraBlockPos = camera.getBlockPosition();

        // Compute camera position in 8-block "units" for occlusion checks.
        // Use the actual render camera (ViewFinder position), not the player.
        double cameraUnitX = Math.floor(cameraPosition.x / 8.0);
        double cameraUnitY = Math.floor(cameraPosition.y / 8.0);
        double cameraUnitZ = Math.floor(cameraPosition.z / 8.0);

        if (cameraUnitX != lr.prevCamX ||
                cameraUnitY != lr.prevCamY ||
                cameraUnitZ != lr.prevCamZ) {
            // ViewFinder moved to a new 8-block cell — invalidate the feed graph.
            graph.invalidate();
        }

        // Store current 8-block unit for future comparisons
        lr.prevCamX = cameraUnitX;
        lr.prevCamY = cameraUnitY;
        lr.prevCamZ = cameraUnitZ;

        minecraft.getProfiler().popPush("update");

        // If the frustum has not already been captured
        if (!hasCapturedFrustum) {
            boolean smartCulling = minecraft.smartCull;

            // Disable smart culling for spectators inside solid blocks
            if (isSpectator && clientLevel.getBlockState(cameraBlockPos).isSolidRender(clientLevel, cameraBlockPos)) {
                //    smartCulling = false;
            }

            // Adjust entity view scale based on render distance and scaling option
            double entityViewScale = Mth.clamp( //TODO: change these
                    (double) minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5
            ) * minecraft.options.entityDistanceScaling().get();
            Entity.setViewScale(entityViewScale);

            minecraft.getProfiler().push("section_occlusion_graph");

            // BFS start-position hack: if a render requested a BFS start override, temporarily
            // teleport the camera to that point JUST around graph.update so the walk begins
            // from a chunk that's actually visible. Mirrors against walls need this because
            // the reflected eye lands inside the wall block — without the override, smart
            // culling propagates "blocked everywhere" and starves the visible-section list.
            // The frustum (already off-axis, computed from the real reflected eye) still does
            // the actual culling; we only relocate the BFS seed.
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


            // Divide camera rotation by 2 to track significant rotation changes
            double cameraRotXHalf = Math.floor(camera.getXRot() / 2.0);
            double cameraRotYHalf = Math.floor(camera.getYRot() / 2.0);

            // Apply frustum update if the graph changed, the camera rotated significantly, or
            // we're rendering with an off-axis frustum (where the bounds change every frame as
            // the viewer moves even though the camera rotation stays pinned to the mirror normal).
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
