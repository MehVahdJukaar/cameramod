package net.mehvahdjukaar.vista.common;

import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

public class ModLootOverrides {

    private static final Set<ResourceLocation> VALID_CHESTS = new HashSet<>();

    private static final ResourceLocation CHEST_LOOT = VistaMod.res("inject/treasure_tapes");
    private static final ResourceLocation CREEPER_LOOT = VistaMod.res("inject/creeper_tapes");
    private static final ResourceLocation ENDERMAN_LOOT = VistaMod.res("inject/enderman_disc");
    private static final ResourceLocation ELDER_GUARDIAN_LOOT = VistaMod.res("inject/elder_guardian_crystalline");
    private static final ResourceLocation CREEPER_TABLE = ResourceLocation.withDefaultNamespace("entities/creeper");
    private static final ResourceLocation ENDERMAN_TABLE = ResourceLocation.withDefaultNamespace("entities/enderman");
    private static final ResourceLocation ELDER_GUARDIAN_TABLE = ResourceLocation.withDefaultNamespace("entities/elder_guardian");

    public static void init() {
        RegHelper.addLootTableInjects(ModLootOverrides::addLootMod);
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:archaeology/trail_ruins_rare"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/bastion_hoglin_stable"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/igloo_chest"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/jungle_temple"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/woodland_mansion"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/stronghold_library"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/shipwreck_supply"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/simple_dungeon"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/nether_bridge"));
        VALID_CHESTS.add(ResourceLocation.parse("minecraft:chests/trial_chambers/reward_ominous_rare"));
    }

    private static void addLootMod(RegHelper.LootInjectEvent event) {
        ResourceLocation key = event.getTable();
        if (CommonConfigs.CHEST_DROP.get() && VALID_CHESTS.contains(key)) {
            event.addTableReference(CHEST_LOOT);
        }
        if (CommonConfigs.CREEPER_DROP.get() && CREEPER_TABLE.equals(key)) {
            event.addTableReference(CREEPER_LOOT);
        }

        if (ENDERMAN_TABLE.equals(key)) {
            event.addTableReference(ENDERMAN_LOOT);
        }

        if (CommonConfigs.isMirrorEnabled() && ELDER_GUARDIAN_TABLE.equals(key)) {
            event.addTableReference(ELDER_GUARDIAN_LOOT);
        }
    }


}
