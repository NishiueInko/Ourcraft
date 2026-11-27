package com.voxelgame.world.level.weather;

/**
 * Trạng thái thời tiết mưa ({@code RAIN}) tại các biome ôn hòa: làm âm u bầu trời,
 * phát âm thanh mưa theo cường độ, dập lửa ngoài trời và không tích tuyết.
 */
public final class RainWeather extends Weather {

    /** Thể hiện Flyweight duy nhất dùng chung toàn dự án (Zero-GC). */
    public static final RainWeather INSTANCE = new RainWeather();

    private RainWeather() {
    }

    @Override
    public String getLabel() {
        return "RAIN";
    }

    @Override
    public float targetSkyStrength(float globalStrength) {
        return globalStrength;
    }

    @Override
    public float targetRainGain(float globalStrength) {
        return globalStrength;
    }

    @Override
    public boolean extinguishesFire() {
        return true;
    }

    @Override
    public boolean accumulatesSnow() {
        return false;
    }
}
