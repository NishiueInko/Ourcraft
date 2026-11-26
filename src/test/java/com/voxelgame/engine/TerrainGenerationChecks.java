package com.voxelgame.engine;

import com.badlogic.gdx.math.Vector3;
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
        checkSnowyPeak(generator);
        checkClimate(generator, repeat);
        long sample = sampleWorld(generator, true);
        long regenerated = sampleWorld(repeat, false);
        if (sample != regenerated) {
            throw new AssertionError("Hai thế giới cùng seed có mẫu block hoặc metadata khác nhau");
        }
    }

    /** Tìm đỉnh địa chất trên lưới lấy mẫu ba block và kiểm tra biome ở đỉnh. */
    private static void checkSnowyPeak(TerrainGenerator generator) {
        int peak = 0;
        for (int x = 0; x < World.BIOME_WIDTH; x += 3) {
            for (int z = 0; z < World.WORLD_SIZE_Z; z += 3) {
                if (generator.getBiome(x, z) == BiomeType.SNOWY_PLAINS) {
                    peak = Math.max(peak, generator.getSurfaceY(x, z));
                }
            }
        }
        if (peak < 140) {
            throw new AssertionError("Đỉnh núi tuyết phải đạt Y=140, đo được Y=" + peak);
        }
    }

    /** Kiểm tra ranh giới uốn lượn, thứ tự biome, độ liên tục và tính tất định. */
    private static void checkClimate(TerrainGenerator generator, TerrainGenerator repeat) {
        TerrainGenerator otherSeed = new TerrainGenerator(WORLD_SEED + 1);
        BiomeType[] expected = {
                BiomeType.SNOWY_PLAINS, BiomeType.FOREST, BiomeType.PLAINS,
                BiomeType.FLOWER_FOREST, BiomeType.DESERT
        };
        int[] lowestBoundary = {
                World.WORLD_SIZE_X, World.WORLD_SIZE_X, World.WORLD_SIZE_X, World.WORLD_SIZE_X
        };
        int[] highestBoundary = new int[4];
        boolean changedSeed = false;
        for (int z = 0; z < World.WORLD_SIZE_Z; z += Chunk.SIZE) {
            int band = 0;
            if (generator.getBiome(0, z) != expected[0]) {
                throw new AssertionError("Thiếu biome tuyết ở mép tây");
            }
            for (int x = 1; x < World.WORLD_SIZE_X; x++) {
                BiomeType biome = generator.getBiome(x, z);
                if (biome == expected[band]) {
                    continue;
                }
                if (band >= 4 || biome != expected[band + 1]
                        || Math.abs(x - (band + 1) * World.BIOME_WIDTH) > 105) {
                    throw new AssertionError("Sai thứ tự hoặc phạm vi biome tại X=" + x + ", Z=" + z);
                }
                lowestBoundary[band] = Math.min(lowestBoundary[band], x);
                highestBoundary[band] = Math.max(highestBoundary[band], x);
                int height = generator.getSurfaceY(x, z);
                if (biome != repeat.getBiome(x, z) || height != repeat.getSurfaceY(x, z)) {
                    throw new AssertionError("Cùng seed cho ranh giới hoặc độ cao khác nhau");
                }
                changedSeed |= biome != otherSeed.getBiome(x, z) || height != otherSeed.getSurfaceY(x, z);
                checkTransition(generator, x - 1, z, x, z, expected[band], generator.getSurfaceY(x - 1, z));
                band++;
            }
            if (band != 4) {
                throw new AssertionError("Thiếu biome tại Z=" + z);
            }
        }
        for (int i = 0; i < 4; i++) {
            if (!changedSeed || highestBoundary[i] - lowestBoundary[i] < 8) {
                throw new AssertionError("Ranh giới thứ " + i + " phải uốn lượn và thay đổi theo seed");
            }
        }
    }

    /** Lấy mẫu ID và metadata của ba biome trong một thế giới sinh đầy đủ, không dùng OpenGL. */
    private static long sampleWorld(TerrainGenerator generator, boolean validate) {
        World world = World.empty();
        try {
            DesertPyramid pyramid = generator.generate(world);
            if (validate) {
                checkSpawn(world, generator);
                checkSurfaceAndRiver(world, generator);
                checkRespawn(world);
                DesertPyramidChecks.run();
                DesertPyramidChecks.checkGenerated(world, pyramid);
                checkDesertSlope(world, generator);
                checkLavaBanks(world, generator);
            }
            long hash = 0xCBF29CE484222325L;
            int[] count = new int[256];
            boolean frozenWater = false;
            boolean canopySnow = false;
            int bandWidth = World.BIOME_WIDTH;
            for (int centerX = bandWidth / 2; centerX < World.WORLD_SIZE_X; centerX += bandWidth) {
                for (int x = centerX - 144; x <= centerX + 144; x++) {
                    for (int z = World.CENTER_Z - 144; z <= World.CENTER_Z + 144; z++) {
                        int surface = generator.getSurfaceY(x, z);
                        if (validate) {
                            checkStrata(world, x, surface, z, generator.getBiome(x, z) == BiomeType.DESERT);
                        }
                        int scanTop = Math.min(World.WORLD_SIZE_Y - 1, Math.max(42, surface + 12));
                        for (int y = 0; y <= scanTop; y++) {
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

    /** Kiểm tra vách sandstone và bảo vệ vật liệu sa mạc tới ranh giới uốn lượn. */
    private static void checkDesertSlope(World world, TerrainGenerator generator) {
        boolean foundSandstoneSlope = false;
        int desertStart = 4 * World.BIOME_WIDTH;
        for (int x = desertStart + 64; x < World.WORLD_SIZE_X - 64 && !foundSandstoneSlope; x += 2) {
            for (int z = 64; z < World.WORLD_SIZE_Z - 64 && !foundSandstoneSlope; z += 2) {
                if (generator.getBiome(x, z) != BiomeType.DESERT) {
                    continue;
                }
                int y = generator.getSurfaceY(x, z);
                if (world.getBlock(x, y, z) == Block.SANDSTONE.getId()
                        && world.getBlock(x, y - 3, z) == Block.SANDSTONE.getId()) {
                    foundSandstoneSlope = true;
                }
            }
        }
        if (!foundSandstoneSlope) {
            throw new AssertionError("Dốc vừa ở sa mạc phải có ít nhất bốn khối sandstone");
        }
        for (int columnX = desertStart - 110; columnX < World.WORLD_SIZE_X; columnX += 3) {
            for (int columnZ = 0; columnZ < World.WORLD_SIZE_Z; columnZ += 3) {
                if (generator.getBiome(columnX, columnZ) != BiomeType.DESERT) {
                    continue;
                }
                int top = generator.getSurfaceY(columnX, columnZ) + 12;
                for (int blockY = 1; blockY <= top; blockY++) {
                    Block block = Block.fromId(world.getBlock(columnX, blockY, columnZ));
                    if (block == Block.SNOW_BLOCK || block == Block.SNOW_LAYER || block == Block.ICE
                            || block == Block.GRAVEL && world.getBlock(columnX, blockY + 1, columnZ)
                                    != Block.WATER.getId()) {
                        throw new AssertionError("Vật liệu sai trong cột sa mạc tại "
                                + columnX + ", " + blockY + ", " + columnZ);
                    }
                }
            }
        }
    }

    /** Kiểm tra mực sông và lớp phủ thực tế ở vùng chuyển tiếp đã uốn. */
    private static void checkSurfaceAndRiver(World world, TerrainGenerator generator) {
        int biomeCount = BiomeType.values().length;
        int[] reaches = new int[biomeCount];
        int[] lowland = new int[biomeCount];
        int snowyPeak = 0;
        for (int x = 0; x < World.WORLD_SIZE_X; x += 4) {
            for (int z = 0; z < World.WORLD_SIZE_Z; z += 4) {
                int y = generator.getSurfaceY(x, z);
                BiomeType biome = world.getBiome(x, z);
                if (biome != generator.getBiome(x, z)) {
                    throw new AssertionError("World và bộ sinh lệch biome tại " + x + ", " + z);
                }
                if (y >= 26 && y <= 34) {
                    lowland[biome.ordinal()]++;
                }
                if (y >= 140 && biome == BiomeType.SNOWY_PLAINS
                        && world.getBlock(x, y + 1, z) == Block.SNOW_BLOCK.getId()) {
                    snowyPeak = Math.max(snowyPeak, y);
                }
                if (z < 380 || z > 750 || y > TerrainGenerator.WATER_LEVEL - 2) {
                    continue;
                }
                byte surface = world.getBlock(x, TerrainGenerator.WATER_LEVEL, z);
                byte below = world.getBlock(x, TerrainGenerator.WATER_LEVEL - 1, z);
                if (surface == (biome == BiomeType.SNOWY_PLAINS ? Block.ICE : Block.WATER).getId()
                        && below == Block.WATER.getId()) {
                    reaches[biome.ordinal()]++;
                }
            }
        }
        for (int i = 0; i < biomeCount; i++) {
            if (reaches[i] < 50) {
                throw new AssertionError("Thiếu đoạn sông đúng cao độ trong biome " + BiomeType.values()[i]);
            }
        }
        if (snowyPeak < 140 || lowland[BiomeType.PLAINS.ordinal()] < 1000
                || lowland[BiomeType.DESERT.ordinal()] < 1000) {
            throw new AssertionError("Thiếu đỉnh hoặc địa hình thấp");
        }
    }

    /** Dấu chân hồi sinh có nền rắn và đủ chỗ, kể cả sau khi xây lên cao. */
    private static void checkRespawn(World world) {
        Vector3 position = new Vector3();
        world.setRespawnPosition(position);
        if (position.x != World.RESPAWN_X || position.z != World.RESPAWN_Z || position.y != 31f) {
            throw new AssertionError("Sai vị trí hồi sinh tự nhiên: " + position);
        }
        world.setBlock(World.RESPAWN_X, 130, World.RESPAWN_Z, Block.OAK_LEAVES.getId());
        world.setRespawnPosition(position);
        if (position.y != 131f) {
            throw new AssertionError("Hồi sinh không tìm được mặt đỡ cao nhất: " + position);
        }
        world.setBlock(World.RESPAWN_X, 130, World.RESPAWN_Z, Block.AIR.getId());
    }

    /** Mỗi loại biome phải có hồ với bờ đúng chất liệu ở các ô chung cạnh mặt dung nham. */
    private static void checkLavaBanks(World world, TerrainGenerator generator) {
        int[] banks = new int[BiomeType.values().length];
        int[] dx = {-1, 1, 0, 0};
        int[] dz = {0, 0, -1, 1};
        for (int x = 64; x < World.WORLD_SIZE_X - 64; x++) {
            int inBand = x % World.BIOME_WIDTH;
            if (inBand < 64 || inBand >= World.BIOME_WIDTH - 64) {
                continue;
            }
            for (int z = World.CENTER_Z - 320; z <= World.CENTER_Z + 320; z++) {
                int originalY = generator.getSurfaceY(x, z);
                for (int y = originalY - 3; y <= originalY; y++) {
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
    private static void checkStrata(World world, int x, int y, int z, boolean desert) {
        if (world.getBlock(x, 0, z) != Block.BEDROCK.getId()) {
            throw new AssertionError("Thiếu đá gốc tại " + x + ", " + z);
        }
        if (world.getBlock(x, y, z) != (desert ? Block.SAND : Block.GRASS).getId()) {
            return;
        }
        int cursor = desert ? y : y - 1;
        Block top = desert ? Block.SAND : Block.DIRT;
        int topDepth = countLayers(world, x, cursor, z, top);
        int min = 3;
        if (topDepth < min || topDepth > (desert ? 5 : 4)) {
            if (touchesBasin(world, x, y, z)) {
                return;
            }
            throw new AssertionError("Sai độ dày " + top.getDisplayName() + " tại " + x + ", " + z);
        }
        cursor -= topDepth;
        if (desert) {
            int sandstoneDepth = countLayers(world, x, cursor, z, Block.SANDSTONE);
            if (sandstoneDepth < 3 || sandstoneDepth > 4) {
                if (touchesBasin(world, x, y, z)) {
                    return;
                }
                throw new AssertionError("Sai độ dày đá cát tại " + x + ", " + z);
            }
            cursor -= sandstoneDepth;
        }
        if (cursor < 1 || countLayers(world, x, cursor, z, Block.STONE) != cursor) {
            throw new AssertionError("Lớp Stone phải liên tục đến đá gốc tại " + x + ", " + z);
        }
    }

    /** Loại cột bờ đã được lượt sinh hồ hoặc sông cắt lại khỏi kiểm tra tầng đất nguyên gốc. */
    private static boolean touchesBasin(World world, int x, int y, int z) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -4; dy <= 1; dy++) {
                    byte id = world.getBlock(x + dx, y + dy, z + dz);
                    if (id == Block.WATER.getId() || id == Block.ICE.getId() || id == Block.LAVA.getId()) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        if ((block == Block.SAND || block == Block.GRAVEL)
                && !Block.fromId(world.getBlock(x, y - 1, z)).isSolid()) {
            throw new AssertionError("Cát hoặc sỏi thiếu nền tại " + x + ", " + y + ", " + z);
        }
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
