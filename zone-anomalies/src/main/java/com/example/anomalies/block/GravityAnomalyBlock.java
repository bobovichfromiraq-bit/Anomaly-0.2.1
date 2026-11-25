package com.example.anomalies.block;

import com.example.anomalies.blockentity.GravityAnomalyBlockEntity;
import com.example.anomalies.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Гравитационная аномалия («Воронка»). Сам блок невидим (пустая модель) — это вихрь из частиц.
 * В покое — лишь редкие пылинки и тихий гул. Когда в зоне кто-то (или что-то) оказывается,
 * состояние {@link #ACTIVE} включается: вихрь становится плотным и ярким, блок светится.
 * Вся механика — в {@link GravityAnomalyBlockEntity}.
 */
public class GravityAnomalyBlock extends BaseEntityBlock implements AnomalyMarker {
    public static final MapCodec<GravityAnomalyBlock> CODEC = simpleCodec(GravityAnomalyBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);

    public GravityAnomalyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected MapCodec<GravityAnomalyBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // модель пустая → блок невидим
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public float detectorPitch() {
        return 0.7F;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravityAnomalyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityTicker<GravityAnomalyBlockEntity> ticker = level.isClientSide
                ? GravityAnomalyBlockEntity::clientTick
                : GravityAnomalyBlockEntity::serverTick;
        return createTickerHelper(type, ModBlockEntities.GRAVITY_ANOMALY.get(), ticker);
    }
}
