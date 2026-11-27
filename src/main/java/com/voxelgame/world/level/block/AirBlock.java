package com.voxelgame.world.level.block;

import com.voxelgame.world.level.World;

/**
 * Khối Không khí (ID = 0): Hoàn toàn trong suốt, không chặn va chạm vật lý và không thể đập vỡ.
 */
public class AirBlock extends Block {

    public AirBlock() {
        super(
                (byte) 0,
                "Air",
                -1.0f,
                false,
                false,
                false,
                new float[]{0.0f, 0.0f, 0.0f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return 0;
    }

    @Override
    public void onBreak(World world, int x, int y, int z) {
        // Không khí không thể bị phá hủy và không rơi ra vật phẩm
    }
}
