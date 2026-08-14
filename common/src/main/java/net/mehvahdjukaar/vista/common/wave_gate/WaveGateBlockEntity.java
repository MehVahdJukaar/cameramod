package net.mehvahdjukaar.vista.common.wave_gate;

import net.mehvahdjukaar.moonlight.api.client.IScreenProvider;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.ui.WaveGateScreen;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.client.video_source.WebUrlVideoSource;
import net.mehvahdjukaar.vista.common.broadcast.LevelBEBroadcastLocation;
import net.mehvahdjukaar.vista.common.cassette.IBroadcastSource;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class WaveGateBlockEntity extends BlockEntity implements IScreenProvider, IBroadcastSource {

    public Object ccPeripheral;

    private String url = "";
    private UUID myUUID;
    private IVideoSource videoSource = IVideoSource.EMPTY;

    public WaveGateBlockEntity(BlockPos pos, BlockState state) {
        super(VistaMod.WAVE_GATE_TILE.get(), pos, state);
        this.myUUID = UUID.randomUUID();
    }

    @Override
    public @Nullable IVideoSource getBroadcastVideo() {
        return getBlockState().getValue(WaveGateBlock.POWERED) ? IVideoSource.EMPTY : videoSource;
    }

    @Override
    public void openScreen(Level level, Player player, Direction direction, Vec3 pos) {
        WaveGateScreen.open(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        this.ensureLinked(level, LevelBEBroadcastLocation.of(this));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.myUUID = tag.getUUID("UUID");
        this.setUrl(tag.getString("url"));
        if (level != null) this.ensureLinked(level, LevelBEBroadcastLocation.of(this));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("UUID", myUUID);
        tag.putString("url", url);
    }

    @Override
    public UUID getBroadcastUUID() {
        return myUUID;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;

        if (PlatHelper.getPhysicalSide().isClient()) {
            this.videoSource = (url.isBlank() || !ClientConfigs.isSafeUrl(url)) ? IVideoSource.EMPTY :
                    new WebUrlVideoSource(url, myUUID);
        }
    }

    public boolean canBeEditedBy(Player player) {
        return CommonConfigs.isWaveGateCraftable() || player.canUseGameMasterBlocks();
    }
}
