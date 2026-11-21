package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Hệ thống Quản lý Âm thanh & Nhạc nền (Background Music Manager) trong libGDX,
 * triển khai giao diện {@link Disposable}.
 *
 * Lớp này sử dụng đối tượng {@link Music} của libGDX (chạy trên OpenAL) để phát luồng (stream)
 * trực tiếp file MP3, hỗ trợ lặp vô hạn và điều chỉnh âm lượng mượt mà theo thời gian thực
 * mà không gây nghẽn luồng xử lý đồ họa (Render Thread).
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Che giấu toàn bộ cơ chế giải mã âm thanh OpenAL đa nền tảng, quản lý bộ đệm streaming
 *      và luồng phát ngầm của libGDX đằng sau các phương thức giao tiếp đơn giản:
 *      {@link #startBackgroundMusic()}, {@link #setVolume(float)}, {@link #changeVolume(float)},
 *      và {@link #dispose()}.
 * 2. Tính Đóng gói (Encapsulation):
 *    - Trạng thái âm lượng {@code volume} [0.0f, 1.0f] và đối tượng phần cứng {@code backgroundMusic}
 *      được đóng gói an toàn ở phạm vi {@code private}. Các thao tác thay đổi âm lượng luôn được
 *      kẹp trong đoạn hợp lệ thông qua setter duy nhất {@link #setVolume(float)}.
 * 3. Quản lý Vòng đời Tài nguyên (Resource Lifecycle):
 *    - Triển khai {@link Disposable} để đồng bộ cơ chế giải phóng tài nguyên OpenAL cùng với
 *      toàn bộ hệ sinh thái libGDX của dự án.
 */
public class AudioManager implements Disposable {

    /** Mức âm lượng hiện tại trong đoạn [0.0f, 1.0f], mặc định 70% (0.70f) */
    private float volume = 0.70f;

    /** Cờ trạng thái đánh dấu nhạc nền đang được bật */
    private boolean running = false;

    /** Đối tượng quản lý stream âm thanh phần cứng OpenAL của libGDX */
    private Music backgroundMusic;

    /** Con trỏ tập tin đại diện cho file nhạc nền MP3 */
    private FileHandle musicFileHandle;

    /**
     * Khởi tạo AudioManager:
     * Tự động tìm kiếm file nhạc MP3 trong Classpath (`src/main/resources`), thư mục dự án
     * hoặc các thư mục con, sau đó khởi tạo đối tượng {@link Music}.
     */
    public AudioManager() {
        // Tự động tìm kiếm linh hoạt file nhạc "1-08. Minecraft.mp3"
        this.musicFileHandle = findMp3FileHandleFlexible(
                "1-08. Minecraft.mp3",
                "audio/1-08. Minecraft.mp3",
                "music/1-08. Minecraft.mp3",
                "minecraft.mp3",
                "bgm.mp3"
        );

        if (this.musicFileHandle != null) {
            try {
                this.backgroundMusic = Gdx.audio.newMusic(this.musicFileHandle);
                this.backgroundMusic.setLooping(true);
                this.backgroundMusic.setVolume(this.volume);
            } catch (Exception e) {
                System.err.println("[AudioManager] Không thể khởi tạo luồng âm thanh từ file: "
                        + this.musicFileHandle.path() + " - Lỗi: " + e.getMessage());
                this.backgroundMusic = null;
            }
        }
    }

    /**
     * Bắt đầu phát lặp lại (Loop) nhạc nền vô hạn trên luồng âm thanh OpenAL.
     * Nếu chưa nạp được nhạc, phương thức sẽ thử tìm kiếm lại một lần nữa trước khi báo lỗi.
     */
    public void startBackgroundMusic() {
        if (backgroundMusic == null) {
            // Thử quét lại tập tin phòng trường hợp file nhạc vừa được người dùng copy vào
            if (musicFileHandle == null) {
                musicFileHandle = findMp3FileHandleFlexible(
                        "1-08. Minecraft.mp3",
                        "audio/1-08. Minecraft.mp3",
                        "music/1-08. Minecraft.mp3",
                        "minecraft.mp3",
                        "bgm.mp3"
                );
            }
            if (musicFileHandle != null) {
                try {
                    backgroundMusic = Gdx.audio.newMusic(musicFileHandle);
                    backgroundMusic.setLooping(true);
                    backgroundMusic.setVolume(this.volume);
                } catch (Exception e) {
                    System.err.println("[AudioManager] Khởi tạo Music thất bại: " + e.getMessage());
                    backgroundMusic = null;
                }
            }
        }

        if (backgroundMusic == null) {
            System.err.println("[AudioManager] Không tìm thấy file nhạc '.mp3'. "
                    + "Hãy đặt file '1-08. Minecraft.mp3' vào thư mục 'src/main/resources/audio/' hoặc thư mục gốc của project.");
            return;
        }

        if (running && backgroundMusic.isPlaying()) {
            return;
        }

        try {
            backgroundMusic.setLooping(true);
            backgroundMusic.setVolume(this.volume);
            backgroundMusic.play();
            running = true;
            System.out.println("[AudioManager] Bắt đầu phát lặp nhạc nền thành công (Âm lượng: " + getVolumePercent() + "%).");
        } catch (Exception e) {
            System.err.println("[AudioManager] Lỗi khi phát nhạc nền: " + e.getMessage());
        }
    }

    /**
     * Thiết lập mức âm lượng nhạc nền theo thời gian thực (từ 0.0f = Tắt tiếng đến 1.0f = 100%).
     * Được gọi trực tiếp khi người chơi bấm nút [-]/[+] hoặc kéo thanh trượt Volume trong Pause Menu.
     *
     * @param newVolume giá trị âm lượng mới
     */
    public void setVolume(float newVolume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, newVolume));
        if (backgroundMusic != null) {
            try {
                backgroundMusic.setVolume(this.volume);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Tăng hoặc giảm âm lượng một khoảng {@code delta} (ví dụ: +0.10f hoặc -0.10f).
     *
     * @param delta mức thay đổi âm lượng
     */
    public void changeVolume(float delta) {
        setVolume(this.volume + delta);
    }

    /**
     * Lấy mức âm lượng hiện tại trong khoảng [0.0f, 1.0f].
     *
     * @return giá trị âm lượng dạng số thực
     */
    public float getVolume() {
        return volume;
    }

    /**
     * Lấy mức âm lượng hiện tại tính theo phần trăm nguyên (0 đến 100) để hiển thị trên Pause Menu.
     *
     * @return phần trăm âm lượng (0 - 100)
     */
    public int getVolumePercent() {
        return Math.round(volume * 100.0f);
    }

    /**
     * Kiểm tra trạng thái phát nhạc hiện tại.
     *
     * @return true nếu nhạc nền đang phát
     */
    public boolean isPlaying() {
        return backgroundMusic != null && backgroundMusic.isPlaying();
    }

    /**
     * Giải phóng toàn bộ tài nguyên âm thanh OpenAL khi đóng game,
     * triển khai phương thức của giao diện {@link Disposable}.
     */
    @Override
    public void dispose() {
        running = false;
        if (backgroundMusic != null) {
            try {
                if (backgroundMusic.isPlaying()) {
                    backgroundMusic.stop();
                }
                backgroundMusic.dispose();
            } catch (Exception ignored) {
            } finally {
                backgroundMusic = null;
            }
        }
    }

    /**
     * Phương thức tương thích ngược với giao diện Cleanable cũ, ủy quyền trực tiếp cho {@link #dispose()}.
     */
    public void cleanup() {
        dispose();
    }

    /**
     * Cơ chế tìm kiếm linh hoạt con trỏ file `.mp3` qua API {@link FileHandle} của libGDX:
     * 1. Tìm trong Internal/Classpath assets (`src/main/resources/`)
     * 2. Tìm trong thư mục cục bộ (Local working directory)
     * 3. Tìm theo các đường dẫn cơ sở thông dụng (`audio/`, `music/`, `src/main/resources/audio/`)
     * 4. Quét tự động cây thư mục tối đa 4 tầng để nhận diện bất kỳ file `.mp3` nào.
     *
     * @param candidateNames danh sách tên file hoặc đường dẫn ứng viên
     * @return {@link FileHandle} nếu tìm thấy, hoặc {@code null} nếu không tìm thấy
     */
    private FileHandle findMp3FileHandleFlexible(String... candidateNames) {
        String[] baseDirs = {
                "",
                "src/main/resources/",
                "src/main/resources/audio/",
                "audio/",
                "music/"
        };

        // 1. Kiểm tra qua cơ chế FileHandle của libGDX (Internal & Classpath)
        for (String name : candidateNames) {
            try {
                FileHandle fh = Gdx.files.internal(name);
                if (fh.exists() && !fh.isDirectory()) {
                    System.out.println("[AudioManager] Đã tìm thấy nhạc nền qua Gdx.files.internal: " + fh.path());
                    return fh;
                }
            } catch (Exception ignored) {
            }

            try {
                String cleanClasspathPath = name.startsWith("/") ? name.substring(1) : name;
                FileHandle fh = Gdx.files.classpath(cleanClasspathPath);
                if (fh.exists() && !fh.isDirectory()) {
                    System.out.println("[AudioManager] Đã tìm thấy nhạc nền qua Gdx.files.classpath: " + fh.path());
                    return fh;
                }
            } catch (Exception ignored) {
            }

            // 2. Kiểm tra theo các thư mục trên ổ cứng (File hệ thống)
            for (String dir : baseDirs) {
                File f = new File(dir + name);
                if (f.exists() && f.isFile()) {
                    System.out.println("[AudioManager] Đã nạp nhạc nền từ ổ cứng: " + f.getPath());
                    return Gdx.files.absolute(f.getAbsolutePath());
                }
            }
        }

        // 3. Nếu người dùng đặt file .mp3 ở một thư mục con bất kỳ trong project, tự động quét tìm file .mp3 đầu tiên
        try {
            Path rootDir = Path.of(".");
            if (Files.exists(rootDir)) {
                try (Stream<Path> stream = Files.walk(rootDir, 4)) {
                    Path foundMp3 = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".mp3"))
                            .findFirst()
                            .orElse(null);

                    if (foundMp3 != null) {
                        System.out.println("[AudioManager] Tự động phát hiện và nạp nhạc nền: " + foundMp3);
                        return Gdx.files.absolute(foundMp3.toAbsolutePath().toString());
                    }
                }
            }
        } catch (IOException ignored) {
        }

        return null;
    }
}
