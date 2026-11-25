package com.example.anomalies.block;

import com.example.anomalies.AnomalyConfig;
import com.example.anomalies.client.vfx.AcidBubbles;
import com.example.anomalies.registry.ModEffects;
import com.example.anomalies.registry.ModSounds;
import com.example.anomalies.util.AnomalyUtil;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/**
 * Кислотная аномалия («Химическая»): цельный блок жижи высотой 14/16, соседние блоки сливаются в лужу.
 * <ul>
 *   <li>В жиже: отравление + замедление + иссушение + зелёный экран, броня медленно разъедается.</li>
 *   <li>Над жижей — ядовитый воздух: если дышать им около 3 секунд, начинается тошнота.</li>
 * </ul>
 */
public class AcidAnomalyBlock extends AmbientAnomalyBlock {
    public static final MapCodec<AcidAnomalyBlock> CODEC = simpleCodec(AcidAnomalyBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);
    private static final DustParticleOptions MIASMA = new DustParticleOptions(new Vector3f(0.55F, 0.85F, 0.20F), 1.5F);
    private static final String COOLDOWN_KEY = "AnomaliesAcidCooldown";
    private static final String ARMOR_KEY = "AnomaliesAcidArmor";
    private static final String HAZE_LAST = "AnomaliesHazeLast";
    private static final String HAZE_EXPOSURE = "AnomaliesHazeExposure";
    private static final int HAZE_INTERVAL = 10;

    public AcidAnomalyBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<AcidAnomalyBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Грани между соседними блоками кислоты не рисуем — лужа выглядит цельной. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return adjacentState.is(this) || super.skipRendering(state, adjacentState, direction);
    }

    @Override
    public float detectorPitch() {
        return 0.95F;
    }

    // ------------------------------------------------------------ ядовитый воздух
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, HAZE_INTERVAL);
        }
    }

    /** Случайные тики «лечат» блоки, у которых по какой-то причине пропал запланированный тик. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, HAZE_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, HAZE_INTERVAL);
        if (!AnomalyConfig.ACID_HAZE.get()) {
            return;
        }
        AABB haze = new AABB(pos.getX(), pos.getY() + 0.875, pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.3, pos.getZ() + 1.0);
        long now = level.getGameTime();
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, haze, AnomalyUtil::canAffect)) {
            CompoundTag data = living.getPersistentData();
            int exposure = now - data.getLong(HAZE_LAST) > 30L ? 0 : data.getInt(HAZE_EXPOSURE);
            exposure += HAZE_INTERVAL;
            data.putLong(HAZE_LAST, now);
            data.putInt(HAZE_EXPOSURE, exposure);
            if (exposure >= 60) { // ~3 секунды вдыхания
                AnomalyUtil.applyOrRefresh(living, MobEffects.CONFUSION, 200, 0);
                level.sendParticles(ParticleTypes.SNEEZE, living.getX(), living.getEyeY() - 0.2, living.getZ(), 2, 0.2, 0.1, 0.2, 0.02);
            }
        }
    }

    // ------------------------------------------------------------ контакт с жижей
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (entity instanceof ItemEntity item) { // брошенный предмет шипит в кислоте
            if (AnomalyUtil.tryCooldown(item, COOLDOWN_KEY, level.getGameTime(), 60)) {
                server.sendParticles(ParticleTypes.ITEM_SLIME, item.getX(), item.getY() + 0.2, item.getZ(), 10, 0.15, 0.1, 0.15, 0.1);
                level.playSound(null, pos, ModSounds.ACID_SIZZLE.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
            }
            return;
        }
        if (!(entity instanceof LivingEntity living) || !AnomalyUtil.canAffect(living)) {
            return;
        }
        int duration = AnomalyConfig.EFFECT_TICKS.get();
        boolean freshContact = !living.hasEffect(ModEffects.ACID_COVER);

        AnomalyUtil.applyOrRefresh(living, MobEffects.POISON, duration, 0);
        AnomalyUtil.applyOrRefresh(living, MobEffects.MOVEMENT_SLOWDOWN, duration, 1);
        AnomalyUtil.applyOrRefresh(living, MobEffects.WITHER, duration, 0);
        AnomalyUtil.applyOrRefresh(living, ModEffects.ACID_COVER, duration, 0);

        if (AnomalyUtil.tryCooldown(entity, ARMOR_KEY, level.getGameTime(), 40)) { // кислота разъедает броню
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = living.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.isDamageableItem()) {
                    stack.hurtAndBreak(1, living, slot);
                }
            }
        }
        if (freshContact) {
            server.sendParticles(ParticleTypes.ITEM_SLIME, entity.getX(), entity.getY() + 0.3, entity.getZ(), 20, 0.3, 0.2, 0.3, 0.15);
            level.playSound(null, pos, ModSounds.ACID_SIZZLE.get(), SoundSource.BLOCKS, 1.2F, 0.9F + level.random.nextFloat() * 0.2F);
        }
    }

    // ------------------------------------------------------------ КЛИЕНТ
    /** Звук-петля, брызги от лопнувших пузырей и лёгкий ядовитый дымок над поверхностью. */
    @Override
    public void ambientTick(Level level, BlockPos pos, BlockState state, int time) {
        RandomSource random = level.random;
        double x = pos.getX(), z = pos.getZ(), top = pos.getY() + AcidBubbles.SURFACE;

        if (time % 40 == 0) { // постоянное бульканье (звук 2,1 c, повтор каждые 2 c — без паузы)
            level.playLocalSound(x + 0.5, top, z + 0.5, ModSounds.ACID_LOOP.get(), SoundSource.BLOCKS, 0.8F, 1.0F, false);
        }
        for (int i = 0; i < AcidBubbles.COUNT; i++) {
            int shifted = AcidBubbles.shifted(time, i);
            if (shifted % AcidBubbles.CYCLE == AcidBubbles.POP_TICK) { // пузырь лопнул
                var p = AcidBubbles.position(pos, i, shifted / AcidBubbles.CYCLE);
                level.addParticle(ParticleTypes.BUBBLE_POP, x + p.x, top + 0.18, z + p.z, 0.0, 0.05, 0.0);
                for (int k = 0; k < 3; k++) {
                    level.addParticle(ParticleTypes.ITEM_SLIME, x + p.x, top + 0.18, z + p.z,
                            (random.nextDouble() - 0.5) * 0.1, 0.12 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.1);
                }
            }
        }
        if (random.nextInt(3) == 0) { // небольшой ядовитый дымок, медленно поднимающийся от поверхности
            level.addParticle(MIASMA, x + 0.1 + random.nextDouble() * 0.8, top + 0.05, z + 0.1 + random.nextDouble() * 0.8,
                    (random.nextDouble() - 0.5) * 0.012, 0.012 + random.nextDouble() * 0.012, (random.nextDouble() - 0.5) * 0.012);
        }
        if (random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.SNEEZE, x + 0.2 + random.nextDouble() * 0.6, top + 0.1, z + 0.2 + random.nextDouble() * 0.6, 0.0, 0.03, 0.0);
        }
        if (random.nextInt(25) == 0) {
            level.playLocalSound(x + 0.5, top, z + 0.5, ModSounds.ACID_BUBBLE.get(), SoundSource.BLOCKS, 0.9F, 0.8F + random.nextFloat() * 0.4F, false);
        }
    }
}
