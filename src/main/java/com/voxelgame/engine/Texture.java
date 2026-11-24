package com.voxelgame.engine;

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
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Quản lý việc đọc các file ảnh (.png, .jpg), tự động chuẩn hóa về kích thước 16x16 pixel
 * và đóng gói thành một tấm ảnh "Texture Atlas" 128x80 duy nhất trên VRAM GPU thông qua
 * thư viện đồ họa libGDX, triển khai {@link Disposable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Che giấu toàn bộ quá trình đọc file linh hoạt đa tầng, giải mã ảnh CPU qua Pixmap,
 *      lấy mẫu điểm ảnh trung tâm (Nearest-Neighbor Resampling), sinh nền Procedural Pixel Art
 *      và cấu hình tham số OpenGL (Nearest, ClampToEdge).
 * 2. Tính Đóng gói (Encapsulation):
 *    - Đối tượng `com.badlogic.gdx.graphics.Texture` nội bộ được khóa `private final`, cung cấp
 *      các phương thức thao tác an toàn `bind()`, `unbind()`, `getGdxTexture()` và `dispose()`.
 */
public class Texture implements Disposable {

    // Kích thước của 1 ô chất liệu tiêu chuẩn (16x16 pixel)
    public static final int TILE_SIZE = 16;
    // Số cột và số hàng của lưới Texture Atlas (8x5 = 40 ô)
    public static final int ATLAS_COLS = 8;
    public static final int ATLAS_ROWS = 5;
    // Kích thước tổng thể của tấm ảnh Texture Atlas (128x80 pixel)
    public static final int ATLAS_WIDTH = TILE_SIZE * ATLAS_COLS;
    public static final int ATLAS_HEIGHT = TILE_SIZE * ATLAS_ROWS;

    // Định danh vị trí (Tile Index) của từng loại bề mặt trong Texture Atlas
    public static final int TILE_DIRT = 0;
    public static final int TILE_GRASS_TOP = 1;
    public static final int TILE_GRASS_SIDE = 2;
    public static final int TILE_STONE = 3;
    public static final int TILE_BEDROCK = 4;
    public static final int TILE_OAK_LOG_SIDE = 5;
    public static final int TILE_OAK_LOG_TOP = 6;
    public static final int TILE_OAK_LEAVES = 7;
    public static final int TILE_WATER = 8;
    public static final int TILE_LAVA = 9;

    // Ô có sẵn cho sa mạc và miền tuyết. Snow Block và Snow Layers dùng chung TILE_SNOW.
    public static final int TILE_SAND = 10;
    public static final int TILE_GRASS_SNOW_SIDE = 11;
    public static final int TILE_DEAD_BUSH = 12;
    public static final int TILE_CACTUS_SIDE = 13;
    public static final int TILE_CACTUS_TOP = 14;
    public static final int TILE_CACTUS_BOTTOM = 15;
    public static final int TILE_SPRUCE_WOOD = 16;
    public static final int TILE_SPRUCE_LEAVES = 17;
    public static final int TILE_ICE = 18;
    public static final int TILE_SNOW = 19;

    // Các ô mới theo thứ tự nhập PNG, giữ nguyên định danh của 20 ô trước.
    public static final int TILE_SANDSTONE = 20;
    public static final int TILE_SANDSTONE_TOP = 21;
    public static final int TILE_SANDSTONE_BOTTOM = 22;
    public static final int TILE_SPRUCE_LOG_TOP = 23;
    public static final int TILE_SHORT_GRASS = 24;
    public static final int TILE_TALL_GRASS_BOTTOM = 25;
    public static final int TILE_TALL_GRASS_TOP = 26;
    public static final int TILE_DANDELION = 27;
    public static final int TILE_POPPY = 28;
    public static final int TILE_CORNFLOWER = 29;
    public static final int TILE_OXEYE_DAISY = 30;
    public static final int TILE_SUGAR_CANE = 31;
    public static final int TILE_GRAVEL = 32;

    /** Mặt gỗ sơn có khóa đồng của rương trang trí. */
    public static final int TILE_CHEST_FRONT = 33;
    /** Mặt gỗ ghép có nẹp dọc của rương trang trí. */
    public static final int TILE_CHEST_SIDE = 34;
    /** Nắp rương với họa tiết khảm hình thoi. */
    public static final int TILE_CHEST_TOP = 35;

    // Hệ số nhân RGB cho thảm thực vật đồng bằng và lá vân sam, lần lượt theo mã màu RGB.
    private static final int PLAINS_TINT = 0x91BD59;
    private static final int SPRUCE_TINT = 0x619961;
    // Giá trị bán trong suốt cho ảnh nước thay thế vốn có alpha tối đa.
    private static final int OPAQUE_WATER_ALPHA = 225;

    /** Quy tắc lấy mẫu và xử lý màu theo từng loại ảnh nguồn. */
    private enum TileStyle {
        OPAQUE, // Giữ RGB và đặt alpha tối đa cho các mặt được vẽ đục.
        PLAINS, // Nhuộm cỏ, lá sồi và mía theo màu đồng bằng, giữ alpha gốc.
        SPRUCE, // Nhuộm lá vân sam theo màu xanh thông, giữ alpha gốc.
        RGBA,   // Giữ nguyên cả bốn kênh của ảnh nguồn.
        CUTOUT, // Giữ RGBA, dùng hình có khoảng rỗng khi thiếu PNG.
        ICE,    // Giữ RGBA, dùng alpha trung gian khi thiếu PNG.
        WATER,  // Lấy khung đầu và nhuộm xanh ảnh nước thang xám.
        LAVA    // Lấy khung đầu của ảnh dung nham và vẽ đục.
    }

    // Đối tượng Texture của libGDX đại diện cho Texture Atlas 128x80 trên VRAM.
    private final com.badlogic.gdx.graphics.Texture gdxTexture;

    /** Dựng atlas một lần khi khởi động và giải phóng bộ đệm ảnh CPU sau khi tải lên GPU. */
    public Texture() {
        Pixmap atlasPixmap = new Pixmap(ATLAS_WIDTH, ATLAS_HEIGHT, Pixmap.Format.RGBA8888);
        try {
            // Ghi đè trực tiếp để giữ alpha gốc, chỉ pha trộn ở lớp phủ cỏ.
            atlasPixmap.setBlending(Pixmap.Blending.None);
            loadExternalTexturesIntoAtlas(atlasPixmap);
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

    /** Nạp các ảnh đóng gói vào đúng ô, ưu tiên tên PNG mới trước tên tệp cũ. */
    private void loadExternalTexturesIntoAtlas(Pixmap atlasPixmap) {
        // Các mặt cơ bản vẫn dùng tên thay thế cũ và hình dự phòng riêng nếu tải thất bại.
        boolean[] loaded = new boolean[TILE_LAVA + 1];
        loaded[TILE_GRASS_TOP] = loadTile(atlasPixmap, TILE_GRASS_TOP, TileStyle.PLAINS,
                "textures/grass_block_top.png");
        boolean grassSideLoaded = loadTile(atlasPixmap, TILE_GRASS_SIDE, TileStyle.OPAQUE,
                "textures/grass_block_side.png");
        loaded[TILE_GRASS_SIDE] = grassSideLoaded;
        loaded[TILE_DIRT] = loadTile(atlasPixmap, TILE_DIRT, TileStyle.OPAQUE, "textures/dirt.png", "dirt.png",
                "Minecraft Dirt Small by terridee, click to purchase fabric.jpg", "dirt.jpg");
        if (!loaded[TILE_GRASS_TOP] || !grassSideLoaded) {
            loadLegacyGrass(atlasPixmap, loaded);
        }
        if (grassSideLoaded) {
            // Ảnh lớp phủ chỉ chứa viền cỏ, các pixel trong suốt để lộ lớp đất bên dưới.
            atlasPixmap.setBlending(Pixmap.Blending.SourceOver);
            try {
                loadTile(atlasPixmap, TILE_GRASS_SIDE, TileStyle.PLAINS, "textures/grass_block_side_overlay.png");
            } finally {
                atlasPixmap.setBlending(Pixmap.Blending.None);
            }
        }

        loaded[TILE_STONE] = loadTile(atlasPixmap, TILE_STONE, TileStyle.OPAQUE, "textures/stone.png", "stone.png");
        loaded[TILE_BEDROCK] = loadTile(atlasPixmap, TILE_BEDROCK, TileStyle.OPAQUE,
                "textures/bedrock.png", "bedrock.png");
        loaded[TILE_OAK_LOG_SIDE] = loadTile(atlasPixmap, TILE_OAK_LOG_SIDE, TileStyle.OPAQUE,
                "textures/oak_log.png", "oak_log.png",
                "Minecraft Oak Log Block Texture Pixel Art.jpg", "oak_log.jpg");
        loaded[TILE_OAK_LOG_TOP] = loadTile(atlasPixmap, TILE_OAK_LOG_TOP, TileStyle.OPAQUE,
                "textures/oak_log_top.png");
        loaded[TILE_OAK_LEAVES] = loadTile(atlasPixmap, TILE_OAK_LEAVES, TileStyle.PLAINS,
                "textures/oak_leaves.png", "oak_leaves.png");
        loaded[TILE_WATER] = loadTile(atlasPixmap, TILE_WATER, TileStyle.WATER, "textures/water_still.png", "water_still.png",
                "fliflight-clear-water-1.0.0-resourcepack-1.21.4/assets/minecraft/textures/block/water_still.png",
                "textures/fliflight-clear-water-1.0.0-resourcepack-1.21.4/assets/minecraft/textures/block/water_still.png");
        loaded[TILE_LAVA] = loadTile(atlasPixmap, TILE_LAVA, TileStyle.LAVA, "textures/lava_still.png")
                || loadTile(atlasPixmap, TILE_LAVA, TileStyle.OPAQUE, "download.jpg", "textures/download.jpg",
                "lava.jpg", "lava.png");
        for (boolean tileLoaded : loaded) {
            if (!tileLoaded) {
                populateProceduralFallbacks(atlasPixmap, loaded);
                break;
            }
        }

        // Mỗi màu dự phòng mô phỏng vật liệu tương ứng và chỉ được dùng khi nạp PNG thất bại.
        loadTileOrFallback(atlasPixmap, TILE_SAND, TileStyle.RGBA, 0xDDCF9A, "sand");
        loadTileOrFallback(atlasPixmap, TILE_GRASS_SNOW_SIDE, TileStyle.RGBA, 0x88664C, "grass_block_snow");
        loadTileOrFallback(atlasPixmap, TILE_DEAD_BUSH, TileStyle.CUTOUT, 0x987140, "dead_bush");
        loadTileOrFallback(atlasPixmap, TILE_CACTUS_SIDE, TileStyle.RGBA, 0x5E892E, "cactus_side");
        loadTileOrFallback(atlasPixmap, TILE_CACTUS_TOP, TileStyle.RGBA, 0x85A743, "cactus_top");
        loadTileOrFallback(atlasPixmap, TILE_CACTUS_BOTTOM, TileStyle.RGBA, 0x7C7741, "cactus_bottom");
        loadTileOrFallback(atlasPixmap, TILE_SPRUCE_WOOD, TileStyle.RGBA, 0x604830, "spruce_log");
        loadTileOrFallback(atlasPixmap, TILE_SPRUCE_LEAVES, TileStyle.SPRUCE, 0x4F783F, "spruce_leaves");
        loadTileOrFallback(atlasPixmap, TILE_ICE, TileStyle.ICE, 0x9CBCFB, "ice");
        loadTileOrFallback(atlasPixmap, TILE_SNOW, TileStyle.RGBA, 0xEDF5F7, "snow");
        loadTileOrFallback(atlasPixmap, TILE_SANDSTONE, TileStyle.OPAQUE, 0xD9C791, "sandstone");
        loadTileOrFallback(atlasPixmap, TILE_SANDSTONE_TOP, TileStyle.OPAQUE, 0xE4D3A3, "sandstone_top");
        loadTileOrFallback(atlasPixmap, TILE_SANDSTONE_BOTTOM, TileStyle.OPAQUE, 0xCDBC86, "sandstone_bottom");
        loadTileOrFallback(atlasPixmap, TILE_SPRUCE_LOG_TOP, TileStyle.OPAQUE, 0x9A7446, "spruce_log_top");
        loadTileOrFallback(atlasPixmap, TILE_SHORT_GRASS, TileStyle.PLAINS, 0x639643, "short_grass");
        loadTileOrFallback(atlasPixmap, TILE_TALL_GRASS_BOTTOM, TileStyle.PLAINS, 0x527E38, "tall_grass_bottom");
        loadTileOrFallback(atlasPixmap, TILE_TALL_GRASS_TOP, TileStyle.PLAINS, 0x649744, "tall_grass_top");
        loadTileOrFallback(atlasPixmap, TILE_DANDELION, TileStyle.CUTOUT, 0xEDCE3E, "dandelion");
        loadTileOrFallback(atlasPixmap, TILE_POPPY, TileStyle.CUTOUT, 0xC13B33, "poppy");
        loadTileOrFallback(atlasPixmap, TILE_CORNFLOWER, TileStyle.CUTOUT, 0x5187E2, "cornflower");
        loadTileOrFallback(atlasPixmap, TILE_OXEYE_DAISY, TileStyle.CUTOUT, 0xEEE8CF, "oxeye_daisy");
        loadTileOrFallback(atlasPixmap, TILE_SUGAR_CANE, TileStyle.PLAINS, 0x8BB05B, "sugar_cane");
        loadTileOrFallback(atlasPixmap, TILE_GRAVEL, TileStyle.OPAQUE, 0x807B78, "gravel");
        drawDecorativeChest(atlasPixmap);
    }

    /** Vẽ rương theo bảng màu gỗ sơn xanh và đồng, ghi đè trực tiếp ba ô atlas. */
    private void drawDecorativeChest(Pixmap atlas) {
        for (int tile = TILE_CHEST_FRONT; tile <= TILE_CHEST_TOP; tile++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                for (int x = 0; x < TILE_SIZE; x++) {
                    int distanceToEdge = Math.min(Math.min(x, TILE_SIZE - 1 - x),
                            Math.min(y, TILE_SIZE - 1 - y));
                    int centerDistance = Math.abs(2 * x - 15) + Math.abs(2 * y - 15);
                    int rgb;
                    if (distanceToEdge == 0) {
                        rgb = 0x223B37;
                    } else if (tile == TILE_CHEST_FRONT && Math.abs(2 * x - 15) + 2 * Math.abs(y - 6) <= 5) {
                        rgb = (x == 7 || y == 5) ? 0xF5D49B : 0xB36D3A;
                    } else if (tile != TILE_CHEST_TOP && y == 5) {
                        rgb = 0x223B37;
                    } else if (distanceToEdge == 1 || (tile == TILE_CHEST_TOP && centerDistance == 12)
                            || (tile != TILE_CHEST_TOP && (x == 3 || x == 12))) {
                        rgb = ((x + y) % 7 == 0) ? 0xE4AC72 : 0x90613C;
                    } else if (tile == TILE_CHEST_TOP && centerDistance < 8) {
                        rgb = 0x5E9B90;
                    } else {
                        rgb = y % 4 == 0 ? 0x375953 : 0x477971;
                    }
                    int grain = ((x * 13 + y * 7 + x * y * 3) % 11) - 5;
                    putAtlasPixel(atlas, tile, x, y, ((rgb >>> 16) & 255) + grain,
                            ((rgb >>> 8) & 255) + grain, (rgb & 255) + grain, 255);
                }
            }
        }
    }

    /** Tận dụng sheet cỏ cũ cho mặt còn thiếu và lấy ô đất của sheet làm dự phòng. */
    private void loadLegacyGrass(Pixmap atlasPixmap, boolean[] loaded) {
        Pixmap grass = loadPixmapFlexible("grass-block.png", "textures/grass-block.png", "grass_block.png");
        if (grass == null) {
            return;
        }
        try {
            if (grass.getWidth() == 32 && grass.getHeight() == 32) {
                if (!loaded[TILE_GRASS_TOP]) {
                    blitResizedToTile(atlasPixmap, grass, 16, 16, 16, 16, TILE_GRASS_TOP, TileStyle.OPAQUE);
                    loaded[TILE_GRASS_TOP] = true;
                }
                if (!loaded[TILE_GRASS_SIDE]) {
                    blitResizedToTile(atlasPixmap, grass, 0, 16, 16, 16, TILE_GRASS_SIDE, TileStyle.OPAQUE);
                    loaded[TILE_GRASS_SIDE] = true;
                }
                if (!loaded[TILE_DIRT]) {
                    blitResizedToTile(atlasPixmap, grass, 0, 0, 16, 16, TILE_DIRT, TileStyle.OPAQUE);
                    loaded[TILE_DIRT] = true;
                }
            } else if (!loaded[TILE_GRASS_TOP]) {
                blitResizedToTile(atlasPixmap, grass, 0, 0, grass.getWidth(), grass.getHeight(),
                        TILE_GRASS_TOP, TileStyle.OPAQUE);
                loaded[TILE_GRASS_TOP] = true;
            }
        } finally {
            grass.dispose();
        }
    }

    /** Nạp một ô atlas và lấy khung đầu đối với dải hoạt ảnh chất lỏng. */
    private boolean loadTile(Pixmap atlasPixmap, int tile, TileStyle style, String... names) {
        Pixmap source = loadPixmapFlexible(names);
        if (source == null) {
            return false;
        }
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            if (style == TileStyle.WATER || style == TileStyle.LAVA) {
                width = Math.min(width, height);
                height = width;
            }
            blitResizedToTile(atlasPixmap, source, 0, 0, width, height, tile, style);
            return true;
        } finally {
            source.dispose();
        }
    }

    /** Dùng PNG đóng gói nếu đọc được, nếu thiếu thì tạo màu, vân và vùng alpha tại ô tương ứng. */
    private void loadTileOrFallback(Pixmap atlasPixmap, int tile, TileStyle style, int color, String name) {
        if (loadTile(atlasPixmap, tile, style, "textures/" + name + ".png")) {
            return;
        }
        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int grain = ((x * 13 + y * 17 + x * y * 7 + tile * 5) % 21) - 10;
                int rgb = color;
                int alpha = 255;
                switch (style) {
                    case PLAINS, CUTOUT -> {
                        // Nét thân nghiêng và cụm lá tạo khoảng trống trên hai mặt phẳng chữ X.
                        int stem = 7 + (y / 4);
                        boolean plant = Math.abs(x - stem) <= 1
                                || (y >= 5 && y <= 12 && Math.abs(x - (16 - y)) <= 1)
                                || (y >= 2 && y <= 6 && Math.abs(x - 7) <= 2);
                        alpha = plant ? 255 : 0;
                    }
                    case SPRUCE -> alpha = (x * 11 + y * 13 + x * y) % 7 == 0 ? 0 : 255;
                    case ICE -> alpha = 190;
                    default -> {
                        if (tile == TILE_GRASS_SNOW_SIDE && y < 3 + (x % 3)) {
                            rgb = 0xE6F3F8;
                        }
                    }
                }
                putAtlasPixel(atlasPixmap, tile, x, y,
                        ((rgb >>> 16) & 255) + grain,
                        ((rgb >>> 8) & 255) + grain,
                        (rgb & 255) + grain, alpha);
            }
        }
    }

    /** Lấy mẫu pixel gần nhất và xử lý màu, alpha trước khi ghi vào ô atlas. */
    private void blitResizedToTile(
            Pixmap atlasPixmap,
            Pixmap src,
            int srcX,
            int srcY,
            int srcW,
            int srcH,
            int tileIndex,
            TileStyle style
    ) {
        for (int ty = 0; ty < TILE_SIZE; ty++) {
            for (int tx = 0; tx < TILE_SIZE; tx++) {
                int sx = srcX + Math.min(srcW - 1, (int) (((tx + 0.5f) / TILE_SIZE) * srcW));
                int sy = srcY + Math.min(srcH - 1, (int) (((ty + 0.5f) / TILE_SIZE) * srcH));

                int pixel = src.getPixel(sx, sy);
                int r = (pixel >>> 24) & 0xFF;
                int g = (pixel >>> 16) & 0xFF;
                int b = (pixel >>> 8) & 0xFF;
                int a = pixel & 0xFF;

                if (style == TileStyle.PLAINS || style == TileStyle.SPRUCE) {
                    // Nhuộm riêng các kênh RGB, giữ nguyên khe alpha trên lá và cây.
                    int tint = style == TileStyle.PLAINS ? PLAINS_TINT : SPRUCE_TINT;
                    r = r * ((tint >>> 16) & 255) / 255;
                    g = g * ((tint >>> 8) & 255) / 255;
                    b = b * (tint & 255) / 255;
                } else if (style == TileStyle.WATER) {
                    if (Math.abs(r - g) < 10 && Math.abs(g - b) < 10) {
                        r = (int) (r * 0.22f + 18);
                        g = (int) (g * 0.58f + 45);
                        b = (int) (b * 0.98f + 70);
                    }
                    if (a == 255) {
                        a = OPAQUE_WATER_ALPHA;
                    }
                } else if (style == TileStyle.OPAQUE || style == TileStyle.LAVA) {
                    a = 255;
                }

                putAtlasPixel(atlasPixmap, tileIndex, tx, ty, r, g, b, a);
            }
        }
    }

    /** Chuyển tọa độ trong ô sang tọa độ pixel toàn atlas. */
    private void putAtlasPixel(Pixmap atlasPixmap, int tileIndex, int tx, int ty, int r, int g, int b, int a) {
        int tileCol = tileIndex % ATLAS_COLS;
        int tileRow = tileIndex / ATLAS_COLS;

        int atlasX = tileCol * TILE_SIZE + tx;
        int atlasY = tileRow * TILE_SIZE + ty;

        int rgba = (clamp(r) << 24) | (clamp(g) << 16) | (clamp(b) << 8) | clamp(a);
        atlasPixmap.drawPixel(atlasX, atlasY, rgba);
    }

    /** Tìm và giải mã ảnh từ classpath, thư mục cục bộ hoặc kết quả quét sâu. */
    private Pixmap loadPixmapFlexible(String... candidateNames) {
        byte[] rawBytes = null;
        String matchedPath = null;

        String[] baseDirs = {
                "",
                "src/main/resources/",
                "src/main/resources/textures/",
                "textures/",
                "texture/"
        };

        for (String candidate : candidateNames) {
            String resourcePath = candidate.startsWith("/") ? candidate : "/" + candidate;
            try (InputStream is = Texture.class.getResourceAsStream(resourcePath)) {
                if (is != null) {
                    rawBytes = is.readAllBytes();
                    matchedPath = "classpath:" + resourcePath;
                    break;
                }
            } catch (IOException ignored) {
            }

            for (String dir : baseDirs) {
                File f = new File(dir + candidate);
                if (f.exists() && f.isFile()) {
                    try {
                        rawBytes = Files.readAllBytes(f.toPath());
                        matchedPath = f.getPath();
                        break;
                    } catch (IOException ignored) {
                    }
                }
            }
            if (rawBytes != null) {
                break;
            }
        }

        if (rawBytes == null && candidateNames.length > 0) {
            String targetFileName = new File(candidateNames[0]).getName().toLowerCase();
            Path found = findFileByName(Path.of("."), targetFileName, 5);
            if (found != null) {
                try {
                    rawBytes = Files.readAllBytes(found);
                    matchedPath = found.toString();
                } catch (IOException ignored) {
                }
            }
        }

        if (rawBytes == null) {
            return null;
        }

        try {
            Pixmap pixmap = new Pixmap(rawBytes, 0, rawBytes.length);
            System.out.println("[Texture] Đã nạp thành công texture: " + matchedPath
                    + " (" + pixmap.getWidth() + "x" + pixmap.getHeight() + ")");
            return pixmap;
        } catch (Exception e) {
            System.err.println("[Texture] Không thể giải mã file ảnh '" + matchedPath + "': " + e.getMessage());
            return null;
        }
    }

    /** Quét có giới hạn theo tên ảnh và bỏ qua các thư mục quản lý Git, Gradle. */
    private Path findFileByName(Path rootDir, String fileNameLower, int maxDepth) {
        if (!Files.exists(rootDir)) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(rootDir, maxDepth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String pathStr = p.toString();
                        return !pathStr.contains(".gradle") && !pathStr.contains(".git");
                    })
                    .filter(p -> p.getFileName().toString().toLowerCase().equals(fileNameLower))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** Dựng các ô cơ bản còn thiếu bằng chuỗi ngẫu nhiên tất định của atlas ban đầu. */
    private void populateProceduralFallbacks(Pixmap atlasPixmap, boolean[] loaded) {
        Random rng = new Random(20261114L);

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int border = (x == 0 || y == 0 || x == TILE_SIZE - 1 || y == TILE_SIZE - 1) ? -8 : 0;

                // 0. DIRT
                int nDirt = rng.nextInt(31) - 15;
                putFallbackPixel(atlasPixmap, loaded, TILE_DIRT, x, y,
                        134 + nDirt + border, 96 + nDirt + border, 67 + nDirt + border, 255);

                // 1. GRASS_TOP
                int nGrass = rng.nextInt(27) - 13;
                putFallbackPixel(atlasPixmap, loaded, TILE_GRASS_TOP, x, y,
                        88 + nGrass + border, 168 + nGrass + border, 52 + nGrass + border, 255);

                // 2. GRASS_SIDE
                int grassEdgeHeight = 3 + ((x * 37 + 13) % 3);
                if (y < grassEdgeHeight) {
                    putFallbackPixel(atlasPixmap, loaded, TILE_GRASS_SIDE, x, y,
                            88 + nGrass + border, 168 + nGrass + border, 52 + nGrass + border, 255);
                } else {
                    putFallbackPixel(atlasPixmap, loaded, TILE_GRASS_SIDE, x, y,
                            134 + nDirt + border, 96 + nDirt + border, 67 + nDirt + border, 255);
                }

                // 3. STONE
                int nStone = rng.nextInt(25) - 12 + ((x + y * 3) % 5 == 0 ? -10 : 0);
                putFallbackPixel(atlasPixmap, loaded, TILE_STONE, x, y,
                        126 + nStone + border, 126 + nStone + border, 126 + nStone + border, 255);

                // 4. BEDROCK
                int nBedrock = ((x * 19 + y * 31 + rng.nextInt(20)) % 3 == 0) ? -35 : 22;
                putFallbackPixel(atlasPixmap, loaded, TILE_BEDROCK, x, y,
                        78 + nBedrock, 78 + nBedrock, 78 + nBedrock, 255);

                // 5. OAK_LOG_SIDE
                int barkStripe = (x % 4 == 0) ? -14 : ((x % 2 == 0) ? 8 : 0);
                int nLog = rng.nextInt(17) - 8 + barkStripe;
                putFallbackPixel(atlasPixmap, loaded, TILE_OAK_LOG_SIDE, x, y,
                        105 + nLog, 82 + nLog, 49 + nLog, 255);

                // 6. OAK_LOG_TOP
                int distFromEdge = Math.min(Math.min(x, TILE_SIZE - 1 - x), Math.min(y, TILE_SIZE - 1 - y));
                if (distFromEdge <= 1) {
                    putFallbackPixel(atlasPixmap, loaded, TILE_OAK_LOG_TOP, x, y,
                            102 + nLog, 80 + nLog, 48 + nLog, 255);
                } else {
                    int ring = (distFromEdge % 2 == 0) ? -10 : 6;
                    putFallbackPixel(atlasPixmap, loaded, TILE_OAK_LOG_TOP, x, y,
                            168 + ring, 137 + ring, 86 + ring, 255);
                }

                // 7. OAK_LEAVES
                int leafPattern = ((x * 7 + y * 13 + rng.nextInt(9)) % 4 == 0) ? -22 : 12;
                int leafAlpha = (x * 11 + y * 13 + x * y) % 7 == 0 ? 0 : 255;
                putFallbackPixel(atlasPixmap, loaded, TILE_OAK_LEAVES, x, y,
                        46 + leafPattern, 118 + leafPattern, 34 + leafPattern, leafAlpha);

                // 8. WATER
                int wave = ((x + y * 2) % 5 == 0) ? 16 : (rng.nextInt(11) - 5);
                putFallbackPixel(atlasPixmap, loaded, TILE_WATER, x, y,
                        42 + wave, 112 + wave, 225 + wave, OPAQUE_WATER_ALPHA);

                // 9. LAVA
                int lavaGlow = ((x * 3 + y * 5) % 7 == 0) ? 35 : (rng.nextInt(31) - 15);
                putFallbackPixel(atlasPixmap, loaded, TILE_LAVA, x, y,
                        230 + lavaGlow, 108 + lavaGlow, 22 + lavaGlow / 2, 255);
            }
        }
    }

    /** Ghi pixel dự phòng nếu ô đó chưa được nạp, giữ chuỗi vân của các ô khác ổn định. */
    private void putFallbackPixel(
            Pixmap atlasPixmap, boolean[] loaded, int tile, int x, int y, int r, int g, int b, int alpha
    ) {
        if (!loaded[tile]) {
            putAtlasPixel(atlasPixmap, tile, x, y, r, g, b, alpha);
        }
    }

    /** Giới hạn giá trị kênh màu vào phạm vi một byte. */
    private int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }

    /**
     * Kích hoạt Texture Atlas vào Texture Unit 0 trên GPU.
     */
    public void bind() {
        gdxTexture.bind(0);
    }

    /**
     * Hủy liên kết Texture trên Texture Unit 0.
     */
    public void unbind() {
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
    }

    /** Trả về atlas dùng chung để các bộ kết xuất có thể lấy texture libGDX. */
    public com.badlogic.gdx.graphics.Texture getGdxTexture() {
        return gdxTexture;
    }

    /** Giải phóng atlas trên GPU khi ứng dụng đóng. */
    @Override
    public void dispose() {
        if (gdxTexture != null) {
            gdxTexture.dispose();
        }
    }
}
