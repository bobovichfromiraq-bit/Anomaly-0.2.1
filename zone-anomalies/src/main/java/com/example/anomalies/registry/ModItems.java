package com.example.anomalies.registry;

import com.example.anomalies.AnomaliesMod;
import com.example.anomalies.item.AnomalyDetectorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AnomaliesMod.MODID);

    public static final DeferredItem<BlockItem> GRAVITY_ANOMALY = ITEMS.registerSimpleBlockItem(ModBlocks.GRAVITY_ANOMALY);
    public static final DeferredItem<BlockItem> ELECTRIC_ANOMALY = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_ANOMALY);
    public static final DeferredItem<BlockItem> ACID_ANOMALY = ITEMS.registerSimpleBlockItem(ModBlocks.ACID_ANOMALY);

    public static final DeferredItem<AnomalyDetectorItem> ANOMALY_DETECTOR =
            ITEMS.registerItem("anomaly_detector", AnomalyDetectorItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {}
}
