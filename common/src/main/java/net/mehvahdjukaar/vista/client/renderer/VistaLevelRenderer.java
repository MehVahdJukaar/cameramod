package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferUploader;
import org.joml.Vector4f;
import net.mehvahdjukaar.moonlight.api.misc.WeakHashSet;
import net.mehvahdjukaar.moonlight.core.client.DummyCamera;
import net.mehvahdjukaar.vista.VistaPlatStuff;
import net.mehvahdjukaar.vista.client.textures.LiveFeedTexture;
import net.mehvahdjukaar.vista.common.view_finder.EntityDetectorHelper;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.integration.CompatHandler;
import net.mehvahdjukaar.vista.mixins.LevelRendererAccessorMixin;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11C;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static net.minecraft.client.Minecraft.ON_OSX;

public class VistaLevelRenderer {

    private static final Set<SectionOcclusionGraph> MANAGED_GRAPHS = new WeakHashSet<>();
    private static final AtomicReference<SectionOcclusionGraph> MC_OWN_GRAPH = new AtomicReference<>(null);
    private static final DummyCamera DUMMY_CAMERA = new DummyCamera();

    private static ViewFinderBlockEntity renderingLiveFeedVF = null;
    private static boolean renderDetectorOutlines = false;

    public static boolean isRenderingLiveFeed() {
        return renderingLiveFeedVF != null;
    }

    public static boolean isViewFinderRenderingLiveFeed(ViewFinderBlockEntity vf) {
        return renderingLiveFeedVF == vf;
    }

    public static boolean shouldRenderLiveFeedOutlines() {
        return renderDetectorOutlines;
    }

    public static void clear() {
        DUMMY_CAMERA.entity = null;
        MC_OWN_GRAPH.set(null);
        MANAGED_GRAPHS.clear();
        renderingLiveFeedVF = null;
        renderDetectorOutlines = false;
    }

    public static void render(LiveFeedTexture text, ViewFinderBlockEntity tile) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.getMainRenderTarget();
        RenderTarget canvas = text.getRenderTarget();
        mc.mainRenderTarget = canvas;

        Camera camera = DUMMY_CAMERA;
        Camera mainCamera = mc.gameRenderer.mainCamera;
        mc.gameRenderer.mainCamera = camera;

        // save old state
        float oldRenderDistance = mc.gameRenderer.renderDistance;
        PostChain oldPostEffect = mc.gameRenderer.postEffect;
        boolean wasEffectActive = mc.gameRenderer.effectActive;

        // switch to feed camera state
        LevelRendererCameraState oldCameraState = LevelRendererCameraState.capture(mc.levelRenderer);
        LevelRendererCameraState feedCameraState = text.getRendererState();

        RenderSystemState oldRenderState = RenderSystemState.capture();

        try {
            renderingLiveFeedVF = tile;

            float partialTicks = mc.getTimer().getGameTimeDeltaTicks();
            setupSceneCamera(tile, camera, partialTicks);

            tryEnableStencil(canvas);
            canvas.bindWrite(true);
            RenderSystem.viewport(0, 0, canvas.width, canvas.height);

            text.applyPostChain();

            // use same FOV basis as player camera preview, then apply viewfinder zoom modifier
            float fov = (float) mc.options.fov().get() * tile.getFOVModifier();

            mc.gameRenderer.renderDistance = Math.min(oldRenderDistance, calculateRenderDistance(fov));

            RenderSystem.clear(16640, ON_OSX);
            FogRenderer.setupNoFog();
            RenderSystem.enableCull();

            feedCameraState.apply(mc.levelRenderer);

            MANAGED_GRAPHS.add(feedCameraState.getOcclusionGraph());
            MC_OWN_GRAPH.set(oldCameraState.getOcclusionGraph());

            // already wrapped outside; don't double-wrap this or it fucks everything over omg.
            renderLevel(mc, canvas, camera, fov);

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
            MC_OWN_GRAPH.set(null);

            // restore old camera state
            oldCameraState.apply(mc.levelRenderer);

            // restore old render state
            oldRenderState.apply();
            // clear depth + stencil; clearing color here causes visible world/water popping
            RenderSystem.clear(GL11C.GL_DEPTH_BUFFER_BIT | GL11C.GL_STENCIL_BUFFER_BIT, ON_OSX);

            // swap back
            renderingLiveFeedVF = null;

            mc.mainRenderTarget = mainTarget;
            mc.gameRenderer.mainCamera = mainCamera;

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

    private static void tryEnableStencil(RenderTarget canvas) {
        try {
            Method isStencil = canvas.getClass().getMethod("isStencilEnabled");
            Object enabled = isStencil.invoke(canvas);
            if (enabled instanceof Boolean b && b) {
                return;
            }

            Method enableStencil = canvas.getClass().getMethod("enableStencil");
            enableStencil.invoke(canvas);
        } catch (Throwable ignored) {
        }
    }


    //same as game renderer render level but simplified
    private static void renderLevel(Minecraft mc, RenderTarget target, Camera camera, float fov) {
        DeltaTracker deltaTracker = mc.getTimer();
        GameRenderer gr = mc.gameRenderer;
        LevelRenderer lr = mc.levelRenderer;
        Matrix4f oldProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());

        Matrix4f projMatrix = createProjectionMatrix(gr, target, fov);
        //fix Y inversion
        gr.resetProjectionMatrix(projMatrix);

        PoseStack poseStack = new PoseStack();

        Quaternionf cameraRotation = camera.rotation().conjugate(new Quaternionf());
        Matrix4f cameraMatrix = (new Matrix4f()).rotation(cameraRotation);
        //this below is what actually renders everything
        Vec3 cameraPos = camera.getPosition();
        lr.prepareCullFrustum(cameraPos, cameraMatrix, projMatrix);

        List<Entity> detectedEntities = List.of();
        if (renderingLiveFeedVF != null && mc.level != null && renderingLiveFeedVF.hasEntityDetectorFilter()
            && renderingLiveFeedVF.areDetectorOutlinesEnabled()) {
            detectedEntities = EntityDetectorHelper.getDetectedEntities(renderingLiveFeedVF, mc.level);
            renderDetectorOutlines = false;
        }

        try {
            lr.renderLevel(deltaTracker, false, camera, gr,
                    gr.lightTexture(), cameraMatrix, projMatrix);

            if (!detectedEntities.isEmpty()) {
                renderDetectedEntityOverlay(mc, camera, detectedEntities);
            }
        } finally {
            renderDetectorOutlines = false;
        }

        Matrix4f modelViewMatrix = RenderSystem.getModelViewMatrix();

        VistaPlatStuff.dispatchRenderStageAfterLevel(mc, poseStack, camera, modelViewMatrix, projMatrix);
        gr.resetProjectionMatrix(oldProjectionMatrix);
    }



    private static int renderDetectedEntityOverlay(Minecraft mc, Camera camera, List<Entity> detectedEntities) {
        if (detectedEntities.isEmpty()) return 0;

        Vec3 camPos = camera.getPosition();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // FORCE disable depth test to ensure it renders on top
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(6.0F); // Very thick lines for visibility
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();

        // Apply Camera Rotation (Inverse) so lines align with view
        poseStack.mulPose(camera.rotation().conjugate(new Quaternionf()));

        // Translate to camera relative coordinates
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        int rendered = 0;
        for (Entity entity : detectedEntities) {
            if (entity == null || entity.isRemoved()) continue;

            AABB box = entity.getBoundingBox().inflate(0.1);
            float minX = (float) box.minX;
            float minY = (float) box.minY;
            float minZ = (float) box.minZ;
            float maxX = (float) box.maxX;
            float maxY = (float) box.maxY;
            float maxZ = (float) box.maxZ;

            int r = 255; // PURE RED (int)
            int g = 0;
            int b = 0;
            int a = 255;

            // Draw Box Lines Manually (12 edges)
            // Bottom Face
            vertex(bufferBuilder, matrix, minX, minY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, minY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, minY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, minY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, minY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, minY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, minY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, minY, minZ, r, g, b, a);
            // Top Face
            vertex(bufferBuilder, matrix, minX, maxY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, maxY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, maxY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, maxY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, maxY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, maxY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, maxY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, maxY, minZ, r, g, b, a);
            // Vertical Edges
            vertex(bufferBuilder, matrix, minX, minY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, maxY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, minY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, maxY, minZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, minY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, maxX, maxY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, minY, maxZ, r, g, b, a);
            vertex(bufferBuilder, matrix, minX, maxY, maxZ, r, g, b, a);
            
            rendered++;
        }
        
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        
        poseStack.popPose();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        
        return rendered;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int r, int g, int b, int a) {
        Vector4f vec = new Vector4f(x, y, z, 1.0F);
        vec.mul(matrix);
        // Using addVertex and setColor based on observed mappings
        consumer.addVertex(vec.x, vec.y, vec.z).setColor(r, g, b, a);
    }



    @SuppressWarnings("ConstantConditions")
    private static void setupSceneCamera(ViewFinderBlockEntity tile, Camera dummyCamera, float partialTicks) {
        Level level = tile.getLevel();
        float pitch = tile.getPitch(partialTicks);
        float yaw = tile.getYaw(partialTicks);

        if (dummyCamera.entity == null) {
            dummyCamera.entity = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        }
        Entity dummyCameraEntity = dummyCamera.getEntity();
        Vec3 pos = tile.getBlockPos().getCenter();
        dummyCameraEntity.setPos(pos);
        dummyCameraEntity.setXRot(pitch);
        dummyCameraEntity.setYRot(yaw + 180);

        dummyCamera.setPosition(pos);
        dummyCamera.setRotation(yaw, pitch);
    }

    private static Matrix4f createProjectionMatrix(GameRenderer gr, RenderTarget target, float fov) {
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

    public static boolean setupRender(LevelRenderer lr, Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator) {
        if (!isRenderingLiveFeed()) {
            return false;
        }

        if (CompatHandler.SODIUM) return false; //?? todo: give this a custom impl that follows what sodium does

        Vec3 cameraPosition = camera.getPosition();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel clientLevel = minecraft.level;

        // Check if the effective render distance has changed; if so, mark all chunks as needing update
        //TODO: change
        if (minecraft.options.getEffectiveRenderDistance() != lr.lastViewDistance) {
            viewAreaStuffChanged(lr); //never invalidate
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

            lr.viewArea.repositionCamera(playerX, playerZ);
        }

        // Update the section render dispatcher with the camera position
        lr.sectionRenderDispatcher.setCamera(cameraPosition);

        clientLevel.getProfiler().popPush("cull");
        minecraft.getProfiler().popPush("culling");

        // Camera's block position (rounded to nearest block)
        BlockPos cameraBlockPos = camera.getBlockPosition();

        Player player = minecraft.player;
        // Compute camera position in 8-block "units" for occlusion checks
        double cameraUnitX = Math.floor(player.getX() / 8.0);
        double cameraUnitY = Math.floor(player.getY() / 8.0);
        double cameraUnitZ = Math.floor(player.getZ() / 8.0);

        // If the camera has moved to a new 8-block unit, invalidate the occlusion graph
        if (cameraUnitX != lr.prevCamX ||
                cameraUnitY != lr.prevCamY ||
                cameraUnitZ != lr.prevCamZ) {
            //this should never triger for us since the camera never moves
            graph.invalidate(); //needs full update
            //update graph if player itself moved so we discard stale far away sections
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

            // Update occlusion graph to determine which sections are visible
            //needs full update should be performed when new chunks came into view (our camera moved too much compared to the vista cam)
            graph.update(smartCulling, camera, frustum, lr.visibleSections);

            minecraft.getProfiler().pop();


            // Divide camera rotation by 2 to track significant rotation changes
            double cameraRotXHalf = Math.floor(camera.getXRot() / 2.0);
            double cameraRotYHalf = Math.floor(camera.getYRot() / 2.0);

            // Apply frustum update if the graph changed or camera rotated significantly
            if (graph.consumeFrustumUpdate() ||
                    cameraRotXHalf != lr.prevCamRotX ||
                    cameraRotYHalf != lr.prevCamRotY) {

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
        for (SectionOcclusionGraph graph : MANAGED_GRAPHS) {
            if (graph != sectionOcclusionGraph) {
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
        for (SectionOcclusionGraph graph : MANAGED_GRAPHS) {
            if (graph != sectionOcclusionGraph) {
                graph.onSectionCompiled(renderSection);
            }
        }
        SectionOcclusionGraph old = MC_OWN_GRAPH.get();
        if (old != null && old != sectionOcclusionGraph) {
            old.onSectionCompiled(renderSection);
        }
    }
}
