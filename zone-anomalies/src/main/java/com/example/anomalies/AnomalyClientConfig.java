package com.example.anomalies;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Клиентские настройки: config/anomalies-client.toml */
public final class AnomalyClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue WORLD_DISTORTION = B
            .comment("Искажение мира вокруг аномалий (пост-обработка). Отключите при проблемах с видеокартой или шейдерпаками.")
            .define("worldDistortion", true);

    public static final ModConfigSpec.DoubleValue DISTORTION_STRENGTH = B
            .comment("Сила искажения (1.0 = стандартная).")
            .defineInRange("distortionStrength", 1.0, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue DISTORTION_WITH_SHADER_PACKS = B
            .comment("Включать искажение мира даже при активном шейдерпаке Iris/Oculus (может ломать картинку). По умолчанию выключено.")
            .define("distortionWithShaderPacks", false);

    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE = B
            .comment("Дрожь камеры рядом с гравитационными и электрическими аномалиями.")
            .define("cameraShake", true);

    public static final ModConfigSpec.BooleanValue TOXIC_FOG = B
            .comment("Зеленоватый ядовитый туман рядом с кислотными аномалиями (при шейдерпаках не действует).")
            .define("toxicFog", true);

    public static final ModConfigSpec SPEC = B.build();

    private AnomalyClientConfig() {}
}
