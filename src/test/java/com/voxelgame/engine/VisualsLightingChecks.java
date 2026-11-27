package com.voxelgame.engine;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Item;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.Sheep;
import com.voxelgame.entity.mob.Zombie;

/**
 * Kiểm tra CPU cho hệ thống đồ họa, ánh sáng, nhuộm màu biome, chất lỏng và hạt của Track B.
 */
public final class VisualsLightingChecks {

    private VisualsLightingChecks() {
    }

    /** Chạy toàn bộ các ca kiểm tra hồi quy của Track B. */
    public static void run() {
        checkSkyAndBlockLighting();
        checkBiomeTintRatios();
        checkLiquidAnimatorFrames();
        checkBreakStageCalculation();
        checkParticlePoolLimits();
        checkFireAnimationFrameSelection();
    }

    /**
     * Kiểm tra lan truyền Sky Light (qua mái kín, lỗ đục, tán lá) và Block Light từ dung nham
     * bằng thuật toán BFS 4-bit trên thế giới trống không cần OpenGL.
     */
    private static void checkSkyAndBlockLighting() {
        World world = World.empty();
        try {
            // Dựng phòng đá kín có sàn tại Y=10, mái tại Y=20 và vách bao x/z = 0..16 (qua biên chunk).
            for (int x = 0; x <= 16; x++) {
                for (int z = 0; z <= 16; z++) {
                    world.setBlock(x, 10, z, Block.STONE.getId());
                    world.setBlock(x, 20, z, Block.STONE.getId());
                    if (x == 0 || x == 16 || z == 0 || z == 16) {
                        for (int y = 11; y <= 19; y++) {
                            world.setBlock(x, y, z, Block.STONE.getId());
                        }
                    }
                }
            }

            // Ô nằm dưới mái đá kín phải tối (< 15, cụ thể bằng 0 khi phòng kín hoàn toàn).
            require(world.getSkyLight(8, 15, 8) < 15,
                    "Ô Y=15 dưới mái đá kín phải có skyLight < 15, nhận được: " + world.getSkyLight(8, 15, 8));
            require(world.getSkyLight(8, 15, 8) == 0,
                    "Phòng đá kín hoàn toàn phải có skyLight == 0, nhận được: " + world.getSkyLight(8, 15, 8));

            // Đục lỗ mái ở (8, 20, 8): cột bên dưới nhận skyLight == 15 và lan ngang giảm 1 mỗi bước.
            world.setBlock(8, 20, 8, Block.AIR.getId());
            require(world.getSkyLight(8, 20, 8) == 15, "Ô lỗ mái phải nhận skyLight == 15");
            require(world.getSkyLight(8, 15, 8) == 15, "Cột dưới lỗ mái tại Y=15 phải nhận skyLight == 15");
            require(world.getSkyLight(8, 11, 8) == 15, "Cột dưới lỗ mái tại Y=11 phải nhận skyLight == 15");
            require(world.getSkyLight(9, 15, 8) == 14, "Lan ngang 1 bước phải còn skyLight == 14");
            require(world.getSkyLight(10, 15, 8) == 13, "Lan ngang 2 bước phải còn skyLight == 13");
            require(world.getSkyLight(12, 15, 8) == 11, "Lan ngang 4 bước phải còn skyLight == 11");

            // Bịt lại lỗ mái -> ánh sáng dưới mái phải tối trở lại.
            world.setBlock(8, 20, 8, Block.STONE.getId());
            require(world.getSkyLight(8, 15, 8) == 0, "Sau khi bịt lỗ mái, skyLight phải quay về 0");

            // Kiểm tra suy giảm Sky Light qua lá cây (-1 mỗi lớp lá).
            world.setBlock(25, 20, 8, Block.OAK_LEAVES.getId());
            require(world.getSkyLight(25, 20, 8) == 14,
                    "Sky light đi qua 1 lớp lá phải suy giảm còn 14, nhận được: " + world.getSkyLight(25, 20, 8));

            // Đặt dung nham tại (8, 12, 8) và kiểm tra Block Light lan truyền BFS 6 hướng.
            world.setBlock(8, 12, 8, Block.LAVA.getId());
            require(world.getBlockLight(8, 12, 8) == 15,
                    "Tại nguồn Block.LAVA phải có blockLight == 15");
            require(world.getBlockLight(9, 12, 8) == 14,
                    "Cách Block.LAVA 1 bước phải có blockLight == 14");
            require(world.getBlockLight(12, 12, 8) == 11,
                    "Cách Block.LAVA 4 bước phải có blockLight == 11");

            // Xóa nguồn dung nham -> Block Light phải được dọn về 0.
            world.setBlock(8, 12, 8, Block.AIR.getId());
            require(world.getBlockLight(8, 12, 8) == 0, "Sau khi xóa Lava, blockLight tại chỗ phải về 0");
            require(world.getBlockLight(9, 12, 8) == 0, "Sau khi xóa Lava, blockLight lân cận phải về 0");

            // Kiểm tra ánh sáng Block Light lan qua biên chunk (x = 15 -> x = 16) và đánh dấu chunk kế dirty.
            world.setBlock(20, 12, 25, Block.STONE.getId());
            Chunk neighborChunk = world.getChunkRaw(1, 0, 1);
            require(neighborChunk != null, "Chunk lân cận (1,0,1) phải tồn tại để kiểm tra dirty");
            world.getBlockLight(16, 12, 25); // Khởi tạo cột ánh sáng trước.
            clearDirtyWithoutGl(neighborChunk);
            require(!neighborChunk.isDirty(), "Chunk sau khi đặt sạch phải có dirty == false");
            world.setBlock(15, 12, 25, Block.LAVA.getId());
            require(world.getBlockLight(16, 12, 25) == 14,
                    "Block light phải lan qua biên chunk từ X=15 sang X=16 với mức 14");
            require(neighborChunk.isDirty(),
                    "Khi ánh sáng lan qua biên chunk, chunk lân cận phải được đánh dấu dirty = true");

            // Kiểm tra độ sáng đỉnh mesh trong phòng kín tối (sàn >= 0.04f) và khi đặt Lava bên trong.
            Chunk insideChunk = world.getChunkRaw(0, 0, 0);
            com.badlogic.gdx.utils.FloatArray opaque = new com.badlogic.gdx.utils.FloatArray();
            com.badlogic.gdx.utils.FloatArray translucent = new com.badlogic.gdx.utils.FloatArray();
            insideChunk.buildVertices(world, opaque, translucent);
            float darkTopShade = findFaceShade(opaque, 8f, 11f, 8f);
            require(Math.abs(darkTopShade - ChunkLighting.MIN_LIGHT_LEVEL) < 0.001f,
                    "Mặt trên sàn trong phòng kín tối phải đạt sàn tối thiểu 0.04f, nhận được: " + darkTopShade);

            // Kiểm tra trực tiếp API của ChunkLighting (setSkyLight / setBlockLight / recomputeColumnSkyLight / propagateLight).
            ChunkLighting directLighting = new ChunkLighting();
            require(directLighting.setSkyLight(world, 30, 15, 30, 7), "setSkyLight phải báo thay đổi");
            require(directLighting.getSkyLight(world, 30, 15, 30) == 7, "getSkyLight phải đọc đúng 7");
            require(directLighting.setBlockLight(world, 30, 15, 30, 12), "setBlockLight phải báo thay đổi");
            require(directLighting.getBlockLight(world, 30, 15, 30) == 12, "getBlockLight phải đọc đúng 12");
            directLighting.recomputeColumnSkyLight(world, 30, 30);
            require(directLighting.getSkyLight(world, 30, 15, 30) == 15,
                    "recomputeColumnSkyLight ở cột thoáng phải khôi phục về 15");
            world.setBlockInternal(32, 15, 30, Block.LAVA.getId(), 0);
            directLighting.propagateLight(world, 32, 15, 30);
            require(directLighting.getBlockLight(world, 33, 15, 30) == 14,
                    "propagateLight quanh Lava phải lan mức 14 sang ô kề");
        } finally {
            world.dispose();
        }
    }

    /**
     * Kiểm tra hệ số nhuộm màu {@link BiomeTint} cho cỏ và lá theo 5 biome:
     * PLAINS xấp xỉ (1, 1, 1), DESERT có kênh đỏ > kênh xanh dương, SNOWY_PLAINS có kênh lam lạnh hơn PLAINS,
     * và pha mượt 3x3 tại ranh giới biome.
     */
    private static void checkBiomeTintRatios() {
        World world = World.empty();
        try {
            float[] plainsGrass = new float[3];
            float[] plainsFoliage = new float[3];
            require(world.getBiome(2000, 1000) == BiomeType.PLAINS,
                    "Tọa độ (2000, 1000) phải nằm trong biome PLAINS");
            BiomeTint.grassMultiplier(world, 2000, 1000, plainsGrass);
            BiomeTint.foliageMultiplier(world, 2000, 1000, plainsFoliage);
            require(Math.abs(plainsGrass[0] - 1.0f) < 0.01f
                            && Math.abs(plainsGrass[1] - 1.0f) < 0.01f
                            && Math.abs(plainsGrass[2] - 1.0f) < 0.01f,
                    "Tại trung tâm PLAINS, grassMultiplier phải xấp xỉ (1, 1, 1)");
            require(Math.abs(plainsFoliage[0] - 1.0f) < 0.01f
                            && Math.abs(plainsFoliage[1] - 1.0f) < 0.01f
                            && Math.abs(plainsFoliage[2] - 1.0f) < 0.01f,
                    "Tại trung tâm PLAINS, foliageMultiplier phải xấp xỉ (1, 1, 1)");

            float[] desertGrass = new float[3];
            float[] desertFoliage = new float[3];
            require(world.getBiome(3600, 1000) == BiomeType.DESERT,
                    "Tọa độ (3600, 1000) phải nằm trong biome DESERT");
            BiomeTint.grassMultiplier(world, 3600, 1000, desertGrass);
            BiomeTint.foliageMultiplier(world, 3600, 1000, desertFoliage);
            require(desertGrass[0] > desertGrass[2] && desertGrass[0] > 1.0f,
                    "Cỏ DESERT phải có kênh đỏ lớn hơn kênh xanh dương (tông vàng úa)");
            require(desertFoliage[0] > desertFoliage[2] && desertFoliage[0] > 1.0f,
                    "Lá DESERT phải có kênh đỏ lớn hơn kênh xanh dương (tông vàng úa)");

            float[] snowyGrass = new float[3];
            float[] snowyFoliage = new float[3];
            require(world.getBiome(200, 1000) == BiomeType.SNOWY_PLAINS,
                    "Tọa độ (200, 1000) phải nằm trong biome SNOWY_PLAINS");
            BiomeTint.grassMultiplier(world, 200, 1000, snowyGrass);
            BiomeTint.foliageMultiplier(world, 200, 1000, snowyFoliage);
            require(snowyGrass[2] > plainsGrass[2] && snowyGrass[2] > snowyGrass[0],
                    "Cỏ SNOWY_PLAINS phải có kênh xanh lam lạnh hơn PLAINS");
            require(snowyFoliage[2] > plainsFoliage[2] && snowyFoliage[2] > snowyFoliage[0],
                    "Lá SNOWY_PLAINS phải có kênh xanh lam lạnh hơn PLAINS");

            float[] flowerGrass = new float[3];
            require(world.getBiome(2800, 1000) == BiomeType.FLOWER_FOREST,
                    "Tọa độ (2800, 1000) phải nằm trong biome FLOWER_FOREST");
            BiomeTint.grassMultiplier(world, 2800, 1000, flowerGrass);

            int boundaryX = -1;
            for (int x = 3000; x <= 3400; x++) {
                if (world.getBiome(x, 1000) == BiomeType.FLOWER_FOREST
                        && world.getBiome(x + 1, 1000) == BiomeType.DESERT) {
                    boundaryX = x;
                    break;
                }
            }
            require(boundaryX != -1, "Phải tìm thấy ranh giới giữa FLOWER_FOREST và DESERT quanh X=3200");
            float[] boundaryGrass = new float[3];
            BiomeTint.grassMultiplier(world, boundaryX, 1000, boundaryGrass);
            require(boundaryGrass[0] > flowerGrass[0] && boundaryGrass[0] < desertGrass[0],
                    "Ranh giới biome phải được pha mượt 3x3 thay vì nhảy bậc cứng");

            // Kiểm tra Chunk ghi đúng màu đỉnh aColor (7 floats/đỉnh): mặt trên GRASS ở Desert khác trắng, mặt bên/đá giữ trắng.
            int desertChunkX = 3600 / Chunk.SIZE;
            int desertChunkZ = 1000 / Chunk.SIZE;
            int gx = desertChunkX * Chunk.SIZE + 2;
            int gz = desertChunkZ * Chunk.SIZE + 2;
            world.setBlock(gx, 10, gz, Block.GRASS.getId());
            world.setBlock(gx + 2, 10, gz, Block.STONE.getId());
            Chunk desertChunk = world.getChunkRaw(desertChunkX, 0, desertChunkZ);
            com.badlogic.gdx.utils.FloatArray opaque = new com.badlogic.gdx.utils.FloatArray();
            com.badlogic.gdx.utils.FloatArray translucent = new com.badlogic.gdx.utils.FloatArray();
            desertChunk.buildVertices(world, opaque, translucent);
            float grassTopColor = findFaceColor(opaque, gx, 11f, gz);
            float grassSideColor = findFaceColor(opaque, gx, 10f, gz);
            float stoneColor = findFaceColor(opaque, gx + 2, 11f, gz);
            require(Float.floatToRawIntBits(grassTopColor) != Float.floatToRawIntBits(BiomeTint.WHITE_PACKED_COLOR),
                    "Mặt trên Block.GRASS ở Desert phải được nhuộm khác trắng");
            require(Float.floatToRawIntBits(grassSideColor) == Float.floatToRawIntBits(BiomeTint.WHITE_PACKED_COLOR),
                    "Mặt bên Block.GRASS có phần đất phải giữ màu trắng 0xFFFFFFFF");
            require(Float.floatToRawIntBits(stoneColor) == Float.floatToRawIntBits(BiomeTint.WHITE_PACKED_COLOR),
                    "Block.STONE phải giữ màu trắng 0xFFFFFFFF");
        } finally {
            world.dispose();
        }
    }

    /**
     * Kiểm tra {@link LiquidAnimator} nạp đúng 32 frame nước và 20 frame dung nham ở chế độ CPU,
     * xoay vòng frame sau mỗi 0.10 giây và giải phóng sạch toàn bộ bộ đệm Pixmap khi gọi dispose().
     */
    private static void checkLiquidAnimatorFrames() {
        GdxNativesLoader.load();
        LiquidAnimator animator = new LiquidAnimator();
        try {
            require(animator.getWaterFrameCount() == 32,
                    "LiquidAnimator phải có đúng 32 frame nước, nhận được: " + animator.getWaterFrameCount());
            require(animator.getLavaFrameCount() == 20,
                    "LiquidAnimator phải có đúng 20 frame dung nham, nhận được: " + animator.getLavaFrameCount());
            require(animator.getCurrentWaterFrame() == 0,
                    "Frame nước ban đầu phải là 0");
            require(animator.getCurrentLavaFrame() == 0,
                    "Frame dung nham ban đầu phải là 0");

            for (int i = 0; i < animator.getWaterFrameCount(); i++) {
                Pixmap waterFrame = animator.getWaterFramePixmap(i);
                require(waterFrame != null
                                && waterFrame.getWidth() == Texture.TILE_SIZE
                                && waterFrame.getHeight() == Texture.TILE_SIZE
                                && waterFrame.getFormat() == Pixmap.Format.RGBA8888,
                        "Frame nước " + i + " phải là Pixmap 16x16 RGBA8888");
            }
            for (int i = 0; i < animator.getLavaFrameCount(); i++) {
                Pixmap lavaFrame = animator.getLavaFramePixmap(i);
                require(lavaFrame != null
                                && lavaFrame.getWidth() == Texture.TILE_SIZE
                                && lavaFrame.getHeight() == Texture.TILE_SIZE
                                && lavaFrame.getFormat() == Pixmap.Format.RGBA8888,
                        "Frame dung nham " + i + " phải là Pixmap 16x16 RGBA8888");
                int lavaRgba = Texture.readNormalizedPixel(lavaFrame, 0, 0);
                require((lavaRgba & 0xFF) == 255,
                        "Frame dung nham phải luôn vẽ đục với alpha == 255");
            }

            for (int i = 0; i < 32; i++) {
                animator.update(0.10f, null);
            }
            require(animator.getCurrentWaterFrame() == 0,
                    "Sau 32 bước 0.10s, frame nước phải quay vòng về 0, nhận được: "
                            + animator.getCurrentWaterFrame());
            require(animator.getCurrentLavaFrame() == 12,
                    "Sau 32 bước 0.10s, frame dung nham phải là 32 % 20 == 12, nhận được: "
                            + animator.getCurrentLavaFrame());

            animator.dispose();
            animator.update(0.10f, null);
            require(animator.getCurrentWaterFrame() == 0 && animator.getCurrentLavaFrame() == 12,
                    "Sau dispose(), update() phải bỏ qua an toàn không đổi trạng thái");
        } finally {
            animator.dispose();
        }
    }

    /**
     * Kiểm tra tính toán giai đoạn vết nứt {@link BlockOutlineRenderer#stageForProgress(float)},
     * khả năng đọc 10 ảnh {@code destroy_stage_0..9.png} đóng gói {@code 160x16},
     * và logic dựng mây 3D dày 4 block có loại bỏ mặt trong của {@link SkyRenderer}.
     */
    private static void checkBreakStageCalculation() {
        GdxNativesLoader.load();

        // 1. Kiểm tra ánh xạ tiến trình đào [0..1] sang giai đoạn vết nứt [-1, 0..9].
        require(BlockOutlineRenderer.stageForProgress(-0.1f) == -1, "Tiến trình âm phải trả về -1");
        require(BlockOutlineRenderer.stageForProgress(0.0f) == -1, "Tiến trình 0.0f phải trả về -1");
        require(BlockOutlineRenderer.stageForProgress(0.001f) == -1, "Tiến trình <= 0.001f phải trả về -1");
        require(BlockOutlineRenderer.stageForProgress(0.05f) == 0, "Tiến trình 0.05f phải ở giai đoạn 0");
        require(BlockOutlineRenderer.stageForProgress(0.50f) == 5, "Tiến trình 0.50f phải ở giai đoạn 5");
        require(BlockOutlineRenderer.stageForProgress(0.99f) == 9, "Tiến trình 0.99f phải ở giai đoạn 9");
        require(BlockOutlineRenderer.stageForProgress(1.00f) == 9, "Tiến trình 1.00f phải kẹp ở giai đoạn 9");

        // 2. Kiểm tra đọc được cả 10 file textures/destroy_stage_0..9.png và đóng gói 160x16.
        for (int stage = 0; stage < 10; stage++) {
            String path = "textures/destroy_stage_" + stage + ".png";
            Pixmap stagePixmap = Texture.loadPixmapFlexible(path);
            require(stagePixmap != null, "Phải đọc được tài nguyên " + path);
            try {
                require(stagePixmap.getWidth() == Texture.TILE_SIZE && stagePixmap.getHeight() == Texture.TILE_SIZE,
                        path + " phải có kích thước 16x16");
            } finally {
                stagePixmap.dispose();
            }
        }
        Pixmap destroyAtlas = BlockOutlineRenderer.buildDestroyAtlasPixmap();
        require(destroyAtlas != null, "Phải dựng được atlas vết nứt 160x16");
        try {
            require(destroyAtlas.getWidth() == 160 && destroyAtlas.getHeight() == 16,
                    "Atlas vết nứt phải có kích thước 160x16, nhận được: "
                            + destroyAtlas.getWidth() + "x" + destroyAtlas.getHeight());
        } finally {
            destroyAtlas.dispose();
        }
        BlockOutlineRenderer outlineRenderer = new BlockOutlineRenderer();
        outlineRenderer.dispose();

        // 3. Kiểm tra đọc clouds.png và logic mây 3D dày 4 block (Y=140..144) có cull mặt trong.
        require(SkyRenderer.CLOUD_THICKNESS == 4.0f, "Độ dày mây 3D phải đúng 4.0f block");
        require(SkyRenderer.CLOUD_HEIGHT == 140.0f, "Cao độ đáy mây phải là 140.0f");
        Pixmap cloudsPixmap = Texture.loadPixmapFlexible(
                "textures/environment/clouds.png", "environment/clouds.png", "clouds.png");
        require(cloudsPixmap != null, "Phải đọc được tài nguyên textures/environment/clouds.png");
        try {
            boolean[][] mask = SkyRenderer.extractCloudMask(cloudsPixmap);
            int width = mask.length;
            int height = mask[0].length;
            int solidCount = 0;
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < height; z++) {
                    if (mask[x][z]) {
                        solidCount++;
                    }
                }
            }
            int exposedQuads = SkyRenderer.countExposedCloudQuads(mask);
            require(solidCount > 0 && exposedQuads > 0, "Lưới mây phải có ô đặc và mặt lộ thiên");
            require(exposedQuads < solidCount * 6,
                    "Thuật toán cull mặt trong phải giảm số mặt xuống dưới 6 mặt/ô");
        } finally {
            cloudsPixmap.dispose();
        }

        // Kiểm tra quy tắc cull mặt trong trên mẫu nhỏ 4x4: 1 ô đơn -> 6 mặt, 2 ô kề nhau -> 10 mặt.
        boolean[][] singleCell = new boolean[4][4];
        singleCell[1][1] = true;
        require(SkyRenderer.countExposedCloudQuads(singleCell) == 6,
                "1 ô mây cô lập phải có đúng 6 mặt");
        boolean[][] twoAdjacent = new boolean[4][4];
        twoAdjacent[1][1] = true;
        twoAdjacent[2][1] = true;
        require(SkyRenderer.countExposedCloudQuads(twoAdjacent) == 10,
                "2 ô mây liền kề phải cull 2 mặt chung, còn đúng 10 mặt");

        SkyRenderer skyRenderer = new SkyRenderer();
        skyRenderer.dispose();
    }

    /**
     * Kiểm tra giới hạn pool tĩnh {@link ParticleManager#MAX_PARTICLES} (512 hạt):
     * sinh hơn 600 hạt liên tiếp phải ghi đè vòng hạt cũ nhất mà không tràn mảng,
     * sau {@code update(10.0f)} toàn bộ hạt hết tuổi thọ về {@code activeCount() == 0},
     * và {@code dispose()} dọn tài nguyên an toàn cả khi {@code Gdx.gl == null}.
     */
    private static void checkParticlePoolLimits() {
        GdxNativesLoader.load();
        ParticleManager manager = new ParticleManager();
        try {
            require(ParticleManager.MAX_PARTICLES == 512,
                    "MAX_PARTICLES phải bằng 512, nhận được: " + ParticleManager.MAX_PARTICLES);
            require(manager.activeCount() == 0,
                    "Khi mới khởi tạo, activeCount() phải bằng 0");

            manager.spawnBlockBreak(10, 20, 30, Block.STONE, 200);
            require(manager.activeCount() == 200,
                    "Sau spawnBlockBreak(200), activeCount() phải bằng 200, nhận được: " + manager.activeCount());

            for (int i = 0; i < 150; i++) {
                manager.spawnFlame(10.5f, 20.5f, 30.5f);
            }
            require(manager.activeCount() == 350,
                    "Sau thêm 150 hạt lửa, activeCount() phải bằng 350, nhận được: " + manager.activeCount());

            manager.spawnSplash(10.5f, 20.5f, 30.5f, 150);
            require(manager.activeCount() == 500,
                    "Sau thêm 150 hạt bắn nước, activeCount() phải bằng 500, nhận được: " + manager.activeCount());

            // Sinh vượt ngưỡng 600+ hạt bằng spawnPotionSplash để kiểm tra ghi đè vòng không tràn mảng.
            for (int i = 0; i < 150; i++) {
                manager.spawnPotionSplash(10.5f, 20.5f, 30.5f, 0x66CCFF);
            }
            require(manager.activeCount() <= ParticleManager.MAX_PARTICLES,
                    "activeCount() không được vượt quá MAX_PARTICLES (512), nhận được: " + manager.activeCount());
            require(manager.activeCount() == ParticleManager.MAX_PARTICLES,
                    "Khi sinh vượt 512 hạt chưa hết hạn, pool ghi đè vòng phải giữ đủ 512 hạt, nhận được: "
                            + manager.activeCount());

            // Cập nhật một bước nhỏ để kiểm tra vật lý không gây NaN hoặc ngoại lệ, rồi cho hết tuổi thọ toàn bộ.
            manager.update(0.05f);
            manager.update(10.0f);
            require(manager.activeCount() == 0,
                    "Sau update(10.0f), toàn bộ hạt phải hết tuổi thọ và activeCount() == 0, nhận được: "
                            + manager.activeCount());

            manager.render(null, null, null);
        } finally {
            manager.dispose();
        }
        // Sau khi dispose(), cập nhật và sinh thêm phải bỏ qua an toàn.
        manager.spawnFlame(0f, 0f, 0f);
        manager.update(0.1f);
        require(manager.activeCount() == 0, "Sau dispose(), ParticleManager không được giữ hạt hoạt động");
    }

    /**
     * Kiểm tra tính toán UV hoạt ảnh lửa {@link MobRenderer#fireFrameUV(double, int, float[])}
     * mỗi 0.05s (20 FPS), xác nhận tài nguyên {@code fire_0.png} / {@code fire_1.png} có >= 16 khung,
     * và xác nhận {@link Sheep} bị đốt bằng {@code Flint and Steel} / {@link Zombie} dưới nắng
     * đều bật trạng thái cháy ({@code getFireTimer() > 0f}).
     */
    private static void checkFireAnimationFrameSelection() {
        GdxNativesLoader.load();

        // 1. Kiểm tra hai tệp tài nguyên fire_0.png và fire_1.png có kích thước dải dọc >= 16 khung.
        String[] firePaths = {"textures/fire_0.png", "textures/fire_1.png"};
        String[] fireFallbacks = {"fire_0.png", "fire_1.png"};
        int detectedFrames = 0;
        for (int i = 0; i < firePaths.length; i++) {
            Pixmap firePixmap = Texture.loadPixmapFlexible(firePaths[i], fireFallbacks[i]);
            require(firePixmap != null, "Phải đọc được tài nguyên " + firePaths[i]);
            try {
                int width = firePixmap.getWidth();
                int height = firePixmap.getHeight();
                require(width > 0 && height > 0 && height % width == 0,
                        firePaths[i] + " phải là dải khung vuông dọc hợp lệ, nhận được: " + width + "x" + height);
                int frameCount = height / width;
                require(frameCount >= 16,
                        firePaths[i] + " phải có >= 16 frame, nhận được: " + frameCount);
                if (i == 0) {
                    detectedFrames = frameCount;
                }
            } finally {
                firePixmap.dispose();
            }
        }

        // 2. Kiểm tra hàm MobRenderer.fireFrameUV cuộn đều 0..frameCount-1 mỗi 0.05s trong [0, 1].
        float[] uv = new float[4];
        float prevV0 = -1.0f;
        for (int frame = 0; frame < detectedFrames; frame++) {
            double timeSeconds = frame * 0.05;
            MobRenderer.fireFrameUV(timeSeconds, detectedFrames, uv);
            float u0 = uv[0];
            float v0 = uv[1];
            float u1 = uv[2];
            float v1 = uv[3];
            require(u0 >= 0.0f && u0 <= 1.0f && u1 >= 0.0f && u1 <= 1.0f && u0 < u1,
                    "UV theo trục U phải nằm gọn trong [0, 1] tại frame " + frame);
            require(v0 >= 0.0f && v0 <= 1.0f && v1 >= 0.0f && v1 <= 1.0f && v0 < v1,
                    "UV theo trục V phải nằm gọn trong [0, 1] tại frame " + frame);
            float expectedV0 = (float) frame / detectedFrames;
            float expectedV1 = (float) (frame + 1) / detectedFrames;
            require(Math.abs(v0 - expectedV0) < 1e-5f && Math.abs(v1 - expectedV1) < 1e-5f,
                    "Sai UV tại frame " + frame + ": v0=" + v0 + ", v1=" + v1);
            if (frame > 0) {
                require(Math.abs(v0 - prevV0) > 1e-5f,
                        "Frame kế tiếp sau bước 0.05s phải khác frame trước tại frame " + frame);
            }
            prevV0 = v0;
        }
        // Kiểm tra xoay vòng về frame 0 sau một chu kỳ đầy đủ.
        MobRenderer.fireFrameUV(detectedFrames * 0.05, detectedFrames, uv);
        require(Math.abs(uv[1]) < 1e-5f,
                "Sau đủ " + detectedFrames + " bước 0.05s, UV lửa phải quay vòng về frame 0");

        // 3. Kiểm tra kích hoạt cháy trên Sheep (Flint and Steel / ignite) và Zombie (dưới nắng sunY > 0.05f).
        World world = World.empty();
        try {
            Sheep sheep = new Sheep(10.5f, 10f, 10.5f);
            require(sheep.getFireTimer() == 0f, "Cừu mới sinh chưa thể đang cháy");
            Player player = new Player(10.5f, 10f, 12.5f);
            player.getHotbar().setSlot(0, Item.FLINT_AND_STEEL, 1);
            player.getHotbar().selectSlot(0);
            boolean ignited = sheep.onPlayerInteract(world, player);
            require(ignited && sheep.getFireTimer() > 0f,
                    "Đốt Sheep bằng Flint and Steel phải bật getFireTimer() > 0f");

            Sheep ignitedSheep = new Sheep(14.5f, 10f, 10.5f);
            ignitedSheep.ignite(4f);
            require(ignitedSheep.getFireTimer() > 0f,
                    "Sheep.ignite(4f) phải bật getFireTimer() > 0f");

            require(world.worldTime().sunY() > 0.05f,
                    "World.empty() mặc định phải có sunY > 0.05f để kiểm tra cháy nắng");
            Zombie zombie = new Zombie(20.5f, 10f, 10.5f, false);
            zombie.update(world, 0.05f);
            require(zombie.getFireTimer() > 0f,
                    "Zombie đứng ngoài trời nắng (sunY > 0.05f) phải tự bốc cháy với getFireTimer() > 0f");
        } finally {
            world.dispose();
        }
    }

    /** Đặt lại cờ dirty bằng reflection trong kiểm thử CPU không có ngữ cảnh OpenGL. */
    private static void clearDirtyWithoutGl(Chunk chunk) {
        try {
            var dirtyField = Chunk.class.getDeclaredField("dirty");
            dirtyField.setAccessible(true);
            dirtyField.setBoolean(chunk, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Không đặt lại được cờ dirty để kiểm tra biên chunk", e);
        }
    }

    /** Tìm hệ số sáng (shade) của đỉnh thuộc mặt nằm tại tọa độ cho trước. */
    private static float findFaceShade(com.badlogic.gdx.utils.FloatArray vertices, float x, float y, float z) {
        for (int i = 0; i < vertices.size; i += Chunk.FLOATS_PER_VERTEX) {
            if (vertices.items[i] == x && vertices.items[i + 1] == y && vertices.items[i + 2] == z) {
                return vertices.items[i + Chunk.SHADE_OFFSET];
            }
        }
        throw new AssertionError("Không tìm thấy đỉnh mặt tại (" + x + ", " + y + ", " + z + ")");
    }

    /** Tìm màu đỉnh đóng gói (aColor) của đỉnh thuộc mặt nằm tại tọa độ cho trước. */
    private static float findFaceColor(com.badlogic.gdx.utils.FloatArray vertices, float x, float y, float z) {
        for (int i = 0; i < vertices.size; i += Chunk.FLOATS_PER_VERTEX) {
            if (vertices.items[i] == x && vertices.items[i + 1] == y && vertices.items[i + 2] == z) {
                return vertices.items[i + Chunk.COLOR_OFFSET];
            }
        }
        throw new AssertionError("Không tìm thấy đỉnh mặt để đọc màu tại (" + x + ", " + y + ", " + z + ")");
    }

    /** Báo lỗi khẳng định rõ ràng khi kiểm tra thất bại. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
