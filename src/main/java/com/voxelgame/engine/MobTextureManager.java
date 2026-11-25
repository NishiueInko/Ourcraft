package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/** Sở hữu cache texture thực thể riêng, ngoài atlas của khối. */
public final class MobTextureManager implements Disposable {
    /** Ảnh GPU và kích thước thật dùng để đổi tọa độ pixel sang UV. */
    record Skin(Texture texture, int width, int height, boolean fallback) {
        /** Dùng đơn vị ảnh 0 chung với shader thế giới. */
        void bind() {
            texture.bind(0);
        }
    }

    /** Ảnh đơn sắc dùng khi tệp thiếu hoặc không giải mã được. */
    private static final int FALLBACK_SIZE = 16;
    /** Ảnh gốc Ourcraft cho cung, giáo và lọ cầm tay. */
    static final MobTextureKey HELD_EQUIPMENT = new MobTextureKey("equipment", "held", 0x8a7059);

    private final Map<MobTextureKey, Skin> skins = new HashMap<>();
    private final Map<MobTextureKey, Skin> layoutFallbacks = new HashMap<>();
    private boolean disposed;

    /** Nạp một biến thể đúng một lần, trước khi bắt đầu lượt vẽ. */
    Skin load(MobTextureKey key) {
        if (key == null) {
            return null;
        }
        if (disposed) {
            throw new IllegalStateException("Cache texture mob đã được giải phóng");
        }
        return skins.computeIfAbsent(key, this::readSkin);
    }

    /** Dùng ảnh dự phòng có chia sẻ khi ảnh giải mã được nhưng bố cục quá nhỏ. */
    Skin layoutFallback(MobTextureKey key) {
        return layoutFallbacks.computeIfAbsent(key, incompatible -> {
            Gdx.app.error("MobTextureManager", "Bố cục ảnh không đủ cho mô hình " + incompatible.path());
            return createFallback(incompatible.fallbackRgb());
        });
    }

    /** Đọc PNG từ classpath để game có thể chạy từ thư mục hoặc JAR. */
    private Skin readSkin(MobTextureKey key) {
        String path = key.path();
        Texture texture = null;
        try {
            FileHandle file = Gdx.files.classpath(path);
            if (!file.exists()) {
                throw new IllegalArgumentException("Thiếu tệp PNG");
            }
            // Hoa văn ngựa có ảnh thang xám kèm alpha. Ép RGBA để OpenGL Core nhận định dạng tải lên.
            texture = new Texture(file, Pixmap.Format.RGBA8888, false);
            configure(texture);
            return new Skin(texture, texture.getWidth(), texture.getHeight(), false);
        } catch (RuntimeException error) {
            if (texture != null) {
                texture.dispose();
            }
            Gdx.app.error("MobTextureManager", "Dùng ảnh dự phòng cho " + path + ": " + error.getMessage());
            return createFallback(key.fallbackRgb());
        }
    }

    /** Tạo ảnh màu và luôn giải phóng bộ đệm pixel ngoài heap ngay sau tải. */
    private static Skin createFallback(int rgb) {
        Pixmap image = new Pixmap(FALLBACK_SIZE, FALLBACK_SIZE, Pixmap.Format.RGBA8888);
        Texture texture = null;
        try {
            image.setColor((rgb << 8) | 0xff);
            image.fill();
            texture = new Texture(image, false);
            configure(texture);
            return new Skin(texture, FALLBACK_SIZE, FALLBACK_SIZE, true);
        } catch (RuntimeException error) {
            if (texture != null) {
                texture.dispose();
            }
            throw error;
        } finally {
            image.dispose();
        }
    }

    /** Giữ rìa pixel sắc nét và ngăn lặp lại texture ở biên. */
    private static void configure(Texture texture) {
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
    }

    /** Giải phóng mỗi texture GPU đúng một lần khi đóng ứng dụng. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Skin skin : skins.values()) {
            skin.texture.dispose();
        }
        skins.clear();
        for (Skin skin : layoutFallbacks.values()) {
            skin.texture.dispose();
        }
        layoutFallbacks.clear();
    }
}
