package com.example.anomalies.client.vfx;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Жизненный цикл пузырей кислоты: растёт → лопается. Рендер и частицы используют одни и те же числа. */
public final class AcidBubbles {
    public static final int COUNT = 4;
    public static final int CYCLE = 36;
    public static final int POP_TICK = 31;       // момент «лопанья» внутри цикла
    public static final double SURFACE = 0.875;  // высота поверхности кислоты в блоке

    private AcidBubbles() {}

    public static int shifted(int time, int bubble) {
        return time + bubble * 9;
    }

    public static Vec3 position(BlockPos pos, int bubble, long cycle) {
        Random rnd = new Random(pos.asLong() * 131L + bubble * 977L + cycle * 7919L);
        return new Vec3(0.15 + rnd.nextDouble() * 0.7, SURFACE, 0.15 + rnd.nextDouble() * 0.7);
    }
}
