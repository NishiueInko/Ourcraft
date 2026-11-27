package com.voxelgame.world.level.weather;

/**
 * Trạng thái thời tiết quang đãng ({@code CLEAR}): không làm tối bầu trời,
 * không phát tiếng mưa, không dập lửa và không tích tuyết.
 */
public final class ClearWeather extends Weather {

    /** Thể hiện Flyweight duy nhất dùng chung toàn dự án (Zero-GC). */
    public static final ClearWeather INSTANCE = new ClearWeather();

    private ClearWeather() {
    }

    @Override
    public String getLabel() {
        return "CLEAR";
    }

    @Override
    public float targetSkyStrength(float globalStrength) {
        return 0f;
    }

    @Override
    public float targetRainGain(float globalStrength) {
        return 0f;
    }

    @Override
    public boolean extinguishesFire() {
        return false;
    }

    @Override
    public boolean accumulatesSnow() {
        return false;
    }
}
