package com.example.anomalies.blockentity;

import com.example.anomalies.AnomalyConfig;
import com.example.anomalies.block.GravityAnomalyBlock;
import com.example.anomalies.client.ClientAnomalies;
import com.example.anomalies.registry.ModBlockEntities;
import com.example.anomalies.registry.ModDamageTypes;
import com.example.anomalies.registry.ModEffects;
import com.example.anomalies.registry.ModSounds;
import com.example.anomalies.util.AnomalyUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Логика гравитационной аномалии.
 * <ol>
 *   <li><b>Покой</b> — едва заметный вихрь, тихий гул; сервер проверяет зону раз в 4 тика.</li>
 *   <li><b>Пробуждение</b> — кто-то (или что-то) вошёл в радиус: состояние ACTIVE, вихрь вспыхивает.</li>
 *   <li><b>Притяжение</b> — тянет к центру живых существ, а также предметы, шарики опыта, снаряды и падающие блоки.</li>
 *   <li><b>Захват → подъём → разрыв</b> — для живых: спираль, треск костей, мгновенная смерть (в обход брони и тотема).</li>
 * </ol>
 */
public class GravityAnomalyBlockEntity extends BlockEntity {
    private static final double GRAB_RADIUS = 1.6;
    private static final double LIFT_HEIGHT = 4.5;
    private static final int IDLE_TICKS_BEFORE_SLEEP = 40;

    /** UUID жертвы → сколько тиков она уже в захвате (не сохраняется). */
    private final Map<UUID, Integer> captured = new HashMap<>();
    private final int phase;
    private int age;
    private int idleTicks;
    /** Яркость 3D-вихря (клиент): ~0.16 в покое → 1.0 в активном состоянии, меняется плавно. */
    private float intensity = 0.16F;
    private float prevIntensity = 0.16F;
    /** Блок под аномалией (клиент): из него сделаны летающие в вихре обломки. */
    private BlockState ground = Blocks.DIRT.defaultBlockState();

    public GravityAnomalyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVITY_ANOMALY.get(), pos, state);
        this.phase = (int) (pos.asLong() & 63L);
    }

    public float intensity(float partialTick) {
        return Mth.lerp(partialTick, prevIntensity, intensity);
    }

    public BlockState groundState() {
        return ground;
    }

    // ================================================================== СЕРВЕР
    private static boolean relevant(Entity e) {
        if (e instanceof LivingEntity living) {
            return AnomalyUtil.canAffect(living);
        }
        return e.isAlive() && (e instanceof ItemEntity || e instanceof ExperienceOrb
                || e instanceof Projectile || e instanceof FallingBlockEntity);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GravityAnomalyBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        be.age++;
        if (!state.getValue(GravityAnomalyBlock.ACTIVE) && be.captured.isEmpty() && be.age % 4 != 0) {
            return; // в покое проверяем зону реже — экономим сервер
        }
        final double radius = AnomalyConfig.GRAVITY_PULL_RADIUS.get();
        final int liftTicks = AnomalyConfig.GRAVITY_LIFT_TICKS.get();
        final double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
        boolean engaged = false;

        AABB zone = new AABB(cx - radius, cy - 1.0, cz - radius, cx + radius, cy + 10.0, cz + radius);
        List<Entity> found = server.getEntitiesOfClass(Entity.class, zone, GravityAnomalyBlockEntity::relevant);
        Set<UUID> present = new HashSet<>();

        for (Entity entity : found) {
            double dx = cx - entity.getX();
            double dz = cz - entity.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (!(entity instanceof LivingEntity living)) { // предметы, опыт, снаряды, падающие блоки
                if (entity.getY() > cy + 4.0) {
                    continue;
                }
                engaged = true;
                if (dist < 1.0 && entity.getY() < cy + 2.0) {
                    double angle = server.random.nextDouble() * Math.PI * 2.0;
                    entity.setDeltaMovement(Math.cos(angle) * 0.35, 0.85, Math.sin(angle) * 0.35);
                    entity.hurtMarked = true;
                    server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.GRAVITY_CRACK.get(), SoundSource.BLOCKS, 1.0F, 1.5F);
                    server.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 0.2, entity.getZ(), 12, 0.2, 0.2, 0.2, 0.2);
                } else {
                    pull(entity, dx, dz, dist, radius, entity instanceof Projectile ? 0.8 : 0.6);
                }
                continue;
            }

            UUID id = living.getUUID();
            present.add(id);
            Integer ticks = be.captured.get(id);
            if (ticks == null) {
                if (living.getY() > cy + 3.5) {
                    continue; // пролетающих высоко над воронкой не трогаем
                }
                engaged = true;
                if (dist < GRAB_RADIUS && living.getY() < cy + 3.0) {
                    ticks = 0;
                    server.playSound(null, living.getX(), living.getY(), living.getZ(), ModSounds.GRAVITY_CRACK.get(),
                            SoundSource.BLOCKS, 1.4F, 0.8F);
                } else {
                    pull(living, dx, dz, dist, radius, 1.0);
                    continue;
                }
            }
            engaged = true;
            ticks++;
            be.captured.put(id, ticks);

            if (ticks >= liftTicks) {
                tearApart(server, be, living);
                continue;
            }
            lift(server, living, cx, cy, cz, ticks, liftTicks);
        }
        be.captured.keySet().retainAll(present);
        be.updateActive(server, pos, state, engaged);
    }

    /** Включает/выключает состояние ACTIVE (с задержкой при засыпании, чтобы вихрь не мигал). */
    private void updateActive(ServerLevel server, BlockPos pos, BlockState state, boolean engaged) {
        boolean active = state.getValue(GravityAnomalyBlock.ACTIVE);
        if (engaged) {
            idleTicks = 0;
            if (!active) {
                server.setBlock(pos, state.setValue(GravityAnomalyBlock.ACTIVE, true), Block.UPDATE_CLIENTS);
                server.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        ModSounds.GRAVITY_ACTIVATE.get(), SoundSource.BLOCKS, 1.4F, 1.0F);
            }
        } else if (active && ++idleTicks > IDLE_TICKS_BEFORE_SLEEP) {
            server.setBlock(pos, state.setValue(GravityAnomalyBlock.ACTIVE, false), Block.UPDATE_CLIENTS);
        }
    }

    /** Горизонтальное притяжение к оси аномалии. */
    private static void pull(Entity entity, double dx, double dz, double dist, double radius, double scale) {
        double k = Math.max(0.0, 1.0 - dist / radius);
        double strength = (0.03 + 0.14 * k * k) * scale;
        double nx = dist > 1.0E-4 ? dx / dist : 0.0;
        double nz = dist > 1.0E-4 ? dz / dist : 0.0;

        Vec3 motion = entity.getDeltaMovement();
        double mx = motion.x + nx * strength;
        double mz = motion.z + nz * strength;
        double speed = Math.sqrt(mx * mx + mz * mz);
        if (speed > 0.5) {
            mx *= 0.5 / speed;
            mz *= 0.5 / speed;
        }
        entity.setDeltaMovement(mx, motion.y, mz);
        entity.hurtMarked = true; // отправить скорость клиенту (важно для игроков)
    }

    /** Подъём по спирали + звук и частицы. */
    private static void lift(ServerLevel server, LivingEntity entity, double cx, double cy, double cz, int ticks, int liftTicks) {
        double progress = ticks / (double) liftTicks;
        double angle = ticks * 0.32 + (entity.getId() % 8) * 0.8;
        double radius = 0.9 - 0.55 * progress;
        double targetY = cy + 0.3 + progress * LIFT_HEIGHT;

        Vec3 target = new Vec3(cx + Math.cos(angle) * radius, targetY, cz + Math.sin(angle) * radius);
        Vec3 velocity = target.subtract(entity.position()).scale(0.35);
        if (velocity.length() > 0.7) {
            velocity = velocity.normalize().scale(0.7);
        }
        AnomalyUtil.applyHidden(entity, ModEffects.GRAVITY_GRIP, 25, 0);
        entity.stopRiding();
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        entity.fallDistance = 0.0F;

        int interval = progress > 0.66 ? 3 : progress > 0.33 ? 5 : 8;
        if (ticks % interval == 0) {
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.GRAVITY_CRACK.get(),
                    SoundSource.BLOCKS, 1.2F, (float) (0.7 + progress * 0.9));
        }
        double midY = entity.getY() + entity.getBbHeight() * 0.5;
        server.sendParticles(ParticleTypes.CRIT, entity.getX(), midY, entity.getZ(), 3, 0.25, entity.getBbHeight() * 0.35, 0.25, 0.1);
        if (progress > 0.6) {
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), midY, entity.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
        }
    }

    /** Мгновенный разрыв. */
    private static void tearApart(ServerLevel server, GravityAnomalyBlockEntity be, LivingEntity entity) {
        double x = entity.getX(), y = entity.getY() + entity.getBbHeight() * 0.5, z = entity.getZ();
        server.playSound(null, x, y, z, ModSounds.GRAVITY_TEAR.get(), SoundSource.BLOCKS, 1.6F, 1.0F);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                x, y, z, 90, 0.35, entity.getBbHeight() * 0.4, 0.35, 0.25);
        server.sendParticles(ParticleTypes.CLOUD, x, y, z, 25, 0.3, 0.4, 0.3, 0.15);
        entity.hurt(server.damageSources().source(ModDamageTypes.GRAVITY_TEAR), Float.MAX_VALUE);
        be.captured.remove(entity.getUUID());
    }

    // ================================================================== КЛИЕНТ
    /**
     * Основную картинку рисует {@code GravityAnomalyRenderer} (3D-меши). Здесь — звук, плавная яркость,
     * регистрация для искажения мира и лёгкие частицы-«пыль», которые дополняют объём.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, GravityAnomalyBlockEntity be) {
        be.age++;
        boolean active = state.getValue(GravityAnomalyBlock.ACTIVE);
        be.prevIntensity = be.intensity;
        be.intensity += ((active ? 1.0F : 0.16F) - be.intensity) * 0.12F;
        ClientAnomalies.report(pos, ClientAnomalies.GRAVITY, be.intensity, level.getGameTime());

        if (be.age % 20 == 1) { // из чего сделаны обломки: берём блок под аномалией
            BlockPos below = pos.below();
            BlockState under = level.getBlockState(below);
            be.ground = under.isCollisionShapeFullBlock(level, below) ? under : Blocks.DIRT.defaultBlockState();
        }

        RandomSource random = level.random;
        double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;

        // фоновый гул (звук 2,6 c, повтор каждые 2,4 c — без паузы); в покое тихий, при активности громче и ниже
        if ((be.age + be.phase) % 48 == 0) {
            level.playLocalSound(cx, cy + 1.0, cz, ModSounds.GRAVITY_HUM.get(), SoundSource.BLOCKS,
                    active ? 1.4F : 0.35F, active ? 0.85F : 1.0F, false);
        }

        if (!active) {
            if (be.age % 4 == 0) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double h = random.nextDouble() * 1.2;
                double r = 0.15 + h * 0.25;
                level.addParticle(ParticleTypes.WHITE_ASH, cx + Math.cos(a) * r, cy + 0.1 + h, cz + Math.sin(a) * r,
                        -Math.sin(a) * 0.03, 0.02, Math.cos(a) * 0.03);
            }
            return;
        }

        // активно: пыль и земля втягиваются в воронку, белые искры в потоке
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 1.2 + random.nextDouble() * 2.2;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, be.ground),
                    cx + Math.cos(a) * r, cy + 0.15, cz + Math.sin(a) * r,
                    -Math.cos(a) * 0.12 - Math.sin(a) * 0.1, 0.18, -Math.sin(a) * 0.12 + Math.cos(a) * 0.1);
        }
        double h = random.nextDouble() * 5.0;
        double a = be.age * 0.4 + h * 2.0;
        double r = 0.2 + h * 0.22;
        level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a) * r, cy + 0.1 + h, cz + Math.sin(a) * r,
                -Math.sin(a) * 0.1, 0.06, Math.cos(a) * 0.1);
        double b = random.nextDouble() * Math.PI * 2.0;
        double rr = 3.0 + random.nextDouble() * 1.5;
        level.addParticle(ParticleTypes.POOF, cx + Math.cos(b) * rr, cy + 0.1, cz + Math.sin(b) * rr,
                -Math.cos(b) * 0.18, 0.02, -Math.sin(b) * 0.18);
    }
}
