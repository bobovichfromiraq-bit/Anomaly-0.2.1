package com.example.anomalies.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Аномалия с постоянными клиентскими эффектами (звук-петля и частицы). */
public interface AmbientAnomaly {
    /** Вызывается каждый клиентский тик. {@code time} — тики жизни блока со сдвигом, чтобы соседние блоки не звучали синхронно. */
    void ambientTick(Level level, BlockPos pos, BlockState state, int time);
}
