package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Khối Gravel của các biome mới. */
final class GravelBlock extends Block {
    /** Khởi tạo khối với kiểu vật liệu và độ cứng cố định. */
    GravelBlock() {
        super((byte) 10, "Gravel", 0.90f, true, false, true, new float[]{0.48f, 0.45f, 0.43f});
    }

    /** Dùng một tile cho cả sáu mặt. */
    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_GRAVEL;
    }

    /** Khối rơi khi không còn va chạm nâng đỡ ở ô dưới. */
    @Override
    public boolean isGravityBlock() {
        return true;
    }
}
