package com.voxelgame.client.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Lưu trữ và nạp cấu hình âm lượng tách biệt giữa Nhạc nền ({@code musicVolume})
 * và Hiệu ứng âm thanh ({@code sfxVolume}) ra tệp {@code options.properties}.
 *
 * <p>Tuân thủ Đóng gói (Encapsulation): mọi giá trị đều được kẹp an toàn trong đoạn {@code [0.0, 1.0]}
 * và mặc định khởi tạo ở mức {@code 0.70} (70%) nếu tệp chưa tồn tại hoặc không đọc được.</p>
 */
public final class AudioOptions {

    /** Âm lượng mặc định 70% cho cả Music và Sound Effects. */
    public static final float DEFAULT_VOLUME = 0.70f;

    /** Khóa thuộc tính lưu trong tệp cấu hình. */
    private static final String KEY_MUSIC_VOLUME = "musicVolume";
    private static final String KEY_SFX_VOLUME = "sfxVolume";

    /** Đường dẫn tệp cấu hình cục bộ nằm tại thư mục làm việc của trò chơi. */
    private final Path filePath;

    /** Âm lượng nhạc nền hiện tại [0.0..1.0]. */
    private float musicVolume = DEFAULT_VOLUME;

    /** Âm lượng hiệu ứng âm thanh (SFX, mob, UI, mưa, dưới nước) hiện tại [0.0..1.0]. */
    private float sfxVolume = DEFAULT_VOLUME;

    /** Khởi tạo với đường dẫn mặc định {@code options.properties} và tự động nạp nếu đã có tệp. */
    public AudioOptions() {
        this(Paths.get("options.properties"));
    }

    /** Khởi tạo với đường dẫn tùy chỉnh (hữu ích cho kiểm thử đơn vị). */
    public AudioOptions(Path filePath) {
        this.filePath = filePath;
        load();
    }

    /** Trả về âm lượng nhạc nền hiện tại trong đoạn [0.0..1.0]. */
    public float getMusicVolume() {
        return musicVolume;
    }

    /** Cập nhật âm lượng nhạc nền (kẹp an toàn trong [0.0..1.0]). */
    public void setMusicVolume(float value) {
        this.musicVolume = clampVolume(value);
    }

    /** Trả về âm lượng hiệu ứng âm thanh hiện tại trong đoạn [0.0..1.0]. */
    public float getSfxVolume() {
        return sfxVolume;
    }

    /** Cập nhật âm lượng hiệu ứng âm thanh (kẹp an toàn trong [0.0..1.0]). */
    public void setSfxVolume(float value) {
        this.sfxVolume = clampVolume(value);
    }

    /** Nạp giá trị từ tệp {@code options.properties}; giữ mặc định 70% nếu tệp thiếu hoặc lỗi định dạng. */
    public void load() {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(filePath)) {
            props.load(in);
            musicVolume = parseVolume(props.getProperty(KEY_MUSIC_VOLUME), DEFAULT_VOLUME);
            sfxVolume = parseVolume(props.getProperty(KEY_SFX_VOLUME), DEFAULT_VOLUME);
        } catch (IOException ignored) {
            musicVolume = DEFAULT_VOLUME;
            sfxVolume = DEFAULT_VOLUME;
        }
    }

    /** Ghi trạng thái âm lượng hiện tại xuống tệp {@code options.properties} để giữ giữa các lần mở game. */
    public void save() {
        if (filePath == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty(KEY_MUSIC_VOLUME, Float.toString(musicVolume));
        props.setProperty(KEY_SFX_VOLUME, Float.toString(sfxVolume));
        try (OutputStream out = Files.newOutputStream(filePath)) {
            props.store(out, "OurCraft Audio Settings");
        } catch (IOException ignored) {
            // Bỏ qua an toàn nếu thư mục chỉ đọc hoặc đang chạy kiểm thử hạn chế ghi đĩa.
        }
    }

    /** Kẹp giá trị âm lượng về đoạn [0.0..1.0], xử lý NaN/Infinity về mức mặc định. */
    private static float clampVolume(float value) {
        if (!Float.isFinite(value)) {
            return DEFAULT_VOLUME;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** Phân tích chuỗi số thực âm lượng từ tệp cấu hình, trả fallback nếu lỗi. */
    private static float parseVolume(String raw, float fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return clampVolume(Float.parseFloat(raw.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
