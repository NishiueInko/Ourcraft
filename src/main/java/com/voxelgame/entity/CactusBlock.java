package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Xương rồng dùng hình thu hẹp và kiểm tra khoảng trống ngang. */
final class CactusBlock extends Block {
    /** Chừa 1/16 ô quanh bốn cạnh ngang. */
    private static final Bounds CACTUS_BOUNDS = new Bounds(0.0625f, 0, 0.0625f, 0.9375f, 1, 0.9375f);

    /** Khởi tạo xương rồng rắn với phần cạnh lộ ra. */
    CactusBlock() {
        super((byte) 12, "Cactus", 0.6f, true, false, false, new float[]{0.30f, 0.53f, 0.24f});
    }

    /** Lấy ảnh mặt cắt hoặc ảnh thân. */
    @Override
    public int getTileIndex(Face face) {
        return switch (face) {
            case TOP -> Texture.TILE_CACTUS_TOP;
            case BOTTOM -> Texture.TILE_CACTUS_BOTTOM;
            default -> Texture.TILE_CACTUS_SIDE;
        };
    }

    /** Hình kết xuất thu hẹp theo hộp va chạm. */
    @Override
    public GeometryType getGeometryType() {
        return GeometryType.CACTUS;
    }

    /** Hộp vật lý dùng chung cho tất cả ô xương rồng. */
    @Override
    public Bounds getCollisionBounds(int state) {
        return CACTUS_BOUNDS;
    }

    /** Tia chọn dùng cùng hộp với hình xương rồng. */
    @Override
    public Bounds getSelectionBounds(int state) {
        return CACTUS_BOUNDS;
    }

    /** Cần nền cát/xương rồng và không được sát khối rắn hoặc dung nham. */
    @Override
    public boolean canPlaceAt(World world, int x, int y, int z) {
        Block below = fromId(world.getBlock(x, y - 1, z));
        return (below == SAND || below == CACTUS)
                && isClear(world, x - 1, y, z) && isClear(world, x + 1, y, z)
                && isClear(world, x, y, z - 1) && isClear(world, x, y, z + 1);
    }

    /** Kiểm tra một ô ngang không chặn xương rồng. */
    private boolean isClear(World world, int x, int y, int z) {
        Block side = fromId(world.getBlock(x, y, z));
        return side != LAVA && side.getCollisionBounds(world.getBlockState(x, y, z)) == null;
    }
}
