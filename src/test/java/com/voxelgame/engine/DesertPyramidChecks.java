package com.voxelgame.engine;

import com.voxelgame.entity.Block;

import java.util.Arrays;

/** Kiểm thử CPU cho khối trang trí và công trình sa mạc. */
final class DesertPyramidChecks {
    /** Chỉ được gọi từ bộ kiểm thử hồi quy. */
    private DesertPyramidChecks() {
    }

    /** Cho phép chạy nhóm kiểm thử độc lập trong lúc sửa bộ sinh. */
    public static void main(String[] args) {
        run();
    }

    /** Kiểm tra rương đăng ký thành khối trang trí dùng quy tắc đào và đặt mặc định. */
    static void run() {
        checkChest();
        checkPyramid();
    }

    /** Kiểm tra công trình được tạo trong lượt sinh thực tế, sau khi đã ghép với địa tầng và hồ. */
    static void checkGenerated(World world, DesertPyramid pyramid) {
        int x = pyramid.centerX();
        int z = pyramid.centerZ();
        int base = pyramid.baseY();
        if (world.getBiome(x, z) != BiomeType.DESERT) {
            throw new AssertionError("Kim tự tháp sinh ngoài biome sa mạc");
        }
        requireBlock(world, x, base + 16, z, Block.SANDSTONE);
        requireBlock(world, x + 4, base - 7, z - 4, Block.CHEST);
        requirePassage(world, x, base, z + 8);
        for (int dx : new int[]{-8, 8}) {
            for (int dz : new int[]{-8, 8}) {
                requireBlock(world, x + dx, base - 8, z + dz, Block.SANDSTONE);
                Block below = Block.fromId(world.getBlock(x + dx, base - 9, z + dz));
                if (below != Block.STONE && below != Block.SANDSTONE) {
                    throw new AssertionError("Chân móng kim tự tháp cần chạm đá tại " + (x + dx) + ", " + (z + dz));
                }
            }
        }
    }

    /** Kiểm tra đăng ký, kết xuất và tương tác cơ bản của rương. */
    private static void checkChest() {
        Block chest = Block.fromId((byte) 26);
        if (chest != Block.CHEST || !chest.isBreakable() || chest.getSelectionBounds(0) != Block.FULL_BOUNDS
                || chest.getCollisionBounds(0) != Block.FULL_BOUNDS) {
            throw new AssertionError("Rương trang trí phải đăng ký tại ID 26 và là khối rắn đào được");
        }
        if (chest.getTileIndex(Block.Face.SOUTH) == chest.getTileIndex(Block.Face.EAST)
                || chest.getTileIndex(Block.Face.TOP) == chest.getTileIndex(Block.Face.EAST)) {
            throw new AssertionError("Mặt có khóa và nắp rương phải có ảnh riêng");
        }
    }

    /** Dựng trên nền cồn cát giả lập và đi theo đường từ sảnh đến tầng ngầm. */
    private static void checkPyramid() {
        short[] heights = new short[World.WORLD_SIZE_X * World.WORLD_SIZE_Z];
        byte[] biomes = new byte[heights.length];
        int zoneX = 2 * World.BIOME_WIDTH + 13;
        int zoneZ = World.CENTER_Z;
        for (int x = zoneX - 13; x <= zoneX + 35; x++) {
            int first = x * World.WORLD_SIZE_Z + zoneZ - 24;
            Arrays.fill(heights, first, first + 49, (short) 31);
            Arrays.fill(biomes, first, first + 49, (byte) BiomeType.DESERT.ordinal());
        }
        // Khép một dải sông trên phần gần Plains để kiểm tra việc chọn dấu chân khô.
        int riverX = zoneX - 1;
        for (int z = zoneZ - 24; z <= zoneZ + 24; z++) {
            biomes[riverX * World.WORLD_SIZE_Z + z] |= 8;
        }
        DesertPyramid pyramid = DesertPyramid.select(heights, biomes);
        if (pyramid == null || pyramid.contains(riverX, pyramid.centerZ(), 0)) {
            throw new AssertionError("Kim tự tháp phải chọn dấu chân khô gần đồng bằng");
        }

        World world = World.empty();
        try {
            int centerX = pyramid.centerX();
            int centerZ = pyramid.centerZ();
            int base = pyramid.baseY();
            for (int x = centerX - 11; x <= centerX + 11; x++) {
                for (int z = centerZ - 11; z <= centerZ + 11; z++) {
                    world.setBlockInternal(x, 0, z, Block.BEDROCK.getId(), 0);
                    for (int y = 1; y <= base; y++) {
                        Block layer = y <= 17 ? Block.STONE : y <= 22 ? Block.SANDSTONE : Block.SAND;
                        world.setBlockInternal(x, y, z, layer.getId(), 0);
                    }
                }
            }
            pyramid.build(world);
            for (int dx : new int[]{-8, 8}) {
                for (int dz : new int[]{-8, 8}) {
                    for (int y = 22; y <= base + 1; y++) {
                        requireBlock(world, centerX + dx, y, centerZ + dz, Block.SANDSTONE);
                    }
                }
            }
            requireBlock(world, centerX + 9, 22, centerZ, Block.SANDSTONE);
            world.setBlock(centerX + 10, base, centerZ, Block.AIR.getId());
            requireBlock(world, centerX + 9, base, centerZ, Block.SAND);
            for (int dz = 5; dz <= 9; dz++) {
                requirePassage(world, centerX, base, centerZ + dz);
            }
            for (int dz = 4; dz >= -3; dz--) {
                requirePassage(world, centerX - 3, base - (5 - dz), centerZ + dz);
            }
            requirePassage(world, centerX - 2, base - 8, centerZ - 3);
            requireBlock(world, centerX + 4, base - 7, centerZ - 4, Block.CHEST);
            world.breakBlockAndSpawnDrop(centerX + 4, base - 7, centerZ - 4);
            requireBlock(world, centerX + 4, base - 7, centerZ - 4, Block.AIR);
            if (!world.tryPlaceBlock(centerX + 4, base - 7, centerZ - 4, Block.CHEST, null)) {
                throw new AssertionError("Rương trang trí phải đặt được như khối thường");
            }
        } finally {
            world.dispose();
        }
    }

    /** Đường đi có sàn rắn và đủ hai ô trống cho người chơi. */
    private static void requirePassage(World world, int x, int floor, int z) {
        requireBlock(world, x, floor, z, Block.SANDSTONE);
        requireBlock(world, x, floor + 1, z, Block.AIR);
        requireBlock(world, x, floor + 2, z, Block.AIR);
    }

    /** Chẩn đoán tọa độ đầu tiên có vật liệu khác dự tính. */
    private static void requireBlock(World world, int x, int y, int z, Block expected) {
        if (world.getBlock(x, y, z) != expected.getId()) {
            throw new AssertionError("Sai khối kim tự tháp tại " + x + ", " + y + ", " + z
                    + " cần " + expected.getDisplayName());
        }
    }
}
