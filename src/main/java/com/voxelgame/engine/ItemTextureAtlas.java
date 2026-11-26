package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.utils.Disposable;
import com.voxelgame.entity.Item;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Đóng gói toàn bộ 56 texture vật phẩm độc lập (16 × 16 RGBA) vào một tấm Texture Atlas
 * duy nhất kích thước 128 × 128 (8 × 8 ô) trên VRAM, triển khai {@link Disposable}.
 */
public final class ItemTextureAtlas implements Disposable {

    /** Kích thước mỗi ô vật phẩm và số cột/hàng của atlas vật phẩm. */
    public static final int TILE_SIZE = 16;
    public static final int ATLAS_COLS = 8;
    public static final int ATLAS_ROWS = 8;
    public static final int ATLAS_WIDTH = TILE_SIZE * ATLAS_COLS;
    public static final int ATLAS_HEIGHT = TILE_SIZE * ATLAS_ROWS;

    private final com.badlogic.gdx.graphics.Texture gdxTexture;

    /** Nạp toàn bộ PNG vật phẩm từ `textures/item/` vào atlas và giải phóng bộ đệm CPU ngay sau đó. */
    public ItemTextureAtlas() {
        Pixmap atlasPixmap = new Pixmap(ATLAS_WIDTH, ATLAS_HEIGHT, Pixmap.Format.RGBA8888);
        try {
            atlasPixmap.setBlending(Pixmap.Blending.None);
            List<Item> items = Item.allStandaloneItems();
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                loadItemIntoTile(atlasPixmap, item);
            }
            gdxTexture = new com.badlogic.gdx.graphics.Texture(atlasPixmap);
        } finally {
            atlasPixmap.dispose();
        }
        try {
            gdxTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            gdxTexture.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
        } catch (RuntimeException | Error failure) {
            gdxTexture.dispose();
            throw failure;
        }
    }

    /** Nạp PNG vật phẩm vào đúng ô của nó, dùng pixel art dự phòng nếu thiếu tệp. */
    private void loadItemIntoTile(Pixmap atlasPixmap, Item item) {
        String textureName = item.getTextureName();
        int tileIndex = item.getAtlasTileIndex();
        Pixmap source = textureName == null ? null : loadPixmap("textures/item/" + textureName + ".png");
        if (source != null) {
            try {
                int srcW = source.getWidth();
                int srcH = source.getHeight();
                for (int ty = 0; ty < TILE_SIZE; ty++) {
                    for (int tx = 0; tx < TILE_SIZE; tx++) {
                        int sx = Math.min(srcW - 1, (int) (((tx + 0.5f) / TILE_SIZE) * srcW));
                        int sy = Math.min(srcH - 1, (int) (((ty + 0.5f) / TILE_SIZE) * srcH));
                        putAtlasPixel(atlasPixmap, tileIndex, tx, ty, source.getPixel(sx, sy));
                    }
                }
                return;
            } finally {
                source.dispose();
            }
        }
        drawFallbackTile(atlasPixmap, tileIndex, item.getFallbackRgb(), item.isCubeItem());
    }

    /** Vẽ ô màu dự phòng khi không tìm thấy PNG để game không bị lỗi hay tàng hình vật phẩm. */
    private void drawFallbackTile(Pixmap atlasPixmap, int tileIndex, int rgb, boolean fullCube) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (int ty = 0; ty < TILE_SIZE; ty++) {
            for (int tx = 0; tx < TILE_SIZE; tx++) {
                boolean visible = fullCube || (tx >= 3 && tx <= 12 && ty >= 3 && ty <= 12);
                int alpha = visible ? 255 : 0;
                int grain = ((tx * 11 + ty * 7 + tileIndex * 5) % 15) - 7;
                int rgba = (clamp(r + grain) << 24)
                        | (clamp(g + grain) << 16)
                        | (clamp(b + grain) << 8)
                        | alpha;
                putAtlasPixel(atlasPixmap, tileIndex, tx, ty, rgba);
            }
        }
    }

    /** Ghi một pixel RGBA8888 vào đúng tọa độ của ô trên atlas. */
    private void putAtlasPixel(Pixmap atlasPixmap, int tileIndex, int tx, int ty, int rgba) {
        int col = tileIndex % ATLAS_COLS;
        int row = tileIndex / ATLAS_COLS;
        atlasPixmap.drawPixel(col * TILE_SIZE + tx, row * TILE_SIZE + ty, rgba);
    }

    /** Tìm và giải mã PNG từ classpath hoặc thư mục tài nguyên cục bộ. */
    private Pixmap loadPixmap(String relativePath) {
        byte[] rawBytes = null;
        try (InputStream is = ItemTextureAtlas.class.getResourceAsStream("/" + relativePath)) {
            if (is != null) {
                rawBytes = is.readAllBytes();
            }
        } catch (IOException ignored) {
        }
        if (rawBytes == null) {
            File file = new File("src/main/resources/" + relativePath);
            if (file.exists() && file.isFile()) {
                try {
                    rawBytes = Files.readAllBytes(file.toPath());
                } catch (IOException ignored) {
                }
            }
        }
        if (rawBytes == null) {
            return null;
        }
        try {
            return new Pixmap(rawBytes, 0, rawBytes.length);
        } catch (Exception e) {
            System.err.println("[ItemTextureAtlas] Không giải mã được " + relativePath + ": " + e.getMessage());
            return null;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** Gắn atlas vật phẩm vào Texture Unit 0 trên GPU. */
    public void bind() {
        gdxTexture.bind(0);
    }

    /** Hủy liên kết texture khỏi Texture Unit 0. */
    public void unbind() {
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
    }

    /** Giải phóng tấm atlas trên VRAM khi đóng game. */
    @Override
    public void dispose() {
        if (gdxTexture != null) {
            gdxTexture.dispose();
        }
    }
}
