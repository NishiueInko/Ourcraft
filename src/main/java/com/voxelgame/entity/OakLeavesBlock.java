package com.voxelgame.entity;

import com.voxelgame.engine.Texture;

/**
 * Khối Lá Cây Sồi (ID = 6): Tạo tán cây trên đỉnh thân gỗ sồi, độ cứng 0.30 giây (đập vỡ rất nhanh).
 */
public class OakLeavesBlock extends Block {

    public OakLeavesBlock() {
        super(
                (byte) 6,
                "Oak Leaves",
                0.30f,
                true,
                false,
                false,
                new float[]{0.20f, 0.52f, 0.15f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_OAK_LEAVES;
    }

    /** Lá giữ vùng trong suốt giữa các cành. */
    @Override
    public RenderLayer getRenderLayer() {
        return RenderLayer.CUTOUT;
    }
}
