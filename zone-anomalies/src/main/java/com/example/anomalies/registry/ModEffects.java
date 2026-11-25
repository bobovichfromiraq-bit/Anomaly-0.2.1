package com.example.anomalies.registry;

import com.example.anomalies.AnomaliesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, AnomaliesMod.MODID);

    /** «Покрытие кислотой» — по нему клиент рисует зелёный оверлей на экране. */
    public static final DeferredHolder<MobEffect, MobEffect> ACID_COVER = marker("acid_cover", MobEffectCategory.HARMFUL, 0x60D23A);
    /** Удар током (≈0,7 с): клиент делает вспышку и тряску камеры. Скрытый, без иконки. */
    public static final DeferredHolder<MobEffect, MobEffect> ELECTRO_SHOCK = marker("electro_shock", MobEffectCategory.HARMFUL, 0x7FC8FF);
    /** Хватка гравитации: клиент делает туннельное зрение, качает камеру и меняет FOV. Скрытый, без иконки. */
    public static final DeferredHolder<MobEffect, MobEffect> GRAVITY_GRIP = marker("gravity_grip", MobEffectCategory.HARMFUL, 0xB4C8E0);

    private static DeferredHolder<MobEffect, MobEffect> marker(String name, MobEffectCategory category, int color) {
        return EFFECTS.register(name, () -> new MarkerEffect(category, color));
    }

    /** Эффект-маркер: сам ничего не делает. */
    private static class MarkerEffect extends MobEffect {
        MarkerEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private ModEffects() {}
}
