package net.mehvahdjukaar.vista.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class LevelRendererCameraState {

    private int lastCameraSectionX = Integer.MIN_VALUE;
    private int lastCameraSectionY = Integer.MIN_VALUE;
    private int lastCameraSectionZ = Integer.MIN_VALUE;
    private double prevCamX = Double.MIN_VALUE;
    private double prevCamY = Double.MIN_VALUE;
    private double prevCamZ = Double.MIN_VALUE;
    private double prevCamRotX = Double.MIN_VALUE;
    private double prevCamRotY = Double.MIN_VALUE;
    @Nullable
    private ViewArea viewArea; //same as the actual one as this doesnt change actualy. unless we want to add it in the fufture to make far away cameras load
    private int lastViewDistance;
    private AtomicReference<LevelRenderer.RenderChunkStorage> renderChunkStorage;

    private LevelRendererCameraState() {
    }

    public static LevelRendererCameraState createNew() {
        var instance = new LevelRendererCameraState();
        instance.sectionOcclusionGraph = new SectionOcclusionGraph();
        Minecraft mc = Minecraft.getInstance();
        LevelRenderer lr = mc.levelRenderer;
     //   instance.viewArea = new ViewArea(lr.sectionRenderDispatcher, mc.level,
                //TODO: change this
      //          mc.options.getEffectiveRenderDistance(), lr);
        instance.sectionOcclusionGraph.waitAndReset(instance.viewArea);
        return instance;
    }

    public void copyFrom(LevelRenderer lr) {
       // this.viewArea = lr.viewArea;
        this.lastViewDistance = lr.lastViewDistance;
        this.sectionOcclusionGraph = lr.sectionOcclusionGraph;
        this.lastCameraSectionX = lr.lastCameraSectionX;
        this.lastCameraSectionY = lr.lastCameraSectionY;
        this.lastCameraSectionZ = lr.lastCameraSectionZ;
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
        lr.sectionOcclusionGraph = this.sectionOcclusionGraph;
        lr.lastViewDistance = this.lastViewDistance;
        lr.lastCameraSectionX = this.lastCameraSectionX;
        lr.lastCameraSectionY = this.lastCameraSectionY;
        lr.lastCameraSectionZ = this.lastCameraSectionZ;
        lr.prevCamX = this.prevCamX;
        lr.prevCamY = this.prevCamY;
        lr.prevCamZ = this.prevCamZ;
        lr.prevCamRotX = this.prevCamRotX;
        lr.prevCamRotY = this.prevCamRotY;
    }

    public SectionOcclusionGraph getOcclusionGraph() {
        return sectionOcclusionGraph;
    }

}
