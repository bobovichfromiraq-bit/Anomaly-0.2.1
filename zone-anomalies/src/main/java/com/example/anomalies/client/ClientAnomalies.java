package com.example.anomalies.client;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Список «живых» аномалий рядом с игроком. Блок-сущности каждый клиентский тик сообщают о себе,
 * а {@link WorldDistortion} выбирает ближайшие и искажает вокруг них картинку.
 * Класс не использует клиентских типов Minecraft, поэтому безопасен для загрузки на сервере.
 */
public final class ClientAnomalies {
    public static final int GRAVITY = 0;
    public static final int ELECTRIC = 1;
    public static final int ACID = 2;

    public record Entry(BlockPos pos, int type, float strength, long tick) {}

    private static final Map<Long, Entry> ENTRIES = new HashMap<>();

    private ClientAnomalies() {}

    public static void report(BlockPos pos, int type, float strength, long gameTime) {
        ENTRIES.put(pos.asLong(), new Entry(pos.immutable(), type, strength, gameTime));
    }

    public static List<Entry> active(long now) {
        ENTRIES.values().removeIf(e -> Math.abs(now - e.tick()) > 5);
        return new ArrayList<>(ENTRIES.values());
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
