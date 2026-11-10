package net.mehvahdjukaar.vista.common.picture_tape;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class PictureTapeEntries {

    private static final List<Predicate<ItemStack>> VALIDATORS = new ArrayList<>();
    private static final List<Unpacker> UNPACKERS = new ArrayList<>();

    static {
        //also catches other mods maps
        register(stack -> stack.has(DataComponents.MAP_ID));
        register(stack -> stack.is(Items.PAINTING));
        register(stack -> DecoratedPotPatterns.getPatternFromItem(stack.getItem()) != null);
    }

    public static void register(Predicate<ItemStack> validator) {
        VALIDATORS.add(validator);
    }

    /**
     * Registers a handler for items that hold several pictures inside them. (i.e. exposure stuff)
     * So that their content can be added
     */
    public static void registerUnpacker(Unpacker unpacker) {
        UNPACKERS.add(unpacker);
    }

    public static boolean isValid(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (Predicate<ItemStack> validator : VALIDATORS) {
            if (validator.test(stack)) return true;
        }
        return false;
    }

    /**
     * Takes up to maxCount pictures out of a multi-picture item, leaving the given stack untouched.
     * Null when the stack isn't a known container or nothing could be taken out of it.
     */
    @Nullable
    public static Unpacked unpack(ItemStack stack, int maxCount) {
        if (stack.isEmpty() || maxCount <= 0) return null;
        for (Unpacker unpacker : UNPACKERS) {
            Unpacked unpacked = unpacker.unpack(stack, maxCount);
            if (unpacked != null && !unpacked.pictures().isEmpty()) return unpacked;
        }
        return null;
    }

    public interface Unpacker {
        // must leave the container stack itself untouched, and return null if it doesn't handle that item
        @Nullable
        Unpacked unpack(ItemStack container, int maxCount);
    }

    /**
     * The pictures taken out of a container, plus what the container itself is left as
     */
    public record Unpacked(List<ItemStack> pictures, ItemStack remainder) {
    }
}
