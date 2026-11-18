package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/**
 * Khối Dung nham Lava (ID = 8): Là chất lỏng (`solid = false`, `liquid = true`, `opaque = false`).
 * Ghi đè `getTopHeight` trả về `0.88f` khi phía trên là không khí.
 */
public class LavaBlock extends Block {

    public LavaBlock() {
        super(
                (byte) 8,
                "Lava",
                -1.0f,
                false,
                true,
                false,
                new float[]{0.92f, 0.42f, 0.10f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_LAVA;
    }

    @Override
    public void onBreak(World world, int x, int y, int z) {
        // Dung nham không thể bị đập vỡ bằng tay không
    }

    @Override
    public float getTopHeight(World world, int wx, int wy, int wz) {
        if (world != null && world.getBlock(wx, wy + 1, wz) == Block.AIR.getId()) {
            return 0.88f;
        }
        return 1.0f;
    }
}
