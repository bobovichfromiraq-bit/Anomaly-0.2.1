package com.example.anomalies.registry;

import com.example.anomalies.AnomaliesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AnomaliesMod.MODID);

    /** Единственное место, где можно взять сами аномалии (рецептов нет). Детектор ещё и крафтится. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ANOMALIES = TABS.register("anomalies",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.anomalies"))
                    .icon(() -> new ItemStack(ModItems.ELECTRIC_ANOMALY.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.ANOMALY_DETECTOR.get());
                        output.accept(ModItems.GRAVITY_ANOMALY.get());
                        output.accept(ModItems.ELECTRIC_ANOMALY.get());
                        output.accept(ModItems.ACID_ANOMALY.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
