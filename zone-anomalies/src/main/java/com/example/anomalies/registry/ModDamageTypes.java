package com.example.anomalies.registry;

import com.example.anomalies.AnomaliesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> GRAVITY_TEAR = key("gravity_tear");
    public static final ResourceKey<DamageType> ELECTRIC_DISCHARGE = key("electric_discharge");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, AnomaliesMod.id(name));
    }

    private ModDamageTypes() {}
}
