package net.mehvahdjukaar.vista.common.tv.enderman;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class EndermanFreezeWhenLookedAtThroughTVGoal extends Goal {
    private final EnderMan enderman;
    @Nullable
    private Player target;

    private TVEndermanObservationController tvAccess;

    public EndermanFreezeWhenLookedAtThroughTVGoal(EnderMan enderman) {
        this.enderman = enderman;
        this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE, Flag.LOOK));
    }


    private void prime(Player player, TVEndermanObservationController tv) {
        this.target = player;
        this.tvAccess = tv;
        this.enderman.setBeingStaredAt();
        this.enderman.setTarget(player);
    }


    @Override
    public boolean canUse() {
        if (tvAccess == null) {
            return false;
        }
        if (tvAccess.isInvalid()) {
            return false;
        }
        LivingEntity t = this.enderman.getTarget();
        if (t instanceof Player p) {
            this.target = p;
            return true;
        }
        return false;
    }

    private boolean isCameraViewValid() {
        return tvAccess.isPlayerLookingAtEnderman(enderman, target);

    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() && isCameraViewValid();
    }

    @Override
    public void start() {
        ((ITVAngeredEnderman) this.enderman).vista$setAngry(true);
        this.enderman.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.enderman.getLookControl().setLookAt(this.target.getX(), this.target.getEyeY(), this.target.getZ());
    }

    @Override
    public void stop() {
        super.stop();
        this.tvAccess = null;
    }

    public static boolean anger(EnderMan man, Player player, TVEndermanObservationController television) {
        EndermanFreezeWhenLookedAtThroughTVGoal goal = findGoal(man);
        if (goal != null) {
            goal.prime(player, television);
            return true;
        }
        return false;
    }


    @Nullable
    private static EndermanFreezeWhenLookedAtThroughTVGoal findGoal(EnderMan man) {
        for (var goal : man.goalSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof EndermanFreezeWhenLookedAtThroughTVGoal g) {
                return g;
            }
        }
        return null;
    }
}
