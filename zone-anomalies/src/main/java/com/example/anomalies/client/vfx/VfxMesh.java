package com.example.anomalies.client.vfx;

import com.example.anomalies.AnomaliesMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 3D-меш из .obj (assets/anomalies/models/vfx/*.obj) — те же файлы открываются в Blender.
 * Грани приводятся к четырёхугольникам (треугольник = вырожденный квад), т.к. рендер-типы Minecraft работают с QUADS.
 * Формат вершины внутри: x, y, z, u, v, nx, ny, nz.
 */
public final class VfxMesh {
    private static final Map<ResourceLocation, VfxMesh> CACHE = new HashMap<>();
    private static final VfxMesh EMPTY = new VfxMesh(new float[0]);

    private final float[] data;
    private final int vertexCount;

    private VfxMesh(float[] data) {
        this.data = data;
        this.vertexCount = data.length / 8;
    }

    public static VfxMesh get(String name) {
        return CACHE.computeIfAbsent(AnomaliesMod.id("models/vfx/" + name + ".obj"), VfxMesh::load);
    }

    public static void clear() {
        CACHE.clear();
    }

    private static VfxMesh load(ResourceLocation id) {
        FloatArrayList pos = new FloatArrayList(), uvs = new FloatArrayList(), nrm = new FloatArrayList();
        FloatArrayList out = new FloatArrayList();
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                AnomaliesMod.LOGGER.error("VFX mesh not found: {}", id);
                return EMPTY;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.charAt(0) == '#') {
                        continue;
                    }
                    String[] t = line.split("\\s+");
                    switch (t[0]) {
                        case "v" -> {
                            pos.add(Float.parseFloat(t[1]));
                            pos.add(Float.parseFloat(t[2]));
                            pos.add(Float.parseFloat(t[3]));
                        }
                        case "vt" -> {
                            uvs.add(Float.parseFloat(t[1]));
                            uvs.add(Float.parseFloat(t[2]));
                        }
                        case "vn" -> {
                            nrm.add(Float.parseFloat(t[1]));
                            nrm.add(Float.parseFloat(t[2]));
                            nrm.add(Float.parseFloat(t[3]));
                        }
                        case "f" -> addFace(t, pos, uvs, nrm, out);
                        default -> { }
                    }
                }
            }
        } catch (Exception e) {
            AnomaliesMod.LOGGER.error("Failed to load VFX mesh {}", id, e);
            return EMPTY;
        }
        return new VfxMesh(out.toFloatArray());
    }

    private static void addFace(String[] t, FloatArrayList pos, FloatArrayList uvs, FloatArrayList nrm, FloatArrayList out) {
        int n = t.length - 1;
        int[] vi = new int[n], ti = new int[n], ni = new int[n];
        for (int i = 0; i < n; i++) {
            String[] c = t[i + 1].split("/");
            vi[i] = Integer.parseInt(c[0]) - 1;
            ti[i] = c.length > 1 && !c[1].isEmpty() ? Integer.parseInt(c[1]) - 1 : -1;
            ni[i] = c.length > 2 && !c[2].isEmpty() ? Integer.parseInt(c[2]) - 1 : -1;
        }
        // нормаль грани (запасной вариант, если в файле нет vn)
        float[] fn = faceNormal(pos, vi);
        if (n == 3) {
            emit(pos, uvs, nrm, out, vi[0], ti[0], ni[0], fn);
            emit(pos, uvs, nrm, out, vi[1], ti[1], ni[1], fn);
            emit(pos, uvs, nrm, out, vi[2], ti[2], ni[2], fn);
            emit(pos, uvs, nrm, out, vi[2], ti[2], ni[2], fn);
        } else if (n == 4) {
            for (int i = 0; i < 4; i++) {
                emit(pos, uvs, nrm, out, vi[i], ti[i], ni[i], fn);
            }
        } else {
            for (int i = 1; i < n - 1; i++) { // веер из треугольников
                emit(pos, uvs, nrm, out, vi[0], ti[0], ni[0], fn);
                emit(pos, uvs, nrm, out, vi[i], ti[i], ni[i], fn);
                emit(pos, uvs, nrm, out, vi[i + 1], ti[i + 1], ni[i + 1], fn);
                emit(pos, uvs, nrm, out, vi[i + 1], ti[i + 1], ni[i + 1], fn);
            }
        }
    }

    private static float[] faceNormal(FloatArrayList p, int[] vi) {
        float ax = p.getFloat(vi[1] * 3) - p.getFloat(vi[0] * 3);
        float ay = p.getFloat(vi[1] * 3 + 1) - p.getFloat(vi[0] * 3 + 1);
        float az = p.getFloat(vi[1] * 3 + 2) - p.getFloat(vi[0] * 3 + 2);
        float bx = p.getFloat(vi[2] * 3) - p.getFloat(vi[0] * 3);
        float by = p.getFloat(vi[2] * 3 + 1) - p.getFloat(vi[0] * 3 + 1);
        float bz = p.getFloat(vi[2] * 3 + 2) - p.getFloat(vi[0] * 3 + 2);
        float x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return len < 1.0E-6F ? new float[]{0, 1, 0} : new float[]{x / len, y / len, z / len};
    }

    private static void emit(FloatArrayList pos, FloatArrayList uvs, FloatArrayList nrm, FloatArrayList out,
                             int vi, int ti, int ni, float[] fn) {
        out.add(pos.getFloat(vi * 3));
        out.add(pos.getFloat(vi * 3 + 1));
        out.add(pos.getFloat(vi * 3 + 2));
        out.add(ti >= 0 ? uvs.getFloat(ti * 2) : 0.0F);
        out.add(ti >= 0 ? uvs.getFloat(ti * 2 + 1) : 0.0F);
        if (ni >= 0) {
            out.add(nrm.getFloat(ni * 3));
            out.add(nrm.getFloat(ni * 3 + 1));
            out.add(nrm.getFloat(ni * 3 + 2));
        } else {
            out.add(fn[0]);
            out.add(fn[1]);
            out.add(fn[2]);
        }
    }

    /**
     * Рисует меш в буфер (формат ENTITY).
     *
     * @param fadeY плавно гасит яркость к нижнему и верхнему краю меша (для воронки и облака, у которых Y∈[0,1])
     */
    public void render(PoseStack.Pose pose, VertexConsumer consumer, int r, int g, int b, int a, int light, boolean fadeY) {
        Matrix4f matrix = pose.pose();
        for (int i = 0; i < vertexCount; i++) {
            int o = i * 8;
            float f = fadeY ? (float) Math.sin(Math.PI * Mth.clamp(data[o + 1], 0.0F, 1.0F)) : 1.0F;
            consumer.addVertex(matrix, data[o], data[o + 1], data[o + 2])
                    .setColor((int) (r * f), (int) (g * f), (int) (b * f), a)
                    .setUv(data[o + 3], data[o + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, data[o + 5], data[o + 6], data[o + 7]);
        }
    }
}
