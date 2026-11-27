package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/**
 * Khối Nước (ID = 7): Là chất lỏng (`solid = false`, `liquid = true`, `opaque = false`),
 * cho phép người chơi bơi xuyên qua và tia Raycast đi xuyên qua để tương tác với lòng hồ.
 * Ghi đè `getTopHeight` trả về `0.88f` khi phía trên là không khí để tạo mặt hồ thấp hơn bờ cỏ.
 */
public class WaterBlock extends LiquidBlock {

    public WaterBlock() {
        super(
                (byte) 7,
                "Water",
                Texture.TILE_WATER,
                new float[]{0.20f, 0.45f, 0.88f}
        );
    }
}
