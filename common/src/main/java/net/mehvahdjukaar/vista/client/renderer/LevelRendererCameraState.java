package net.mehvahdjukaar.vista.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class LevelRendererCameraState {

    private int lastCameraChunkX = Integer.MIN_VALUE;
    private int lastCameraChunkY = Integer.MIN_VALUE;
    private int lastCameraChunkZ = Integer.MIN_VALUE;
    private double lastCameraX = Integer.MIN_VALUE;
    private double lastCameraY = Integer.MIN_VALUE;
    private double lastCameraZ = Integer.MIN_VALUE;
    private double prevCamX = Double.MIN_VALUE;
    private double prevCamY = Double.MIN_VALUE;
    private double prevCamZ = Double.MIN_VALUE;
    private double prevCamRotX = Double.MIN_VALUE;
    private double prevCamRotY = Double.MIN_VALUE;
    @Nullable
    private ViewArea viewArea; //same as the actual one as this doesnt change actualy. unless we want to add it in the fufture to make far away cameras load
    private int lastViewDistance;
    private final AtomicReference<LevelRenderer.RenderChunkStorage> renderChunkStorage = new AtomicReference<>();;

    private LevelRendererCameraState() {
    }

    public static LevelRendererCameraState createNew() {
        var instance = new LevelRendererCameraState();
        Minecraft mc = Minecraft.getInstance();
        LevelRenderer lr = mc.levelRenderer;
        instance.renderChunkStorage.set(new LevelRenderer.RenderChunkStorage(lr.viewArea.chunks.length));

        return instance;
    }

    public void copyFrom(LevelRenderer lr) {
       // this.viewArea = lr.viewArea;
        this.lastViewDistance = lr.lastViewDistance;
        this.renderChunkStorage.set(lr.renderChunkStorage.get());
        this.lastCameraChunkX = lr.lastCameraChunkX;
        this.lastCameraChunkY = lr.lastCameraChunkY;
        this.lastCameraChunkZ = lr.lastCameraChunkZ;
        this.lastCameraX = lr.lastCameraX;
        this.lastCameraY = lr.lastCameraY;
        this.lastCameraZ = lr.lastCameraZ;
        this.prevCamX = lr.prevCamX;
        this.prevCamY = lr.prevCamY;
        this.prevCamZ = lr.prevCamZ;
        this.prevCamRotX = lr.prevCamRotX;
        this.prevCamRotY = lr.prevCamRotY;
    }

    public static LevelRendererCameraState capture(LevelRenderer lr) {
        var instance = new LevelRendererCameraState();
        instance.copyFrom(lr);
        return instance;
    }

    public void apply(LevelRenderer lr) {
      //  lr.viewArea = this.viewArea;
        lr.renderChunkStorage.set(this.renderChunkStorage.get());
        lr.lastViewDistance = this.lastViewDistance;
        lr.lastCameraChunkX = this.lastCameraChunkX;
        lr.lastCameraChunkY = this.lastCameraChunkY;
        lr.lastCameraChunkZ = this.lastCameraChunkZ;
        lr.lastCameraX = this.lastCameraX;
        lr.lastCameraY = this.lastCameraY;
        lr.lastCameraZ = this.lastCameraZ;
        lr.prevCamX = this.prevCamX;
        lr.prevCamY = this.prevCamY;
        lr.prevCamZ = this.prevCamZ;
        lr.prevCamRotX = this.prevCamRotX;
        lr.prevCamRotY = this.prevCamRotY;
    }

    public LevelRenderer.RenderChunkStorage getChunkStorage () {
        return this.renderChunkStorage.get();
    }
}
