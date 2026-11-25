package com.example.anomalies;

import com.example.anomalies.registry.ModBlockEntities;
import com.example.anomalies.registry.ModBlocks;
import com.example.anomalies.registry.ModCreativeTabs;
import com.example.anomalies.registry.ModEffects;
import com.example.anomalies.registry.ModItems;
import com.example.anomalies.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** Точка входа мода «Zone Anomalies» (NeoForge 1.21.1). */
@Mod(AnomaliesMod.MODID)
public class AnomaliesMod {
    public static final String MODID = "anomalies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AnomaliesMod(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, AnomalyConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, AnomalyClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
