package com.example.anomalies.util;

import com.example.anomalies.AnomalyConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

public final class AnomalyUtil {
    private AnomalyUtil() {}

    /** Может ли аномалия подействовать на это существо. */
    public static boolean canAffect(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity instanceof ArmorStand) {
            return false;
        }
        return !(entity instanceof Player player && player.isCreative() && !AnomalyConfig.AFFECT_CREATIVE.get());
    }

    /** Накладывает эффект, а пока он действует — только продлевает его (без спама пакетами). */
    public static void applyOrRefresh(LivingEntity entity, Holder<MobEffect> effect, int duration, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < duration - 20) {
            entity.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
        }
    }

    /** Перезарядка, привязанная к сущности (хранится в её NBT). true — можно действовать, перезарядка запущена. */
    public static boolean tryCooldown(Entity entity, String key, long now, int ticks) {
        var data = entity.getPersistentData();
        if (now < data.getLong(key)) {
            return false;
        }
        data.putLong(key, now + ticks);
        return true;
    }

    /** Скрытый служебный эффект (без иконки и частиц) — используется для клиентских экранных эффектов. */
    public static void applyHidden(LivingEntity entity, Holder<MobEffect> effect, int duration, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current == null || current.getDuration() < duration / 3) {
            entity.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, false));
        }
    }
}
