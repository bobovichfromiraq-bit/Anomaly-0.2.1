package com.example.anomalies.blockentity;

import com.example.anomalies.block.AcidAnomalyBlock;
import com.example.anomalies.block.AmbientAnomaly;
import com.example.anomalies.block.ElectricAnomalyBlock;
import com.example.anomalies.client.ClientAnomalies;
import com.example.anomalies.client.vfx.ElectricArcs;
import com.example.anomalies.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Клиентский счётчик времени и кэш молний для электрической и кислотной аномалий. */
public class AmbientAnomalyBlockEntity extends BlockEntity {
    private final int phase;
    private int age;
    private List<ElectricArcs.Arc> arcs = List.of();
    private long arcsIndex = Long.MIN_VALUE;
    private int arcsExtra = -1;

    public AmbientAnomalyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AMBIENT_ANOMALY.get(), pos, state);
        this.phase = (int) (pos.asLong() & 63L);
    }

    /** Время жизни со сдвигом: общее для звуков, частиц и рендера (чтобы они совпадали по фазе). */
    public int time() {
        return age + phase;
    }

    /** Молнии для данного отрезка времени (пересчитываются раз в {@link ElectricArcs#PERIOD} тиков). */
    public List<ElectricArcs.Arc> arcs(long index, int extra) {
        if (level != null && (index != arcsIndex || extra != arcsExtra)) {
            arcs = ElectricArcs.generate(level, worldPosition, index, extra);
            arcsIndex = index;
            arcsExtra = extra;
        }
        return arcs;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AmbientAnomalyBlockEntity be) {
        be.age++;
        if (state.getBlock() instanceof ElectricAnomalyBlock) {
            ClientAnomalies.report(pos, ClientAnomalies.ELECTRIC, 0.9F, level.getGameTime());
        } else if (state.getBlock() instanceof AcidAnomalyBlock) {
            ClientAnomalies.report(pos, ClientAnomalies.ACID, 1.0F, level.getGameTime());
        }
        if (state.getBlock() instanceof AmbientAnomaly ambient) {
            ambient.ambientTick(level, pos, state, be.time());
        }
    }
}
