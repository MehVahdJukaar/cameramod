package net.mehvahdjukaar.vista.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class ViewFinderHud {

    private static final ResourceLocation BAR_SPRITE = VistaMod.res("textures/gui/sprites/hud/bar.png");
    private static final ResourceLocation INDICATOR_SPRITE = VistaMod.res("textures/gui/sprites/hud/indicator.png");
    private static final ResourceLocation LOCKED_INDICATOR_SPRITE = VistaMod.res("textures/gui/sprites/hud/lock.png");
    private static final ResourceLocation OVERLAY = VistaMod.res("textures/gui/viewfinder_scope.png");

    protected final Minecraft mc;

    private float scopeScale;

    public ViewFinderHud() {
        this.mc = Minecraft.getInstance();
    }

    public void render(GuiGraphics graphics, float deltaTicks) {
        if (mc.options.hideGui) return;

        this.scopeScale = Mth.lerp(0.5F * deltaTicks, this.scopeScale, 1.125F);

        if (ViewFinderController.isActive()) {

            ViewFinderBlockEntity tile = ViewFinderController.getAccess().getInternalTile();

            setupOverlayRenderState();
            int screenWidth = graphics.guiWidth();
            int screenHeight = graphics.guiHeight();

            renderSpyglassOverlay(graphics, this.scopeScale);
            renderBar(graphics, screenWidth, screenHeight, tile.getZoomLevel(), deltaTicks);
        }
    }

    public void setupOverlayRenderState() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }

    private void blitSprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int width, int height) {
        g.blit(sprite, x, y, 0, 0, width, height, width, height);
    }

    private void renderBar(GuiGraphics graphics, int screenWidth, int screenHeight, int zoomLevel, float partialTicks) {
        int xpBarLeft = screenWidth / 2 - 91;

        int xpBarTop = screenHeight - 32 + 3;
        blitSprite(graphics, BAR_SPRITE, xpBarLeft + 1, xpBarTop - 1 + 4, 180, 8);

        int zoomOffset = (zoomLevel - 1) * 4;

        if (ViewFinderController.isLocked()) {
            blitSprite(graphics, LOCKED_INDICATOR_SPRITE, xpBarLeft + zoomOffset, xpBarTop + 1, 9, 11);
        } else {
            blitSprite(graphics, INDICATOR_SPRITE, xpBarLeft + zoomOffset, xpBarTop + 5, 11, 7);
        }

        int color = 0xff8800;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        String s = String.valueOf(zoomLevel);
        int i1 = (screenWidth - mc.font.width(s)) / 2;
        int j1 = screenHeight - 31 - 4;
        graphics.drawString(mc.font, s, i1 + 1, j1, 0, false);
        graphics.drawString(mc.font, s, i1 - 1, j1, 0, false);
        graphics.drawString(mc.font, s, i1, j1 + 1, 0, false);
        graphics.drawString(mc.font, s, i1, j1 - 1, 0, false);
        graphics.drawString(mc.font, s, i1, j1, color, false);
    }


    private void renderSpyglassOverlay(GuiGraphics guiGraphics, float scopeScale) {
        float f = (float) Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        float scaleThing = Math.min((float) guiGraphics.guiWidth() / f, (float) guiGraphics.guiHeight() / f) * scopeScale;
        int i = Mth.floor(f * scaleThing);

        int j = Mth.floor(f * scaleThing);
        int k = (guiGraphics.guiWidth() - i) / 2;
        int l = (guiGraphics.guiHeight() - j) / 2;
        int i1 = k + i;
        int j1 = l + j;

        RenderSystem.enableBlend();
        guiGraphics.blit(OVERLAY, k, l, -90, 0.0F, 0.0F, i, j, i, j);
        RenderSystem.disableBlend();
        guiGraphics.fill(RenderType.guiOverlay(), 0, j1, guiGraphics.guiWidth(), guiGraphics.guiHeight(), -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiGraphics.guiWidth(), l, -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), 0, l, k, j1, -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), i1, l, guiGraphics.guiWidth(), j1, -90, -16777216);
    }

}