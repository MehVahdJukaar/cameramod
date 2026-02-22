package net.mehvahdjukaar.vista.client.renderer;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

public record RenderSystemState(
        Matrix4f projMatrix,
        Matrix4f modelViewMatrix,
        Matrix4f textureMatrix,
        Matrix4f lastSavedProj,
        VertexSorting vertexSorting,
        VertexSorting lastSavedVertexSorting,
        PoseStack modelViewStack,
        int[] shaderTextures,
        float[] shaderColor,
        float glintAlpha,
        float shaderFogStart,
        float shaderFogEnd,
        float[] shaderFogColor,
        FogShape shaderFogShape,
        Vector3f[] lights,
        float shaderGameTime,
        float lineWidth,
        ShaderInstance shader) {

    public static RenderSystemState capture() {
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f model = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f texture = new Matrix4f(RenderSystem.getTextureMatrix());
        Matrix4f lastSavedProj = new Matrix4f(RenderSystem.savedProjectionMatrix);
        VertexSorting lastVertexSorting = RenderSystem.getVertexSorting();
        VertexSorting lastSavedVertexSorting = RenderSystem.savedVertexSorting;
        PoseStack modelViewStack = RenderSystem.modelViewStack;
        int[] textures = RenderSystem.shaderTextures.clone();
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        float glintAlpha = RenderSystem.getShaderGlintAlpha();
        float shaderFogStart = RenderSystem.getShaderFogStart();
        float shaderFogEnd = RenderSystem.getShaderFogEnd();
        float[] shaderFogColor = RenderSystem.getShaderFogColor();
        FogShape shaderFogShape = RenderSystem.getShaderFogShape();
        Vector3f[] lights = RenderSystem.shaderLightDirections.clone();
        float gameTime = RenderSystem.getShaderGameTime();
        float lineWidth = RenderSystem.getShaderLineWidth();
        ShaderInstance shader = RenderSystem.getShader();
        return new RenderSystemState(proj, model, texture, lastSavedProj, lastVertexSorting, lastSavedVertexSorting,
                modelViewStack, textures, shaderColor, glintAlpha, shaderFogStart, shaderFogEnd, shaderFogColor,
                shaderFogShape, lights, gameTime, lineWidth, shader);
    }

    public void apply() {
        if (modelViewMatrix != null) RenderSystem.modelViewStack = (modelViewStack);
        RenderSystem.setProjectionMatrix(projMatrix, vertexSorting);
        RenderSystem.getModelViewMatrix().set(modelViewMatrix);
        RenderSystem.setTextureMatrix(textureMatrix);
        RenderSystem.savedProjectionMatrix = lastSavedProj;
        RenderSystem.savedVertexSorting = lastSavedVertexSorting;
        System.arraycopy(shaderTextures, 0, RenderSystem.shaderTextures, 0, shaderTextures.length);
        System.arraycopy(shaderColor, 0, RenderSystem.getShaderColor(), 0, shaderColor.length);
        RenderSystem.setShaderGlintAlpha(glintAlpha);
        RenderSystem.setShaderFogStart(shaderFogStart);
        RenderSystem.setShaderFogEnd(shaderFogEnd);
        System.arraycopy(shaderFogColor, 0, RenderSystem.getShaderFogColor(), 0, shaderFogColor.length);
        RenderSystem.setShaderFogShape(shaderFogShape);
        System.arraycopy(lights, 0, RenderSystem.shaderLightDirections, 0, lights.length);
        RenderSystem.shaderGameTime = shaderGameTime;
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.setShader(() -> shader);
    }
}
