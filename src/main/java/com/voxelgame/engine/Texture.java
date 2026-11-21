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
 * và đóng gói thành một tấm ảnh "Texture Atlas" 64x64 duy nhất trên VRAM GPU thông qua
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
    // Số cột và số hàng của lưới Texture Atlas (4x4 = 16 ô)
    public static final int ATLAS_COLS = 4;
    public static final int ATLAS_ROWS = 4;
    // Kích thước tổng thể của tấm ảnh Texture Atlas (64x64 pixel)
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

    // Đối tượng Texture của libGDX đại diện cho Texture Atlas 64x64 trên VRAM
    private final com.badlogic.gdx.graphics.Texture gdxTexture;

    public Texture() {
        // Khởi tạo Pixmap 64x64 định dạng RGBA8888 trên bộ nhớ RAM
        Pixmap atlasPixmap = new Pixmap(ATLAS_WIDTH, ATLAS_HEIGHT, Pixmap.Format.RGBA8888);

        // 1. Vẽ sẵn toàn bộ 10 ô bằng thuật toán Procedural Pixel Art sắc nét làm nền dự phòng
        populateProceduralFallbacks(atlasPixmap);

        // 2. Đọc các file ảnh thật (.png / .jpg) và ghi đè lên từng ô trong Atlas
        loadExternalTexturesIntoAtlas(atlasPixmap);

        // 3. Khởi tạo đối tượng OpenGL Texture 2D của libGDX và nạp tấm Atlas lên VRAM
        gdxTexture = new com.badlogic.gdx.graphics.Texture(atlasPixmap);
        gdxTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        gdxTexture.setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);

        // Giải phóng ngay Pixmap trên CPU sau khi đã đẩy dữ liệu lên VRAM
        atlasPixmap.dispose();
    }

    private void loadExternalTexturesIntoAtlas(Pixmap atlasPixmap) {
        // 1. Nạp ảnh BEDROCK (bedrock.png) -> Ô 4
        Pixmap bedrockImg = loadPixmapFlexible(
                "bedrock.png",
                "textures/bedrock.png"
        );
        if (bedrockImg != null) {
            blitResizedToTile(atlasPixmap, bedrockImg, 0, 0, bedrockImg.getWidth(), bedrockImg.getHeight(), TILE_BEDROCK, false);
            bedrockImg.dispose();
        }

        // 2. Nạp ảnh STONE (stone.png) -> Ô 3
        Pixmap stoneImg = loadPixmapFlexible(
                "stone.png",
                "textures/stone.png"
        );
        if (stoneImg != null) {
            blitResizedToTile(atlasPixmap, stoneImg, 0, 0, stoneImg.getWidth(), stoneImg.getHeight(), TILE_STONE, false);
            stoneImg.dispose();
        }

        // 3. Nạp ảnh GRASS BLOCK (grass-block.png) -> Ô 1 (GRASS_TOP) và Ô 2 (GRASS_SIDE)
        Pixmap grassImg = loadPixmapFlexible(
                "grass-block.png",
                "textures/grass-block.png",
                "grass_block.png"
        );
        if (grassImg != null) {
            if (grassImg.getWidth() == 32 && grassImg.getHeight() == 32) {
                blitResizedToTile(atlasPixmap, grassImg, 16, 16, 16, 16, TILE_GRASS_TOP, false);
                blitResizedToTile(atlasPixmap, grassImg, 0, 16, 16, 16, TILE_GRASS_SIDE, false);
                blitResizedToTile(atlasPixmap, grassImg, 0, 0, 16, 16, TILE_DIRT, false);
            } else {
                blitResizedToTile(atlasPixmap, grassImg, 0, 0, grassImg.getWidth(), grassImg.getHeight(), TILE_GRASS_TOP, false);
            }
            grassImg.dispose();
        }

        // 4. Nạp ảnh DIRT -> Ô 0
        Pixmap dirtImg = loadPixmapFlexible(
                "Minecraft Dirt Small by terridee, click to purchase fabric.jpg",
                "textures/Minecraft Dirt Small by terridee, click to purchase fabric.jpg",
                "dirt.jpg",
                "dirt.png"
        );
        if (dirtImg != null) {
            blitResizedToTile(atlasPixmap, dirtImg, 0, 0, dirtImg.getWidth(), dirtImg.getHeight(), TILE_DIRT, false);
            dirtImg.dispose();
        }

        // 5. Nạp ảnh OAK LOG -> Ô 5 (OAK_LOG_SIDE)
        Pixmap oakLogImg = loadPixmapFlexible(
                "Minecraft Oak Log Block Texture Pixel Art.jpg",
                "textures/Minecraft Oak Log Block Texture Pixel Art.jpg",
                "oak_log.jpg",
                "oak_log.png"
        );
        if (oakLogImg != null) {
            blitResizedToTile(atlasPixmap, oakLogImg, 0, 0, oakLogImg.getWidth(), oakLogImg.getHeight(), TILE_OAK_LOG_SIDE, false);
            oakLogImg.dispose();
        }

        // 6. Nạp ảnh OAK LEAVES (nếu có) -> Ô 7
        Pixmap leavesImg = loadPixmapFlexible(
                "oak_leaves.png",
                "textures/oak_leaves.png"
        );
        if (leavesImg != null) {
            blitResizedToTile(atlasPixmap, leavesImg, 0, 0, leavesImg.getWidth(), leavesImg.getHeight(), TILE_OAK_LEAVES, false);
            leavesImg.dispose();
        }

        // 7. Nạp ảnh WATER (water_still.png) -> Ô 8
        Pixmap waterImg = loadPixmapFlexible(
                "water_still.png",
                "textures/water_still.png",
                "fliflight-clear-water-1.0.0-resourcepack-1.21.4/assets/minecraft/textures/block/water_still.png",
                "textures/fliflight-clear-water-1.0.0-resourcepack-1.21.4/assets/minecraft/textures/block/water_still.png"
        );
        if (waterImg != null) {
            int frameSize = Math.min(waterImg.getWidth(), waterImg.getHeight());
            blitResizedToTile(atlasPixmap, waterImg, 0, 0, frameSize, frameSize, TILE_WATER, true);
            waterImg.dispose();
        }

        // 8. Nạp ảnh LAVA (download.jpg) -> Ô 9
        Pixmap lavaImg = loadPixmapFlexible(
                "download.jpg",
                "textures/download.jpg",
                "lava.jpg",
                "lava.png"
        );
        if (lavaImg != null) {
            blitResizedToTile(atlasPixmap, lavaImg, 0, 0, lavaImg.getWidth(), lavaImg.getHeight(), TILE_LAVA, false);
            lavaImg.dispose();
        }
    }

    private void blitResizedToTile(
            Pixmap atlasPixmap,
            Pixmap src,
            int srcX,
            int srcY,
            int srcW,
            int srcH,
            int tileIndex,
            boolean tintWaterBlue
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

                if (tintWaterBlue && Math.abs(r - g) < 10 && Math.abs(g - b) < 10) {
                    r = clamp((int) (r * 0.22f + 18));
                    g = clamp((int) (g * 0.58f + 45));
                    b = clamp((int) (b * 0.98f + 70));
                    a = 225;
                } else {
                    a = 255;
                }

                putAtlasPixel(atlasPixmap, tileIndex, tx, ty, r, g, b, a);
            }
        }
    }

    private void putAtlasPixel(Pixmap atlasPixmap, int tileIndex, int tx, int ty, int r, int g, int b, int a) {
        int tileCol = tileIndex % ATLAS_COLS;
        int tileRow = tileIndex / ATLAS_COLS;

        int atlasX = tileCol * TILE_SIZE + tx;
        int atlasY = tileRow * TILE_SIZE + ty;

        int rgba = (clamp(r) << 24) | (clamp(g) << 16) | (clamp(b) << 8) | clamp(a);
        atlasPixmap.drawPixel(atlasX, atlasY, rgba);
    }

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

    private void populateProceduralFallbacks(Pixmap atlasPixmap) {
        Random rng = new Random(20261114L);

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int border = (x == 0 || y == 0 || x == TILE_SIZE - 1 || y == TILE_SIZE - 1) ? -8 : 0;

                // 0. DIRT
                int nDirt = rng.nextInt(31) - 15;
                putAtlasPixel(atlasPixmap, TILE_DIRT, x, y,
                        134 + nDirt + border, 96 + nDirt + border, 67 + nDirt + border, 255);

                // 1. GRASS_TOP
                int nGrass = rng.nextInt(27) - 13;
                putAtlasPixel(atlasPixmap, TILE_GRASS_TOP, x, y,
                        88 + nGrass + border, 168 + nGrass + border, 52 + nGrass + border, 255);

                // 2. GRASS_SIDE
                int grassEdgeHeight = 3 + ((x * 37 + 13) % 3);
                if (y < grassEdgeHeight) {
                    putAtlasPixel(atlasPixmap, TILE_GRASS_SIDE, x, y,
                            88 + nGrass + border, 168 + nGrass + border, 52 + nGrass + border, 255);
                } else {
                    putAtlasPixel(atlasPixmap, TILE_GRASS_SIDE, x, y,
                            134 + nDirt + border, 96 + nDirt + border, 67 + nDirt + border, 255);
                }

                // 3. STONE
                int nStone = rng.nextInt(25) - 12 + ((x + y * 3) % 5 == 0 ? -10 : 0);
                putAtlasPixel(atlasPixmap, TILE_STONE, x, y,
                        126 + nStone + border, 126 + nStone + border, 126 + nStone + border, 255);

                // 4. BEDROCK
                int nBedrock = ((x * 19 + y * 31 + rng.nextInt(20)) % 3 == 0) ? -35 : 22;
                putAtlasPixel(atlasPixmap, TILE_BEDROCK, x, y,
                        78 + nBedrock, 78 + nBedrock, 78 + nBedrock, 255);

                // 5. OAK_LOG_SIDE
                int barkStripe = (x % 4 == 0) ? -14 : ((x % 2 == 0) ? 8 : 0);
                int nLog = rng.nextInt(17) - 8 + barkStripe;
                putAtlasPixel(atlasPixmap, TILE_OAK_LOG_SIDE, x, y,
                        105 + nLog, 82 + nLog, 49 + nLog, 255);

                // 6. OAK_LOG_TOP
                int distFromEdge = Math.min(Math.min(x, TILE_SIZE - 1 - x), Math.min(y, TILE_SIZE - 1 - y));
                if (distFromEdge <= 1) {
                    putAtlasPixel(atlasPixmap, TILE_OAK_LOG_TOP, x, y, 102 + nLog, 80 + nLog, 48 + nLog, 255);
                } else {
                    int ring = (distFromEdge % 2 == 0) ? -10 : 6;
                    putAtlasPixel(atlasPixmap, TILE_OAK_LOG_TOP, x, y,
                            168 + ring, 137 + ring, 86 + ring, 255);
                }

                // 7. OAK_LEAVES
                int leafPattern = ((x * 7 + y * 13 + rng.nextInt(9)) % 4 == 0) ? -22 : 12;
                putAtlasPixel(atlasPixmap, TILE_OAK_LEAVES, x, y,
                        46 + leafPattern, 118 + leafPattern, 34 + leafPattern, 255);

                // 8. WATER
                int wave = ((x + y * 2) % 5 == 0) ? 16 : (rng.nextInt(11) - 5);
                putAtlasPixel(atlasPixmap, TILE_WATER, x, y,
                        42 + wave, 112 + wave, 225 + wave, 225);

                // 9. LAVA
                int lavaGlow = ((x * 3 + y * 5) % 7 == 0) ? 35 : (rng.nextInt(31) - 15);
                putAtlasPixel(atlasPixmap, TILE_LAVA, x, y,
                        230 + lavaGlow, 108 + lavaGlow, 22 + lavaGlow / 2, 255);
            }
        }
    }

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

    public com.badlogic.gdx.graphics.Texture getGdxTexture() {
        return gdxTexture;
    }

    @Override
    public void dispose() {
        if (gdxTexture != null) {
            gdxTexture.dispose();
        }
    }
}
