package com.voxelgame.engine;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Hệ thống Quản lý Âm thanh & Nhạc nền (Background Music Manager) triển khai {@link Cleanable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Che giấu toàn bộ quy trình giải mã luồng bit MP3 (`Bitstream`, `Decoder`), biến đổi tín hiệu số
 *      PCM 16-bit Little-Endian và điều khiển luồng chạy ngầm (`Daemon Thread`) đằng sau các phương thức
 *      giao tiếp đơn giản: `startBackgroundMusic()`, `setVolume()`, `changeVolume()`, `cleanup()`.
 * 2. Tính Đóng gói (Encapsulation):
 *    - Các trạng thái luồng âm thanh (`volume`, `running`, `bgmThread`, `cachedMp3Bytes`) được khóa `private`
 *      và kiểm soát đồng bộ giữa luồng UI và luồng âm thanh bằng từ khóa `volatile`, đảm bảo giới hạn
 *      âm lượng luôn an toàn trong đoạn `[0.0f, 1.0f]`.
 */
public class AudioManager implements Cleanable {

    // Biến volatile đảm bảo khi luồng giao diện (UI) thay đổi âm lượng hoặc tắt nhạc,
    // luồng phát nhạc ngầm (bgmThread) sẽ nhận được giá trị mới ngay lập tức.
    private volatile float volume = 0.70f; // Âm lượng mặc định: 70% (0.70f)
    private volatile boolean running = false;

    private Thread bgmThread;
    private final byte[] cachedMp3Bytes;

    public AudioManager() {
        // Tự động tìm và nạp file nhạc "1-08. Minecraft.mp3" vào bộ nhớ đệm (RAM)
        this.cachedMp3Bytes = loadMp3BytesFlexible(
                "1-08. Minecraft.mp3",
                "audio/1-08. Minecraft.mp3",
                "music/1-08. Minecraft.mp3",
                "minecraft.mp3",
                "bgm.mp3"
        );
    }

    /**
     * Bắt đầu phát lặp lại (Loop) nhạc nền trên luồng chạy ngầm.
     */
    public void startBackgroundMusic() {
        if (cachedMp3Bytes == null || cachedMp3Bytes.length == 0) {
            System.err.println("[AudioManager] Không tìm thấy file nhạc '.mp3'. "
                    + "Hãy đặt file '1-08. Minecraft.mp3' vào thư mục 'src/main/resources/audio/' hoặc thư mục gốc của project.");
            return;
        }

        if (running) {
            return;
        }
        running = true;

        bgmThread = new Thread(this::musicLoopWorker, "VoxelGame-BGM-Thread");
        // Đặt làm Daemon Thread để nếu cửa sổ game đóng đột ngột, luồng nhạc cũng tự động kết thúc theo JVM
        bgmThread.setDaemon(true);
        bgmThread.start();
    }

    /**
     * Vòng lặp giải mã MP3 -> PCM 16-bit -> Khuếch đại theo biến `volume` -> Phát ra thiết bị âm thanh.
     */
    private void musicLoopWorker() {
        while (running) {
            SourceDataLine soundLine = null;
            Bitstream bitstream = null;

            try (InputStream bais = new BufferedInputStream(new ByteArrayInputStream(cachedMp3Bytes))) {
                bitstream = new Bitstream(bais);
                Decoder decoder = new Decoder();

                Header frameHeader;
                // Đọc lần lượt từng khung âm thanh (Audio Frame) trong file MP3
                while (running && (frameHeader = bitstream.readFrame()) != null) {
                    // Giải mã khung MP3 hiện tại thành các mẫu sóng âm PCM 16-bit
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
                    short[] pcmSamples = output.getBuffer();
                    int sampleLength = output.getBufferLength();

                    // Khởi tạo đường truyền âm thanh phần cứng (SourceDataLine) ở khung đầu tiên
                    if (soundLine == null) {
                        int sampleRate = decoder.getOutputFrequency();
                        int channels = decoder.getOutputChannels();
                        // Định dạng PCM 16-bit, có dấu (signed), thứ tự byte Little-Endian
                        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
                        soundLine = AudioSystem.getSourceDataLine(format);
                        soundLine.open(format, 8192);
                        soundLine.start();
                    }

                    // Lấy mức âm lượng hiện tại (0.0f -> 1.0f)
                    float currentVol = this.volume;

                    // Chuyển đổi mảng mẫu 16-bit (short) sang mảng byte (Little-Endian) đồng thời nhân với hệ số âm lượng
                    byte[] pcmBytes = new byte[sampleLength * 2];
                    for (int i = 0; i < sampleLength; i++) {
                        int scaledSample = Math.round(pcmSamples[i] * currentVol);
                        // Giới hạn trong khoảng hợp lệ của kiểu short 16-bit [-32768, 32767] để tránh rè tiếng (Clipping)
                        if (scaledSample > Short.MAX_VALUE) {
                            scaledSample = Short.MAX_VALUE;
                        } else if (scaledSample < Short.MIN_VALUE) {
                            scaledSample = Short.MIN_VALUE;
                        }

                        // Tách số nguyên 16-bit thành 2 byte theo chuẩn Little-Endian (Byte thấp trước, Byte cao sau)
                        pcmBytes[i * 2] = (byte) (scaledSample & 0xFF);
                        pcmBytes[i * 2 + 1] = (byte) ((scaledSample >> 8) & 0xFF);
                    }

                    // Đẩy khối dữ liệu PCM đã chỉnh âm lượng ra card âm thanh
                    soundLine.write(pcmBytes, 0, pcmBytes.length);

                    // Đánh dấu đã xử lý xong khung MP3 hiện tại để tiến tới khung kế tiếp
                    bitstream.closeFrame();
                }

                // Nếu bài hát đã chạy đến cuối file mà game vẫn đang mở, đợi hết phần âm còn lại trong bộ đệm rồi lặp lại
                if (soundLine != null && running) {
                    soundLine.drain();
                }
            } catch (Exception e) {
                System.err.println("[AudioManager] Lỗi khi phát nhạc nền MP3: " + e.getMessage());
                break;
            } finally {
                if (soundLine != null) {
                    try {
                        soundLine.stop();
                        soundLine.close();
                    } catch (Exception ignored) {
                    }
                }
                if (bitstream != null) {
                    try {
                        bitstream.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * Thiết lập mức âm lượng nhạc nền theo thời gian thực (từ 0.0f = Tắt tiếng đến 1.0f = 100%).
     * Được gọi trực tiếp khi người chơi bấm nút [-]/[+] hoặc kéo thanh trượt Volume trong Pause Menu.
     */
    public void setVolume(float newVolume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, newVolume));
    }

    /**
     * Tăng hoặc giảm âm lượng một khoảng `delta` (ví dụ: +0.10f hoặc -0.10f).
     */
    public void changeVolume(float delta) {
        setVolume(this.volume + delta);
    }

    /**
     * Lấy mức âm lượng hiện tại trong khoảng [0.0f, 1.0f].
     */
    public float getVolume() {
        return volume;
    }

    /**
     * Lấy mức âm lượng hiện tại tính theo phần trăm nguyên (0 đến 100) để hiển thị trên Pause Menu.
     */
    public int getVolumePercent() {
        return Math.round(volume * 100.0f);
    }

    /**
     * Ghi đè phương thức của giao diện {@link Cleanable}:
     * Dừng luồng nhạc nền và giải phóng thiết bị âm thanh ngay lập tức khi đóng cửa sổ game.
     */
    @Override
    public void cleanup() {
        running = false;
        if (bgmThread != null && bgmThread.isAlive()) {
            bgmThread.interrupt();
            try {
                bgmThread.join(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Tìm kiếm linh hoạt file `.mp3` trong Classpath (`src/main/resources`), thư mục gốc của project,
     * hoặc bất kỳ file `.mp3` nào được người dùng copy vào thư mục dự án.
     */
    private byte[] loadMp3BytesFlexible(String... candidateNames) {
        String[] baseDirs = {
                "",
                "src/main/resources/",
                "src/main/resources/audio/",
                "audio/",
                "music/"
        };

        // 1. Kiểm tra theo danh sách đường dẫn ưu tiên
        for (String name : candidateNames) {
            String resPath = name.startsWith("/") ? name : "/" + name;
            try (InputStream is = AudioManager.class.getResourceAsStream(resPath)) {
                if (is != null) {
                    System.out.println("[AudioManager] Đã nạp nhạc nền từ Classpath: " + resPath);
                    return is.readAllBytes();
                }
            } catch (IOException ignored) {
            }

            for (String dir : baseDirs) {
                File f = new File(dir + name);
                if (f.exists() && f.isFile()) {
                    try {
                        System.out.println("[AudioManager] Đã nạp nhạc nền từ ổ cứng: " + f.getPath());
                        return Files.readAllBytes(f.toPath());
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        // 2. Nếu người dùng đặt file .mp3 ở một thư mục con bất kỳ trong project, tự động quét tìm file .mp3 đầu tiên
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
                    return Files.readAllBytes(foundMp3);
                }
            } catch (IOException ignored) {
            }
        }

        return null;
    }
}
