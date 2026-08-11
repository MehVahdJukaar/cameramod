package net.mehvahdjukaar.vista.integration.vampirism;

import net.mehvahdjukaar.candlelight.api.PlatformImpl;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Contract;

// A player turned vampire is still minecraft:player, so the cant_see_through_mirror /
// cant_see_through_tv entity type tags can't match it. Vampirism has no vampire mobs, only players.
public class VampirismCompat {

    // false when Vampirism isn't present
    @Contract
    @PlatformImpl
    public static boolean isVampire(Player player) {
        throw new AssertionError();
    }
}
