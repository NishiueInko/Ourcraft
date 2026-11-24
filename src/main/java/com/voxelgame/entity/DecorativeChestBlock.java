package com.voxelgame.entity;

import com.voxelgame.engine.Texture;

/** Rương trang trí bằng gỗ sơn, dùng hình khối và tương tác đào, đặt mặc định. */
public final class DecorativeChestBlock extends Block {
    /** Nối mã rương sau các mã khối đã dùng trong thế giới. */
    private static final byte ID = 26;
    /** Thời gian đào tay của khối gỗ trang trí, tính theo giây. */
    private static final float HARDNESS = 2.5f;

    /** Đăng ký rương là khối rắn đục có thể đào để nhận lại chính nó. */
    DecorativeChestBlock() {
        super(ID, "Decorative Chest", HARDNESS, true, false, true,
                new float[]{0.21f, 0.43f, 0.40f});
    }

    /** Hiển thị khóa trên mặt nam và hoa văn khảm trên nắp. */
    @Override
    public int getTileIndex(Face face) {
        return switch (face) {
            case SOUTH -> Texture.TILE_CHEST_FRONT;
            case TOP -> Texture.TILE_CHEST_TOP;
            case NORTH, EAST, WEST, BOTTOM -> Texture.TILE_CHEST_SIDE;
        };
    }
}
