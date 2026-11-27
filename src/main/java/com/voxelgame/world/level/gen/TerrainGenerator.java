package com.voxelgame.world.level.gen;

import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.Chunk;
import com.voxelgame.world.level.World;
import static com.voxelgame.world.level.World.BIOME_WIDTH;
import static com.voxelgame.world.level.World.CENTER_X;
import static com.voxelgame.world.level.World.CENTER_Z;
import static com.voxelgame.world.level.World.MAX_TERRAIN_Y;
import static com.voxelgame.world.level.World.WORLD_SIZE_X;
import static com.voxelgame.world.level.World.WORLD_SIZE_Z;

/** Sinh địa tầng, mặt nước và hệ sinh thái theo seed, trước khi bắt đầu cập nhật gameplay. */
public final class TerrainGenerator {
    /** Các trường noise độc lập để độ cao, lớp phủ và thực vật không lặp cùng mẫu. */
    private static final long HEIGHT = 0x380678E2B9DA63C8L;
    private static final long DUNES = 0x6F6017CBC5CF12ABL;
    private static final long STRATA = 0x5A771538F0B488F1L;
    private static final long BIOME_MIX = 0x1395370232F0F301L;
    private static final long LAKES = 0x120D5452C134307DL;
    private static final long SHORE = 0x6884472CF4B94C11L;
    private static final long TREES = 0x033316FE9806C3ACL;
    private static final long FLOWERS_SEED = 0x4C81A3D92E74B517L;
    private static final long FLOWER_GRADIENT = 0x29F4C608B315E8A3L;
    private static final long GRASS_SEED = 0x71D28E4A90C36F5BL;
    private static final long TALL_GRASS_SEED = 0x5B3E91C742A81D6FL;
    private static final long CACTUS_SEED = 0x64A72F85C193E04DL;
    private static final long DEAD_BUSH_SEED = 0x1E95B472D836A2C9L;
    private static final long SUGAR_CANE_SEED = 0x3A6C82F15E094B73L;
    /** Các trường điều khiển sườn núi, xói mòn, lòng sông và mật độ cây. */
    private static final long RIDGES = 0x5A68B6F19F6D4CDFL;
    private static final long EROSION = 0x6B7458C57B81ACF1L;
    private static final long RIVERS = 0x63C628D5188333F3L;
    private static final long FOREST = 0x472440A1512B0DC9L;

    /** Mực sông theo cao độ của ô mặt nước, dùng chung cho cả sông lạnh và sông ấm. */
    public static final int WATER_LEVEL = 28;
    /** Chỉ giữ ứng viên hồ ở vùng thấp hoặc thung lũng dưới chân đồi. */
    private static final int MAX_LAKE_LEVEL = 48;
    /** Vùng xuất phát bằng phẳng và thoáng, dùng cả dấu chân của người chơi và phần mép tán cây. */
    private static final int SPAWN_HEIGHT = 30;
    private static final int SPAWN_CLEARANCE = 8;
    /** Ba bit thấp trong bộ đệm lưu mã của năm biome (0..4). */
    static final int BIOME_MASK = 7;
    /** Cờ dùng chung với mã biome trong bộ đệm để chỉ kiểm tra nguồn nhiệt ở vùng có Lava. */
    private static final int NEAR_LAVA = 8;
    /** Cờ nhận diện dải sông và cột đã được lượt hồ sử dụng. */
    public static final int RIVER = 16;
    private static final int LAKE = 32;
    /** Bốn loài hoa dùng cho dải gradient của Flower Forest. */
    private static final Block[] FLOWERS = { Block.DANDELION, Block.POPPY, Block.CORNFLOWER, Block.OXEYE_DAISY };
    /** Bảng trọng số hoa của Plains chuẩn Minecraft (Dandelion 2 : Poppy 1 : Cornflower 1 : Oxeye Daisy 1). */
    private static final Block[] PLAINS_FLOWERS = {
            Block.DANDELION, Block.DANDELION, Block.POPPY, Block.CORNFLOWER, Block.OXEYE_DAISY
    };
    /** Bảng trọng số hoa của Forest chuẩn Minecraft (chỉ Dandelion 2 : Poppy 1). */
    private static final Block[] FOREST_FLOWERS = { Block.DANDELION, Block.DANDELION, Block.POPPY };
    /** Bảng mã biome, cấp phát một lần để tra nhanh trong các lượt sinh. */
    private static final BiomeType[] BIOMES = BiomeType.values();

    /** Seed gốc giữ cố định trong vòng đời bộ sinh. */
    private final long seed;

    /** Khởi tạo bộ sinh với seed dùng cho tất cả các lượt. */
    public TerrainGenerator(long seed) {
        this.seed = seed;
    }

    /** Tra cùng biome đã dùng để chọn vật liệu, lớp phủ và thực vật cho năm dải khí hậu. */
    public BiomeType getBiome(int x, int z) {
        float warpedX = climateX(x, z);
        if (warpedX < BIOME_WIDTH) {
            return BiomeType.SNOWY_PLAINS;
        }
        if (warpedX < 2 * BIOME_WIDTH) {
            return BiomeType.FOREST;
        }
        if (warpedX < 3 * BIOME_WIDTH) {
            return BiomeType.PLAINS;
        }
        return warpedX < 4 * BIOME_WIDTH ? BiomeType.FLOWER_FOREST : BiomeType.DESERT;
    }

    /** Lấy độ cao địa tầng của một cột trước khi tạo hồ và thảm thực vật. */
    public int getSurfaceY(int x, int z) {
        return surfaceY(x, z, riverDistance(x, z));
    }

    /** Sinh toàn bộ nội dung; hai bộ đệm cột chỉ tồn tại trong quá trình khởi tạo. */
    public DesertPyramid generate(World world) {
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

    /** Đo khoảng cách tới đường đồng mức của dải sông chạy qua năm biome. */
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
        float desert = smoothBetween(x, 4 * BIOME_WIDTH - 65, 4 * BIOME_WIDTH + 130);
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

    /** Sinh hồ dung nham mặt đất theo từng chunk 16×16 với xác suất chuẩn 1/200 chunk (bỏ hồ nước nhỏ). */
    private void generateLakes(World world, short[] heights, byte[] biomes, DesertPyramid pyramid) {
        long lakeSeed = seed ^ LAKES;
        for (int cx = 1; cx < World.WORLD_CHUNKS_X - 1; cx++) {
            for (int cz = 1; cz < World.WORLD_CHUNKS_Z - 1; cz++) {
                if (TerrainNoise.unit(lakeSeed, cx, cz) >= 1f / 200f) {
                    continue;
                }
                int x = cx * Chunk.SIZE + sampleInt(lakeSeed + 1, cx, cz, Chunk.SIZE);
                int z = cz * Chunk.SIZE + sampleInt(lakeSeed + 2, cx, cz, Chunk.SIZE);
                float radiusVariation = TerrainNoise.unit(lakeSeed + 3, cx, cz);
                carveLavaLake(world, heights, biomes, pyramid, x, z, 4.5f + radiusVariation * 3f, 3);
            }
        }
    }

    /** Khoét hồ dung nham có mặt phẳng, đáy cong và bờ nguyên khối bao quanh ngang mực chất lỏng. */
    private boolean carveLavaLake(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                                  int centerX, int centerZ, float radius, int depth) {
        // Bờ Lava rộng để bao kín cả những chỗ rìa hồ đổi bán kính qua hai ô kề.
        float shoreWidth = 1.9f;
        float shoreNoise = 1.1f;
        float enclosingRadius = radius + shoreNoise + shoreWidth;
        int reach = (int) Math.ceil(enclosingRadius);
        if (centerX - reach - 2 < 0 || centerX + reach + 2 >= WORLD_SIZE_X
                || centerZ - reach - 2 < 0 || centerZ + reach + 2 >= WORLD_SIZE_Z
                || nearSpawn(centerX, centerZ, reach + SPAWN_CLEARANCE + 6)
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
                    write(world, x, bottomY, z, lavaLining);
                    for (int y = bottomY + 1; y <= level; y++) {
                        write(world, x, y, z, Block.LAVA);
                    }
                    for (int y = level + 1; y <= heights[index]; y++) {
                        write(world, x, y, z, Block.AIR);
                    }
                    heights[index] = (short) level;
                    markLavaColumns(biomes, x, z);
                } else {
                    int shoreY = Math.min(heights[index], level + 1);
                    Block shore = biome == BiomeType.DESERT ? Block.SAND : Block.GRASS;
                    boolean newSand = shore == Block.SAND && world.getBlock(x, shoreY, z) != Block.SAND.getId();
                    write(world, x, shoreY, z, shore);
                    if (newSand) {
                        write(world, x, shoreY - 1, z, Block.SANDSTONE);
                    }
                    // Giữ lại vật liệu bờ sẵn có và lót các thành còn hở ngang mực Lava.
                    if (shoreY > level && world.getBlock(x, level, z) != shore.getId()) {
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

    /** Đánh dấu các cột có thể ở trong bán kính Manhattan 2 tính từ ô Lava mới sinh. */
    private static void markLavaColumns(byte[] biomes, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            int remaining = 2 - Math.abs(dx);
            for (int dz = -remaining; dz <= remaining; dz++) {
                biomes[columnIndex(x + dx, z + dz)] |= NEAR_LAVA;
            }
        }
    }

    /** Đóng băng mặt sông và phủ đúng 1 lớp tuyết trên toàn bộ cột lộ trời ở Snowy Plains. */
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
                } else if (Block.SNOW_LAYER.canPlaceAt(world, x, y + 1, z)
                        && !nearLava(world, biomes, x, y + 1, z)) {
                    write(world, x, y + 1, z, Block.SNOW_LAYER);
                }
            }
        }
    }

    /** Dáng cây xác định chiều cao thân và vật liệu tán theo vùng khí hậu. */
    private enum TreeShape {
        OAK(4, 3, 2, 2, false),
        OLD_OAK(9, 3, 3, 3, false),
        SPRUCE(7, 3, 3, 4, true),
        PINE(7, 3, 2, 4, true);

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

    /** Nhận diện vùng rừng thông Grove liên tục trong Snowy Plains. */
    private boolean isSnowyGrove(int x, int z) {
        float grove = TerrainNoise.perlin(seed ^ FOREST, x / 91f, z / 91f)
                + 0.32f * TerrainNoise.perlin(seed ^ FOREST ^ 1L, x / 43f, z / 43f);
        return grove > 0.12f;
    }

    /** Sinh cây theo từng chunk 16×16 khớp mật độ và tỷ lệ loài chuẩn Minecraft 1.21.11. */
    private void generateTrees(World world, short[] heights, byte[] biomes, DesertPyramid pyramid) {
        long treeSeed = seed ^ TREES;
        for (int cx = 0; cx < World.WORLD_CHUNKS_X; cx++) {
            for (int cz = 0; cz < World.WORLD_CHUNKS_Z; cz++) {
                int centerX = cx * Chunk.SIZE + 8;
                int centerZ = cz * Chunk.SIZE + 8;
                BiomeType chunkBiome = biomeAt(biomes, centerX, centerZ);
                if (chunkBiome == BiomeType.DESERT) {
                    continue;
                }
                boolean snowyGrove = false;
                int attempts;
                switch (chunkBiome) {
                    case PLAINS -> attempts = TerrainNoise.unit(treeSeed, cx, cz) < 0.05f ? 1 : 0;
                    case FOREST -> attempts = 10;
                    case FLOWER_FOREST -> attempts = 6;
                    case SNOWY_PLAINS -> {
                        snowyGrove = isSnowyGrove(centerX, centerZ);
                        attempts = snowyGrove ? 10 : (TerrainNoise.unit(treeSeed, cx, cz) < 0.10f ? 1 : 0);
                    }
                    default -> attempts = 0;
                }
                for (int attempt = 0; attempt < attempts; attempt++) {
                    long h = chunkRoll(treeSeed, cx, cz, attempt + 1);
                    int x = cx * Chunk.SIZE + (int) (h & 15);
                    int z = cz * Chunk.SIZE + (int) ((h >>> 4) & 15);
                    if (biomeAt(biomes, x, z) != chunkBiome
                            || nearSpawn(x, z, SPAWN_CLEARANCE + 5)
                            || pyramid.contains(x, z, 4)) {
                        continue;
                    }
                    int groundY = heights[columnIndex(x, z)];
                    if (world.getBlock(x, groundY, z) != Block.GRASS.getId()) {
                        continue;
                    }
                    if (chunkBiome == BiomeType.SNOWY_PLAINS) {
                        if (groundY >= 62 || (snowyGrove && (groundY < 30 || !isSnowyGrove(x, z)))) {
                            continue;
                        }
                    } else if (groundY > 77) {
                        continue;
                    }
                    float variety = ((h >>> 8) & 0xFFFFFFL) * 0x1.0p-24f;
                    TreeShape shape = treeShape(chunkBiome, snowyGrove, variety);
                    int height = shape.minHeight + (int) ((((h >>> 32) & 0xFFFFL) * shape.variation) >>> 16);
                    placeTree(world, biomes, x, groundY + 1, z, height, shape);
                }
            }
        }
    }

    /** Chọn dáng cây theo biome và phân vùng Grove/Flat của Snowy Plains. */
    private static TreeShape treeShape(BiomeType biome, boolean snowyGrove, float variety) {
        return switch (biome) {
            case PLAINS -> variety < (1f / 3f) ? TreeShape.OLD_OAK : TreeShape.OAK;
            case FOREST, FLOWER_FOREST -> variety < 0.10f ? TreeShape.OLD_OAK : TreeShape.OAK;
            case SNOWY_PLAINS -> snowyGrove && variety < (1f / 3f) ? TreeShape.PINE : TreeShape.SPRUCE;
            default -> TreeShape.OAK;
        };
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

    /** Mọi cột lá vân sam lộ trời ở Snowy Plains (trừ vùng sát Lava) đều phủ 1 lớp tuyết. */
    private void addCanopySnow(World world, byte[] biomes, int x, int topY, int z, TreeShape shape) {
        for (int leafX = x - shape.crownRadius; leafX <= x + shape.crownRadius; leafX++) {
            for (int leafZ = z - shape.crownRadius; leafZ <= z + shape.crownRadius; leafZ++) {
                if (biomeAt(biomes, leafX, leafZ) != BiomeType.SNOWY_PLAINS) {
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

    /** Sinh hoa, cỏ, xương rồng, bụi khô và mía theo từng chunk 16×16 chuẩn Minecraft 1.21.11. */
    private void generatePlants(World world, short[] heights, byte[] biomes, DesertPyramid pyramid) {
        // Lượt 1: Sinh hoa và cỏ cao trước để cỏ ngắn không chiếm chỗ các cụm hoa và cỏ hai ô.
        for (int cx = 0; cx < World.WORLD_CHUNKS_X; cx++) {
            for (int cz = 0; cz < World.WORLD_CHUNKS_Z; cz++) {
                BiomeType chunkBiome = biomeAt(biomes, cx * Chunk.SIZE + 8, cz * Chunk.SIZE + 8);
                if (chunkBiome == BiomeType.PLAINS) {
                    generateFlowerPatch(world, heights, biomes, pyramid, cx, cz, chunkBiome,
                            seed ^ FLOWERS_SEED, 0.5f, 10, 4, PLAINS_FLOWERS, false);
                    generateTallGrassPatch(world, heights, biomes, pyramid, cx, cz, chunkBiome);
                } else if (chunkBiome == BiomeType.FOREST) {
                    generateFlowerPatch(world, heights, biomes, pyramid, cx, cz, chunkBiome,
                            seed ^ FLOWERS_SEED, 0.25f, 7, 3, FOREST_FLOWERS, false);
                } else if (chunkBiome == BiomeType.FLOWER_FOREST) {
                    for (int patch = 0; patch < 3; patch++) {
                        generateFlowerPatch(world, heights, biomes, pyramid, cx, cz, chunkBiome,
                                (seed ^ FLOWERS_SEED) + patch * 31L, 1.0f, 10, 5, FLOWERS, true);
                    }
                }
            }
        }

        // Lượt 2: Sinh cỏ ngắn, xương rồng, bụi khô và mía theo mật độ chuẩn từng biome.
        for (int cx = 0; cx < World.WORLD_CHUNKS_X; cx++) {
            for (int cz = 0; cz < World.WORLD_CHUNKS_Z; cz++) {
                BiomeType chunkBiome = biomeAt(biomes, cx * Chunk.SIZE + 8, cz * Chunk.SIZE + 8);
                if (chunkBiome == BiomeType.PLAINS) {
                    generateShortGrassPatches(world, heights, biomes, pyramid, cx, cz, chunkBiome, 4, 32, 5);
                    generateSugarCanePatch(world, heights, biomes, pyramid, cx, cz, chunkBiome, 1f / 6f);
                } else if (chunkBiome == BiomeType.FOREST || chunkBiome == BiomeType.FLOWER_FOREST) {
                    generateShortGrassPatches(world, heights, biomes, pyramid, cx, cz, chunkBiome, 2, 12, 4);
                    generateSugarCanePatch(world, heights, biomes, pyramid, cx, cz, chunkBiome, 1f / 6f);
                } else if (chunkBiome == BiomeType.DESERT) {
                    generateDesertPlants(world, heights, biomes, pyramid, cx, cz);
                    generateSugarCanePatch(world, heights, biomes, pyramid, cx, cz, chunkBiome, 1.0f);
                }
            }
        }
    }

    /** Sinh một cụm hoa theo trọng số bảng loài hoặc theo trường gradient 2D ở Flower Forest. */
    private void generateFlowerPatch(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                                     int cx, int cz, BiomeType chunkBiome, long featureSeed,
                                     float chunkChance, int attempts, int spread,
                                     Block[] palette, boolean gradient) {
        if (chunkChance < 1.0f && TerrainNoise.unit(featureSeed, cx, cz) >= chunkChance) {
            return;
        }
        long centerHash = chunkRoll(featureSeed, cx, cz, 1);
        int originX = cx * Chunk.SIZE + (int) (centerHash & 15);
        int originZ = cz * Chunk.SIZE + (int) ((centerHash >>> 4) & 15);
        int baseIndex = (int) ((((centerHash >>> 8) & 0xFFFFL) * palette.length) >>> 16);
        Block baseFlower = palette[baseIndex];
        for (int attempt = 0; attempt < attempts; attempt++) {
            long h = chunkRoll(featureSeed, cx, cz, attempt + 2);
            int x = originX + triangularOffset(h, 0, spread);
            int z = originZ + triangularOffset(h, 16, spread);
            int y = canPlaceGroundPlant(world, heights, biomes, pyramid, x, z, chunkBiome);
            if (y < 0 || !isGrassSoil(world.getBlock(x, y - 1, z))) {
                continue;
            }
            Block flower;
            if (gradient) {
                float field = TerrainNoise.perlin(seed ^ FLOWER_GRADIENT, x / 42f, z / 42f);
                float normalized = Math.max(0f, Math.min(0.9999f, (field + 0.42f) / 0.84f));
                flower = palette[(int) (normalized * palette.length)];
            } else {
                float pick = ((h >>> 32) & 0xFFFFL) * 0x1.0p-16f;
                if (pick < 0.70f) {
                    flower = baseFlower;
                } else {
                    int alt = (int) ((((h >>> 48) & 0xFFFFL) * palette.length) >>> 16);
                    flower = palette[alt];
                }
            }
            write(world, x, y, z, flower);
        }
    }

    /** Sinh cụm cỏ cao hai ô ở Plains với rarity 1/5 chunk. */
    private void generateTallGrassPatch(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                                        int cx, int cz, BiomeType chunkBiome) {
        long featureSeed = seed ^ TALL_GRASS_SEED;
        if (TerrainNoise.unit(featureSeed, cx, cz) >= 0.2f) {
            return;
        }
        long centerHash = chunkRoll(featureSeed, cx, cz, 1);
        int originX = cx * Chunk.SIZE + (int) (centerHash & 15);
        int originZ = cz * Chunk.SIZE + (int) ((centerHash >>> 4) & 15);
        for (int attempt = 0; attempt < 36; attempt++) {
            long h = chunkRoll(featureSeed, cx, cz, attempt + 2);
            int x = originX + triangularOffset(h, 0, 5);
            int z = originZ + triangularOffset(h, 16, 5);
            int y = canPlaceGroundPlant(world, heights, biomes, pyramid, x, z, chunkBiome);
            if (y < 0 || y + 1 >= World.WORLD_SIZE_Y
                    || !isGrassSoil(world.getBlock(x, y - 1, z))
                    || world.getBlock(x, y + 1, z) != Block.AIR.getId()) {
                continue;
            }
            write(world, x, y, z, Block.TALL_GRASS);
            world.setBlockInternal(x, y + 1, z, Block.TALL_GRASS.getId(), 1);
        }
    }

    /** Sinh các cụm cỏ ngắn trên mặt cỏ trống theo số patch và lần thử của từng biome. */
    private void generateShortGrassPatches(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                                           int cx, int cz, BiomeType chunkBiome,
                                           int patches, int attemptsPerPatch, int spread) {
        long featureSeed = seed ^ GRASS_SEED;
        for (int patch = 0; patch < patches; patch++) {
            int saltBase = patch * (attemptsPerPatch + 1) + 1;
            long centerHash = chunkRoll(featureSeed, cx, cz, saltBase);
            int originX = cx * Chunk.SIZE + (int) (centerHash & 15);
            int originZ = cz * Chunk.SIZE + (int) ((centerHash >>> 4) & 15);
            for (int attempt = 0; attempt < attemptsPerPatch; attempt++) {
                long h = chunkRoll(featureSeed, cx, cz, saltBase + 1 + attempt);
                int x = originX + triangularOffset(h, 0, spread);
                int z = originZ + triangularOffset(h, 16, spread);
                int y = canPlaceGroundPlant(world, heights, biomes, pyramid, x, z, chunkBiome);
                if (y >= 0 && isGrassSoil(world.getBlock(x, y - 1, z))) {
                    write(world, x, y, z, Block.SHORT_GRASS);
                }
            }
        }
    }

    /** Sinh xương rồng (1/5 chunk, chiều cao biased_to_bottom) và 2 bụi khô mỗi chunk ở Desert. */
    private void generateDesertPlants(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                                      int cx, int cz) {
        long cactusSeed = seed ^ CACTUS_SEED;
        if (TerrainNoise.unit(cactusSeed, cx, cz) < 0.2f) {
            long h = chunkRoll(cactusSeed, cx, cz, 1);
            int x = cx * Chunk.SIZE + (int) (h & 15);
            int z = cz * Chunk.SIZE + (int) ((h >>> 4) & 15);
            int y = canPlaceGroundPlant(world, heights, biomes, pyramid, x, z, BiomeType.DESERT);
            if (y >= 0 && Block.CACTUS.canPlaceAt(world, x, y, z)) {
                float roll = ((h >>> 8) & 0xFFFFFFL) * 0x1.0p-24f;
                int height = roll < 0.56f ? 1 : roll < 0.84f ? 2 : 3;
                growColumn(world, x, y, z, height, Block.CACTUS);
            }
        }
        long bushSeed = seed ^ DEAD_BUSH_SEED;
        for (int patch = 0; patch < 2; patch++) {
            long h = chunkRoll(bushSeed, cx, cz, patch + 1);
            int x = cx * Chunk.SIZE + (int) (h & 15);
            int z = cz * Chunk.SIZE + (int) ((h >>> 4) & 15);
            int y = canPlaceGroundPlant(world, heights, biomes, pyramid, x, z, BiomeType.DESERT);
            if (y >= 0 && Block.DEAD_BUSH.canPlaceAt(world, x, y, z)) {
                write(world, x, y, z, Block.DEAD_BUSH);
            }
        }
    }

    /** Sinh cụm mía dọc bờ sông ấm: 1/6 chunk ở biome ôn hòa và 1 lượt/chunk ở Desert. */
    private void generateSugarCanePatch(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                                        int cx, int cz, BiomeType chunkBiome, float chunkChance) {
        long caneSeed = seed ^ SUGAR_CANE_SEED;
        if (chunkChance < 1.0f && TerrainNoise.unit(caneSeed, cx, cz) >= chunkChance) {
            return;
        }
        long centerHash = chunkRoll(caneSeed, cx, cz, 1);
        int originX = cx * Chunk.SIZE + (int) (centerHash & 15);
        int originZ = cz * Chunk.SIZE + (int) ((centerHash >>> 4) & 15);
        for (int attempt = 0; attempt < 20; attempt++) {
            long h = chunkRoll(caneSeed, cx, cz, attempt + 2);
            int x = originX + triangularOffset(h, 0, 4);
            int z = originZ + triangularOffset(h, 16, 4);
            int y = canPlaceGroundPlant(world, heights, biomes, pyramid, x, z, chunkBiome);
            if (y >= 0 && Block.SUGAR_CANE.canPlaceAt(world, x, y, z)) {
                float roll = ((h >>> 32) & 0xFFFFFFL) * 0x1.0p-24f;
                int height = roll < 0.50f ? 2 : roll < 0.83f ? 3 : 4;
                growColumn(world, x, y, z, height, Block.SUGAR_CANE);
            }
        }
    }

    /** Kiểm tra biên, vùng an toàn, kim tự tháp, đúng biome và ô trống tại mặt đất; trả -1 nếu không hợp lệ. */
    private static int canPlaceGroundPlant(World world, short[] heights, byte[] biomes, DesertPyramid pyramid,
                                           int x, int z, BiomeType expectedBiome) {
        if (x < 0 || x >= WORLD_SIZE_X || z < 0 || z >= WORLD_SIZE_Z
                || nearSpawn(x, z, SPAWN_CLEARANCE)
                || pyramid.contains(x, z, 0)
                || biomeAt(biomes, x, z) != expectedBiome) {
            return -1;
        }
        int y = heights[columnIndex(x, z)] + 1;
        if (y >= World.WORLD_SIZE_Y || world.getBlock(x, y, z) != Block.AIR.getId()) {
            return -1;
        }
        return y;
    }

    /** Kiểm tra nền cỏ hoặc đất hợp lệ cho hoa và cỏ đồng bằng/rừng. */
    private static boolean isGrassSoil(byte soil) {
        return soil == Block.GRASS.getId() || soil == Block.DIRT.getId();
    }

    /** Băm tọa độ chunk và muối phụ thành giá trị 64-bit tất định không cấp phát bộ nhớ. */
    private static long chunkRoll(long featureSeed, int cx, int cz, int salt) {
        return TerrainNoise.hash(featureSeed ^ (salt * 0x9E3779B97F4A7C15L), cx, cz);
    }

    /** Tạo độ lệch tam giác [-spread, +spread] từ 16 bit băm để mô phỏng phân tán cụm Minecraft. */
    private static int triangularOffset(long hash, int shift, int spread) {
        int range = spread + 1;
        int first = (int) ((((hash >>> shift) & 0xFFL) * range) >>> 8);
        int second = (int) ((((hash >>> (shift + 8)) & 0xFFL) * range) >>> 8);
        return first - second;
    }

    /** Sinh từng đoạn liên tục từ gốc và áp dụng cùng luật sinh tồn như lúc chơi. */
    private static void growColumn(World world, int x, int y, int z, int height, Block plant) {
        for (int offset = 0; offset < height; offset++) {
            int partY = y + offset;
            if (partY >= World.WORLD_SIZE_Y
                    || world.getBlock(x, partY, z) != Block.AIR.getId()
                    || !plant.canPlaceAt(world, x, partY, z)) {
                return;
            }
            write(world, x, partY, z, plant);
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

    /** Tra mã biome của một cột, bỏ qua cờ phát hiện Lava, sông và hồ. */
    private static BiomeType biomeAt(byte[] biomes, int x, int z) {
        return BIOMES[biomes[columnIndex(x, z)] & BIOME_MASK];
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
