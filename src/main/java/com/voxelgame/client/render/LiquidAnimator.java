package com.voxelgame.client.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * Quản lý hoạt ảnh cho khối Nước (32 frame) và Dung nham (20 frame) bằng cách nạp
 * dải ảnh nguồn một lần duy nhất lúc khởi tạo, cắt sẵn thành từng {@link Pixmap}
 * {@code 16x16} định dạng {@code RGBA8888}, và cập nhật từng ô tương ứng trên
 * {@link Texture} atlas qua {@code glTexSubImage2D} mỗi {@code 0.10s} mà không tạo rác GC.
 */
public class LiquidAnimator implements Disposable {

    /** Số lượng khung hình hoạt ảnh chuẩn của nước từ {@code water_still.png} (16x512). */
    public static final int WATER_FRAME_COUNT = 32;

    /** Số lượng khung hình hoạt ảnh chuẩn của dung nham từ {@code lava_still.png} (16x320). */
    public static final int LAVA_FRAME_COUNT = 20;

    /** Thời lượng hiển thị mỗi khung hình chất lỏng (giây). */
    public static final float FRAME_DURATION_SECONDS = 0.10f;

    /** Danh sách tên ứng viên để tìm texture nước, khớp thứ tự ưu tiên của {@link Texture}. */
    private static final String[] WATER_TEXTURE_CANDIDATES = {
            "textures/water_still.png",
            "water_still.png",
            "fliflight-clear-water-1.0.0-resourcepack-1.21.4/assets/minecraft/textures/block/water_still.png",
            "textures/fliflight-clear-water-1.0.0-resourcepack-1.21.4/assets/minecraft/textures/block/water_still.png"
    };

    /** Danh sách tên ứng viên để tìm texture dung nham, khớp thứ tự ưu tiên của {@link Texture}. */
    private static final String[] LAVA_TEXTURE_CANDIDATES = {
            "textures/lava_still.png",
            "download.jpg",
            "textures/download.jpg",
            "lava.jpg",
            "lava.png"
    };

    private final Pixmap[] waterFrames = new Pixmap[WATER_FRAME_COUNT];
    private final Pixmap[] lavaFrames = new Pixmap[LAVA_FRAME_COUNT];

    private int currentWaterFrame;
    private int currentLavaFrame;
    private float frameTimer;
    private boolean disposed;

    /**
     * Khởi tạo bộ phát hoạt ảnh chất lỏng, tự động nạp thư viện native libGDX nếu cần,
     * giải mã {@code water_still.png} và {@code lava_still.png} một lần thành bộ đệm
     * khung hình {@code RGBA8888} trên CPU sẵn sàng đẩy lên GPU.
     */
    public LiquidAnimator() {
        GdxNativesLoader.load();
        try {
            loadLiquidFrames(waterFrames, WATER_FRAME_COUNT, Texture.TileStyle.WATER, WATER_TEXTURE_CANDIDATES);
            loadLiquidFrames(lavaFrames, LAVA_FRAME_COUNT, Texture.TileStyle.LAVA, LAVA_TEXTURE_CANDIDATES);
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    /**
     * Nạp dải ảnh chất lỏng từ tài nguyên một lần, tách thành các khung {@code 16x16 RGBA8888}
     * và áp dụng đúng quy tắc nhuộm màu/alpha của {@link Texture}. Nếu thiếu tệp nguồn,
     * sinh khung hình pixel dự phòng tất định để không bao giờ để rỗng bộ đệm.
     */
    private static void loadLiquidFrames(
            Pixmap[] outFrames,
            int targetFrameCount,
            Texture.TileStyle style,
            String... candidateNames
    ) {
        Pixmap source = Texture.loadPixmapFlexible(candidateNames);
        if (source == null) {
            populateProceduralLiquidFrames(outFrames, targetFrameCount, style);
            return;
        }
        try {
            int srcWidth = Math.max(1, source.getWidth());
            int srcHeight = Math.max(1, source.getHeight());
            // Mỗi khung trên dải dọc có chiều cao bằng chiều rộng (hoặc toàn ảnh nếu không đủ).
            int frameHeight = Math.min(srcWidth, srcHeight);
            int availableFrames = Math.max(1, srcHeight / frameHeight);

            for (int frameIndex = 0; frameIndex < targetFrameCount; frameIndex++) {
                int srcFrame = frameIndex % availableFrames;
                int srcY = Math.min(srcHeight - frameHeight, srcFrame * frameHeight);
                Pixmap frame = new Pixmap(Texture.TILE_SIZE, Texture.TILE_SIZE, Pixmap.Format.RGBA8888);
                frame.setBlending(Pixmap.Blending.None);

                for (int ty = 0; ty < Texture.TILE_SIZE; ty++) {
                    int sy = srcY + Math.min(frameHeight - 1, (int) (((ty + 0.5f) / Texture.TILE_SIZE) * frameHeight));
                    for (int tx = 0; tx < Texture.TILE_SIZE; tx++) {
                        int sx = Math.min(srcWidth - 1, (int) (((tx + 0.5f) / Texture.TILE_SIZE) * srcWidth));
                        int rgba = Texture.sampleStyledRgba(source, sx, sy, style);
                        frame.drawPixel(tx, ty, rgba);
                    }
                }
                outFrames[frameIndex] = frame;
            }
        } finally {
            source.dispose();
        }
    }

    /**
     * Sinh các khung hình pixel art dự phòng khi thiếu ảnh chất lỏng bên ngoài,
     * dịch pha theo chỉ số khung để vẫn có hiệu ứng chuyển động liên tục.
     */
    private static void populateProceduralLiquidFrames(
            Pixmap[] outFrames,
            int targetFrameCount,
            Texture.TileStyle style
    ) {
        boolean isWater = style == Texture.TileStyle.WATER;
        for (int frameIndex = 0; frameIndex < targetFrameCount; frameIndex++) {
            Pixmap frame = new Pixmap(Texture.TILE_SIZE, Texture.TILE_SIZE, Pixmap.Format.RGBA8888);
            frame.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < Texture.TILE_SIZE; y++) {
                int shiftedY = (y + frameIndex) % Texture.TILE_SIZE;
                for (int x = 0; x < Texture.TILE_SIZE; x++) {
                    int rgba;
                    if (isWater) {
                        int wave = ((x + shiftedY * 2) % 5 == 0) ? 16 : (((x * 7 + shiftedY * 11) % 11) - 5);
                        rgba = Texture.packRgba(42 + wave, 112 + wave, 225 + wave, Texture.OPAQUE_WATER_ALPHA);
                    } else {
                        int glow = ((x * 3 + shiftedY * 5) % 7 == 0) ? 35 : (((x * 13 + shiftedY * 17) % 31) - 15);
                        rgba = Texture.packRgba(230 + glow, 108 + glow, 22 + glow / 2, 255);
                    }
                    frame.drawPixel(x, y, rgba);
                }
            }
            outFrames[frameIndex] = frame;
        }
    }

    /**
     * Cộng dồn thời gian trôi qua và chuyển sang khung hình kế tiếp mỗi {@code 0.10s}.
     * Khi khung hình thay đổi và có ngữ cảnh OpenGL cùng {@code atlas != null},
     * tải trực tiếp hai ô {@link Texture#TILE_WATER} và {@link Texture#TILE_LAVA} lên GPU
     * theo cơ chế zero-GC (không cấp phát bộ nhớ mới).
     *
     * @param dt    khoảng thời gian giữa hai frame (giây)
     * @param atlas atlas khối dùng chung (có thể {@code null} khi kiểm thử CPU)
     */
    public void update(float dt, Texture atlas) {
        if (disposed || dt <= 0.0f) {
            return;
        }
        frameTimer += dt;
        boolean frameChanged = false;
        while (frameTimer >= FRAME_DURATION_SECONDS) {
            frameTimer -= FRAME_DURATION_SECONDS;
            currentWaterFrame = (currentWaterFrame + 1) % WATER_FRAME_COUNT;
            currentLavaFrame = (currentLavaFrame + 1) % LAVA_FRAME_COUNT;
            frameChanged = true;
        }
        if (frameChanged && atlas != null && Gdx.gl != null) {
            atlas.updateTile(Texture.TILE_WATER, waterFrames[currentWaterFrame]);
            atlas.updateTile(Texture.TILE_LAVA, lavaFrames[currentLavaFrame]);
        }
    }

    /** Trả về tổng số khung hình hoạt ảnh của nước (32). */
    public int getWaterFrameCount() {
        return WATER_FRAME_COUNT;
    }

    /** Trả về tổng số khung hình hoạt ảnh của dung nham (20). */
    public int getLavaFrameCount() {
        return LAVA_FRAME_COUNT;
    }

    /** Trả về chỉ số khung hình nước hiện tại trong khoảng {@code 0..31}. */
    public int getCurrentWaterFrame() {
        return currentWaterFrame;
    }

    /** Trả về chỉ số khung hình dung nham hiện tại trong khoảng {@code 0..19}. */
    public int getCurrentLavaFrame() {
        return currentLavaFrame;
    }

    /** Trả về Pixmap của một khung hình nước đã chuẩn hóa RGBA8888 để kiểm tra hoặc đọc pixel. */
    public Pixmap getWaterFramePixmap(int index) {
        return waterFrames[index];
    }

    /** Trả về Pixmap của một khung hình dung nham đã chuẩn hóa RGBA8888 để kiểm tra hoặc đọc pixel. */
    public Pixmap getLavaFramePixmap(int index) {
        return lavaFrames[index];
    }

    /** Giải phóng 100% bộ đệm {@link Pixmap} off-heap của cả nước và dung nham. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        disposeFrameArray(waterFrames);
        disposeFrameArray(lavaFrames);
    }

    /** Dọn sạch và gán null từng phần tử Pixmap trong mảng khung hình. */
    private static void disposeFrameArray(Pixmap[] frames) {
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] != null) {
                frames[i].dispose();
                frames[i] = null;
            }
        }
    }
}
