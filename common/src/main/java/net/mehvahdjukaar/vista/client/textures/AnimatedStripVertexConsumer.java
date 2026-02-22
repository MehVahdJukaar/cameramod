package net.mehvahdjukaar.vista.client.textures;

import com.mojang.blaze3d.vertex.VertexConsumer;

public class AnimatedStripVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final AnimationStripData stripData;
    private final int frameIndex;

    public AnimatedStripVertexConsumer(int frameIndex, AnimationStripData stripData, VertexConsumer delegate) {
        this.frameIndex = frameIndex;
        this.stripData = stripData;
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.delegate.uv(this.stripData.getU(u, frameIndex), this.stripData.getV(v, frameIndex));
        return this;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        this.delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        this.delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float normalX, float normalY, float normalZ) {
        this.delegate.normal(normalX, normalY, normalZ);
        return this;
    }

    @Override
    public void endVertex() {
        delegate .endVertex();
    }

    @Override
    public void defaultColor(int i, int j, int k, int l) {
        delegate.defaultColor(i, j, k, l);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }

}
