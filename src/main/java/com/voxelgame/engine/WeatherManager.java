package com.voxelgame.engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.Player;

import java.util.Random;

/** Quản lý chu kỳ giáng thủy chuẩn Minecraft, khí hậu cục bộ và âm lượng mưa. */
public final class WeatherManager {
    /** Mỗi lần chuyển cường độ mất năm giây từ đầu đến cuối. */
    private static final float FADE_SECONDS = 5f;
    /** Chu kỳ trời quang chuẩn Minecraft Java: 12.000–180.000 tick (10–150 phút). */
    static final int CLEAR_MIN_SECONDS = 600;
    static final int CLEAR_MAX_SECONDS = 9_000;
    /** Chu kỳ giáng thủy chuẩn Minecraft Java: 12.000–24.000 tick (10–20 phút). */
    static final int PRECIP_MIN_SECONDS = 600;
    static final int PRECIP_MAX_SECONDS = 1_200;

    private final Random cycleRandom;
    private boolean precipitation;
    private float remaining;
    private float strength;
    private float skyStrength;
    private float rainGain;
    private float shelter = 1f;
    private BiomeType biome = BiomeType.PLAINS;
    private double animationSeconds;

    /** Khởi tạo chu kỳ thời tiết tất định theo seed thế giới. */
    public WeatherManager(long seed) {
        cycleRandom = new Random(seed);
        scheduleNext();
    }

    /** Tự chuyển sau 10–150 phút quang đãng hoặc 10–20 phút giáng thủy theo chuẩn Minecraft. */
    private void scheduleNext() {
        remaining = precipitation
                ? PRECIP_MIN_SECONDS + cycleRandom.nextInt(PRECIP_MAX_SECONDS - PRECIP_MIN_SECONDS + 1)
                : CLEAR_MIN_SECONDS + cycleRandom.nextInt(CLEAR_MAX_SECONDS - CLEAR_MIN_SECONDS + 1);
    }

    /** Đảo thời tiết và bắt đầu một khoảng tự động mới. */
    public void toggle() {
        precipitation = !precipitation;
        scheduleNext();
    }

    /** Tính cường độ liên tục, kể cả khi vượt qua mốc chuyển tự động giữa hai frame. */
    void update(float seconds) {
        animationSeconds += seconds;
        while (seconds >= remaining) {
            fade(remaining);
            seconds -= remaining;
            toggle();
        }
        remaining -= seconds;
        fade(seconds);
    }

    /** Tiến cường độ về mục tiêu mà không vượt qua hai đầu. */
    private void fade(float seconds) {
        strength = approach(strength, precipitation ? 1f : 0f, seconds / FADE_SECONDS);
    }

    /** Cập nhật khí hậu quanh người chơi; tích tuyết thực hiện theo random tick 1/16 của World. */
    public void update(float seconds, World world, Vector3 eye, Player player) {
        update(seconds);
        int x = MathUtils.floor(eye.x);
        int z = MathUtils.floor(eye.z);
        biome = world.getBiome(x, z);
        skyStrength = approach(skyStrength, biome.isDry() ? 0f : strength, seconds * 0.6f);
        rainGain = approach(rainGain, biome.hasRain() ? strength : 0f, seconds * 0.65f);
        shelter = approach(shelter, world.hasWeatherRoof(x, eye.y, z) ? 0.4f : 1f, seconds * 1.5f);
    }

    /** Bước tuyến tính có chặn để các giá trị nhỏ về đúng 0. */
    private static float approach(float current, float target, float step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    /** Thời gian còn lại của pha thời tiết hiện tại (giây), phục vụ kiểm thử chu kỳ. */
    float remainingSeconds() {
        return remaining;
    }

    /** Cường độ toàn cục áp dụng khi dựng các cột mưa và tuyết. */
    public float strength() {
        return strength;
    }

    /** Tỷ lệ âm u tại vùng đang đứng, chuyển êm ở ranh giới sa mạc. */
    public float skyStrength() {
        return skyStrength;
    }

    /** Hệ số luồng mưa trước khi áp dụng âm lượng từ Pause Menu. */
    public float rainVolume() {
        return rainGain * shelter;
    }

    /** Biome hiện tại dùng cho HUD. */
    public BiomeType biome() {
        return biome;
    }

    /** Nhãn HUD phản ánh cường độ thực tế và khí hậu ở cột hiện tại. */
    public String label() {
        if (strength <= 0f || biome.isDry()) {
            return "CLEAR";
        }
        return biome.hasRain() ? "RAIN" : "SNOW";
    }

    /** Thời gian hoạt ảnh không bị nhân tốc độ bởi F8 và dừng khi Pause. */
    public double animationSeconds() {
        return animationSeconds;
    }
}
