package com.voxelgame.engine;

import com.voxelgame.entity.Block;

/** Kiểm tra CPU cho vùng khí hậu và tính liên tục của địa hình. */
final class TerrainGenerationChecks {
    /** Seed mặc định dùng bởi thế giới chơi. */
    private static final long WORLD_SEED = 20261114L;

    /** Nhóm hồi quy được gọi trực tiếp từ chương trình kiểm thử CPU. */
    private TerrainGenerationChecks() {
    }

    /** Kiểm tra vùng sinh, các ranh giới và mức ảnh hưởng của seed. */
    static void run() {
        TerrainGenerator generator = new TerrainGenerator(WORLD_SEED);
        TerrainGenerator repeat = new TerrainGenerator(WORLD_SEED);
        checkClimate(generator, repeat);
        long sample = sampleWorld(generator, true);
        long regenerated = sampleWorld(repeat, false);
        if (sample != regenerated) {
            throw new AssertionError("Hai thế giới cùng seed có mẫu block hoặc metadata khác nhau");
        }
    }

    /** Đo bề rộng theo X qua các hàng chunk và kiểm tra tính tất định cùng độ mượt ở ranh giới. */
    private static void checkClimate(TerrainGenerator generator, TerrainGenerator repeat) {
        TerrainGenerator otherSeed = new TerrainGenerator(WORLD_SEED + 1);
        boolean changedSeed = false;
        BiomeType[] expected = {BiomeType.SNOWY_PLAINS, BiomeType.PLAINS, BiomeType.DESERT};
        for (int row = 0; row <= World.WORLD_CHUNKS_Z; row++) {
            int z = Math.min(row * Chunk.SIZE, World.WORLD_SIZE_Z - 1);
            int band = 0;
            int start = 0;
            BiomeType biome = generator.getBiome(0, z);
            for (int x = 1; x <= World.WORLD_SIZE_X; x++) {
                BiomeType next = x == World.WORLD_SIZE_X ? null : generator.getBiome(x, z);
                if (next == biome) {
                    continue;
                }
                int width = x - start;
                if (band >= expected.length || biome != expected[band] || width != 1000) {
                    throw new AssertionError("Sai thứ tự hoặc chiều rộng biome tại Z=" + z + ": " + biome + ", " + width);
                }
                band++;
                if (next != null) {
                    int height = generator.getSurfaceY(x, z);
                    if (next != repeat.getBiome(x, z) || biome != repeat.getBiome(x - 1, z)
                            || height != repeat.getSurfaceY(x, z)) {
                        throw new AssertionError("Cùng seed cho ranh giới hoặc độ cao khác nhau");
                    }
                    changedSeed |= height != otherSeed.getSurfaceY(x, z);
                    checkTransition(generator, x - 1, z, x, z, biome, generator.getSurfaceY(x - 1, z));
                }
                start = x;
                biome = next;
            }
            if (band != expected.length) {
                throw new AssertionError("Thiếu biome tại Z=" + z);
            }
        }
        if (generator.getBiome(World.CENTER_X, World.CENTER_Z) != BiomeType.PLAINS) {
            throw new AssertionError("Vùng spawn phải là Plains");
        }
        if (!changedSeed) {
            throw new AssertionError("Đổi seed phải đổi độ cao địa hình");
        }
    }

    /** Lấy mẫu ID và metadata của ba biome trong một thế giới sinh đầy đủ, không dùng OpenGL. */
    private static long sampleWorld(TerrainGenerator generator, boolean validate) {
        World world = World.empty();
        try {
            generator.generate(world);
            if (validate) {
                checkSpawn(world, generator);
                checkSurfaceMix(world, generator);
                checkLavaBanks(world, generator);
            }
            long hash = 0xCBF29CE484222325L;
            int[] count = new int[256];
            boolean frozenWater = false;
            boolean canopySnow = false;
            int bandWidth = World.WORLD_SIZE_X / 3;
            for (int centerX = bandWidth / 2; centerX < World.WORLD_SIZE_X; centerX += bandWidth) {
                for (int x = centerX - 144; x <= centerX + 144; x++) {
                    for (int z = World.CENTER_Z - 144; z <= World.CENTER_Z + 144; z++) {
                        if (validate) {
                            checkStrata(world, generator, x, z);
                        }
                        for (int y = 0; y <= World.MAX_TERRAIN_Y + 11; y++) {
                            byte id = world.getBlock(x, y, z);
                            int state = world.getBlockState(x, y, z);
                            hash = (hash ^ ((Byte.toUnsignedInt(id) << 8) | state)) * 0x100000001B3L;
                            if (!validate) {
                                continue;
                            }
                            count[Byte.toUnsignedInt(id)]++;
                            Block block = Block.fromId(id);
                            if (block == Block.ICE) {
                                frozenWater |= world.getBlock(x, y - 1, z) == Block.WATER.getId();
                            } else if (block == Block.SNOW_LAYER) {
                                canopySnow |= world.getBlock(x, y - 1, z) == Block.SPRUCE_LEAVES.getId();
                            }
                            verifySupport(world, x, y, z, block, state);
                        }
                    }
                }
            }
            if (validate) {
                if (!frozenWater || !canopySnow) {
                    throw new AssertionError("Thiếu hồ đóng băng có nước bên dưới hoặc tuyết đọng trên lá vân sam");
                }
                Block[] expected = { Block.STONE, Block.DIRT, Block.SAND, Block.SANDSTONE, Block.GRAVEL,
                        Block.CACTUS, Block.DEAD_BUSH, Block.TALL_GRASS, Block.SHORT_GRASS, Block.SUGAR_CANE,
                        Block.OAK_LOG, Block.SPRUCE_LOG, Block.SNOW_LAYER, Block.SNOW_BLOCK,
                        Block.DANDELION, Block.POPPY, Block.CORNFLOWER, Block.OXEYE_DAISY };
                for (Block block : expected) {
                    if (count[Byte.toUnsignedInt(block.getId())] == 0) {
                        throw new AssertionError("Mẫu địa hình ba biome thiếu " + block.getDisplayName());
                    }
                }
            }
            return hash;
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra vật liệu thực sự xen kẽ ở hai phía của cả hai ranh giới. */
    private static void checkSurfaceMix(World world, TerrainGenerator generator) {
        int[] crossovers = new int[4];
        for (int z = 96; z < World.WORLD_SIZE_Z - 96; z += 2) {
            for (int offset = 1; offset <= 14; offset++) {
                int cold = 1000 - offset;
                int plainsWest = 1000 + offset - 1;
                int plainsEast = 2000 - offset;
                int hot = 2000 + offset - 1;
                if (topSoil(world, generator, cold, z) == Block.GRASS && !hasSnow(world, generator, cold, z)) {
                    crossovers[0]++;
                }
                if (hasSnow(world, generator, plainsWest, z)) {
                    crossovers[1]++;
                }
                if (topSoil(world, generator, plainsEast, z) == Block.SAND) {
                    crossovers[2]++;
                }
                if (topSoil(world, generator, hot, z) == Block.GRASS) {
                    crossovers[3]++;
                }
            }
        }
        for (int count : crossovers) {
            if (count < 250) {
                throw new AssertionError("Bề mặt chưa xen đủ mảng ở hai phía ranh giới biome");
            }
        }
    }

    /** Đọc mặt nền ở cao độ địa tầng gốc để bỏ qua hoa, cỏ và lá cây trên mặt. */
    private static Block topSoil(World world, TerrainGenerator generator, int x, int z) {
        return Block.fromId(world.getBlock(x, generator.getSurfaceY(x, z), z));
    }

    /** Tìm lớp tuyết nằm trên mặt nền gốc. */
    private static boolean hasSnow(World world, TerrainGenerator generator, int x, int z) {
        Block cover = Block.fromId(world.getBlock(x, generator.getSurfaceY(x, z) + 1, z));
        return cover == Block.SNOW_LAYER || cover == Block.SNOW_BLOCK;
    }

    /** Mỗi loại biome phải có hồ với bờ đúng chất liệu ở các ô chung cạnh mặt dung nham. */
    private static void checkLavaBanks(World world, TerrainGenerator generator) {
        int[] banks = new int[BiomeType.values().length];
        int[] dx = {-1, 1, 0, 0};
        int[] dz = {0, 0, -1, 1};
        for (int x = 64; x < World.WORLD_SIZE_X - 64; x++) {
            if (x % 1000 < 64 || x % 1000 >= 936) {
                continue;
            }
            for (int z = World.CENTER_Z - 320; z <= World.CENTER_Z + 320; z++) {
                for (int y = 1; y <= World.MAX_TERRAIN_Y; y++) {
                    if (world.getBlock(x, y, z) != Block.LAVA.getId()
                            || world.getBlock(x, y + 1, z) == Block.LAVA.getId()) {
                        continue;
                    }
                    BiomeType biome = generator.getBiome(x, z);
                    Block expected = biome == BiomeType.DESERT ? Block.SAND : Block.GRASS;
                    Block lining = biome == BiomeType.DESERT ? Block.SANDSTONE : Block.DIRT;
                    for (int side = 0; side < dx.length; side++) {
                        int bankX = x + dx[side];
                        int bankZ = z + dz[side];
                        byte neighbor = world.getBlock(bankX, y, bankZ);
                        if (neighbor == Block.LAVA.getId()) {
                            continue;
                        }
                        byte upper = world.getBlock(bankX, y + 1, bankZ);
                        boolean soilUnderTree = biome != BiomeType.DESERT
                                && (upper == Block.DIRT.getId() || upper == Block.OAK_LOG.getId()
                                || upper == Block.SPRUCE_LOG.getId());
                        if (neighbor != expected.getId() && (neighbor != lining.getId()
                                || (upper != expected.getId() && !soilUnderTree))) {
                            throw new AssertionError("Bờ hồ dung nham sai vật liệu ở " + biome
                                    + " tại " + bankX + ", " + y + ", " + bankZ);
                        }
                        banks[biome.ordinal()]++;
                    }
                }
            }
        }
        for (BiomeType biome : BiomeType.values()) {
            if (banks[biome.ordinal()] == 0) {
                throw new AssertionError("Mẫu kiểm tra thiếu bờ hồ dung nham trong " + biome);
            }
        }
    }

    /** Kiểm tra mặt cắt địa tầng nguyên vẹn bên ngoài các cột đã đổi khi sinh hồ hoặc cây. */
    private static void checkStrata(World world, TerrainGenerator generator, int x, int z) {
        if (world.getBlock(x, 0, z) != Block.BEDROCK.getId()) {
            throw new AssertionError("Thiếu đá gốc tại " + x + ", " + z);
        }
        int y = generator.getSurfaceY(x, z);
        boolean desert = generator.getBiome(x, z) == BiomeType.DESERT;
        if (world.getBlock(x, y, z) != (desert ? Block.SAND : Block.GRASS).getId()) {
            return;
        }
        int cursor = desert ? y : y - 1;
        Block top = desert ? Block.SAND : Block.DIRT;
        int topDepth = countLayers(world, x, cursor, z, top);
        int min = desert ? 3 : 2;
        if (topDepth < min || topDepth > min + 1) {
            throw new AssertionError("Sai độ dày " + top.getDisplayName() + " tại " + x + ", " + z);
        }
        cursor -= topDepth;
        if (desert) {
            int sandstoneDepth = countLayers(world, x, cursor, z, Block.SANDSTONE);
            if (sandstoneDepth < 3 || sandstoneDepth > 4) {
                throw new AssertionError("Sai độ dày đá cát tại " + x + ", " + z);
            }
            cursor -= sandstoneDepth;
        }
        if (cursor < 1 || countLayers(world, x, cursor, z, Block.STONE) != cursor) {
            throw new AssertionError("Lớp Stone phải liên tục đến đá gốc tại " + x + ", " + z);
        }
    }

    /** Đếm các ô cùng loại liên tiếp đi xuống, chừa tầng đá gốc riêng. */
    private static int countLayers(World world, int x, int y, int z, Block block) {
        int cursor = y;
        while (cursor > 0 && world.getBlock(x, cursor, z) == block.getId()) {
            cursor--;
        }
        return y - cursor;
    }

    /** Dấu chân tại tâm nằm trên mặt cỏ có đủ hai ô trống cho người chơi. */
    private static void checkSpawn(World world, TerrainGenerator generator) {
        int y = generator.getSurfaceY(World.CENTER_X, World.CENTER_Z);
        for (int x = World.CENTER_X - 1; x <= World.CENTER_X; x++) {
            for (int z = World.CENTER_Z - 1; z <= World.CENTER_Z; z++) {
                if (world.getBlock(x, y, z) != Block.GRASS.getId()
                        || world.getBlock(x, y + 1, z) != Block.AIR.getId()
                        || world.getBlock(x, y + 2, z) != Block.AIR.getId()) {
                    throw new AssertionError("Spawn bị vướng hoặc thiếu nền tại " + x + ", " + y + ", " + z);
                }
            }
        }
    }

    /** Xác nhận các cấu trúc sinh hàng loạt vẫn hợp lệ ngay khi chạm vào trong gameplay. */
    private static void verifySupport(World world, int x, int y, int z, Block block, int state) {
        if ((block == Block.ICE || block == Block.SNOW_LAYER || block == Block.SNOW_BLOCK)
                && world.hasLavaNear(x, y, z)) {
            throw new AssertionError("Băng/tuyết sinh gần dung nham tại " + x + ", " + y + ", " + z);
        }
        if (block == Block.CACTUS || block == Block.SUGAR_CANE || block == Block.SNOW_LAYER) {
            if (!block.canPlaceAt(world, x, y, z)) {
                throw new AssertionError(block.getDisplayName()
                        + " sinh sai nền hoặc ô kề tại " + x + ", " + y + ", " + z);
            }
        } else if (block == Block.TALL_GRASS) {
            int partnerY = state == 0 ? y + 1 : y - 1;
            if (world.getBlock(x, partnerY, z) != block.getId()
                    || world.getBlockState(x, partnerY, z) != 1 - state
                    || (state == 0 && !block.canPlaceAt(world, x, y, z))) {
                throw new AssertionError("Cỏ cao thiếu nửa còn lại hoặc sai nền tại " + x + ", " + y + ", " + z);
            }
        }
    }

    /** Đo độ chênh của hai cột kề nằm hai phía ranh giới biome. */
    private static void checkTransition(TerrainGenerator generator, int x, int z, int nextX, int nextZ,
                                        BiomeType biome, int height) {
        if (biome != generator.getBiome(nextX, nextZ)) {
            int nextHeight = generator.getSurfaceY(nextX, nextZ);
            if (Math.abs(nextHeight - height) > 2) {
                throw new AssertionError("Bậc địa hình qua ranh giới tại " + x + ", " + z
                        + ": " + height + " và " + nextHeight);
            }
        }
    }
}
