package net.mehvahdjukaar.vista.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.mehvahdjukaar.ml_classes.TileOrEntityTarget;
import net.mehvahdjukaar.moonlight.api.platform.network.ChannelHandler;
import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public record ClientBoundControlViewFinderPacket(TileOrEntityTarget target) implements Message {

    public ClientBoundControlViewFinderPacket(FriendlyByteBuf buf) {
        this(TileOrEntityTarget.read(buf));
    }

    @Override
    public void writeToBuffer(FriendlyByteBuf buf) {
        target.write(buf);
    }

    @Override
    public void handle(ChannelHandler.Context context) {
        // client world
        handleCannonControlPacket(this);
    }

    @Environment(value = EnvType.CLIENT)
    public static void handleCannonControlPacket(ClientBoundControlViewFinderPacket packet) {
        var level = Minecraft.getInstance().level;
        ViewFinderAccess access = ViewFinderAccess.find(level, packet.target());
        if (access != null) {
            ViewFinderController.startControlling(access);
        }
    }
}