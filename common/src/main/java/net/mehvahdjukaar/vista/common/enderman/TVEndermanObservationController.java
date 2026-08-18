package net.mehvahdjukaar.vista.common.enderman;

import com.mojang.authlib.GameProfile;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.vista.client.renderer.ViewFinderBlockEntityRenderer;
import net.mehvahdjukaar.vista.common.broadcast.BroadcastManager;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class TVEndermanObservationController extends AbstractEndermanObservationController {

    private static final float PLAYERS_TO_TV_DIST = 20;
    private static final float ENDERMEN_TO_CAMERA_DIST = 20;
    private static final GameProfile VIEW_FINDER_PLAYER = new GameProfile(
            UUID.fromString("33242C44-27d9-1f22-3d27-99D2C45d1378"),
            "[VIEW_FINDER_ENDERMAN_PLAYER]");

    private final UUID broadcastUUID;
    private final TVBlockEntity myTv;

    public TVEndermanObservationController(@NotNull UUID broadcastUUID, TVBlockEntity tv) {
        this.broadcastUUID = broadcastUUID;
        this.myTv = tv;
    }

    @Override
    protected Level level() {
        return myTv.getLevel();
    }

    @Override
    protected GameProfile fakePlayerProfile() {
        return VIEW_FINDER_PLAYER;
    }

    @Override
    protected float playersScreenDist() {
        return PLAYERS_TO_TV_DIST;
    }

    @Override
    protected float endermenSearchDist() {
        return ENDERMEN_TO_CAMERA_DIST;
    }

    @Override
    public boolean isInvalid() {
        return myTv.isRemoved();
    }

    @Nullable
    @Override
    protected TickContext openTick() {
        Level level = level();
        BroadcastManager manager = BroadcastManager.getInstance(level);
        if (!(manager.getBroadcast(broadcastUUID, level.isClientSide) instanceof ViewFinderBlockEntity vf)) {
            return null;
        }
        // Endermen are searched around the *remote* ViewFinder, not the TV.
        return new TickContext(myTv.getScreenRect(), vf.getBlockPos(),
                (fp, hit) -> orientAtViewFinder(vf, fp, hit));
    }

    private static boolean orientAtViewFinder(ViewFinderBlockEntity vf, Player fakePlayer, ScreenSpectatorView hit) {
        Vec3 lensFacing = new Vec3(vf.getGlobalFacing(1));
        Vec3 lensCenter = Vec3.atCenterOf(vf.getBlockPos());
        float eyeH = fakePlayer.getEyeHeight();

        if (PlatHelper.getPhysicalSide().isClient() && ClientConfigs.rendersDebug()) {
            ViewFinderBlockEntityRenderer.debugLastPlayer = new WeakReference<>(fakePlayer);
        }

        Vec3 look = vf.getPixelRayDirection(hit.localHit().x, hit.localHit().y);
        float yRot = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
        float xRot = (float) -Math.toDegrees(Math.atan2(look.y, horiz));

        Vec3 t = lensCenter.add(lensFacing.scale(-ViewFinderBlockEntity.NEAR_PLANE));
        fakePlayer.setPos(t.x, t.y - eyeH, t.z);
        fakePlayer.setYRot(yRot);
        fakePlayer.setYHeadRot(yRot);
        fakePlayer.setXRot(xRot);

        // move forward to skip our own BB since it cant be empty due to particles
        float offset = 0.8f;
        Vec3 forward = lensFacing.normalize();
        fakePlayer.setPos(
                fakePlayer.getX() + forward.x * offset,
                fakePlayer.getY() + forward.y * offset,
                fakePlayer.getZ() + forward.z * offset
        );
        return true;
    }
}
