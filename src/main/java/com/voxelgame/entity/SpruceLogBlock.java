package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Khối Spruce Log có tile riêng cho các mặt. */
final class SpruceLogBlock extends Block {
    /** Khởi tạo vật liệu rắn và đục. */
    SpruceLogBlock() {
        super((byte) 24, "Spruce Log", 3.0f, true, false, true, new float[]{0.39f, 0.27f, 0.16f});
    }

    /** Chọn tile theo mặt đang dựng. */
    @Override
    public int getTileIndex(Face face) {
        return switch (face) {
            case TOP -> Texture.TILE_SPRUCE_LOG_TOP;
            case BOTTOM -> Texture.TILE_SPRUCE_LOG_TOP;
            default -> Texture.TILE_SPRUCE_WOOD;
        };
    }
}
