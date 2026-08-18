package net.mehvahdjukaar.vista.common.enderman;

import com.mojang.authlib.GameProfile;
import net.mehvahdjukaar.moonlight.api.util.FakePlayerManager;
import net.mehvahdjukaar.vista.common.ScreenRect;
import net.mehvahdjukaar.vista.common.view_finder.EndermanLookResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractEndermanObservationController {

    protected record ScreenSpectatorView(Player player, Vec2 localHit, double distance) {
    }

    @FunctionalInterface
    protected interface FakePlayerOrienter {
        /**
         * Position + orient the fake player so that EnderMan.isLookingAtMe answers
         * "is this enderman in the path of the world ray emerging from the given hit".
         * Return false to skip this hit.
         */
        boolean orient(Player fakePlayer, ScreenSpectatorView hit);
    }

    protected record TickContext(ScreenRect screenBasis, BlockPos endermenAnchor, FakePlayerOrienter orient) {
    }

    protected abstract Level level();

    protected abstract GameProfile fakePlayerProfile();

    protected abstract float playersScreenDist();

    protected abstract float endermenSearchDist();

    public abstract boolean isInvalid();

    @Nullable
    protected abstract TickContext openTick();

    // called from EndermanFreezeWhenLookedAtThroughTVGoal.canContinueToUse()
    public boolean isPlayerLookingAtEnderman(EnderMan enderMan, Player player) {
        TickContext ctx = openTick();
        if (ctx == null) return false;
        ScreenSpectatorView view = getPlayerHit(player, ctx.screenBasis(), playersScreenDist());
        if (view == null) return false;
        Player fakePlayer = FakePlayerManager.get(fakePlayerProfile(), level());
        return !checkEndermenLookedAt(List.of(view), List.of(enderMan), fakePlayer, ctx.orient()).isEmpty();
    }

    public boolean tick() {
        TickContext ctx = openTick();
        if (ctx == null) return false;
        Level level = level();

        List<ScreenSpectatorView> views = findPlayersLookingAtScreen(level.players(), ctx.screenBasis(), playersScreenDist());
        if (views.isEmpty()) return false;

        List<EnderMan> enderMen = findEndermenNear(level, ctx.endermenAnchor(), endermenSearchDist());
        if (enderMen.isEmpty()) return false;

        Player fakePlayer = FakePlayerManager.get(fakePlayerProfile(), level);
        List<EndermanLookResult> looks = checkEndermenLookedAt(views, enderMen, fakePlayer, ctx.orient());

        boolean anyAnger = false;
        for (var r : looks) {
            if (EndermanFreezeWhenLookedAtThroughTVGoal.anger(r.enderman(), r.player(), this)) anyAnger = true;
        }
        return anyAnger;
    }

    protected static List<ScreenSpectatorView> findPlayersLookingAtScreen(Collection<? extends Player> players,
                                                                          ScreenRect sb, float maxDist) {
        if (players.isEmpty()) return List.of();
        List<ScreenSpectatorView> result = new ArrayList<>();
        for (Player p : players) {
            if (p.isCreative()) continue;
            ScreenSpectatorView vr = getPlayerHit(p, sb, maxDist);
            if (vr != null) result.add(vr);
        }
        return result;
    }

    protected static List<EnderMan> findEndermenNear(Level level, BlockPos anchor, float range) {
        Vec3 center = Vec3.atCenterOf(anchor);
        double rangeSq = (double) range * range;
        AABB aabb = new AABB(anchor).inflate(range);
        return level.getEntitiesOfClass(EnderMan.class, aabb,
                em -> em.distanceToSqr(center.x, center.y, center.z) < rangeSq);
    }

    protected static List<EndermanLookResult> checkEndermenLookedAt(List<ScreenSpectatorView> views,
                                                                    List<EnderMan> endermen,
                                                                    Player fakePlayer,
                                                                    FakePlayerOrienter orient) {
        List<EndermanLookResult> results = new ArrayList<>();
        for (ScreenSpectatorView vr : views) {
            if (!orient.orient(fakePlayer, vr)) continue;
            for (EnderMan man : endermen) {
                if (man.isLookingAtMe(fakePlayer)) {
                    results.add(new EndermanLookResult(vr.player(), man));
                }
            }
        }
        return results;
    }

    // where the player is looking on the screen, null if they aren't looking at it
    @Nullable
    protected static ScreenSpectatorView getPlayerHit(Player player, ScreenRect sb, float maxDist) {
        final double EPS = 1e-6;
        Vec3 eyePos = player.getEyePosition(1.0F);

        Vec3 eyeToCenter = sb.center().subtract(eyePos);
        double distSq = eyeToCenter.lengthSqr();
        if (distSq > (maxDist * maxDist)) return null;
        eyeToCenter = eyeToCenter.scale(1.0 / Math.sqrt(distSq));

        if (eyeToCenter.dot(sb.normal()) > 0.0) return null;

        Vec3 playerView = player.getViewVector(1.0F).normalize();
        double denom = playerView.dot(sb.normal());
        if (Math.abs(denom) < EPS) return null;
        double t = sb.center().subtract(eyePos).dot(sb.normal()) / denom;
        if (t <= 0.0) return null;

        Vec3 hit = eyePos.add(playerView.scale(t));
        Vec2 local = sb.projectLocal(hit);
        if (local == null) return null;
        return new ScreenSpectatorView(player, local, t);
    }
}
