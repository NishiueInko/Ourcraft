package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Khối Sandstone có tile riêng cho các mặt. */
final class SandstoneBlock extends Block {
    /** Khởi tạo vật liệu rắn và đục. */
    SandstoneBlock() {
        super((byte) 11, "Sandstone", 4.0f, true, false, true, new float[]{0.83f, 0.74f, 0.54f});
    }

    /** Chọn tile theo mặt đang dựng. */
    @Override
    public int getTileIndex(Face face) {
        return switch (face) {
            case TOP -> Texture.TILE_SANDSTONE_TOP;
            case BOTTOM -> Texture.TILE_SANDSTONE_BOTTOM;
            default -> Texture.TILE_SANDSTONE;
        };
    }
}
