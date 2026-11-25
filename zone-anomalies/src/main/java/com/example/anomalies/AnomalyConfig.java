package com.example.anomalies;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Настройки: config/anomalies-common.toml */
public final class AnomalyConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue AFFECT_CREATIVE = B
            .comment("Действуют ли аномалии на игроков в творческом режиме (по умолчанию нет, чтобы не мешать строить).")
            .define("affectCreativePlayers", false);

    public static final ModConfigSpec.DoubleValue GRAVITY_PULL_RADIUS = B
            .comment("Радиус, в котором гравитационная аномалия начинает затягивать (в блоках).")
            .defineInRange("gravityPullRadius", 5.0, 2.0, 16.0);

    public static final ModConfigSpec.IntValue GRAVITY_LIFT_TICKS = B
            .comment("Сколько тиков жертва поднимается вверх перед разрывом (20 тиков = 1 сек).",
                    "Не ставьте больше 75: на серверах без allow-flight игрока, «парящего» дольше 80 тиков, кикает античит ванили.")
            .defineInRange("gravityLiftTicks", 70, 20, 75);

    public static final ModConfigSpec.DoubleValue ELECTRIC_DAMAGE = B
            .comment("Урон электрической аномалии в половинках сердец (6.0 = 3 сердца).")
            .defineInRange("electricDamage", 6.0, 0.0, 1000.0);

    public static final ModConfigSpec.IntValue EFFECT_TICKS = B
            .comment("Длительность эффектов электрической и кислотной аномалий в тиках (100 = 5 секунд).")
            .defineInRange("effectDurationTicks", 100, 20, 6000);

    public static final ModConfigSpec.DoubleValue DETECTOR_RANGE = B
            .comment("Дальность детектора аномалий в блоках.")
            .defineInRange("detectorRange", 16.0, 4.0, 48.0);

    public static final ModConfigSpec.BooleanValue ACID_HAZE = B
            .comment("Ядовитый воздух над кислотой: долгое пребывание в испарениях вызывает тошноту.")
            .define("acidHazeSickness", true);

    public static final ModConfigSpec.BooleanValue ELECTRIC_REDSTONE = B
            .comment("Электрическая аномалия на 0,6 с подаёт сигнал редстоуна 15 после каждого разряда.")
            .define("electricRedstoneSignal", true);

    public static final ModConfigSpec SPEC = B.build();

    private AnomalyConfig() {}
}
