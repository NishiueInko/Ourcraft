package com.voxelgame.engine;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.stream.Stream;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Quản lý việc đọc các file ảnh thực tế (.png, .jpg) bằng thư viện STBImage,
 * tự động chuẩn hóa về kích thước 16x16 pixel và đóng gói thành một tấm ảnh
 * "Texture Atlas" 64x64 duy nhất trên bộ nhớ GPU (VRAM), triển khai {@link Cleanable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Che giấu toàn bộ quá trình cấp phát bộ nhớ Off-heap (`MemoryUtil`), giải mã ảnh C/JNI (`STBImage`),
 *      lấy mẫu điểm ảnh trung tâm (Center-point Nearest-Neighbor Resampling) và cấu hình tham số OpenGL
 *      (`glTexImage2D`, `GL_NEAREST`, `GL_CLAMP_TO_EDGE`).
 * 2. Tính Đóng gói (Encapsulation):
 *    - Mã định danh `textureId` trên GPU và lớp nội bộ `LoadedImage` được khóa `private`, chỉ cung cấp
 *      các phương thức thao tác an toàn `bind()`, `unbind()` và `cleanup()`.
 */
public class Texture implements Cleanable {

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

    // ID của đối tượng Texture trên GPU (đóng gói private final)
    private final int textureId;

    public Texture() {
        ByteBuffer atlasBuffer = BufferUtils.createByteBuffer(ATLAS_WIDTH * ATLAS_HEIGHT * 4);

        // 1. Vẽ sẵn toàn bộ 10 ô bằng thuật toán Procedural Pixel Art sắc nét làm nền dự phòng
        populateProceduralFallbacks(atlasBuffer);

        // 2. Đọc các file ảnh thật (.png / .jpg) bằng STBImage và ghi đè lên từng ô trong Atlas
        loadExternalTexturesIntoAtlas(atlasBuffer);

        atlasBuffer.position(0);
        atlasBuffer.limit(ATLAS_WIDTH * ATLAS_HEIGHT * 4);

        // 3. Khởi tạo đối tượng OpenGL Texture 2D và đẩy tấm Atlas lên VRAM
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA,
                ATLAS_WIDTH,
                ATLAS_HEIGHT,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                atlasBuffer
        );

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void loadExternalTexturesIntoAtlas(ByteBuffer atlasBuffer) {
        STBImage.stbi_set_flip_vertically_on_load(false);

        // 1. Nạp ảnh BEDROCK (bedrock.png) -> Ô 4
        LoadedImage bedrockImg = loadImageFlexible(
                "bedrock.png",
                "textures/bedrock.png"
        );
        if (bedrockImg != null) {
            blitResizedToTile(atlasBuffer, bedrockImg, 0, 0, bedrockImg.width, bedrockImg.height, TILE_BEDROCK, false);
            bedrockImg.free();
        }

        // 2. Nạp ảnh STONE (stone.png) -> Ô 3
        LoadedImage stoneImg = loadImageFlexible(
                "stone.png",
                "textures/stone.png"
        );
        if (stoneImg != null) {
            blitResizedToTile(atlasBuffer, stoneImg, 0, 0, stoneImg.width, stoneImg.height, TILE_STONE, false);
            stoneImg.free();
        }

        // 3. Nạp ảnh GRASS BLOCK (grass-block.png) -> Ô 1 (GRASS_TOP) và Ô 2 (GRASS_SIDE)
        LoadedImage grassImg = loadImageFlexible(
                "grass-block.png",
                "textures/grass-block.png",
                "grass_block.png"
        );
        if (grassImg != null) {
            if (grassImg.width == 32 && grassImg.height == 32) {
                blitResizedToTile(atlasBuffer, grassImg, 16, 16, 16, 16, TILE_GRASS_TOP, false);
                blitResizedToTile(atlasBuffer, grassImg, 0, 16, 16, 16, TILE_GRASS_SIDE, false);
                blitResizedToTile(atlasBuffer, grassImg, 0, 0, 16, 16, TILE_DIRT, false);
            } else {
                blitResizedToTile(atlasBuffer, grassImg, 0, 0, grassImg.width, grassImg.height, TILE_GRASS_TOP, false);
            }
            grassImg.free();
        }

        // 4. Nạp ảnh DIRT -> Ô 0
        LoadedImage dirtImg = loadImageFlexible(
                "Minecraft Dirt Small by terridee, click to purchase fabric.jpg",
                "textures/Minecraft Dirt Small by terridee, click to purchase fabric.jpg",
                "dirt.jpg",
                "dirt.png"
        );
        if (dirtImg != null) {
            blitResizedToTile(atlasBuffer, dirtImg, 0, 0, dirtImg.width, dirtImg.height, TILE_DIRT, false);
            dirtImg.free();
        }

        // 5. Nạp ảnh OAK LOG -> Ô 5 (OAK_LOG_SIDE)
        LoadedImage oakLogImg = loadImageFlexible(
                "Minecraft Oak Log Block Texture Pixel Art.jpg",
                "textures/Minecraft Oak Log Block Texture Pixel Art.jpg",
                "oak_log.jpg",
                "oak_log.png"
        );
        if (oakLogImg != null) {
            blitResizedToTile(atlasBuffer, oakLogImg, 0, 0, oakLogImg.width, oakLogImg.height, TILE_OAK_LOG_SIDE, false);
            oakLogImg.free();
        }

        // 6. Nạp ảnh OAK LEAVES (nếu có) -> Ô 7
        LoadedImage leavesImg = loadImageFlexible(
                "oak_leaves.png",
                "textures/oak_leaves.png"
        );
        if (leavesImg != null) {
            blitResizedToTile(atlasBuffer, leavesImg, 0, 0, leavesImg.width, leavesImg.height, TILE_OAK_LEAVES, false);
            leavesImg.free();
        }

        // 7. Nạp ảnh WATER (water_still.png) -> Ô 8
        LoadedImage waterImg = loadImageFlexible(
                "water_still.png",
                "textures/water_still.png",
                "fliflight-clear-water-1.0.0-resourcepack-1.21.4/assets/minecraft/textures/block/water_still.png",
                "textures/fliflight-clear-water-1.0.0-resourcepack-1.21.4/assets/minecraft/textures/block/water_still.png"
        );
        if (waterImg != null) {
            int frameSize = Math.min(waterImg.width, waterImg.height);
            blitResizedToTile(atlasBuffer, waterImg, 0, 0, frameSize, frameSize, TILE_WATER, true);
            waterImg.free();
        }

        // 8. Nạp ảnh LAVA (download.jpg) -> Ô 9
        LoadedImage lavaImg = loadImageFlexible(
                "download.jpg",
                "textures/download.jpg",
                "lava.jpg",
                "lava.png"
        );
        if (lavaImg != null) {
            blitResizedToTile(atlasBuffer, lavaImg, 0, 0, lavaImg.width, lavaImg.height, TILE_LAVA, false);
            lavaImg.free();
        }
    }

    private void blitResizedToTile(
            ByteBuffer atlasBuffer,
            LoadedImage src,
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

                int srcPixelOffset = (sy * src.width + sx) * 4;
                int r = src.pixels.get(srcPixelOffset) & 0xFF;
                int g = src.pixels.get(srcPixelOffset + 1) & 0xFF;
                int b = src.pixels.get(srcPixelOffset + 2) & 0xFF;
                int a = src.pixels.get(srcPixelOffset + 3) & 0xFF;

                if (tintWaterBlue && Math.abs(r - g) < 10 && Math.abs(g - b) < 10) {
                    r = clamp((int) (r * 0.22f + 18));
                    g = clamp((int) (g * 0.58f + 45));
                    b = clamp((int) (b * 0.98f + 70));
                    a = 225;
                } else {
                    a = 255;
                }

                putAtlasPixel(atlasBuffer, tileIndex, tx, ty, r, g, b, a);
            }
        }
    }

    private void putAtlasPixel(ByteBuffer atlasBuffer, int tileIndex, int tx, int ty, int r, int g, int b, int a) {
        int tileCol = tileIndex % ATLAS_COLS;
        int tileRow = tileIndex / ATLAS_COLS;

        int atlasX = tileCol * TILE_SIZE + tx;
        int atlasY = tileRow * TILE_SIZE + ty;

        int byteIndex = (atlasY * ATLAS_WIDTH + atlasX) * 4;
        atlasBuffer.put(byteIndex, (byte) clamp(r));
        atlasBuffer.put(byteIndex + 1, (byte) clamp(g));
        atlasBuffer.put(byteIndex + 2, (byte) clamp(b));
        atlasBuffer.put(byteIndex + 3, (byte) clamp(a));
    }

    private static class LoadedImage {
        final ByteBuffer pixels;
        final int width;
        final int height;

        LoadedImage(ByteBuffer pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }

        void free() {
            STBImage.stbi_image_free(pixels);
        }
    }

    private LoadedImage loadImageFlexible(String... candidateNames) {
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

        ByteBuffer encodedBuffer = MemoryUtil.memAlloc(rawBytes.length);
        try (MemoryStack stack = stackPush()) {
            encodedBuffer.put(rawBytes).flip();

            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            IntBuffer pChannels = stack.mallocInt(1);

            ByteBuffer decodedPixels = STBImage.stbi_load_from_memory(
                    encodedBuffer, pWidth, pHeight, pChannels, 4
            );

            if (decodedPixels == null) {
                System.err.println("[Texture] Không thể giải mã file ảnh '" + matchedPath + "': "
                        + STBImage.stbi_failure_reason());
                return null;
            }

            System.out.println("[Texture] Đã nạp thành công texture: " + matchedPath
                    + " (" + pWidth.get(0) + "x" + pHeight.get(0) + ")");
            return new LoadedImage(decodedPixels, pWidth.get(0), pHeight.get(0));
        } finally {
            MemoryUtil.memFree(encodedBuffer);
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

    private void populateProceduralFallbacks(ByteBuffer atlasBuffer) {
        Random rng = new Random(20261114L);

        for (int y = 0; y < TILE_SIZE; y++) {
            for (int x = 0; x < TILE_SIZE; x++) {
                int border = (x == 0 || y == 0 || x == TILE_SIZE - 1 || y == TILE_SIZE - 1) ? -8 : 0;

                // 0. DIRT
                int nDirt = rng.nextInt(31) - 15;
                putAtlasPixel(atlasBuffer, TILE_DIRT, x, y,
                        134 + nDirt + border, 96 + nDirt + border, 67 + nDirt + border, 255);

                // 1. GRASS_TOP
                int nGrass = rng.nextInt(27) - 13;
                putAtlasPixel(atlasBuffer, TILE_GRASS_TOP, x, y,
                        88 + nGrass + border, 168 + nGrass + border, 52 + nGrass + border, 255);

                // 2. GRASS_SIDE
                int grassEdgeHeight = 3 + ((x * 37 + 13) % 3);
                if (y < grassEdgeHeight) {
                    putAtlasPixel(atlasBuffer, TILE_GRASS_SIDE, x, y,
                            88 + nGrass + border, 168 + nGrass + border, 52 + nGrass + border, 255);
                } else {
                    putAtlasPixel(atlasBuffer, TILE_GRASS_SIDE, x, y,
                            134 + nDirt + border, 96 + nDirt + border, 67 + nDirt + border, 255);
                }

                // 3. STONE
                int nStone = rng.nextInt(25) - 12 + ((x + y * 3) % 5 == 0 ? -10 : 0);
                putAtlasPixel(atlasBuffer, TILE_STONE, x, y,
                        126 + nStone + border, 126 + nStone + border, 126 + nStone + border, 255);

                // 4. BEDROCK
                int nBedrock = ((x * 19 + y * 31 + rng.nextInt(20)) % 3 == 0) ? -35 : 22;
                putAtlasPixel(atlasBuffer, TILE_BEDROCK, x, y,
                        78 + nBedrock, 78 + nBedrock, 78 + nBedrock, 255);

                // 5. OAK_LOG_SIDE
                int barkStripe = (x % 4 == 0) ? -14 : ((x % 2 == 0) ? 8 : 0);
                int nLog = rng.nextInt(17) - 8 + barkStripe;
                putAtlasPixel(atlasBuffer, TILE_OAK_LOG_SIDE, x, y,
                        105 + nLog, 82 + nLog, 49 + nLog, 255);

                // 6. OAK_LOG_TOP
                int distFromEdge = Math.min(Math.min(x, TILE_SIZE - 1 - x), Math.min(y, TILE_SIZE - 1 - y));
                if (distFromEdge <= 1) {
                    putAtlasPixel(atlasBuffer, TILE_OAK_LOG_TOP, x, y, 102 + nLog, 80 + nLog, 48 + nLog, 255);
                } else {
                    int ring = (distFromEdge % 2 == 0) ? -10 : 6;
                    putAtlasPixel(atlasBuffer, TILE_OAK_LOG_TOP, x, y,
                            168 + ring, 137 + ring, 86 + ring, 255);
                }

                // 7. OAK_LEAVES
                int leafPattern = ((x * 7 + y * 13 + rng.nextInt(9)) % 4 == 0) ? -22 : 12;
                putAtlasPixel(atlasBuffer, TILE_OAK_LEAVES, x, y,
                        46 + leafPattern, 118 + leafPattern, 34 + leafPattern, 255);

                // 8. WATER
                int wave = ((x + y * 2) % 5 == 0) ? 16 : (rng.nextInt(11) - 5);
                putAtlasPixel(atlasBuffer, TILE_WATER, x, y,
                        42 + wave, 112 + wave, 225 + wave, 225);

                // 9. LAVA
                int lavaGlow = ((x * 3 + y * 5) % 7 == 0) ? 35 : (rng.nextInt(31) - 15);
                putAtlasPixel(atlasBuffer, TILE_LAVA, x, y,
                        230 + lavaGlow, 108 + lavaGlow, 22 + lavaGlow / 2, 255);
            }
        }
    }

    private int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }

    public void bind() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    @Override
    public void cleanup() {
        glDeleteTextures(textureId);
    }
}
