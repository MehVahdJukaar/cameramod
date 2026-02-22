package net.mehvahdjukaar.vista.integration.computer_craft;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.mehvahdjukaar.moonlight.api.misc.RegSupplier;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Contract;

public class CCCompat {

    @Contract
    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @Contract
    @ExpectPlatform
    public static void setup() {
        throw new AssertionError();
    }


}
