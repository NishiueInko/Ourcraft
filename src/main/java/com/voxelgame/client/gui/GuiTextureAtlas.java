package com.voxelgame.client.gui;

import com.voxelgame.client.render.Texture;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.utils.Disposable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Đóng gói toàn bộ bộ sprite giao diện (HUD & Widget) chuẩn Minecraft 1.21.11 trong
 * {@code src/main/resources/textures/gui/} thành một Texture Atlas 512 × 256 trên VRAM GPU
 * (lọc điểm gần nhất {@code Nearest} + {@code ClampToEdge}), đồng thời quản lý riêng
 * {@code inventoryTexture} (256 × 256) cho màn hình túi đồ 36 ô.
 *
 * <p>Đặc điểm kỹ thuật:</p>
 * <ul>
 *     <li>Mọi pixel đầu vào đều chuẩn hóa qua {@link Texture#readNormalizedPixel(Pixmap, int, int)}
 *     để khắc phục hoàn toàn lỗi libGDX giải mã PNG thang xám ({@code colorType = 0 / 4}) thành trắng tinh.</li>
 *     <li>Toàn bộ bảng tọa độ UV được tính sẵn một lần trong khối {@code static}, phương thức
 *     {@link #uvOf(SpriteKey)} trả trực tiếp mảng {@code float[4]} dựng sẵn (zero-GC trong Game Loop).</li>
 *     <li>Hỗ trợ dựng {@link Pixmap} trên CPU qua {@link #buildHudAtlasPixmap()} mà không cần OpenGL Context
 *     để phục vụ kiểm thử hồi quy tự động.</li>
 * </ul>
 */
public class GuiTextureAtlas implements Disposable {

    /** Chiều rộng và chiều cao của Texture Atlas HUD & Widget trên VRAM. */
    public static final int ATLAS_WIDTH = 512;
    public static final int ATLAS_HEIGHT = 256;

    /** Kích thước gốc của tệp nền túi đồ {@code inventory.png} và vùng khung chính 176 × 166. */
    public static final int INVENTORY_TEXTURE_SIZE = 256;
    public static final int INVENTORY_PANEL_WIDTH = 176;
    public static final int INVENTORY_PANEL_HEIGHT = 166;

    /** Tọa độ UV [u0, v0, u1, v1] của khung túi đồ 176 × 166 nằm tại góc (0, 0) của ảnh 256 × 256. */
    public static final float[] INVENTORY_PANEL_UV = {
            0.0f,
            0.0f,
            (float) INVENTORY_PANEL_WIDTH / INVENTORY_TEXTURE_SIZE,
            (float) INVENTORY_PANEL_HEIGHT / INVENTORY_TEXTURE_SIZE
    };

    /** Đường dẫn tài nguyên của ảnh nền khung túi đồ chuẩn Minecraft 1.21.11. */
    public static final String INVENTORY_TEXTURE_PATH = "textures/gui/container/inventory.png";

    /**
     * Danh sách đầy đủ các sprite HUD và Widget được đóng gói vào atlas 512 × 256.
     * Mỗi phần tử giữ đường dẫn tài nguyên PNG, vị trí góc trên-trái trên atlas và kích thước pixel gốc.
     */
    public enum SpriteKey {
        HOTBAR("textures/gui/sprites/hud/hotbar.png", 0, 0, 182, 22),
        HOTBAR_SELECTION("textures/gui/sprites/hud/hotbar_selection.png", 184, 0, 24, 23),
        CROSSHAIR("textures/gui/sprites/hud/crosshair.png", 210, 0, 15, 15),
        WIDGET_SLIDER_HANDLE("textures/gui/sprites/widget/slider_handle.png", 228, 0, 8, 20),
        WIDGET_SLIDER_HANDLE_HIGHLIGHTED("textures/gui/sprites/widget/slider_handle_highlighted.png", 238, 0, 8, 20),

        HEART_CONTAINER("textures/gui/sprites/hud/heart/container.png", 0, 26, 9, 9),
        HEART_FULL("textures/gui/sprites/hud/heart/full.png", 11, 26, 9, 9),
        HEART_HALF("textures/gui/sprites/hud/heart/half.png", 22, 26, 9, 9),
        HEART_POISONED_FULL("textures/gui/sprites/hud/heart/poisoned_full.png", 33, 26, 9, 9),
        HEART_POISONED_HALF("textures/gui/sprites/hud/heart/poisoned_half.png", 44, 26, 9, 9),
        HEART_VEHICLE_CONTAINER("textures/gui/sprites/hud/heart/vehicle_container.png", 55, 26, 9, 9),
        HEART_VEHICLE_FULL("textures/gui/sprites/hud/heart/vehicle_full.png", 66, 26, 9, 9),
        HEART_VEHICLE_HALF("textures/gui/sprites/hud/heart/vehicle_half.png", 77, 26, 9, 9),

        FOOD_EMPTY("textures/gui/sprites/hud/food_empty.png", 0, 38, 9, 9),
        FOOD_FULL("textures/gui/sprites/hud/food_full.png", 11, 38, 9, 9),
        FOOD_HALF("textures/gui/sprites/hud/food_half.png", 22, 38, 9, 9),
        FOOD_EMPTY_HUNGER("textures/gui/sprites/hud/food_empty_hunger.png", 33, 38, 9, 9),
        FOOD_FULL_HUNGER("textures/gui/sprites/hud/food_full_hunger.png", 44, 38, 9, 9),
        FOOD_HALF_HUNGER("textures/gui/sprites/hud/food_half_hunger.png", 55, 38, 9, 9),
        AIR("textures/gui/sprites/hud/air.png", 66, 38, 9, 9),
        AIR_BURSTING("textures/gui/sprites/hud/air_bursting.png", 77, 38, 9, 9),
        AIR_EMPTY("textures/gui/sprites/hud/air_empty.png", 88, 38, 9, 9),

        JUMP_BAR_BACKGROUND("textures/gui/sprites/hud/jump_bar_background.png", 0, 50, 182, 5),
        JUMP_BAR_PROGRESS("textures/gui/sprites/hud/jump_bar_progress.png", 0, 57, 182, 5),
        JUMP_BAR_COOLDOWN("textures/gui/sprites/hud/jump_bar_cooldown.png", 0, 64, 182, 5),

        WIDGET_BUTTON("textures/gui/sprites/widget/button.png", 0, 74, 200, 20),
        WIDGET_BUTTON_HIGHLIGHTED("textures/gui/sprites/widget/button_highlighted.png", 0, 96, 200, 20),
        WIDGET_BUTTON_DISABLED("textures/gui/sprites/widget/button_disabled.png", 0, 118, 200, 20),
        WIDGET_SLIDER("textures/gui/sprites/widget/slider.png", 0, 140, 200, 20),
        WIDGET_SLIDER_HIGHLIGHTED("textures/gui/sprites/widget/slider_highlighted.png", 0, 162, 200, 20);

        /** Đường dẫn tệp PNG trong classpath hoặc thư mục {@code src/main/resources/}. */
        public final String resourcePath;
        /** Tọa độ góc trên-trái (pixel) trên atlas 512 × 256. */
        public final int atlasX;
        public final int atlasY;
        /** Kích thước pixel chuẩn của sprite. */
        public final int width;
        public final int height;

        SpriteKey(String resourcePath, int atlasX, int atlasY, int width, int height) {
            this.resourcePath = resourcePath;
            this.atlasX = atlasX;
            this.atlasY = atlasY;
            this.width = width;
            this.height = height;
        }
    }

    /** Mảng các phần tử {@link SpriteKey} và bảng UV dựng sẵn để tra cứu zero-GC. */
    private static final SpriteKey[] ALL_KEYS = SpriteKey.values();
    private static final float[][] PRECOMPUTED_UVS = createUvTable();

    private static float[][] createUvTable() {
        float[][] table = new float[ALL_KEYS.length][];
        for (int i = 0; i < ALL_KEYS.length; i++) {
            SpriteKey key = ALL_KEYS[i];
            table[i] = new float[]{
                    (float) key.atlasX / ATLAS_WIDTH,
                    (float) key.atlasY / ATLAS_HEIGHT,
                    (float) (key.atlasX + key.width) / ATLAS_WIDTH,
                    (float) (key.atlasY + key.height) / ATLAS_HEIGHT
            };
        }
        return table;
    }

    /** Texture GPU chứa toàn bộ 30 sprite HUD & Widget (512 × 256, Nearest + ClampToEdge). */
    private final com.badlogic.gdx.graphics.Texture hudTexture;

    /** Texture GPU riêng cho khung nền giao diện túi đồ 36 ô (256 × 256, Nearest + ClampToEdge). */
    private final com.badlogic.gdx.graphics.Texture inventoryTexture;

    /**
     * Khởi tạo bộ Texture GUI trên GPU: đóng gói atlas HUD/Widget và nạp ảnh {@code inventory.png}.
     * Toàn bộ {@link Pixmap} trung gian trên CPU được giải phóng ngay sau khi tải lên VRAM.
     */
    public GuiTextureAtlas() {
        com.badlogic.gdx.graphics.Texture builtHud = null;
        com.badlogic.gdx.graphics.Texture builtInventory = null;

        Pixmap hudPixmap = buildHudAtlasPixmap();
        try {
            builtHud = new com.badlogic.gdx.graphics.Texture(hudPixmap);
        } finally {
            hudPixmap.dispose();
        }

        try {
            builtHud.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            builtHud.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);

            Pixmap invPixmap = buildInventoryPixmap();
            try {
                builtInventory = new com.badlogic.gdx.graphics.Texture(invPixmap);
            } finally {
                invPixmap.dispose();
            }
            builtInventory.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            builtInventory.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
        } catch (RuntimeException | Error failure) {
            if (builtInventory != null) {
                builtInventory.dispose();
            }
            builtHud.dispose();
            throw failure;
        }

        this.hudTexture = builtHud;
        this.inventoryTexture = builtInventory;
    }

    /**
     * Trả về mảng UV {@code [u0, v0, u1, v1]} đã tính sẵn cho một {@link SpriteKey} (zero-GC).
     *
     * @param key khóa định danh sprite HUD hoặc Widget
     * @return mảng 4 phần tử {@code [u0, v0, u1, v1]} trong đoạn {@code [0, 1]}
     */
    public static float[] uvOf(SpriteKey key) {
        if (key == null) {
            throw new IllegalArgumentException("SpriteKey không được null");
        }
        return PRECOMPUTED_UVS[key.ordinal()];
    }

    /**
     * Dựng tấm {@link Pixmap} RGBA8888 kích thước 512 × 256 trên CPU chứa toàn bộ sprite HUD và Widget.
     * Phương thức công khai tĩnh để kiểm thử CPU có thể xác nhận giải mã ảnh không cần OpenGL Context.
     *
     * @return {@link Pixmap} đã chép đủ 30 sprite (người gọi có trách nhiệm gọi {@code dispose()})
     */
    public static Pixmap buildHudAtlasPixmap() {
        Pixmap atlas = new Pixmap(ATLAS_WIDTH, ATLAS_HEIGHT, Pixmap.Format.RGBA8888);
        atlas.setBlending(Pixmap.Blending.None);
        atlas.setColor(0);
        atlas.fill();

        for (int i = 0; i < ALL_KEYS.length; i++) {
            SpriteKey key = ALL_KEYS[i];
            Pixmap source = loadPixmap(key.resourcePath);
            if (source == null) {
                fillFallbackRect(atlas, key.atlasX, key.atlasY, key.width, key.height);
                continue;
            }
            try {
                blitNormalized(atlas, source, key.atlasX, key.atlasY, key.width, key.height);
            } finally {
                source.dispose();
            }
        }
        return atlas;
    }

    /**
     * Nạp ảnh {@code inventory.png} và chuẩn hóa pixel thành {@link Pixmap} RGBA8888 256 × 256.
     */
    private static Pixmap buildInventoryPixmap() {
        Pixmap result = new Pixmap(INVENTORY_TEXTURE_SIZE, INVENTORY_TEXTURE_SIZE, Pixmap.Format.RGBA8888);
        result.setBlending(Pixmap.Blending.None);
        result.setColor(0);
        result.fill();

        Pixmap source = loadPixmap(INVENTORY_TEXTURE_PATH);
        if (source == null) {
            fillFallbackRect(result, 0, 0, INVENTORY_PANEL_WIDTH, INVENTORY_PANEL_HEIGHT);
            return result;
        }
        try {
            blitNormalized(result, source, 0, 0, INVENTORY_TEXTURE_SIZE, INVENTORY_TEXTURE_SIZE);
        } finally {
            source.dispose();
        }
        return result;
    }

    /**
     * Sao chép từng điểm ảnh từ {@code source} sang {@code dst} tại vị trí {@code (dstX, dstY)}
     * qua {@link Texture#readNormalizedPixel(Pixmap, int, int)} để xử lý chính xác ảnh thang xám.
     */
    private static void blitNormalized(Pixmap dst, Pixmap source, int dstX, int dstY, int dstW, int dstH) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW <= 0 || srcH <= 0) {
            return;
        }
        for (int ty = 0; ty < dstH; ty++) {
            int sy = (srcH == dstH) ? ty : Math.min(srcH - 1, (int) (((ty + 0.5f) / dstH) * srcH));
            for (int tx = 0; tx < dstW; tx++) {
                int sx = (srcW == dstW) ? tx : Math.min(srcW - 1, (int) (((tx + 0.5f) / dstW) * srcW));
                int rgba = Texture.readNormalizedPixel(source, sx, sy);
                dst.drawPixel(dstX + tx, dstY + ty, rgba);
            }
        }
    }

    /** Tô vùng dự phòng tối giản nếu tệp PNG bị thiếu để tránh lỗi sập ứng dụng. */
    private static void fillFallbackRect(Pixmap dst, int x, int y, int w, int h) {
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                boolean border = px == 0 || py == 0 || px == w - 1 || py == h - 1;
                int rgba = border ? 0x909098FF : 0x383C46D8;
                dst.drawPixel(x + px, y + py, rgba);
            }
        }
    }

    /** Tìm và giải mã tệp PNG từ classpath hoặc thư mục {@code src/main/resources/}. */
    private static Pixmap loadPixmap(String relativePath) {
        byte[] rawBytes = null;
        String resourcePath = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        try (InputStream is = GuiTextureAtlas.class.getResourceAsStream(resourcePath)) {
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
            System.err.println("[GuiTextureAtlas] Không thể giải mã ảnh GUI '" + relativePath + "': " + e.getMessage());
            return null;
        }
    }

    /** Gắn Texture Atlas HUD & Widget vào Texture Unit 0 trên GPU. */
    public void bindHudAtlas() {
        hudTexture.bind(0);
    }

    /** Gắn Texture khung túi đồ {@code inventory.png} vào Texture Unit 0 trên GPU. */
    public void bindInventoryTexture() {
        inventoryTexture.bind(0);
    }

    /** Hủy liên kết Texture trên Texture Unit 0. */
    public void unbind() {
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
    }

    /** Trả về đối tượng Texture GPU của atlas HUD & Widget. */
    public com.badlogic.gdx.graphics.Texture getHudTexture() {
        return hudTexture;
    }

    /** Trả về đối tượng Texture GPU của nền túi đồ {@code inventory.png}. */
    public com.badlogic.gdx.graphics.Texture getInventoryTexture() {
        return inventoryTexture;
    }

    /** Giải phóng toàn bộ Texture GPU đã cấp phát cho HUD, Widget và Inventory. */
    @Override
    public void dispose() {
        if (hudTexture != null) {
            hudTexture.dispose();
        }
        if (inventoryTexture != null) {
            inventoryTexture.dispose();
        }
    }
}
