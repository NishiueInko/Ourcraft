package com.voxelgame.engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.Player;

import java.util.Random;

/** Quản lý chu kỳ giáng thủy, khí hậu cục bộ, âm lượng và công việc tích tuyết. */
public final class WeatherManager {
    /** Mỗi lần chuyển cường độ mất năm giây từ đầu đến cuối. */
    private static final float FADE_SECONDS = 5f;
    /** Thử tối đa một cột mỗi lần, trong vùng 25 × 25 quanh mắt. */
    private static final int ACCUMULATION_RADIUS = 12;
    private final Random cycleRandom;
    private final Random snowRandom;
    private boolean precipitation;
    private float remaining;
    private float strength;
    private float accumulationTime;
    private float skyStrength;
    private float rainGain;
    private float shelter = 1f;
    private BiomeType biome = BiomeType.PLAINS;
    private double animationSeconds;

    /** Tách nguồn ngẫu nhiên của chu kỳ và tuyết để số cột thử không đổi lịch thời tiết. */
    public WeatherManager(long seed) {
        cycleRandom = new Random(seed);
        snowRandom = new Random(seed ^ 0x5F24E6A9L);
        scheduleNext();
    }

    /** Tự chuyển sau 3–7 phút quang hoặc 1–3 phút giáng thủy. */
    private void scheduleNext() {
        remaining = precipitation ? 60 + cycleRandom.nextInt(121) : 180 + cycleRandom.nextInt(241);
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

    /** Cập nhật khí hậu quanh người chơi và thử tích tuyết ở mật độ thấp. */
    public void update(float seconds, World world, Vector3 eye, Player player) {
        update(seconds);
        int x = MathUtils.floor(eye.x);
        int z = MathUtils.floor(eye.z);
        biome = world.getBiome(x, z);
        skyStrength = approach(skyStrength, biome == BiomeType.DESERT ? 0f : strength, seconds * 0.6f);
        rainGain = approach(rainGain, biome == BiomeType.PLAINS ? strength : 0f, seconds * 0.65f);
        shelter = approach(shelter, world.hasWeatherRoof(x, eye.y, z) ? 0.4f : 1f, seconds * 1.5f);

        accumulationTime += seconds;
        if (accumulationTime >= 0.35f) {
            accumulationTime = 0f;
            if (strength > 0.5f) {
                int span = ACCUMULATION_RADIUS * 2 + 1;
                int sampleX = x + snowRandom.nextInt(span) - ACCUMULATION_RADIUS;
                int sampleZ = z + snowRandom.nextInt(span) - ACCUMULATION_RADIUS;
                world.accumulateSnow(sampleX, sampleZ, player, snowRandom.nextInt(12) == 0);
            }
        }
    }

    /** Bước tuyến tính có chặn để các giá trị nhỏ về đúng 0. */
    private static float approach(float current, float target, float step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
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
        if (strength <= 0f || biome == BiomeType.DESERT) {
            return "CLEAR";
        }
        return biome == BiomeType.PLAINS ? "RAIN" : "SNOW";
    }

    /** Thời gian hoạt ảnh không bị nhân tốc độ bởi F8 và dừng khi Pause. */
    public double animationSeconds() {
        return animationSeconds;
    }
}
