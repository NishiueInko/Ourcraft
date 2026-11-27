package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;

/**
 * Khối Đá (ID = 3): Phân bố ở tầng địa chất sâu, độ cứng 5.0 giây khi đập bằng
 * tay không.
 */
public class StoneBlock extends SolidBlock {

    public StoneBlock() {
        super(
                (byte) 3,
                "Stone",
                5.0f,
                Texture.TILE_STONE,
                new float[] { 0.50f, 0.50f, 0.50f });
    }
}
