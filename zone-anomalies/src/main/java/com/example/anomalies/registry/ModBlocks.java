package com.example.anomalies.registry;

import com.example.anomalies.AnomaliesMod;
import com.example.anomalies.block.AcidAnomalyBlock;
import com.example.anomalies.block.ElectricAnomalyBlock;
import com.example.anomalies.block.GravityAnomalyBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AnomaliesMod.MODID);

    /**
     * Общие свойства: неразрушимы в выживании (как бедрок), ничего не дропают и не имеют коллизии
     * (в аномалию можно «наступить»). Ломаются только в творческом режиме.
     */
    private static BlockBehaviour.Properties base(MapColor color, int light) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .noOcclusion()
                .noCollission()
                .pushReaction(PushReaction.BLOCK)
                .sound(SoundType.GLASS)
                .lightLevel(state -> light);
    }

    public static final DeferredBlock<GravityAnomalyBlock> GRAVITY_ANOMALY =
            BLOCKS.registerBlock("gravity_anomaly", GravityAnomalyBlock::new,
                    base(MapColor.SNOW, 0).lightLevel(state -> state.getValue(GravityAnomalyBlock.ACTIVE) ? 9 : 0));

    public static final DeferredBlock<ElectricAnomalyBlock> ELECTRIC_ANOMALY =
            BLOCKS.registerBlock("electric_anomaly", ElectricAnomalyBlock::new,
                    base(MapColor.COLOR_LIGHT_BLUE, 12).lightLevel(state -> state.getValue(ElectricAnomalyBlock.POWERED) ? 15 : 12));

    public static final DeferredBlock<AcidAnomalyBlock> ACID_ANOMALY =
            BLOCKS.registerBlock("acid_anomaly", AcidAnomalyBlock::new, base(MapColor.COLOR_LIGHT_GREEN, 6));

    private ModBlocks() {}
}
