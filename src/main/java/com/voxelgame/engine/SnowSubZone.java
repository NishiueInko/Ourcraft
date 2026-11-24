package com.voxelgame.engine;

import com.voxelgame.entity.Block;

/** Phân vùng địa hình trong biome tuyết để chọn bảng sinh thú. */
public enum SnowSubZone {
    /** Đồng bằng thấp, bao gồm mặt hồ đóng băng và vùng trống không có vân sam. */
    SNOWY_PLAINS_FLAT,
    /** Rừng vân sam ở độ cao trung bình. */
    SNOWY_GROVE,
    /** Sườn đá có chênh cao hoặc vùng núi từ độ cao 62 trở lên. */
    SNOWY_SLOPES_PEAKS;

    /** Ngưỡng cao độ chân, tính theo block, để phân biệt đồng bằng, sườn và đỉnh. */
    private static final int GROVE_MIN_Y = 30;
    private static final int ROCKY_SLOPE_MIN_Y = 48;
    private static final int PEAK_MIN_Y = 62;
    /** TerrainGenerator chọn đá hoặc sỏi khi chênh cột chung cạnh từ hai block. */
    private static final int MIN_RELIEF = 2;
    private static final int GROUND_PROBE_DEPTH = 4;
    /** Miền tìm vân sam là cầu bán kính 10 block, quanh vị trí chân của mob. */
    private static final int SPRUCE_RADIUS = 10;
    private static final int SPRUCE_RADIUS_SQUARED = SPRUCE_RADIUS * SPRUCE_RADIUS;
    private static final int[] SPRUCE_VERTICAL_REACH = buildVerticalReach();

    /** Phân loại điểm sinh trong biome tuyết, với y là ô chân ngay phía trên nền. */
    public static SnowSubZone classify(World world, int x, int y, int z) {
        if (y >= PEAK_MIN_Y || y >= ROCKY_SLOPE_MIN_Y && hasRockySlope(world, x, y - 1, z)) {
            return SNOWY_SLOPES_PEAKS;
        }
        if (y >= GROVE_MIN_Y && hasNearbySpruce(world, x, y, z)) {
            return SNOWY_GROVE;
        }
        return SNOWY_PLAINS_FLAT;
    }

    /** Đọc đá gốc dưới tuyết mỏng rồi tìm chênh cao ở bốn cột chung cạnh. */
    private static boolean hasRockySlope(World world, int x, int groundY, int z) {
        byte ground = world.getBlock(x, groundY, z);
        if (ground == Block.SNOW_LAYER.getId()) {
            groundY--;
            ground = world.getBlock(x, groundY, z);
        }
        if (ground != Block.STONE.getId() && ground != Block.GRAVEL.getId() && ground != Block.SNOW_BLOCK.getId()) {
            return false;
        }
        return hasRelief(world, x - 1, groundY, z) || hasRelief(world, x + 1, groundY, z)
                || hasRelief(world, x, groundY, z - 1) || hasRelief(world, x, groundY, z + 1);
    }

    /** Bỏ thân cây, tán lá và ô trống khi tìm mặt đất gần cao độ của ứng viên. */
    private static boolean hasRelief(World world, int x, int groundY, int z) {
        for (int y = groundY + GROUND_PROBE_DEPTH; y >= groundY - GROUND_PROBE_DEPTH; y--) {
            byte ground = world.getBlock(x, y, z);
            if (ground == Block.STONE.getId() || ground == Block.GRAVEL.getId() || ground == Block.SNOW_BLOCK.getId()
                    || ground == Block.GRASS.getId() || ground == Block.DIRT.getId() || ground == Block.ICE.getId()) {
                return Math.abs(y - groundY) >= MIN_RELIEF;
            }
        }
        return false;
    }

    /** Tính trước khoảng quét dọc sau khi biết bình phương khoảng cách ngang. */
    private static int[] buildVerticalReach() {
        int[] reach = new int[SPRUCE_RADIUS_SQUARED + 1];
        for (int i = 0; i <= SPRUCE_RADIUS_SQUARED; i++) {
            reach[i] = (int) Math.sqrt(SPRUCE_RADIUS_SQUARED - i);
        }
        return reach;
    }

    /** Quét từng cột trong cầu, dừng ngay khi tìm thấy thân hoặc lá vân sam. */
    private static boolean hasNearbySpruce(World world, int x, int y, int z) {
        for (int dx = -SPRUCE_RADIUS; dx <= SPRUCE_RADIUS; dx++) {
            int maxDz = SPRUCE_VERTICAL_REACH[dx * dx];
            for (int dz = -maxDz; dz <= maxDz; dz++) {
                int reach = SPRUCE_VERTICAL_REACH[dx * dx + dz * dz];
                for (int dy = -reach; dy <= reach; dy++) {
                    byte block = world.getBlock(x + dx, y + dy, z + dz);
                    if (block == Block.SPRUCE_LOG.getId() || block == Block.SPRUCE_LEAVES.getId()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
