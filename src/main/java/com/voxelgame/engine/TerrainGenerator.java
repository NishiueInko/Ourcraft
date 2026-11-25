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
    /** Các trường điều khiển sườn núi, xói mòn, lòng sông và mật độ cây. */
    private static final long RIDGES = 0x5A68B6F19F6D4CDFL;
    private static final long EROSION = 0x6B7458C57B81ACF1L;
    private static final long RIVERS = 0x63C628D5188333F3L;
    private static final long FOREST = 0x472440A1512B0DC9L;

    /** Mực sông theo cao độ của ô mặt nước, dùng chung cho cả sông lạnh và sông ấm. */
    static final int WATER_LEVEL = 28;
    /** Chỉ giữ ứng viên hồ ở vùng thấp hoặc thung lũng dưới chân đồi. */
    private static final int MAX_LAKE_LEVEL = 48;
    /** Vùng xuất phát bằng phẳng và thoáng, dùng cả dấu chân của người chơi và phần mép tán cây. */
    private static final int SPAWN_HEIGHT = 30;
    private static final int SPAWN_CLEARANCE = 8;
    /** Cờ dùng chung với mã biome trong bộ đệm để chỉ kiểm tra nguồn nhiệt ở vùng có Lava. */
    private static final int NEAR_LAVA = 4;
    /** Cờ nhận diện dải sông và cột đã được lượt hồ sử dụng. */
    static final int RIVER = 8;
    private static final int LAKE = 16;
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

    /** Tra cùng biome đã dùng để chọn vật liệu, lớp phủ và thực vật. */
    BiomeType getBiome(int x, int z) {
        float warpedX = climateX(x, z);
        if (warpedX < BIOME_WIDTH) {
            return BiomeType.SNOWY_PLAINS;
        }
        return warpedX < 2 * BIOME_WIDTH ? BiomeType.PLAINS : BiomeType.DESERT;
    }

    /** Lấy độ cao địa tầng của một cột trước khi tạo hồ và thảm thực vật. */
    int getSurfaceY(int x, int z) {
        return surfaceY(x, z, riverDistance(x, z));
    }

    /** Sinh toàn bộ nội dung; hai bộ đệm cột chỉ tồn tại trong quá trình khởi tạo. */
    DesertPyramid generate(World world) {
        short[] heights = new short[WORLD_SIZE_X * WORLD_SIZE_Z];
        byte[] biomes = new byte[heights.length];
        sampleColumns(heights, biomes);
        DesertPyramid pyramid = DesertPyramid.select(heights, biomes);
        generateStrata(world, heights, biomes);
        generateLakes(world, heights, biomes, pyramid);
        generateIceAndSnow(world, heights, biomes);
        pyramid.build(world);
        generateTrees(world, heights, biomes, pyramid);
        generatePlants(world, heights, biomes, pyramid);
        return pyramid;
    }

    /** Làm cong các biên khí hậu với hai thang Perlin theo mặt phẳng XZ. */
    private float climateX(int x, int z) {
        return x + 0.5f + 70f * TerrainNoise.perlin(seed ^ BIOME_MIX, x / 233f, z / 233f)
                + 27f * TerrainNoise.perlin(seed ^ BIOME_MIX ^ 1, x / 76f, z / 76f);
    }

    /** Đo khoảng cách tới đường đồng mức của dải sông chạy qua ba biome. */
    private float riverDistance(int x, int z) {
        float bend = 184f * TerrainNoise.perlin(seed ^ RIVERS, x / 390f, 0.37f)
                + 39f * TerrainNoise.perlin(seed ^ RIVERS ^ 1, x / 137f, 0.67f);
        return Math.abs(z - 565f - bend
                + 11f * TerrainNoise.perlin(seed ^ RIVERS ^ 2, x / 170f, z / 170f));
    }

    /** Tính địa hình liên tục trước khi lượng tử hóa thành các tầng voxel. */
    private int surfaceY(int x, int z, float riverDistance) {
        float land = TerrainNoise.fractal(seed ^ HEIGHT, x / 212f, z / 212f);
        float erosion = TerrainNoise.perlin(seed ^ EROSION, x / 134f, z / 134f);
        float cold = 1f - smoothBetween(x, BIOME_WIDTH - 225, BIOME_WIDTH + 25);
        float desert = smoothBetween(x, 2 * BIOME_WIDTH - 65, 2 * BIOME_WIDTH + 130);
        float relief = 0.21f + 0.29f * land - 0.18f * erosion;
        if (cold > 0f) {
            float ridge = TerrainNoise.ridged(seed ^ RIDGES, (x + erosion * 21f) / 117f, z / 117f);
            float range = smoothBetween(TerrainNoise.perlin(seed ^ RIDGES ^ 1, x / 304f, z / 304f),
                    -0.32f, 0.27f);
            relief += cold * (0.33f + 0.61f * range) * ridge;
        }
        float height = elevationSpline(relief);
        if (desert > 0f) {
            float dunes = TerrainNoise.fractal(seed ^ DUNES, (x + z * 0.28f) / 46f, z / 75f);
            float terrace = Math.round((31f + land * 7f + dunes * 7f) / 2f) * 2f;
            height = TerrainNoise.mix(height, terrace, desert);
        }
        if (riverDistance < 96f) {
            float riverFloor = 22f + 14f * smoothBetween(riverDistance, 1f, 25f);
            float lowered = TerrainNoise.mix(riverFloor, height, smoothBetween(riverDistance, 15f, 96f));
            height = Math.min(height, lowered);
        }
        float clear = Math.min(safeAreaBlend(x - CENTER_X, z - CENTER_Z),
                safeAreaBlend(x - World.RESPAWN_X, z - World.RESPAWN_Z));
        return Math.max(18, Math.min(MAX_TERRAIN_Y, Math.round(TerrainNoise.mix(SPAWN_HEIGHT, height, clear))));
    }

    /** Nội suy theo ba đoạn, tăng độ dốc về phía đỉnh núi. */
    private static float elevationSpline(float relief) {
        if (relief < 0.30f) {
            return TerrainNoise.mix(26f, 34f, smoothBetween(relief, 0f, 0.30f));
        }
        if (relief < 0.65f) {
            return TerrainNoise.mix(34f, 63f, smoothBetween(relief, 0.30f, 0.65f));
        }
        float summit = Math.min(1f, (relief - 0.65f) / 0.43f);
        return 63f + 91f * summit * summit;
    }

    /** Chuyển tiếp từ nền an toàn bằng phẳng sang địa hình bao quanh. */
    private static float safeAreaBlend(int dx, int dz) {
        return smoothBetween(distanceSquared(dx, dz), SPAWN_CLEARANCE * SPAWN_CLEARANCE, 30 * 30);
    }

    /** Lưu độ cao để so sánh độ dốc và chọn dấu chân công trình. */
    private void sampleColumns(short[] heights, byte[] biomes) {
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                int index = columnIndex(x, z);
                float river = riverDistance(x, z);
                heights[index] = (short) surfaceY(x, z, river);
                int flags = river < 18f ? RIVER : 0;
                biomes[index] = (byte) (getBiome(x, z).ordinal() | flags);
            }
        }
    }

    /** Ghi địa tầng theo dải và dùng cao độ nguyên gốc để chọn vật liệu theo độ dốc. */
    private void generateStrata(World world, short[] heights, byte[] biomes) {
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                int index = columnIndex(x, z);
                int y = heights[index];
                int slope = slopeAt(heights, x, z, y);
                boolean desert = biomeAt(biomes, x, z) == BiomeType.DESERT;
                boolean beach = (biomes[index] & RIVER) != 0 && y <= WATER_LEVEL + 1;
                long layers = TerrainNoise.hash(seed ^ STRATA, x / 3, z / 3);
                Block surface = desert ? Block.SANDSTONE : Block.STONE;
                int topDepth = 1;
                int liningDepth = desert ? 3 + (int) ((layers >>> 3) & 1) : 0;
                // Vách Desert giữ sandstone, còn nền thoải và bờ sông dùng cát có lớp đệm.
                if (desert) {
                    if (beach || slope <= 1) {
                        surface = Block.SAND;
                        topDepth = 3 + (int) ((layers >>> 1) & 3) % 3;
                    }
                } else if (beach && (layers & 3) != 0) {
                    surface = (layers & 1) == 0 ? Block.GRAVEL : Block.SAND;
                    liningDepth = surface == Block.SAND ? 2 : 0;
                } else if (slope <= 1) {
                    surface = Block.DIRT;
                    topDepth = 3 + (int) (layers & 1);
                } else if (slope == 2) {
                    surface = (layers & 1) == 0 ? Block.STONE : Block.GRAVEL;
                    liningDepth = 0;
                }
                boolean turf = surface == Block.DIRT;
                int surfaceStart = y - topDepth + (turf ? 0 : 1);
                int liningStart = surfaceStart - liningDepth;
                world.fillGeneratedColumn(x, z, 0, 1, Block.BEDROCK);
                world.fillGeneratedColumn(x, z, 1, liningStart, Block.STONE);
                if (liningDepth != 0) {
                    world.fillGeneratedColumn(x, z, liningStart, surfaceStart, Block.SANDSTONE);
                }
                world.fillGeneratedColumn(x, z, surfaceStart, turf ? y : y + 1, surface);
                if (turf) {
                    world.fillGeneratedColumn(x, z, y, y + 1, Block.GRASS);
                }
            }
        }
        // Đổi độ cao cột sau khi các cột xung quanh đã chọn xong vật liệu.
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                int index = columnIndex(x, z);
                int y = heights[index];
                if ((biomes[index] & RIVER) != 0 && y < WATER_LEVEL) {
                    world.fillGeneratedColumn(x, z, y + 1, WATER_LEVEL + 1, Block.WATER);
                    heights[index] = WATER_LEVEL;
                }
            }
        }
    }

    /** Lấy bậc dốc lớn nhất qua bốn cột chung cạnh, kể cả qua biên chunk. */
    private static int slopeAt(short[] heights, int x, int z, int y) {
        int west = Math.abs(y - heights[columnIndex(Math.max(0, x - 1), z)]);
        int east = Math.abs(y - heights[columnIndex(Math.min(WORLD_SIZE_X - 1, x + 1), z)]);
        int north = Math.abs(y - heights[columnIndex(x, Math.max(0, z - 1))]);
        int south = Math.abs(y - heights[columnIndex(x, Math.min(WORLD_SIZE_Z - 1, z + 1))]);
        return Math.max(Math.max(west, east), Math.max(north, south));
    }

    /** Đặt trước một hồ lạnh và một ốc đảo trong hai biome tương ứng, rồi sinh hồ trên lưới so le. */
    private void generateLakes(World world, short[] heights, byte[] biomes, DesertPyramid pyramid) {
        long lakeSeed = seed ^ LAKES;
        int coldLakeX = CENTER_X - BIOME_WIDTH;
        int warmLakeX = CENTER_X + BIOME_WIDTH;
        int coldLakeZ = CENTER_Z + sampleInt(lakeSeed, coldLakeX, CENTER_Z, 13) - 6;
        int warmLakeZ = CENTER_Z + sampleInt(lakeSeed, warmLakeX, CENTER_Z, 13) - 6;
        placeLandmarkLake(world, heights, biomes, pyramid, coldLakeX, coldLakeZ, BiomeType.SNOWY_PLAINS);
        placeLandmarkLake(world, heights, biomes, pyramid, warmLakeX, warmLakeZ, BiomeType.DESERT);

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
                    carveLake(world, heights, biomes, pyramid, x, z, 4.5f + radiusVariation * 3f, 3, Block.LAVA);
                } else if (choice < lavaChance + waterChance) {
                    if (carveLake(world, heights, biomes, pyramid, x, z, 5.5f + radiusVariation * 4f, 4, Block.WATER)
                            && biome == BiomeType.DESERT) {
                        plantOasisPalms(world, heights, biomes, x, z);
                    }
                }
            }
        }
    }

    /** Tìm nền bằng gần tâm biome cho hồ lạnh và ốc đảo dành làm mốc địa hình. */
    private void placeLandmarkLake(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                                   int centerX, int centerZ, BiomeType biome) {
        for (int radius = 0; radius <= 320; radius += 8) {
            for (int dx = -radius; dx <= radius; dx += 8) {
                for (int dz = -radius; dz <= radius; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (biomeAt(biomes, x, z) == biome
                            && carveLake(world, heights, biomes, pyramid, x, z, 7f, 4, Block.WATER)) {
                        if (biome == BiomeType.DESERT) {
                            plantOasisPalms(world, heights, biomes, x, z);
                        }
                        return;
                    }
                }
            }
        }
    }

    /** Khoét hồ có mặt phẳng, đáy cong và bờ nguyên khối bao quanh ngang mực chất lỏng. */
    private boolean carveLake(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                              int centerX, int centerZ, float radius, int depth, Block liquid) {
        boolean lava = liquid == Block.LAVA;
        // Bờ Lava rộng hơn để bao kín cả những chỗ rìa hồ đổi bán kính qua hai ô kề.
        float shoreWidth = lava ? 1.9f : 1.3f;
        float shoreNoise = 1.1f;
        float enclosingRadius = radius + shoreNoise + shoreWidth;
        int reach = (int) Math.ceil(enclosingRadius);
        if (nearSpawn(centerX, centerZ, reach + SPAWN_CLEARANCE + 6)
                || pyramid.contains(centerX, centerZ, reach + 3)) {
            return false;
        }
        int level = MAX_TERRAIN_Y;
        int peak = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (distanceSquared(dx, dz) <= enclosingRadius * enclosingRadius) {
                    int index = columnIndex(centerX + dx, centerZ + dz);
                    if ((biomes[index] & (RIVER | LAKE)) != 0) {
                        return false;
                    }
                    level = Math.min(level, heights[index]);
                    peak = Math.max(peak, heights[index]);
                    if (peak - level > 3) {
                        return false;
                    }
                }
            }
        }
        if (level > MAX_LAKE_LEVEL) {
            return false;
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
                biomes[index] |= LAKE;
                // Chọn vật liệu theo cột đích để hồ đi qua ranh giới vẫn giữ đúng khí hậu.
                BiomeType biome = biomeAt(biomes, x, z);
                Block lavaLining = biome == BiomeType.DESERT ? Block.SANDSTONE : Block.DIRT;
                if (distance <= edge) {
                    int localDepth = Math.max(1, Math.round(depth * (1f - distance / edge * 0.82f)));
                    int bottomY = level - localDepth;
                    Block floor = lava ? lavaLining : waterLakeFloor(biome, x, z);
                    write(world, x, bottomY, z, floor);
                    if (floor == Block.SAND) {
                        write(world, x, bottomY - 1, z, Block.SANDSTONE);
                    }
                    for (int y = bottomY + 1; y <= level; y++) {
                        write(world, x, y, z, liquid);
                    }
                    for (int y = level + 1; y <= heights[index]; y++) {
                        write(world, x, y, z, Block.AIR);
                    }
                    heights[index] = (short) level;
                    if (lava) {
                        markLavaColumns(biomes, x, z);
                    }
                } else {
                    // Hạ từng đoạn bờ ngang mặt nước để tạo nền ẩm cho mía.
                    boolean lowShore = !lava && TerrainNoise.fractal(seed ^ SHORE ^ LAKES, x / 7f, z / 7f) > -0.08f;
                    int shoreY = Math.min(heights[index], lowShore ? level : level + 1);
                    Block shore = biome == BiomeType.DESERT ? Block.SAND : Block.GRASS;
                    boolean newSand = shore == Block.SAND && world.getBlock(x, shoreY, z) != Block.SAND.getId();
                    write(world, x, shoreY, z, shore);
                    if (newSand) {
                        write(world, x, shoreY - 1, z, Block.SANDSTONE);
                    }
                    // Giữ lại vật liệu bờ sẵn có và lót các thành còn hở ngang mực Lava.
                    if (lava && shoreY > level && world.getBlock(x, level, z) != shore.getId()) {
                        write(world, x, level, z, lavaLining);
                    }
                    for (int y = shoreY + 1; y <= heights[index]; y++) {
                        write(world, x, y, z, Block.AIR);
                    }
                    heights[index] = (short) shoreY;
                }
            }
        }
        return true;
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
    private void generateIceAndSnow(World world, short[] heights, byte[] biomes) {
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                if (biomeAt(biomes, x, z) != BiomeType.SNOWY_PLAINS) {
                    continue;
                }
                int y = heights[columnIndex(x, z)];
                byte top = world.getBlock(x, y, z);
                if (top == Block.WATER.getId() && !nearLava(world, biomes, x, y, z)) {
                    write(world, x, y, z, Block.ICE);
                } else if (y > 110 && Block.fromId(top).isOpaque() && !nearLava(world, biomes, x, y + 1, z)) {
                    write(world, x, y + 1, z, Block.SNOW_BLOCK);
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

    /** Dáng cây xác định chiều cao thân và vật liệu tán theo vùng khí hậu. */
    private enum TreeShape {
        OAK(4, 3, 2, 2, false),
        OLD_OAK(9, 3, 3, 3, false),
        OAK_BUSH(1, 1, 2, 0, false),
        SPRUCE(7, 3, 3, 4, true),
        PINE(7, 3, 2, 4, true),
        DWARF_PINE(3, 2, 1, 2, true);

        /** Hai mốc dùng tính chiều cao thân theo seed. */
        final int minHeight;
        final int variation;
        /** Bán kính rộng nhất, độ sâu từ đỉnh thân, và kiểu lá. */
        final int crownRadius;
        final int crownDepth;
        final boolean conifer;

        /** Lưu kích thước và vật liệu dùng chung cho hai lượt kiểm tra và ghi hình cây. */
        TreeShape(int minHeight, int variation, int crownRadius, int crownDepth, boolean conifer) {
            this.minHeight = minHeight;
            this.variation = variation;
            this.crownRadius = crownRadius;
            this.crownDepth = crownDepth;
            this.conifer = conifer;
        }
    }

    /** Lưới so le lấy mật độ liên tục từ Perlin để tạo trảng trống và cụm cây dày. */
    private void generateTrees(World world, short[] heights, byte[] biomes, DesertPyramid pyramid) {
        long treeSeed = seed ^ TREES;
        for (int gridX = 5; gridX < WORLD_SIZE_X - 4; gridX += 6) {
            for (int gridZ = 5; gridZ < WORLD_SIZE_Z - 4; gridZ += 6) {
                int x = gridX + sampleInt(treeSeed, gridX, gridZ, 3) - 1;
                int z = gridZ + sampleInt(treeSeed + 1, gridX, gridZ, 3) - 1;
                BiomeType biome = biomeAt(biomes, x, z);
                if (biome == BiomeType.DESERT || nearSpawn(x, z, SPAWN_CLEARANCE + 5)
                        || pyramid.contains(x, z, 4)) {
                    continue;
                }
                int groundY = heights[columnIndex(x, z)];
                float forest = TerrainNoise.perlin(seed ^ FOREST, x / 91f, z / 91f)
                        + 0.32f * TerrainNoise.perlin(seed ^ FOREST ^ 1L, x / 43f, z / 43f);
                float density = TerrainNoise.mix(0.018f, 0.77f, smoothBetween(forest, -0.19f, 0.29f));
                if (biome == BiomeType.SNOWY_PLAINS && groundY >= 62) {
                    density *= 0.32f;
                }
                if (groundY > 77 || TerrainNoise.unit(treeSeed + 2, x, z) >= density
                        || world.getBlock(x, groundY, z) != Block.GRASS.getId()) {
                    continue;
                }
                TreeShape shape = treeShape(biome, groundY, TerrainNoise.unit(treeSeed + 3, x, z));
                int height = shape.minHeight + sampleInt(treeSeed + 4, x, z, shape.variation);
                placeTree(world, biomes, x, groundY + 1, z, height, shape);
            }
        }
    }

    /** Chọn dáng cây thấp dần theo cao độ trong biome tuyết, và đa dạng tuổi sồi ở Plains. */
    private static TreeShape treeShape(BiomeType biome, int y, float variety) {
        if (biome == BiomeType.PLAINS) {
            return variety < 0.19f ? TreeShape.OAK_BUSH : variety < 0.34f ? TreeShape.OLD_OAK : TreeShape.OAK;
        }
        if (y >= 62) {
            return TreeShape.DWARF_PINE;
        }
        return y >= 45 ? TreeShape.PINE : TreeShape.SPRUCE;
    }

    /** Kiểm tra thân và mọi ô tán trước khi ghi cây, rồi phủ tuyết lên tán thông hợp lệ. */
    private void placeTree(World world, byte[] biomes, int x, int baseY, int z, int height, TreeShape shape) {
        int topY = baseY + height - 1;
        byte groundCover = world.getBlock(x, baseY, z);
        if (groundCover != Block.AIR.getId() && (!shape.conifer
                || (groundCover != Block.SNOW_LAYER.getId() && groundCover != Block.SNOW_BLOCK.getId()))) {
            return;
        }
        for (int y = baseY + 1; y <= topY; y++) {
            if (world.getBlock(x, y, z) != Block.AIR.getId()) {
                return;
            }
        }
        if (!treeCanopy(world, x, topY, z, shape, false)) {
            return;
        }
        write(world, x, baseY - 1, z, Block.DIRT);
        Block log = shape.conifer ? Block.SPRUCE_LOG : Block.OAK_LOG;
        for (int y = baseY; y <= topY; y++) {
            write(world, x, y, z, log);
        }
        treeCanopy(world, x, topY, z, shape, true);
        if (shape.conifer) {
            addCanopySnow(world, biomes, x, topY, z, shape);
        }
    }

    /** Duyệt chung hình tán ở lượt xét khoảng trống và lượt ghi block. */
    private boolean treeCanopy(World world, int x, int topY, int z, TreeShape shape, boolean place) {
        Block leaves = shape.conifer ? Block.SPRUCE_LEAVES : Block.OAK_LEAVES;
        for (int depth = -1; depth <= shape.crownDepth; depth++) {
            int y = topY - depth;
            int radius = crownRadius(shape, depth);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int leafX = x + dx;
                    int leafZ = z + dz;
                    if (depth >= 0 && dx == 0 && dz == 0) {
                        continue;
                    }
                    boolean corner = radius > 0 && Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (shape.conifer || depth < 0
                            || TerrainNoise.unit(seed ^ TREES ^ y, leafX, leafZ) < 0.55f)) {
                        continue;
                    }
                    if (place) {
                        write(world, leafX, y, leafZ, leaves);
                    } else if (!world.isInsideWorld(leafX, y, leafZ)
                            || world.getBlock(leafX, y, leafZ) != Block.AIR.getId()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Tạo tán tròn của sồi, tán thông nhọn hoặc váy vân sam rộng. */
    private static int crownRadius(TreeShape shape, int depth) {
        if (!shape.conifer) {
            return depth < 0 ? shape.crownRadius - 1 : shape.crownRadius;
        }
        if (depth < 1) {
            return 0;
        }
        return Math.min(shape.crownRadius, shape == TreeShape.SPRUCE ? depth : (depth + 1) / 2);
    }

    /** Cột lá chỉ nhận tuyết ở mặt trên đầu tiên thoáng ra bầu trời. */
    private void addCanopySnow(World world, byte[] biomes, int x, int topY, int z, TreeShape shape) {
        for (int leafX = x - shape.crownRadius; leafX <= x + shape.crownRadius; leafX++) {
            for (int leafZ = z - shape.crownRadius; leafZ <= z + shape.crownRadius; leafZ++) {
                if (biomeAt(biomes, leafX, leafZ) != BiomeType.SNOWY_PLAINS
                        || TerrainNoise.unit(seed ^ SNOW, leafX, leafZ) >= 0.64f) {
                    continue;
                }
                for (int y = topY + 1; y >= topY - shape.crownDepth; y--) {
                    byte top = world.getBlock(leafX, y, leafZ);
                    if (top == Block.AIR.getId()) {
                        continue;
                    }
                    if (top == Block.SPRUCE_LEAVES.getId()
                            && world.getBlock(leafX, y + 1, leafZ) == Block.AIR.getId()
                            && !nearLava(world, biomes, leafX, y + 1, leafZ)) {
                        write(world, leafX, y + 1, leafZ, Block.SNOW_LAYER);
                    }
                    break;
                }
            }
        }
    }

    /** Chọn tối đa hai gốc trên các bờ đối diện của ốc đảo đã tạo mặt nước. */
    private void plantOasisPalms(World world, short[] heights, byte[] biomes, int centerX, int centerZ) {
        boolean alongX = (TerrainNoise.hash(seed ^ TREES, centerX, centerZ) & 1) == 0;
        for (int side = -1; side <= 1; side += 2) {
            boolean placed = false;
            for (int distance = 4; distance <= 13 && !placed; distance++) {
                for (int tangent = -distance; tangent <= distance && !placed; tangent++) {
                    int x = centerX + (alongX ? side * distance : tangent);
                    int z = centerZ + (alongX ? tangent : side * distance);
                    if (!world.isInsideWorld(x, 0, z) || biomeAt(biomes, x, z) != BiomeType.DESERT) {
                        continue;
                    }
                    int groundY = heights[columnIndex(x, z)];
                    if ((biomes[columnIndex(x, z)] & LAKE) == 0
                            || world.getBlock(x, groundY, z) != Block.SAND.getId()
                            || !hasOasisWater(world, x, groundY, z)) {
                        continue;
                    }
                    placed = placePalm(world, x, groundY + 1, z);
                }
            }
        }
    }

    /** Tìm mặt nước gần gốc dù nền cát có thể cao hơn mặt nước một block. */
    private static boolean hasOasisWater(World world, int x, int groundY, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 2) {
                    continue;
                }
                for (int y = groundY - 1; y <= groundY; y++) {
                    if (world.getBlock(x + dx, y, z + dz) == Block.WATER.getId()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Tạo thân thanh và chùm tàu lá xòe, với đầu tàu rủ thấp hơn tâm hai block. */
    private boolean placePalm(World world, int x, int baseY, int z) {
        int topY = baseY + 5 + sampleInt(seed ^ FOREST, x, z, 3);
        for (int y = baseY; y <= topY; y++) {
            if (world.getBlock(x, y, z) != Block.AIR.getId()) {
                return false;
            }
        }
        if (!palmCanopy(world, x, topY, z, false)) {
            return false;
        }
        for (int y = baseY; y <= topY; y++) {
            write(world, x, y, z, Block.OAK_LOG);
        }
        palmCanopy(world, x, topY, z, true);
        return true;
    }

    /** Ghi tán tám hướng bằng hai loại cung dài/ngắn và cạnh lá xen kẽ. */
    private static boolean palmCanopy(World world, int x, int y, int z, boolean place) {
        if (!palmLeaf(world, x, y + 1, z, place)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                boolean diagonal = dx != 0 && dz != 0;
                int length = diagonal ? 3 : 4;
                int previousY = y + 1;
                for (int step = 1; step <= length; step++) {
                    int leafY = y + (step < length - 1 ? 1 : step == length ? -1 : 0);
                    int nextX = x + dx * step;
                    int nextZ = z + dz * step;
                    // Nối tàu lá bằng các mặt chung cả khi đổi cao độ hoặc đi chéo.
                    if ((diagonal && !palmLeaf(world, nextX, previousY, nextZ - dz, place))
                            || (leafY != previousY && !palmLeaf(world, nextX, previousY, nextZ, place))
                            || !palmLeaf(world, nextX, leafY, nextZ, place)) {
                        return false;
                    }
                    previousY = leafY;
                }
            }
        }
        return true;
    }

    /** Kiểm tra trước, sau đó cho phép các nhánh dùng chung ô nối khi ghi lá. */
    private static boolean palmLeaf(World world, int x, int y, int z, boolean place) {
        if (place) {
            write(world, x, y, z, Block.OAK_LEAVES);
            return true;
        }
        return world.isInsideWorld(x, y, z) && world.getBlock(x, y, z) == Block.AIR.getId();
    }

    /** Rải thực vật sau khi có nền, nước và cây; bỏ qua các vị trí đã bị cấu trúc khác chiếm. */
    private void generatePlants(World world, short[] heights, byte[] biomes, DesertPyramid pyramid) {
        long plantSeed = seed ^ PLANTS;
        for (int x = 0; x < WORLD_SIZE_X; x++) {
            for (int z = 0; z < WORLD_SIZE_Z; z++) {
                int y = heights[columnIndex(x, z)] + 1;
                if (nearSpawn(x, z, SPAWN_CLEARANCE) || pyramid.contains(x, z, 0)
                        || world.getBlock(x, y, z) != Block.AIR.getId()) {
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
        if (roll < 0.14f) {
            float strength = 0.43f;
            Block species = null;
            for (int index = 0; index < FLOWERS.length; index++) {
                float flowerPatch = TerrainNoise.simplex(plantSeed + 7 + index, x / 31f, z / 31f);
                if (flowerPatch > strength) {
                    strength = flowerPatch;
                    species = FLOWERS[index];
                }
            }
            if (species != null) {
                write(world, x, y, z, species);
                return;
            }
        }
        if (patch > -0.09f && roll < 0.26f) {
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
        return BIOMES[biomes[columnIndex(x, z)] & 3];
    }

    /** Tính vị trí cột trong bộ đệm liên tục theo trục Z. */
    private static int columnIndex(int x, int z) {
        return x * WORLD_SIZE_Z + z;
    }

    /** Chọn chỉ số nguyên tất định trong khoảng [0, bound). */
    private static int sampleInt(long seed, int x, int z, int bound) {
        return (int) (TerrainNoise.unit(seed, x, z) * bound);
    }

    /** Kiểm tra khoảng cách ngang tới cả hai vùng an toàn cho người chơi. */
    private static boolean nearSpawn(int x, int z, int radius) {
        return distanceSquared(x - CENTER_X, z - CENTER_Z) <= radius * radius
                || distanceSquared(x - World.RESPAWN_X, z - World.RESPAWN_Z) <= radius * radius;
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
