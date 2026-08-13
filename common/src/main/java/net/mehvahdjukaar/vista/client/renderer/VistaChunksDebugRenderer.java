package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.IClientChunkCacheExt;
import net.mehvahdjukaar.vista.common.chunk_tracking.ExtraChunkViewData;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Map;
import java.util.Set;

public class VistaChunksDebugRenderer implements DebugRenderer.SimpleDebugRenderer {

    public static final VistaChunksDebugRenderer INSTANCE = new VistaChunksDebugRenderer();

    private static final int SCAN_RADIUS = 32;

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, double camX, double camY, double camZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!ClientConfigs.rendersDebug()) return;

        ClientChunkCache chunkSource = mc.level.getChunkSource();
        Map<Long, LevelChunk> pinned = (chunkSource instanceof IClientChunkCacheExt ext)
                ? ext.vista$getPinnedChunks() : Map.of();
        camY += 60;
        int pcx = (int) Math.floor(camX) >> 4;
        int pcz = (int) Math.floor(camZ) >> 4;
        int viewDist = mc.options.renderDistance().get();

        VertexConsumer lines = buf.getBuffer(RenderType.lines());

        MinecraftServer server = PlatHelper.getCurrentServer();
        ServerLevel sl = (server != null) ? server.getLevel(mc.level.dimension()) : null;

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;

                boolean serverHas = sl != null && sl.getChunkSource().hasChunk(cx, cz);
                boolean clientHas = chunkSource.getChunk(cx, cz, ChunkStatus.FULL, false) != null;

                if (serverHas) {
                    chunkBox(ps, lines, camX, camZ, cx, cz, -camY - 2, -camY - 3,
                            0.1f, 0.1f, 0.8f, 0.6f);
                }
                if (clientHas) {
                    chunkBox(ps, lines, camX, camZ, cx, cz, -camY, -camY - 1,
                            0.25f, 0.55f, 0.9f, 0.7f);
                }
            }
        }

        ExtraChunkViewData zoneData = VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA;
        if (zoneData.getZones().isEmpty()) return;

       /*
        chunkRectBorder(ps, lines, camX, camZ,
                pcx - viewDist, pcz - viewDist,
                pcx + viewDist + 1, pcz + viewDist + 1,
                1f, 1f, 1f, 0.3f);
*/
        for (ExtraChunkViewData.Zone zone : zoneData.getZones()) {
            ChunkPos c = zone.center();
            int r = zone.radius();
            /*
            chunkRectBorder(ps, lines, camX, camZ,
                    c.x - r, c.z - r, c.x + r + 1, c.z + r + 1,
                    1f, 0.53f, 0f, 0.9f);*/
            double wx = c.getMinBlockX() + 8 - camX;
            double wz = c.getMinBlockZ() + 8 - camZ;
            LevelRenderer.renderLineBox(ps, lines,
                    wx - 1, -camY, wz - 1,
                    wx + 1, 12 - camY, wz + 1,
                    1f, 0.53f, 0f, 1f);
        }

        Set<ChunkPos> allZone = zoneData.getAllChunks();
        for (ChunkPos pos : allZone) {
            if (Math.abs(pos.x - pcx) > SCAN_RADIUS || Math.abs(pos.z - pcz) > SCAN_RADIUS) continue;

            boolean hasPinned = pinned.containsKey(pos.toLong());
            boolean inView = Math.abs(pos.x - pcx) <= viewDist && Math.abs(pos.z - pcz) <= viewDist;

            float r, g, b;
            if (!hasPinned && !inView) {
                r = 1f;
                g = 0.2f;
                b = 0.2f;   // RED: missing
            } else if (hasPinned && inView) {
                r = 1f;
                g = 0.87f;
                b = 0f;     // YELLOW: pinned + normal view
            } else if (hasPinned) {
                r = 0.2f;
                g = 1f;
                b = 0.35f;   // LIME: pinned outside view
            } else {
                r = 0f;
                g = 0.87f;
                b = 1f;     // CYAN: normal view
            }
            // taller slab, slightly inset
            chunkBox(ps, lines, camX, camZ, pos.x, pos.z, -camY + 5, -camY, r, g, b, 1f);
        }
    }

    // camera-relative wireframe box for one chunk column
    private static void chunkBox(PoseStack ps, VertexConsumer vc,
                                 double camX, double camZ, int cx, int cz,
                                 double yMin, double yMax,
                                 float r, float g, float b, float a) {
        double x0 = cx * 16.0 - camX + 0.1;
        double z0 = cz * 16.0 - camZ + 0.1;
        double x1 = x0 + 15.8;
        double z1 = z0 + 15.8;
        LevelRenderer.renderLineBox(ps, vc, x0, yMin, z0, x1, yMax, z1, r, g, b, a);
    }

    @Override
    public void clear() {
    }
}
