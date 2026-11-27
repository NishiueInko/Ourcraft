package com.voxelgame.world.level.gen;

import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;
import static com.voxelgame.world.level.World.BIOME_WIDTH;
import static com.voxelgame.world.level.World.CENTER_Z;
import static com.voxelgame.world.level.World.WORLD_SIZE_X;
import static com.voxelgame.world.level.World.WORLD_SIZE_Z;

/** Dấu chân và cách dựng kim tự tháp sa mạc có sảnh nổi, cầu thang và phòng ngầm. */
public record DesertPyramid(int centerX, int centerZ, int baseY) {
    /** Nửa bề rộng thân vuông ở tầng thấp nhất. */
    private static final int BASE_RADIUS = 8;
    /** Bán kính dành cho thân công trình, chân tường và bậc dẫn bên ngoài. */
    private static final int SITE_RADIUS = 11;
    /** Chiều cao từ mặt sàn đến đỉnh, tính theo số lớp voxel. */
    private static final int ROOF_HEIGHT = 16;
    /** Độ sâu mặt sàn phòng ngầm so với sảnh. */
    private static final int CELLAR_DEPTH = 8;
    /** Bước giữa những tâm ứng viên để giữ số cột cần kiểm tra thấp. */
    private static final int SITE_STEP = 13;
    /** Ba bit thấp là mã biome, cờ sông nằm ở bit 4. */
    private static final int BIOME_AND_RIVER_MASK = TerrainGenerator.BIOME_MASK | TerrainGenerator.RIVER;

    /** Chọn một khu đất khô, ưu tiên thềm thấp, phẳng và gần mép rừng hoa. */
    public static DesertPyramid select(short[] heights, byte[] biomes) {
        int desert = BiomeType.DESERT.ordinal();
        int boundary = 4 * BIOME_WIDTH;
        int bestScore = Integer.MAX_VALUE;
        DesertPyramid best = null;
        for (int x = boundary - 96; x < WORLD_SIZE_X - SITE_RADIUS; x += SITE_STEP) {
            for (int z = SITE_RADIUS + 1; z < WORLD_SIZE_Z - SITE_RADIUS; z += SITE_STEP) {
                int range = drySiteRange(heights, biomes, desert, x, z);
                if (range == 0) {
                    continue;
                }
                int lowest = range & 0xFFFF;
                int highest = range >>> 16;
                int score = (highest - lowest) * 22 + Math.abs(highest - 32) * 9
                        + Math.abs(x - boundary) / 8 + Math.abs(z - CENTER_Z) / 15;
                if (score < bestScore) {
                    bestScore = score;
                    best = new DesertPyramid(x, z, highest);
                }
            }
        }
        return best;
    }

    /** Trả độ cao thấp nhất và cao nhất trong dấu chân, hoặc 0 nếu có cột bất hợp lệ. */
    private static int drySiteRange(short[] heights, byte[] biomes, int desert, int centerX, int centerZ) {
        int highest = 0;
        int lowest = World.WORLD_SIZE_Y;
        for (int x = centerX - SITE_RADIUS; x <= centerX + SITE_RADIUS; x++) {
            int index = x * WORLD_SIZE_Z + centerZ - SITE_RADIUS;
            int afterLast = index + 2 * SITE_RADIUS + 1;
            for (; index < afterLast; index++) {
                int height = heights[index];
                if ((biomes[index] & BIOME_AND_RIVER_MASK) != desert
                        || height < CELLAR_DEPTH + 3 || height + ROOF_HEIGHT >= World.WORLD_SIZE_Y) {
                    return 0;
                }
                highest = Math.max(highest, height);
                lowest = Math.min(lowest, height);
            }
        }
        return highest << 16 | lowest;
    }

    /** Kiểm tra một cột có thuộc vùng dành riêng cho công trình, kể cả khoảng đệm. */
    public boolean contains(int x, int z, int margin) {
        return Math.abs(x - centerX) <= SITE_RADIUS + margin && Math.abs(z - centerZ) <= SITE_RADIUS + margin;
    }

    /** Ghép công trình vào địa tầng đã điền trước khi sinh hồ và thực vật. */
    public void build(World world) {
        buildFoundation(world);
        buildHallAndRoof(world);
        buildEntranceAndSupport(world);
    }

    /** Lấp thân móng, chỉ chừa khoảng trống có sàn và vách của cầu thang và phòng rương. */
    private void buildFoundation(World world) {
        int bottomY = baseY - CELLAR_DEPTH;
        for (int dx = -BASE_RADIUS; dx <= BASE_RADIUS; dx++) {
            for (int dz = -BASE_RADIUS; dz <= BASE_RADIUS; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                extendFooting(world, x, bottomY - 1, z);
                for (int y = bottomY; y <= baseY; y++) {
                    int level = y - baseY;
                    boolean stair = dx >= -4 && dx <= -3 && dz >= -3 && dz <= 4
                            && level > dz - 5 && level <= dz - 2;
                    boolean room = dx >= -2 && dx <= 5 && dz >= -5 && dz <= 0
                            && level > -CELLAR_DEPTH && level <= 3 - CELLAR_DEPTH;
                    Block material = stair || room ? Block.AIR : Block.SANDSTONE;
                    if (dx == 4 && dz == -4 && level == 1 - CELLAR_DEPTH) {
                        material = Block.CHEST;
                    }
                    write(world, x, y, z, material);
                }
            }
        }
    }

    /** Dựng các tầng giật cấp, sảnh bốn cột và khung đá ở cửa nam. */
    private void buildHallAndRoof(World world) {
        for (int layer = 1; layer <= ROOF_HEIGHT; layer++) {
            int radius = layer == ROOF_HEIGHT ? 0 : BASE_RADIUS - (layer - 1) / 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int absX = Math.abs(dx);
                    boolean pillar = absX == 2 && Math.abs(dz) == 3;
                    boolean altar = dx == 0 && dz == -4 && layer <= 2;
                    boolean hall = absX <= 5 && Math.abs(dz) <= 5 && layer <= 4 && !pillar && !altar;
                    boolean door = absX <= 1 && dz >= 5 && layer <= 3;
                    boolean pattern = dz == radius && ((layer <= 3 && absX == 2)
                            || (layer == 4 && absX <= 2));
                    Block material = Block.SANDSTONE;
                    if (hall || door) {
                        material = Block.AIR;
                    } else if (pattern || (altar && layer == 1)) {
                        material = Block.STONE;
                    }
                    write(world, centerX + dx, baseY + layer, centerZ + dz, material);
                }
            }
        }
    }

    /** Làm ba bậc phía nam và lót sandstone dưới các dải cát quanh chân tường. */
    private void buildEntranceAndSupport(World world) {
        for (int dz = BASE_RADIUS + 1; dz <= SITE_RADIUS; dz++) {
            int minimumStepY = baseY - (dz - BASE_RADIUS - 1);
            for (int dx = -2; dx <= 2; dx++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                // Nâng các nền thấp thành bậc, giữ cao độ tự nhiên nếu nền đã cao hơn.
                int stepY = baseY;
                while (stepY > minimumStepY && world.getBlock(x, stepY, z) == Block.AIR.getId()) {
                    stepY--;
                }
                extendFooting(world, x, stepY - 1, z);
                write(world, x, stepY, z, Block.SANDSTONE);
            }
        }
        int edge = BASE_RADIUS + 1;
        for (int offset = -edge; offset <= edge; offset++) {
            lineSand(world, centerX - edge, centerZ + offset);
            lineSand(world, centerX + edge, centerZ + offset);
            lineSand(world, centerX + offset, centerZ - edge);
            lineSand(world, centerX + offset, centerZ + edge);
        }
    }

    /** Lấp phần móng nằm trên lớp đá tự nhiên đầu tiên mà vẫn giữ Bedrock. */
    private static void extendFooting(World world, int x, int top, int z) {
        for (int y = top; y > 0; y--) {
            byte block = world.getBlock(x, y, z);
            if (block == Block.STONE.getId() || block == Block.SANDSTONE.getId()
                    || block == Block.BEDROCK.getId()) {
                return;
            }
            write(world, x, y, z, Block.SANDSTONE);
        }
    }

    /** Tìm đáy dải cát hoặc sỏi liền mặt đất rồi đặt lớp đỡ ngay dưới nó. */
    private void lineSand(World world, int x, int z) {
        int y = baseY;
        while (y > 1 && world.getBlock(x, y, z) == Block.AIR.getId()) {
            y--;
        }
        int top = y;
        while (y > 1 && Block.fromId(world.getBlock(x, y, z)).isGravityBlock()) {
            y--;
        }
        if (y < top) {
            write(world, x, y, z, Block.SANDSTONE);
            extendFooting(world, x, y - 1, z);
        }
    }

    /** Ghi ô trong lượt sinh với metadata mặc định và xử lý theo nhóm. */
    private static void write(World world, int x, int y, int z, Block block) {
        world.setBlockInternal(x, y, z, block.getId(), 0);
    }
}
