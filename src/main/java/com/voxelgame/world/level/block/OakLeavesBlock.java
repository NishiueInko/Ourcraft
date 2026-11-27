package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;

/**
 * Khối Lá Cây Sồi (ID = 6): Tạo tán cây trên đỉnh thân gỗ sồi, độ cứng 0.30 giây (đập vỡ rất nhanh).
 */
public class OakLeavesBlock extends LeavesBlock {

    public OakLeavesBlock() {
        super(
                (byte) 6,
                "Oak Leaves",
                0.30f,
                Texture.TILE_OAK_LEAVES,
                new float[]{0.20f, 0.52f, 0.15f}
        );
    }
}
