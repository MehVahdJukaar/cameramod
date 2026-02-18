package net.mehvahdjukaar.vista.client.ui;

import net.mehvahdjukaar.moonlight.api.platform.network.ChannelHandler;
import net.mehvahdjukaar.vista.common.projector.SignalProjectorBlockEntity;
import net.mehvahdjukaar.vista.network.ModNetwork;
import net.mehvahdjukaar.vista.network.ServerBoundSyncSignalProjectorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class SignalProjectorScreen extends Screen {
    private static final Component EDIT = Component.translatable("gui.vista.signal_projector.edit");

    private EditBox editBox;
    private final SignalProjectorBlockEntity tile;

    public SignalProjectorScreen(SignalProjectorBlockEntity te) {
        super(EDIT);
        this.tile = te;
    }

    public static void open(SignalProjectorBlockEntity te) {
        Minecraft.getInstance().setScreen(new SignalProjectorScreen(te));
    }


    @Override
    public void init() {
        assert this.minecraft != null;

        String message = tile.getUrl();

        this.editBox = new EditBox(this.font, this.width / 2 - 100, this.height / 4 + 10, 200, 20, this.title) {
            protected MutableComponent createNarrationMessage() {
                return super.createNarrationMessage();
            }
        };
        this.editBox.setValue(message);
        this.editBox.setMaxLength(300);
        this.addRenderableWidget(this.editBox);
        this.setInitialFocus(this.editBox);
        this.editBox.setFocused(true);
    }

    @Override
    public void removed() {
        //update this client immediately
        String str = this.editBox.getValue();
        //  this.tile.setUrl(str); updated by packet layer
        ModNetwork.CHANNEL.sendToServer(new ServerBoundSyncSignalProjectorPacket(this.tile.getBlockPos(), str));

    }

    private void onDone() {
        this.tile.setChanged();
        this.minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        this.onDone();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (keyCode != 257 && keyCode != 335) {
            return false;
        } else {
            this.onDone();
            return true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 16777215);
    }
}