package com.example.anomalies.client;

import java.lang.reflect.Method;

/**
 * Мягкая совместимость с Iris/Oculus (без зависимости при сборке — только через рефлексию).
 * Пока включён шейдерпак, пост-обработка мода отключается: шейдерпак сам владеет кадром, и наш проход
 * исказил бы картинку неправильно. 3D-эффекты (рендер блок-сущностей), частицы и звуки работают как обычно.
 */
public final class IrisCompat {
    private static final String[] API_CLASSES = {
            "net.irisshaders.iris.api.v0.IrisApi",   // Iris
            "net.coderbot.iris.api.v0.IrisApi"       // Oculus / старые версии
    };

    private static boolean resolved;
    private static Object api;
    private static Method isShaderPackInUse;
    private static long nextCheck;
    private static boolean cached;

    private IrisCompat() {}

    private static void resolve() {
        resolved = true;
        for (String name : API_CLASSES) {
            try {
                Class<?> type = Class.forName(name);
                api = type.getMethod("getInstance").invoke(null);
                isShaderPackInUse = type.getMethod("isShaderPackInUse");
                return;
            } catch (Throwable ignored) {
                // этого API нет — пробуем следующий
            }
        }
    }

    /** Установлен ли Iris/Oculus. */
    public static boolean present() {
        if (!resolved) {
            resolve();
        }
        return isShaderPackInUse != null;
    }

    /** Включён ли сейчас шейдерпак (проверка кэшируется на полсекунды). */
    public static boolean shaderPackActive() {
        long now = System.nanoTime();
        if (now < nextCheck) {
            return cached;
        }
        nextCheck = now + 500_000_000L;
        if (!present()) {
            cached = false;
            return false;
        }
        try {
            cached = (Boolean) isShaderPackInUse.invoke(api);
        } catch (Throwable t) {
            cached = false;
            isShaderPackInUse = null;
        }
        return cached;
    }
}
