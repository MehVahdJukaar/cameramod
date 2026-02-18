package net.mehvahdjukaar.vista.common.cassette;

import com.mojang.serialization.MapCodec;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;

public class CassetteTapeLootFunction implements LootItemFunction {

    public static final MapCodec<CassetteTapeLootFunction> CODEC = MapCodec.unit((new CassetteTapeLootFunction()));

    @Override
    public LootItemFunctionType getType() {
        return VistaMod.CASSETTE_TAPE_LOOT_FUNCTION.get();
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {

        Level level = context.getLevel();
        var holders = level.registryAccess().lookupOrThrow(VistaMod.CASSETTE_TAPE_REGISTRY_KEY)
                .listElements()
                .filter(h -> !h.is(VistaMod.SUPPORTER_TAPES_TAG))
                .toList();
        if (!holders.isEmpty()) {
            int index = level.random.nextInt(holders.size());
            stack.set(VistaMod.CASSETTE_TAPE_COMPONENT.get(), holders.get(index));
        }

        return stack;
    }
}
