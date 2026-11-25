package com.example.anomalies.registry;

import com.example.anomalies.AnomaliesMod;
import com.example.anomalies.blockentity.AmbientAnomalyBlockEntity;
import com.example.anomalies.blockentity.GravityAnomalyBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AnomaliesMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravityAnomalyBlockEntity>> GRAVITY_ANOMALY =
            BLOCK_ENTITIES.register("gravity_anomaly", () -> BlockEntityType.Builder
                    .of(GravityAnomalyBlockEntity::new, ModBlocks.GRAVITY_ANOMALY.get())
                    .build(null));

    /** Клиентский «оживитель» электрической и кислотной аномалий: фоновые звуки и частицы. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmbientAnomalyBlockEntity>> AMBIENT_ANOMALY =
            BLOCK_ENTITIES.register("ambient_anomaly", () -> BlockEntityType.Builder
                    .of(AmbientAnomalyBlockEntity::new, ModBlocks.ELECTRIC_ANOMALY.get(), ModBlocks.ACID_ANOMALY.get())
                    .build(null));

    private ModBlockEntities() {}
}
