package net.mehvahdjukaar.vista.integration.vampirism;

import net.mehvahdjukaar.candlelight.api.PlatformImpl;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Contract;

public class VampirismCompat {

    @Contract
    @PlatformImpl
    public static boolean isVampire(Player player) {
        throw new AssertionError();
    }
}
