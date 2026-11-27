package com.voxelgame.world.level.weather;

/**
 * Trạng thái thời tiết tuyết rơi ({@code SNOW}) tại {@code Snowy Plains}: làm âm u bầu trời,
 * không phát âm thanh mưa, không dập lửa và kích hoạt tích lũy tuyết trên bề mặt lộ thiên.
 */
public final class SnowWeather extends Weather {

    /** Thể hiện Flyweight duy nhất dùng chung toàn dự án (Zero-GC). */
    public static final SnowWeather INSTANCE = new SnowWeather();

    private SnowWeather() {
    }

    @Override
    public String getLabel() {
        return "SNOW";
    }

    @Override
    public float targetSkyStrength(float globalStrength) {
        return globalStrength;
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
        return true;
    }
}
