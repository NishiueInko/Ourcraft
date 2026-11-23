package com.voxelgame.engine;

import com.voxelgame.entity.Block;

import static com.voxelgame.engine.World.BIOME_WIDTH;
import static com.voxelgame.engine.World.CENTER_X;
import static com.voxelgame.engine.World.CENTER_Z;
import static com.voxelgame.engine.World.MAX_TERRAIN_Y;
import static com.voxelgame.engine.World.WORLD_SIZE_X;
import static com.voxelgame.engine.World.WORLD_SIZE_Z;

/** Sinh địa tầng, mặt nước và hệ sinh thái theo seed, trước khi bắt đầu cập nhật gameplay. */
final class TerrainGenerator {
    /** Các trường noise độc lập để độ cao, lớp phủ và thực vật không lặp cùng mẫu. */
    private static final long HEIGHT = 0x380678E2B9DA63C8L;
    private static final long DUNES = 0x6F6017CBC5CF12ABL;
    private static final long STRATA = 0x5A771538F0B488F1L;
    private static final long BIOME_MIX = 0x1395370232F0F301L;
    private static final long LAKES = 0x120D5452C134307DL;
    private static final long SHORE = 0x6884472CF4B94C11L;
    private static final long SNOW = 0x7F55382BE1620D13L;
    private static final long TREES = 0x033316FE9806C3ACL;
    private static final long PLANTS = 0x33861755932C9621L;

    /** Ngưỡng phân loại nóng/lạnh và nửa bề rộng vùng trộn độ cao quanh mỗi ngưỡng. */
    private static final float CLIMATE_THRESHOLD = 0.31f;
    private static final float TRANSITION = 0.18f;
    /** Quy đổi khoảng cách đến ranh giới sang khác biệt nhiệt độ để trộn độ cao. */
    private static final float CLIMATE_SLOPE_WIDTH = 160f;
    /** Trộn vật liệu và thảm thực vật trong 32 block về mỗi phía của mốc khí hậu. */
    private static final int SURFACE_BLEND_RADIUS = 32;
    /** Vùng xuất phát bằng phẳng và thoáng, dùng cả dấu chân của người chơi và phần mép tán cây. */
    private static final int SPAWN_HEIGHT = 12;
    private static final int SPAWN_CLEARANCE = 8;
    /** Cờ dùng chung với mã biome trong bộ đệm để chỉ kiểm tra nguồn nhiệt ở vùng có Lava. */
    private static final int NEAR_LAVA = 4;
    /** Bốn loài hoa có thể chiếm ưu thế trong một cụm thực vật. */
    private static final Block[] FLOWERS = { Block.DANDELION, Block.POPPY, Block.CORNFLOWER, Block.OXEYE_DAISY };
    /** Bảng mã biome, cấp phát một lần để tra nhanh trong các lượt sinh. */
    private static final BiomeType[] BIOMES = BiomeType.values();

    /** Seed gốc giữ cố định trong vòng đời bộ sinh. */
    private final long seed;

    /** Khởi tạo bộ sinh với seed dùng cho tất cả các lượt. */
    TerrainGenerator(long seed) {
        this.seed = seed;
    }

    /** Tra biome gốc của một cột từ tọa độ thế giới. */
    BiomeType getBiome(int x, int z) {
        return classify(temperature(x));
    }

    /** Lấy độ cao địa tầng của một cột trước khi tạo hồ và thảm thực vật. */
    int getSurfaceY(int x, int z) {
        return surfaceY(x, z, temperature(x));
    }

    /** Sinh toàn bộ nội dung; hai bộ đệm cột chỉ tồn tại trong quá trình khởi tạo. */
    void generate(World world) {
        byte[] heights = new byte[WORLD_SIZE_X * WORLD_SIZE_Z];
        byte[] biomes = new byte[heights.length];
        generateStrata(world, heights, biomes);
        generateLakes(world, heights, biomes);
        generateIceAndSnow(world, heights, biomes);
        generateTrees(world, heights, biomes);
        generatePlants(world, heights, biomes);
    }

    /** Phân loại địa hình theo trường nhiệt độ liên tục. */
    private static BiomeType classify(float temperature) {
        if (temperature > CLIMATE_THRESHOLD) {
            return BiomeType.DESERT;
        }
        if (temperature < -CLIMATE_THRESHOLD) {
            return BiomeType.SNOWY_PLAINS;
        }
        return BiomeType.PLAINS;
    }

    /** Giữ hai mốc khí hậu cách nhau 1.000 block và trộn nhiệt độ qua biên. */
    private static float temperature(int x) {
        float columnCenter = x + 0.5f;
        float cold = CLIMATE_THRESHOLD + (BIOME_WIDTH - columnCenter) / CLIMATE_SLOPE_WIDTH;
        float warm = CLIMATE_THRESHOLD + (columnCenter - 2 * BIOME_WIDTH) / CLIMATE_SLOPE_WIDTH;
        return Math.max(0f, Math.min(1f, warm)) - Math.max(0f, Math.min(1f, cold));
    }

    /** Chọn bề mặt theo tỷ lệ chuyển tiếp và noise tạo cụm, thêm biến thiên nhỏ ở mép từng cụm. */
    private BiomeType blendSurface(int x, int z, int boundaryX, float eastWeight) {
        long mixSeed = seed ^ BIOME_MIX;
        float patch = TerrainNoise.fractal(mixSeed, x / 22f, z / 22f);
        float grain = TerrainNoise.unit(mixSeed + 3, x, z) - 0.5f;
        boolean east = eastWeight > 0.5f + 0.38f * patch + 0.24f * grain;
        if (boundaryX == BIOME_WIDTH) {
            return east ? BiomeType.PLAINS : BiomeType.SNOWY_PLAINS;
        }
        return east ? BiomeType.DESERT : BiomeType.PLAINS;
    }

    /** Nội suy chiều cao ở hai ngưỡng khí hậu và làm phẳng nền an toàn dưới chân người chơi. */
    private int surfaceY(int x, int z, float temperature) {
        float hills = TerrainNoise.fractal(seed ^ HEIGHT, x / 71f, z / 71f);
        float plains = 11.5f + hills * 4.8f;
        float desertBlend = smoothBetween(temperature, CLIMATE_THRESHOLD - TRANSITION,
                CLIMATE_THRESHOLD + TRANSITION);
        float snowBlend = smoothBetween(-temperature, CLIMATE_THRESHOLD - TRANSITION,
                CLIMATE_THRESHOLD + TRANSITION);
        float terrain = TerrainNoise.mix(plains, 11.9f + hills * 3.6f, snowBlend);
        if (desertBlend > 0f) {
            float dunes = TerrainNoise.fractal(seed ^ DUNES, (x + z * 0.28f) / 35f, z / 56f);
            terrain = TerrainNoise.mix(terrain, 12.8f + hills * 3.2f + dunes * 3.7f, desertBlend);
        }
        float spawnBlend = smoothBetween(distanceSquared(x - CENTER_X, z - CENTER_Z),
                SPAWN_CLEARANCE * SPAWN_CLEARANCE, 25 * 25);
        return Math.max(9, Math.min(MAX_TERRAIN_Y, Math.round(TerrainNoise.mix(SPAWN_HEIGHT, terrain, spawnBlend))));
    }

    /** Ghi lớp đá gốc, đá, đất hoặc cát theo thứ tự từ đáy lên. */
    private void generateStrata(World world, byte[] heights, byte[] biomes) {
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            float temperature = temperature(x);
            BiomeType climate = classify(temperature);
            int boundaryX = x < CENTER_X ? BIOME_WIDTH : 2 * BIOME_WIDTH;
            float eastWeight = smoothBetween(x + 0.5f, boundaryX - SURFACE_BLEND_RADIUS,
                    boundaryX + SURFACE_BLEND_RADIUS);
            boolean transition = eastWeight > 0f && eastWeight < 1f;
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                // Các lượt sinh tuyết, cây và cỏ cùng đọc vật liệu đã trộn của cột.
                BiomeType biome = transition ? blendSurface(x, z, boundaryX, eastWeight) : climate;
                int y = surfaceY(x, z, temperature);
                int index = columnIndex(x, z);
                heights[index] = (byte) y;
                biomes[index] = (byte) biome.ordinal();
                long layers = TerrainNoise.hash(seed ^ STRATA, x / 3, z / 3);
                int topThickness = 2 + (int) (layers & 1);
                int sandstoneThickness = 0;
                if (biome == BiomeType.DESERT) {
                    topThickness++;
                    sandstoneThickness = 3 + (int) ((layers >>> 1) & 1);
                }
                int topBase = y - topThickness + (biome == BiomeType.DESERT ? 1 : 0);

                write(world, x, 0, z, Block.BEDROCK);
                for (int stoneY = 1; stoneY < topBase - sandstoneThickness; stoneY++) {
                    write(world, x, stoneY, z, Block.STONE);
                }
                for (int sandstoneY = topBase - sandstoneThickness; sandstoneY < topBase; sandstoneY++) {
                    write(world, x, sandstoneY, z, Block.SANDSTONE);
                }
                Block soil = biome == BiomeType.DESERT ? Block.SAND : Block.DIRT;
                for (int soilY = topBase; soilY < y; soilY++) {
                    write(world, x, soilY, z, soil);
                }
                write(world, x, y, z, biome == BiomeType.DESERT ? Block.SAND : Block.GRASS);
            }
        }
    }

    /** Đặt trước một hồ lạnh và một ốc đảo trong hai biome tương ứng, rồi sinh hồ trên lưới so le. */
    private void generateLakes(World world, byte[] heights, byte[] biomes) {
        long lakeSeed = seed ^ LAKES;
        int coldLakeX = CENTER_X - BIOME_WIDTH;
        int warmLakeX = CENTER_X + BIOME_WIDTH;
        int coldLakeZ = CENTER_Z + sampleInt(lakeSeed, coldLakeX, CENTER_Z, 13) - 6;
        int warmLakeZ = CENTER_Z + sampleInt(lakeSeed, warmLakeX, CENTER_Z, 13) - 6;
        carveLake(world, heights, biomes, coldLakeX, coldLakeZ, 8f, 3, Block.WATER);
        carveLake(world, heights, biomes, warmLakeX, warmLakeZ, 6f, 3, Block.WATER);

        int gridSpacing = 64;
        for (int gridX = gridSpacing / 2; gridX < WORLD_SIZE_X - 16; gridX += gridSpacing) {
            for (int gridZ = gridSpacing / 2; gridZ < WORLD_SIZE_Z - 16; gridZ += gridSpacing) {
                int x = gridX + sampleInt(lakeSeed + 1, gridX, gridZ, 25) - 12;
                int z = gridZ + sampleInt(lakeSeed + 2, gridX, gridZ, 25) - 12;
                if (nearSpawn(x, z, 26)
                        || distanceSquared(x - coldLakeX, z - coldLakeZ) < 27 * 27
                        || distanceSquared(x - warmLakeX, z - warmLakeZ) < 27 * 27) {
                    continue;
                }
                BiomeType biome = biomeAt(biomes, x, z);
                float choice = TerrainNoise.unit(lakeSeed + 3, gridX, gridZ);
                float lavaChance = biome == BiomeType.DESERT ? 0.17f : 0.08f;
                float waterChance = biome == BiomeType.DESERT ? 0.06f : 0.36f;
                float radiusVariation = TerrainNoise.unit(lakeSeed + 4, gridX, gridZ);
                if (choice < lavaChance) {
                    carveLake(world, heights, biomes, x, z, 4.5f + radiusVariation * 3f, 3, Block.LAVA);
                } else if (choice < lavaChance + waterChance) {
                    carveLake(world, heights, biomes, x, z, 5.5f + radiusVariation * 4f, 4, Block.WATER);
                }
            }
        }
    }

    /** Khoét hồ có mặt phẳng, đáy cong và bờ nguyên khối bao quanh ngang mực chất lỏng. */
    private void carveLake(World world, byte[] heights, byte[] biomes, int centerX, int centerZ,
                           float radius, int depth, Block liquid) {
        boolean lava = liquid == Block.LAVA;
        BiomeType centerBiome = biomeAt(biomes, centerX, centerZ);
        Block lavaShore = centerBiome == BiomeType.DESERT ? Block.SAND : Block.GRASS;
        Block lavaLining = centerBiome == BiomeType.DESERT ? Block.SANDSTONE : Block.DIRT;
        // Bờ Lava rộng hơn để bao kín cả những chỗ rìa hồ đổi bán kính qua hai ô kề.
        float shoreWidth = lava ? 1.9f : 1.3f;
        float shoreNoise = 1.1f;
        float enclosingRadius = radius + shoreNoise + shoreWidth;
        int reach = (int) Math.ceil(enclosingRadius);
        int level = MAX_TERRAIN_Y;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (distanceSquared(dx, dz) <= enclosingRadius * enclosingRadius) {
                    level = Math.min(level, heights[columnIndex(centerX + dx, centerZ + dz)]);
                }
            }
        }

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int index = columnIndex(x, z);
                float distance = (float) Math.sqrt(distanceSquared(dx, dz));
                float edge = radius + TerrainNoise.fractal(seed ^ SHORE, x / 8f, z / 8f) * shoreNoise;
                if (distance > edge + shoreWidth) {
                    continue;
                }
                if (distance <= edge) {
                    int localDepth = Math.max(1, Math.round(depth * (1f - distance / edge * 0.82f)));
                    int bottomY = level - localDepth;
                    Block floor = lava ? lavaLining : waterLakeFloor(biomeAt(biomes, x, z), x, z);
                    write(world, x, bottomY, z, floor);
                    for (int y = bottomY + 1; y <= level; y++) {
                        write(world, x, y, z, liquid);
                    }
                    for (int y = level + 1; y <= heights[index]; y++) {
                        write(world, x, y, z, Block.AIR);
                    }
                    heights[index] = (byte) level;
                    if (lava) {
                        markLavaColumns(biomes, x, z);
                    }
                } else {
                    // Hạ từng đoạn bờ ngang mặt nước để tạo nền ẩm cho mía.
                    boolean lowShore = !lava && TerrainNoise.fractal(seed ^ SHORE ^ LAKES, x / 7f, z / 7f) > -0.08f;
                    int shoreY = Math.min(heights[index], lowShore ? level : level + 1);
                    BiomeType biome = biomeAt(biomes, x, z);
                    Block shore = lava ? lavaShore : biome == BiomeType.DESERT ? Block.SAND : Block.GRASS;
                    write(world, x, shoreY, z, shore);
                    // Giữ lại vật liệu bờ sẵn có và lót các thành còn hở ngang mực Lava.
                    if (lava && shoreY > level && world.getBlock(x, level, z) != shore.getId()) {
                        write(world, x, level, z, lavaLining);
                    }
                    for (int y = shoreY + 1; y <= heights[index]; y++) {
                        write(world, x, y, z, Block.AIR);
                    }
                    heights[index] = (byte) shoreY;
                }
            }
        }
    }

    /** Chọn vật liệu đáy phù hợp khí hậu và trộn Dirt, Sand, Gravel ở Plains. */
    private Block waterLakeFloor(BiomeType biome, int x, int z) {
        if (biome == BiomeType.DESERT) {
            return Block.SAND;
        }
        float sample = TerrainNoise.unit(seed ^ SHORE, x, z);
        if (sample < 0.24f) {
            return Block.GRAVEL;
        }
        if (sample < 0.51f) {
            return Block.SAND;
        }
        return Block.DIRT;
    }

    /** Đánh dấu các cột có thể ở trong bán kính Manhattan 2 tính từ ô Lava mới sinh. */
    private static void markLavaColumns(byte[] biomes, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            int remaining = 2 - Math.abs(dx);
            for (int dz = -remaining; dz <= remaining; dz++) {
                biomes[columnIndex(x + dx, z + dz)] |= NEAR_LAVA;
            }
        }
    }

    /** Đóng băng mặt hồ và rải các mảng tuyết có chiều dày khác nhau trên cỏ. */
    private void generateIceAndSnow(World world, byte[] heights, byte[] biomes) {
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                if (biomeAt(biomes, x, z) != BiomeType.SNOWY_PLAINS) {
                    continue;
                }
                int y = heights[columnIndex(x, z)];
                byte top = world.getBlock(x, y, z);
                if (top == Block.WATER.getId() && !nearLava(world, biomes, x, y, z)) {
                    write(world, x, y, z, Block.ICE);
                } else if (top == Block.GRASS.getId() && !nearLava(world, biomes, x, y + 1, z)) {
                    float patch = TerrainNoise.fractal(seed ^ SNOW, x / 19f, z / 19f);
                    if (patch > 0.57f) {
                        write(world, x, y + 1, z, Block.SNOW_BLOCK);
                    } else {
                        int state = patch > 0.39f ? 2 : patch > 0.22f ? 1 : 0;
                        world.setBlockInternal(x, y + 1, z, Block.SNOW_LAYER.getId(), state);
                    }
                }
            }
        }
    }

    /** Đi qua các ô lưới cây thưa; khoảng cách tối thiểu giữa hai thân giữ tán cây tách nhau. */
    private void generateTrees(World world, byte[] heights, byte[] biomes) {
        long treeSeed = seed ^ TREES;
        for (int gridX = 4; gridX < WORLD_SIZE_X - 3; gridX += 7) {
            for (int gridZ = 4; gridZ < WORLD_SIZE_Z - 3; gridZ += 7) {
                int x = gridX + sampleInt(treeSeed, gridX, gridZ, 3) - 1;
                int z = gridZ + sampleInt(treeSeed + 1, gridX, gridZ, 3) - 1;
                BiomeType biome = biomeAt(biomes, x, z);
                if (biome == BiomeType.DESERT || nearSpawn(x, z, SPAWN_CLEARANCE + 3)) {
                    continue;
                }
                boolean spruce = biome == BiomeType.SNOWY_PLAINS;
                float choice = TerrainNoise.unit(treeSeed + 2, x, z);
                int groundY = heights[columnIndex(x, z)];
                if (choice >= (spruce ? 0.26f : 0.21f) || world.getBlock(x, groundY, z) != Block.GRASS.getId()) {
                    continue;
                }
                int height = (spruce ? 6 : 4) + sampleInt(treeSeed + 3, x, z, 3);
                placeTree(world, biomes, x, groundY + 1, z, height, spruce);
            }
        }
    }

    /** Dựng thân, tán rộng của sồi hoặc các tầng tán hình nón của vân sam và lớp tuyết trên lá lộ thiên. */
    private void placeTree(World world, byte[] biomes, int x, int baseY, int z, int height, boolean spruce) {
        int topY = baseY + height - 1;
        byte groundCover = world.getBlock(x, baseY, z);
        if (groundCover != Block.AIR.getId() && (!spruce
                || (groundCover != Block.SNOW_LAYER.getId() && groundCover != Block.SNOW_BLOCK.getId()))) {
            return;
        }
        for (int y = baseY + 1; y <= topY; y++) {
            if (world.getBlock(x, y, z) != Block.AIR.getId()) {
                return;
            }
        }
        write(world, x, baseY - 1, z, Block.DIRT);
        Block log = spruce ? Block.SPRUCE_LOG : Block.OAK_LOG;
        Block leaves = spruce ? Block.SPRUCE_LEAVES : Block.OAK_LEAVES;
        for (int y = baseY; y <= topY; y++) {
            write(world, x, y, z, log);
        }
        int foliageDepth = spruce ? height - 3 : 2;
        for (int y = topY - foliageDepth; y <= topY + 1; y++) {
            int fromTop = topY - y;
            int radius = spruce ? Math.min(2, (fromTop + 1) / 2) : fromTop > 0 ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int leafX = x + dx;
                    int leafZ = z + dz;
                    boolean corner = radius > 0 && Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (spruce || y > topY
                            || TerrainNoise.unit(seed ^ TREES ^ y, leafX, leafZ) < 0.55f)) {
                        continue;
                    }
                    if (world.getBlock(leafX, y, leafZ) == Block.AIR.getId()) {
                        write(world, leafX, y, leafZ, leaves);
                    }
                }
            }
        }
        if (spruce) {
            addCanopySnow(world, biomes, x, topY, z, foliageDepth);
        }
    }

    /** Cột lá chỉ nhận tuyết ở mặt trên đầu tiên thoáng ra bầu trời. */
    private void addCanopySnow(World world, byte[] biomes, int x, int topY, int z, int foliageDepth) {
        for (int leafX = x - 2; leafX <= x + 2; leafX++) {
            for (int leafZ = z - 2; leafZ <= z + 2; leafZ++) {
                if (TerrainNoise.unit(seed ^ SNOW, leafX, leafZ) >= 0.64f) {
                    continue;
                }
                for (int y = topY + 1; y >= topY - foliageDepth; y--) {
                    byte top = world.getBlock(leafX, y, leafZ);
                    if (top == Block.AIR.getId()) {
                        continue;
                    }
                    if (top == Block.SPRUCE_LEAVES.getId() && !nearLava(world, biomes, leafX, y + 1, leafZ)) {
                        write(world, leafX, y + 1, leafZ, Block.SNOW_LAYER);
                    }
                    break;
                }
            }
        }
    }

    /** Rải thực vật sau khi có nền, nước và cây; bỏ qua các vị trí đã bị cấu trúc khác chiếm. */
    private void generatePlants(World world, byte[] heights, byte[] biomes) {
        long plantSeed = seed ^ PLANTS;
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                int y = heights[columnIndex(x, z)] + 1;
                if (nearSpawn(x, z, SPAWN_CLEARANCE) || world.getBlock(x, y, z) != Block.AIR.getId()) {
                    continue;
                }
                byte soil = world.getBlock(x, y - 1, z);
                boolean grassSoil = soil == Block.GRASS.getId() || soil == Block.DIRT.getId();
                boolean sandSoil = soil == Block.SAND.getId();
                if (!grassSoil && !sandSoil) {
                    continue;
                }
                float roll = TerrainNoise.unit(plantSeed, x, z);
                BiomeType biome = biomeAt(biomes, x, z);
                if (roll < 0.12f && Block.SUGAR_CANE.canPlaceAt(world, x, y, z)) {
                    growColumn(world, x, y, z, 1 + sampleInt(plantSeed + 1, x, z, 3), Block.SUGAR_CANE);
                } else if (biome == BiomeType.DESERT && sandSoil) {
                    if (roll < 0.006f) {
                        growColumn(world, x, y, z, 1 + sampleInt(plantSeed + 2, x, z, 3), Block.CACTUS);
                    } else if (roll < 0.029f) {
                        write(world, x, y, z, Block.DEAD_BUSH);
                    }
                } else if (biome == BiomeType.PLAINS && grassSoil && roll < 0.32f) {
                    plantMeadow(world, x, y, z, roll, plantSeed);
                }
            }
        }
    }

    /** Sinh từng đoạn liên tục từ gốc và áp dụng cùng luật sinh tồn như lúc chơi. */
    private static void growColumn(World world, int x, int y, int z, int height, Block plant) {
        for (int offset = 0; offset < height; offset++) {
            int partY = y + offset;
            if (world.getBlock(x, partY, z) != Block.AIR.getId() || !plant.canPlaceAt(world, x, partY, z)) {
                return;
            }
            write(world, x, partY, z, plant);
        }
    }

    /** Dùng noise để tạo cụm cỏ, cụm hoa và công bố đủ hai nửa cỏ cao. */
    private static void plantMeadow(World world, int x, int y, int z, float roll, long plantSeed) {
        float patch = TerrainNoise.fractal(plantSeed + 3, x / 16f, z / 16f);
        if (patch > 0.20f && roll < 0.043f) {
            int species = sampleInt(plantSeed + 4, x / 11, z / 11, FLOWERS.length);
            write(world, x, y, z, FLOWERS[species]);
        } else if (patch > -0.09f && roll < 0.26f) {
            if (roll < 0.055f && world.getBlock(x, y + 1, z) == Block.AIR.getId()) {
                write(world, x, y, z, Block.TALL_GRASS);
                world.setBlockInternal(x, y + 1, z, Block.TALL_GRASS.getId(), 1);
            } else {
                write(world, x, y, z, Block.SHORT_GRASS);
            }
        }
    }

    /** Kiểm tra Lava theo khoảng cách Manhattan 3D khi cột nằm gần hồ. */
    private static boolean nearLava(World world, byte[] biomes, int x, int y, int z) {
        return (biomes[columnIndex(x, z)] & NEAR_LAVA) != 0 && world.hasLavaNear(x, y, z);
    }

    /** Ghi ID với trạng thái mặc định qua đường sinh hàng loạt của World. */
    private static void write(World world, int x, int y, int z, Block block) {
        world.setBlockInternal(x, y, z, block.getId(), 0);
    }

    /** Tra mã biome của một cột, bỏ qua cờ phát hiện Lava. */
    private static BiomeType biomeAt(byte[] biomes, int x, int z) {
        return BIOMES[biomes[columnIndex(x, z)] & ~NEAR_LAVA];
    }

    /** Tính vị trí cột trong bộ đệm liên tục theo trục Z. */
    private static int columnIndex(int x, int z) {
        return x * WORLD_SIZE_Z + z;
    }

    /** Chọn chỉ số nguyên tất định trong khoảng [0, bound). */
    private static int sampleInt(long seed, int x, int z, int bound) {
        return (int) (TerrainNoise.unit(seed, x, z) * bound);
    }

    /** Kiểm tra khoảng cách theo mặt phẳng ngang tới tâm spawn. */
    private static boolean nearSpawn(int x, int z, int radius) {
        return distanceSquared(x - CENTER_X, z - CENTER_Z) <= radius * radius;
    }

    /** Ánh xạ một đoạn hữu hạn về trọng số mượt trong khoảng [0, 1]. */
    private static float smoothBetween(float value, float start, float end) {
        return TerrainNoise.smooth(Math.max(0f, Math.min(1f, (value - start) / (end - start))));
    }

    /** Bình phương khoảng cách ngang, tránh lấy căn khi chỉ cần so khoảng cách. */
    private static int distanceSquared(int dx, int dz) {
        return dx * dx + dz * dz;
    }
}
