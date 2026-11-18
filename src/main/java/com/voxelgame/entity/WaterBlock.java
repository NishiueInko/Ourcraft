package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/**
 * Khối Nước (ID = 7): Là chất lỏng (`solid = false`, `liquid = true`, `opaque = false`),
 * cho phép người chơi bơi xuyên qua và tia Raycast đi xuyên qua để tương tác với lòng hồ.
 * Ghi đè `getTopHeight` trả về `0.88f` khi phía trên là không khí để tạo mặt hồ thấp hơn bờ cỏ.
 */
public class WaterBlock extends Block {

    public WaterBlock() {
        super(
                (byte) 7,
                "Water",
                -1.0f,
                false,
                true,
                false,
                new float[]{0.20f, 0.45f, 0.88f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_WATER;
    }

    @Override
    public void onBreak(World world, int x, int y, int z) {
        // Chất lỏng không thể bị đập vỡ bằng tay không
    }

    @Override
    public float getTopHeight(World world, int wx, int wy, int wz) {
        if (world != null && world.getBlock(wx, wy + 1, wz) == Block.AIR.getId()) {
            return 0.88f;
        }
        return 1.0f;
    }
}
