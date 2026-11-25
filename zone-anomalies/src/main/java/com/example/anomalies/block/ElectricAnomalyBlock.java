package com.example.anomalies.block;

import com.example.anomalies.AnomalyConfig;
import com.example.anomalies.blockentity.AmbientAnomalyBlockEntity;
import com.example.anomalies.client.vfx.ElectricArcs;
import com.example.anomalies.registry.ModDamageTypes;
import com.example.anomalies.registry.ModEffects;
import com.example.anomalies.registry.ModSounds;
import com.example.anomalies.util.AnomalyUtil;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Электрическая аномалия («Электра»): парящий плазменный шар с молниями во все стороны.
 * Наступил — 3 сердца урона + замедление + слепота на 5 секунд. Перезарядка 2 с на каждую жертву.
 * Каждый разряд ещё и на 0,6 с даёт сигнал редстоуна 15 (аномалия «глушит» электронику) — можно строить ловушки.
 */
public class ElectricAnomalyBlock extends AmbientAnomalyBlock {
    public static final MapCodec<ElectricAnomalyBlock> CODEC = simpleCodec(ElectricAnomalyBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
    private static final String COOLDOWN_KEY = "AnomaliesElectricCooldown";
    private static final int COOLDOWN_TICKS = 40;
    private static final int POWER_TICKS = 12;

    public ElectricAnomalyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected MapCodec<ElectricAnomalyBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public float detectorPitch() {
        return 1.25F;
    }

    // ------------------------------------------------------------ редстоун
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
        }
    }

    /** Короткий импульс редстоуна после разряда. */
    private void pulse(ServerLevel server, BlockPos pos) {
        if (!AnomalyConfig.ELECTRIC_REDSTONE.get()) {
            return;
        }
        BlockState current = server.getBlockState(pos);
        if (!current.is(this)) {
            return;
        }
        if (!current.getValue(POWERED)) {
            server.setBlock(pos, current.setValue(POWERED, true), Block.UPDATE_ALL);
            server.updateNeighborsAt(pos, this);
        }
        server.scheduleTick(pos, this, POWER_TICKS);
    }

    // ------------------------------------------------------------ разряд
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        long now = level.getGameTime();

        if (entity instanceof ItemEntity item) { // брошенный предмет тоже вызывает разряд
            if (AnomalyUtil.tryCooldown(item, COOLDOWN_KEY, now, COOLDOWN_TICKS)) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.2, item.getZ(), 25, 0.2, 0.2, 0.2, 0.5);
                level.playSound(null, pos, ModSounds.ELECTRIC_ZAP.get(), SoundSource.BLOCKS, 0.7F, 1.4F);
                item.setDeltaMovement(item.getDeltaMovement().add(0.0, 0.3, 0.0));
                item.hurtMarked = true;
                pulse(server, pos);
            }
            return;
        }
        if (!(entity instanceof LivingEntity living) || !AnomalyUtil.canAffect(living)) {
            return;
        }
        if (!AnomalyUtil.tryCooldown(entity, COOLDOWN_KEY, now, COOLDOWN_TICKS)) {
            return;
        }

        int duration = AnomalyConfig.EFFECT_TICKS.get();
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1));
        living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 0));
        living.addEffect(new MobEffectInstance(ModEffects.ELECTRO_SHOCK, 14, 0, false, false, false));
        living.hurt(level.damageSources().source(ModDamageTypes.ELECTRIC_DISCHARGE),
                AnomalyConfig.ELECTRIC_DAMAGE.get().floatValue());

        double mid = entity.getY() + entity.getBbHeight() * 0.5;
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), mid, entity.getZ(),
                50, 0.4, entity.getBbHeight() * 0.45, 0.4, 0.7);
        server.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 0.2, entity.getZ(), 14, 0.3, 0.1, 0.3, 0.3);
        level.playSound(null, pos, ModSounds.ELECTRIC_ZAP.get(), SoundSource.BLOCKS, 1.6F, 0.9F + level.random.nextFloat() * 0.2F);
        pulse(server, pos);
    }

    // ------------------------------------------------------------ КЛИЕНТ
    /** Звук-петля, искры в точках удара молний, вспышка при разряде (сами молнии рисует рендерер). */
    @Override
    public void ambientTick(Level level, BlockPos pos, BlockState state, int time) {
        RandomSource random = level.random;
        boolean powered = state.getValue(POWERED);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.9, cz = pos.getZ() + 0.5;

        if (time % 36 == 0) { // постоянный треск (звук 1,9 c, повтор каждые 1,8 c — без паузы)
            level.playLocalSound(cx, cy, cz, ModSounds.ELECTRIC_CRACKLE.get(), SoundSource.BLOCKS, 0.9F, 1.0F, false);
        }
        if (time % ElectricArcs.PERIOD == 0 && level.getBlockEntity(pos) instanceof AmbientAnomalyBlockEntity be) {
            for (ElectricArcs.Arc arc : be.arcs(time / ElectricArcs.PERIOD, powered ? 8 : 0)) {
                if (arc.grounded() && !arc.branch() && random.nextInt(2) == 0) {
                    var end = arc.end();
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + end.x, pos.getY() + end.y, pos.getZ() + end.z,
                            (random.nextDouble() - 0.5) * 0.5, 0.15 + random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.5);
                }
            }
        }
        if (powered && time % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz,
                        (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2);
            }
        }
        // шар «чувствует» приближение: рядом звук громче и выше
        if (random.nextInt(80) == 0) {
            var near = level.getNearestPlayer(cx, cy, cz, 6.0, false);
            level.playLocalSound(cx, cy, cz, ModSounds.ELECTRIC_IDLE.get(), SoundSource.BLOCKS,
                    near != null ? 1.1F : 0.8F, near != null ? 1.2F : 0.8F + random.nextFloat() * 0.4F, false);
        }
    }
}
