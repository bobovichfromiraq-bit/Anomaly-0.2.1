package com.example.anomalies.block;

import com.example.anomalies.blockentity.AmbientAnomalyBlockEntity;
import com.example.anomalies.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Общая основа электрической и кислотной аномалий: клиентский тикер для звуков и частиц. */
public abstract class AmbientAnomalyBlock extends BaseEntityBlock implements AmbientAnomaly, AnomalyMarker {
    protected AmbientAnomalyBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmbientAnomalyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide) {
            return null;
        }
        BlockEntityTicker<AmbientAnomalyBlockEntity> ticker = AmbientAnomalyBlockEntity::clientTick;
        return createTickerHelper(type, ModBlockEntities.AMBIENT_ANOMALY.get(), ticker);
    }
}
