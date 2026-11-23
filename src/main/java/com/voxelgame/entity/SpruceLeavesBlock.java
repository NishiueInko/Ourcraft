package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/** Khối Spruce Leaves của các biome mới. */
final class SpruceLeavesBlock extends Block {
    /** Khởi tạo khối với kiểu vật liệu và độ cứng cố định. */
    SpruceLeavesBlock() {
        super((byte) 25, "Spruce Leaves", 0.30f, true, false, false, new float[]{0.26f, 0.48f, 0.28f});
    }

    /** Dùng một tile cho cả sáu mặt. */
    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_SPRUCE_LEAVES;
    }

    /** Giữ các khe trong tán lá. */
    @Override
    public RenderLayer getRenderLayer() {
        return RenderLayer.CUTOUT;
    }
}
