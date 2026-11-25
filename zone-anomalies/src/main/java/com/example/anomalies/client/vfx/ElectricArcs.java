package com.example.anomalies.client.vfx;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Молнии Электры: фрактальные (метод срединного смещения) с ответвлениями, лучами во все стороны от шара,
 * как на референсе. Луч обрывается на первом твёрдом блоке (там молния «бьёт» в поверхность).
 * Каждые {@link #PERIOD} тиков — новый набор. Результат кэшируется в блок-сущности.
 */
public final class ElectricArcs {
    public static final int PERIOD = 3;
    public static final Vec3 CENTER = new Vec3(0.5, 0.95, 0.5);
    private static final int MAIN = 14;

    /** @param brightness относительная яркость 0..1; @param branch ответвление (тоньше и тусклее) */
    public record Arc(Vec3[] points, boolean grounded, float brightness, boolean branch) {
        public Vec3 end() {
            return points[points.length - 1];
        }
    }

    private ElectricArcs() {}

    public static List<Arc> generate(Level level, BlockPos pos, long index, int extra) {
        Random rnd = new Random(pos.asLong() * 31L + index * 0x9E3779B97F4A7C15L);
        List<Arc> arcs = new ArrayList<>();
        Vec3 origin = new Vec3(pos.getX() + CENTER.x, pos.getY() + CENTER.y, pos.getZ() + CENTER.z);
        int n = MAIN + extra;
        for (int i = 0; i < n; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / n;                       // равномерно по сфере (спираль Фибоначчи)
            double radius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double phi = i * 2.399963 + rnd.nextDouble() * 0.9;
            Vec3 dir = new Vec3(Math.cos(phi) * radius + (rnd.nextDouble() - 0.5) * 0.25,
                    y * 0.9 + (rnd.nextDouble() - 0.5) * 0.25,
                    Math.sin(phi) * radius + (rnd.nextDouble() - 0.5) * 0.25).normalize();
            double length = 1.2 + rnd.nextDouble() * 2.3;

            double reach = length;
            boolean grounded = false;
            for (double t = 0.3; t <= length; t += 0.25) {              // обрыв на первой твёрдой поверхности
                BlockPos bp = BlockPos.containing(origin.add(dir.scale(t)));
                BlockState state = level.getBlockState(bp);
                if (!state.getCollisionShape(level, bp).isEmpty()) {
                    reach = Math.max(0.3, t - 0.1);
                    grounded = true;
                    break;
                }
            }
            Vec3 end = CENTER.add(dir.scale(reach));
            bolt(rnd, arcs, CENTER, end, grounded, (float) (0.55 + rnd.nextDouble() * 0.45));
        }
        return arcs;
    }

    private static void bolt(Random rnd, List<Arc> out, Vec3 from, Vec3 to, boolean grounded, float brightness) {
        List<Vec3> pts = jagged(rnd, from, to, 4, from.distanceTo(to) * 0.17);
        out.add(new Arc(pts.toArray(new Vec3[0]), grounded, brightness, false));
        for (int i = 2; i < pts.size() - 3; i++) {                       // ответвления
            if (rnd.nextDouble() < 0.12) {
                Vec3 base = pts.get(i);
                Vec3 along = pts.get(i + 2).subtract(base);
                Vec3 dir = along.add(rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5).normalize();
                Vec3 tip = base.add(dir.scale(0.3 + rnd.nextDouble() * 0.5));
                List<Vec3> sub = jagged(rnd, base, tip, 2, 0.07);
                out.add(new Arc(sub.toArray(new Vec3[0]), false, brightness * 0.6F, true));
            }
        }
    }

    /** Срединное смещение: каждый уровень делит отрезки пополам и сдвигает середину случайно. */
    private static List<Vec3> jagged(Random rnd, Vec3 a, Vec3 b, int levels, double displacement) {
        List<Vec3> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        double d = displacement;
        for (int l = 0; l < levels; l++) {
            List<Vec3> next = new ArrayList<>(pts.size() * 2);
            for (int i = 0; i < pts.size() - 1; i++) {
                Vec3 p = pts.get(i), q = pts.get(i + 1);
                Vec3 mid = p.add(q).scale(0.5).add((rnd.nextDouble() - 0.5) * d, (rnd.nextDouble() - 0.5) * d, (rnd.nextDouble() - 0.5) * d);
                next.add(p);
                next.add(mid);
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
            d *= 0.55;
        }
        return pts;
    }
}
