package net.mehvahdjukaar.vista.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.common.cassette.CassetteItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class JEICompat implements IModPlugin {

    private static final ResourceLocation ID = VistaMod.res("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(VistaMod.CASSETTE.get(), CassetteSubtypeInterpreter.INSTANCE);
    }

    public enum CassetteSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
        INSTANCE;

        @Override
        public Object getSubtypeData(ItemStack stack, UidContext uidContext) {
            return CassetteItem.getCassette(stack);
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext uidContext) {
            Object component = CassetteItem.getCassette(stack);
            return component == null ? "" : component.toString();
        }
    }


}