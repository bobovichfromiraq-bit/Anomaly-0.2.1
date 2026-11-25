package com.example.anomalies.registry;

import com.example.anomalies.AnomaliesMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, AnomaliesMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_HUM = register("gravity_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_ACTIVATE = register("gravity_activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_CRACK = register("gravity_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_TEAR = register("gravity_tear");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIC_CRACKLE = register("electric_crackle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIC_ZAP = register("electric_zap");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIC_IDLE = register("electric_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACID_LOOP = register("acid_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACID_BUBBLE = register("acid_bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACID_SIZZLE = register("acid_sizzle");

    public static final DeferredHolder<SoundEvent, SoundEvent> DETECTOR_BEEP = register("detector_beep");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AnomaliesMod.id(name)));
    }

    private ModSounds() {}
}
