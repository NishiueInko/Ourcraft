package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.voxelgame.entity.Block;

import java.util.concurrent.ThreadLocalRandom;

/** Phát danh sách nhạc theo vòng xáo và hiệu ứng âm thanh của các tương tác voxel. */
public class AudioManager implements Disposable {
    /** Đường dẫn đóng gói của năm bài nhạc đã chọn. */
    private static final String[] TRACKS = {
            "calm1.ogg", "calm3.ogg", "hal3.ogg", "piano2.ogg", "piano3.ogg"
    };
    /** Khoảng im lặng ba giây, đo bằng đồng hồ đơn điệu kể từ lúc nhận callback. */
    private static final long TRACK_GAP_NANOS = 3_000_000_000L;
    /** Khoảng chờ trước khi thử lại nếu mọi nguồn phát OpenAL đang bận. */
    private static final long RAIN_RETRY_NANOS = 200_000_000L;
    /** Gốc tài nguyên âm thanh bên trong classpath. */
    private static final String MUSIC_ROOT = "audio/music/";
    private static final String SFX_ROOT = "audio/sfx/";

    /** Các nhóm mẫu ngắn phát theo sự kiện và theo vật liệu. */
    public enum Effect {
        DIG_GRASS("dig/grass", 4), DIG_GRAVEL("dig/gravel", 4), DIG_SAND("dig/sand", 4),
        DIG_SNOW("dig/snow", 4), DIG_STONE("dig/stone", 4), DIG_WOOD("dig/wood", 4),
        STEP_GRASS("step/grass", 6), STEP_GRAVEL("step/gravel", 4), STEP_SAND("step/sand", 5),
        STEP_SNOW("step/snow", 4), STEP_STONE("step/stone", 6), STEP_WOOD("step/wood", 6),
        GLASS_BREAK("random/glass", 3), PICKUP("random/pop"), CLICK("random/click"),
        CACTUS_HURT("damage/hit", 3), SWIM("liquid/swim", 18),
        WATER_ENTER("liquid/splash", "liquid/splash2"), DIVE("ambient/underwater/enter", 3),
        SURFACE("ambient/underwater/exit", 3), WATER_NEARBY("liquid/water"),
        LAVA_NEARBY("liquid/lava"), LAVA_POP("liquid/lavapop");

        /** Đường dẫn tương đối của các biến thể trong nhóm, chưa gồm đuôi OGG. */
        private final String[] paths;

        /** Gom mẫu có tên riêng vào cùng một sự kiện. */
        Effect(String... paths) {
            this.paths = paths;
        }

        /** Gom dãy mẫu được đánh số từ 1 trong gói nguồn. */
        Effect(String prefix, int count) {
            paths = new String[count];
            for (int i = 0; i < count; i++) {
                paths[i] = prefix + (i + 1);
            }
        }
    }

    /** Mẫu đào và bước chân; băng dùng riêng tiếng kính ở thời điểm vỡ. */
    private enum Material {
        GRASS(Effect.DIG_GRASS, Effect.STEP_GRASS),
        GRAVEL(Effect.DIG_GRAVEL, Effect.STEP_GRAVEL),
        SAND(Effect.DIG_SAND, Effect.STEP_SAND),
        SNOW(Effect.DIG_SNOW, Effect.STEP_SNOW),
        STONE(Effect.DIG_STONE, Effect.STEP_STONE),
        WOOD(Effect.DIG_WOOD, Effect.STEP_WOOD),
        ICE(Effect.DIG_STONE, Effect.STEP_STONE);

        private final Effect dig;
        private final Effect step;

        /** Ghép hai nhóm mẫu mô tả cùng một vật liệu. */
        Material(Effect dig, Effect step) {
            this.dig = dig;
            this.step = step;
        }
    }

    /** Tái sử dụng bảng mẫu và thứ tự bài trong suốt phiên chơi. */
    private final Sound[][] sounds = new Sound[Effect.values().length][];
    private final MobSoundBank mobSounds;
    private final FileHandle[] trackFiles = new FileHandle[TRACKS.length];
    private final int[] trackOrder = new int[TRACKS.length];
    /** Tọa độ người nghe dùng chung cho mọi sự kiện mob trong cùng lượt cập nhật. */
    private float listenerX;
    private float listenerY;
    private float listenerZ;
    private boolean mobListenerReady;
    /** Các luồng nhạc, âm nền dưới nước và tiếng mưa hoạt động độc lập. */
    private Music backgroundMusic;
    private Music underwaterAmbience;
    private Music rainAmbience;
    private float rainGain;
    private boolean rainPlaying;
    private boolean rainPaused;
    private long rainRetryAt;
    /** Con trỏ vòng xáo, bài vừa mở thành công và mốc hết khoảng nghỉ. */
    private int nextTrack = TRACKS.length;
    private int lastTrack = -1;
    private long trackDeadline;
    /** Các cờ vòng đời giúp chia sẻ đường cập nhật với game đang Pause. */
    private boolean running;
    private boolean waitingForTrack;
    private boolean underwaterPlaying;
    private boolean disposed;
    /** Âm lượng chung của nhạc nền và mưa, điều khiển bằng Pause Menu. */
    private float volume = 0.70f;

    /** Nạp trước mẫu ngắn và xác định các tài nguyên nhạc đã đóng gói. */
    public AudioManager() {
        for (int i = 0; i < TRACKS.length; i++) {
            trackFiles[i] = findAsset(MUSIC_ROOT + TRACKS[i]);
        }
        for (Effect effect : Effect.values()) {
            Sound[] variants = new Sound[effect.paths.length];
            sounds[effect.ordinal()] = variants;
            for (int i = 0; i < variants.length; i++) {
                FileHandle file = findAsset(SFX_ROOT + effect.paths[i] + ".ogg");
                if (file != null) {
                    try {
                        variants[i] = Gdx.audio.newSound(file);
                    } catch (RuntimeException error) {
                        logLoadError(file, error);
                    }
                }
            }
        }
        FileHandle rainFile = findAsset("audio/weather/rain.wav");
        if (rainFile != null) {
            try {
                rainAmbience = Gdx.audio.newMusic(rainFile);
                rainAmbience.setLooping(true);
                rainAmbience.setVolume(0f);
            } catch (RuntimeException error) {
                logLoadError(rainFile, error);
                if (rainAmbience != null) {
                    rainAmbience.dispose();
                    rainAmbience = null;
                }
            }
        }
        FileHandle underwater = findAsset(SFX_ROOT + "ambient/underwater/underwater_ambience.ogg");
        if (underwater != null) {
            try {
                underwaterAmbience = Gdx.audio.newMusic(underwater);
                underwaterAmbience.setLooping(true);
                underwaterAmbience.setVolume(0.18f);
            } catch (RuntimeException error) {
                logLoadError(underwater, error);
                if (underwaterAmbience != null) {
                    underwaterAmbience.dispose();
                    underwaterAmbience = null;
                }
            }
        }
        mobSounds = new MobSoundBank();
    }

    /** Chỉ tìm tài nguyên có đường dẫn cố định trong classpath của trò chơi. */
    static FileHandle findAsset(String path) {
        FileHandle file = Gdx.files.classpath(path);
        return file.exists() ? file : null;
    }

    /** Ghi đường dẫn mẫu lỗi để có thể phát hiện tài nguyên hỏng hoặc thiếu bộ giải mã. */
    static void logLoadError(FileHandle file, RuntimeException error) {
        System.err.println("[AudioManager] Không mở được " + file.path() + " - " + error.getMessage());
    }

    /** Bắt đầu vòng nhạc đầu tiên, giữ nguyên bài hiện tại nếu được gọi thêm. */
    public void startBackgroundMusic() {
        if (!running && !disposed) {
            running = true;
            playNextTrack();
        }
    }

    /** Kiểm tra hết khoảng nghỉ và đổi bài ngoài callback cập nhật OpenAL của libGDX. */
    public void update() {
        if (waitingForTrack && System.nanoTime() - trackDeadline >= 0L) {
            waitingForTrack = false;
            playNextTrack();
        }
        if (!rainPlaying && !rainPaused && rainGain > 0f) {
            syncRainPlayback();
        }
    }

    /** Tạo hoán vị mới bằng Fisher–Yates và tránh trùng ngay với bài vừa phát. */
    private void shuffleTracks() {
        for (int i = 0; i < trackOrder.length; i++) {
            trackOrder[i] = i;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = trackOrder.length - 1; i > 0; i--) {
            swapTracks(i, random.nextInt(i + 1));
        }
        if (trackOrder[0] == lastTrack) {
            swapTracks(0, random.nextInt(1, trackOrder.length));
        }
        nextTrack = 0;
    }

    /** Đổi chỗ hai vị trí trong vòng nhạc. */
    private void swapTracks(int first, int second) {
        int previous = trackOrder[first];
        trackOrder[first] = trackOrder[second];
        trackOrder[second] = previous;
    }

    /** Nếu cuối vòng chỉ có tệp lỗi, vẫn thử đủ cả vòng mới để tìm bài còn dùng được. */
    private void playNextTrack() {
        disposeCurrentTrack();
        int maxAttempts = trackOrder.length - nextTrack + trackOrder.length;
        for (int attempts = 0; attempts < maxAttempts; attempts++) {
            if (nextTrack == trackOrder.length) {
                shuffleTracks();
            }
            int track = trackOrder[nextTrack++];
            FileHandle file = trackFiles[track];
            if (file == null) {
                continue;
            }
            try {
                backgroundMusic = Gdx.audio.newMusic(file);
                backgroundMusic.setLooping(false);
                backgroundMusic.setVolume(volume);
                backgroundMusic.setOnCompletionListener(this::onTrackComplete);
                backgroundMusic.play();
                lastTrack = track;
                return;
            } catch (RuntimeException error) {
                logLoadError(file, error);
                disposeCurrentTrack();
                trackFiles[track] = null;
            }
        }
        running = false;
    }

    /** Lập khoảng nghỉ khi luồng âm thanh báo đã phát hết bài. */
    private void onTrackComplete(Music track) {
        if (running && track == backgroundMusic) {
            trackDeadline = System.nanoTime() + TRACK_GAP_NANOS;
            waitingForTrack = true;
        }
    }

    /** Tháo callback trước khi giải phóng luồng nhạc cũ. */
    private void disposeCurrentTrack() {
        if (backgroundMusic != null) {
            backgroundMusic.setOnCompletionListener(null);
            backgroundMusic.dispose();
            backgroundMusic = null;
        }
    }

    /** Áp dụng ngay âm lượng và dùng lại giá trị này cho những bài tiếp theo. */
    public void setVolume(float newVolume) {
        volume = Math.max(0f, Math.min(1f, newVolume));
        if (backgroundMusic != null) {
            backgroundMusic.setVolume(volume);
        }
        if (rainAmbience != null) {
            rainAmbience.setVolume(volume * rainGain);
        }
    }

    /** Áp dụng cường độ mưa cục bộ sau khi đã xét biome và mái che. */
    public void setRainGain(float gain) {
        if (rainGain != gain) {
            rainGain = gain;
            if (rainAmbience != null) {
                rainAmbience.setVolume(volume * rainGain);
            }
            syncRainPlayback();
        }
    }

    /** Tạm dừng luồng mưa khi mở menu và giữ vị trí phát để tiếp tục. */
    public void setRainPaused(boolean paused) {
        rainPaused = paused;
        syncRainPlayback();
    }

    /** Chỉ đổi trạng thái OpenAL khi bắt đầu, tạnh mưa hoặc bật/tắt Pause. */
    private void syncRainPlayback() {
        boolean shouldPlay = !rainPaused && rainGain > 0f;
        if (rainAmbience != null && shouldPlay != rainPlaying) {
            if (shouldPlay) {
                long now = System.nanoTime();
                if (rainRetryAt != 0L && now - rainRetryAt < 0L) {
                    return;
                }
                rainAmbience.play();
                rainPlaying = rainAmbience.isPlaying();
                rainRetryAt = rainPlaying ? 0L : now + RAIN_RETRY_NANOS;
            } else {
                rainAmbience.pause();
                rainPlaying = false;
            }
        }
    }

    /** Tăng hoặc giảm âm lượng theo bước của giao diện. */
    public void changeVolume(float delta) {
        setVolume(volume + delta);
    }

    /** Đọc âm lượng hiện tại trong khoảng từ 0 đến 1. */
    public float getVolume() {
        return volume;
    }

    /** Quy đổi âm lượng sang phần trăm nguyên cho Pause Menu. */
    public int getVolumePercent() {
        return Math.round(volume * 100f);
    }

    /** Kiểm tra xem bài nhạc hiện tại đang phát hay đang ở khoảng nghỉ. */
    public boolean isPlaying() {
        return backgroundMusic != null && backgroundMusic.isPlaying();
    }

    /** Chọn vật liệu theo nhóm âm thanh, độc lập với biome của cột. */
    private static Material materialOf(Block block) {
        if (block == Block.DIRT || block == Block.GRAVEL) {
            return Material.GRAVEL;
        }
        if (block == Block.SAND) {
            return Material.SAND;
        }
        if (block == Block.SNOW_LAYER || block == Block.SNOW_BLOCK) {
            return Material.SNOW;
        }
        if (block == Block.OAK_LOG || block == Block.SPRUCE_LOG || block == Block.CHEST) {
            return Material.WOOD;
        }
        if (block == Block.ICE) {
            return Material.ICE;
        }
        if (block == Block.STONE || block == Block.BEDROCK || block == Block.SANDSTONE) {
            return Material.STONE;
        }
        return Material.GRASS;
    }

    /** Phát tiếng chạm nhẹ theo nhịp giữ chuột đào. */
    public void playMining(Block block) {
        playEffect(materialOf(block).step, 0.19f);
    }

    /** Phát tiếng khối vỡ sau khi thay đổi trong thế giới đã được ghi. */
    public void playBreak(Block block, float gain) {
        Material material = materialOf(block);
        playEffect(material == Material.ICE ? Effect.GLASS_BREAK : material.dig, 0.65f * gain);
    }

    /** Phát tiếng đặt khối với cao độ thấp hơn tiếng phá. */
    public void playPlace(Block block) {
        playSample(materialOf(block).dig, 0.58f, 0.8f);
    }

    /** Phát tiếng bước chân, giảm âm lượng khi rón rén. */
    public void playStep(Block block, boolean sneaking) {
        playEffect(materialOf(block).step, sneaking ? 0.10f : 0.28f);
    }

    /** Phát tiếng tiếp đất của người chơi hoặc cát/sỏi rơi. */
    public void playLanding(Block block, float gain) {
        playEffect(materialOf(block).step, 0.51f * gain);
    }

    /** Phát một biến thể của sự kiện ở cao độ gần với mẫu nguồn. */
    public void playEffect(Effect effect, float gain) {
        playSample(effect, gain, 1f);
    }

    /** Cập nhật tọa độ người nghe trước lượt mô phỏng phát sự kiện mob. */
    public void setMobListener(float x, float y, float z) {
        listenerX = x;
        listenerY = y;
        listenerZ = z;
        mobListenerReady = true;
    }

    /** Phát một biến thể tại tọa độ thế giới, dùng khoảng nghe mặc định khi bán kính không dương. */
    public void playMobSound(String mobId, MobSoundEvent event, float x, float y, float z,
                             float maxRange, float pitchMultiplier) {
        if (disposed || !mobListenerReady || event == null) {
            return;
        }
        float dx = x - listenerX;
        float dy = y - listenerY;
        float dz = z - listenerZ;
        float range = maxRange > 0f ? maxRange : event.defaultRange();
        float gain = mobGain(dx * dx + dy * dy + dz * dz, range);
        if (gain <= 0f) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Sound sound = mobSounds.choose(mobId, event, random);
        if (sound != null) {
            sound.play(gain, mobPitch(pitchMultiplier, random.nextFloat()), 0f);
        }
    }

    /** Suy giảm tuyến tính từ nguồn đến rìa, kiểm tra bình phương trước khi tính căn. */
    static float mobGain(float distanceSquared, float maxRange) {
        if (!(distanceSquared >= 0f && distanceSquared < (double) maxRange * maxRange)
                || !(maxRange > 0f && Float.isFinite(maxRange))) {
            return 0f;
        }
        return 1f - (float) Math.sqrt(distanceSquared) / maxRange;
    }

    /** Biến thiên cao độ ±6% quanh hệ số yêu cầu, giới hạn trong miền phát của libGDX. */
    static float mobPitch(float pitchMultiplier, float randomUnit) {
        float multiplier = pitchMultiplier > 0f && Float.isFinite(pitchMultiplier) ? pitchMultiplier : 1f;
        return Math.max(0.5f, Math.min(2f, multiplier * (0.94f + 0.12f * randomUnit)));
    }

    /** Tận dụng bộ đệm OpenAL đã nạp và tìm biến thể kế nếu có tệp lỗi. */
    private void playSample(Effect effect, float gain, float pitch) {
        if (disposed || gain <= 0f) {
            return;
        }
        Sound[] variants = sounds[effect.ordinal()];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int index = random.nextInt(variants.length);
        for (int i = 0; i < variants.length; i++) {
            Sound sample = variants[index];
            if (sample != null) {
                sample.play(gain, pitch * (0.94f + 0.12f * random.nextFloat()), 0f);
                return;
            }
            index = (index + 1) % variants.length;
        }
    }

    /** Điều khiển vòng âm nền dưới nước riêng với luồng nhạc nền chính. */
    public void setUnderwater(boolean underwater) {
        if (underwaterAmbience != null && underwater != underwaterPlaying) {
            underwaterPlaying = underwater;
            if (underwater) {
                underwaterAmbience.play();
            } else {
                underwaterAmbience.stop();
            }
        }
    }

    /** Hủy bộ hẹn giờ và giải phóng mỗi luồng, mỗi bộ đệm đúng một lần. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        running = false;
        waitingForTrack = false;
        mobSounds.dispose();
        disposeCurrentTrack();
        if (rainAmbience != null) {
            rainAmbience.dispose();
            rainAmbience = null;
        }
        if (underwaterAmbience != null) {
            underwaterAmbience.dispose();
            underwaterAmbience = null;
        }
        for (Sound[] variants : sounds) {
            for (Sound sound : variants) {
                if (sound != null) {
                    sound.dispose();
                }
            }
        }
    }

    /** Điểm gọi tương thích của hệ thống âm thanh cũ. */
    public void cleanup() {
        dispose();
    }
}
