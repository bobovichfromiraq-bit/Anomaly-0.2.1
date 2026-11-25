package com.example.anomalies.block;

/** Помечает блоки-аномалии: по нему их находит детектор. */
public interface AnomalyMarker {
    /** Базовая высота тона писка детектора для этого типа аномалии. */
    float detectorPitch();
}
