package com.voxelgame.world.level.weather;

/**
 * Lớp trừu tượng gốc đại diện cho một trạng thái thời tiết cục bộ theo mẫu thiết kế State / Strategy.
 * Các lớp con ({@link ClearWeather}, {@link RainWeather}, {@link SnowWeather}) là các Flyweight bất biến
 * nhằm bảo đảm Zero-GC trên vòng lặp cập nhật 60 FPS.
 */
public abstract class Weather {

    /** Constructor bảo vệ chỉ cho các trạng thái thời tiết con khởi tạo singleton. */
    protected Weather() {
    }

    /** Nhãn hiển thị trạng thái thời tiết trên giao diện HUD ({@code CLEAR}, {@code RAIN}, {@code SNOW}). */
    public abstract String getLabel();

    /**
     * Tính cường độ âm u mục tiêu của bầu trời dựa trên cường độ giáng thủy toàn cục.
     *
     * @param globalStrength cường độ giáng thủy toàn cục trong đoạn {@code [0, 1]}
     * @return cường độ âm u mục tiêu cho bầu trời
     */
    public abstract float targetSkyStrength(float globalStrength);

    /**
     * Tính âm lượng mục tiêu của tiếng mưa dựa trên cường độ giáng thủy toàn cục.
     *
     * @param globalStrength cường độ giáng thủy toàn cục trong đoạn {@code [0, 1]}
     * @return âm lượng mục tiêu cho luồng âm thanh mưa
     */
    public abstract float targetRainGain(float globalStrength);

    /** Cho biết trạng thái thời tiết này có dập tắt lửa trên thực thể ngoài trời hay không. */
    public abstract boolean extinguishesFire();

    /** Cho biết trạng thái thời tiết này có tích lũy lớp tuyết trên bề mặt lộ thiên hay không. */
    public abstract boolean accumulatesSnow();
}
