package com.voxelgame.entity;

import com.voxelgame.engine.Texture;

/**
 * Khối Đá (ID = 3): Phân bố ở tầng địa chất sâu, độ cứng 5.0 giây khi đập bằng tay không.
 */
public class StoneBlock extends Block {

    public StoneBlock() {
        super(
                (byte) 3,
                "Stone",
                5.0f,
                true,
                false,
                true,
                new float[]{0.50f, 0.50f, 0.50f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_STONE;
    }
}
