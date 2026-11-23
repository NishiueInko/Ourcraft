package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Khối Snow Block của các biome mới. */
final class SnowBlock extends Block {
    /** Khởi tạo khối với kiểu vật liệu và độ cứng cố định. */
    SnowBlock() {
        super((byte) 22, "Snow Block", 0.35f, true, false, true, new float[]{0.94f, 0.96f, 1.0f});
    }

    /** Dùng một tile cho cả sáu mặt. */
    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_SNOW;
    }
}
