package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/**
 * Khối Dung nham Lava (ID = 8): Là chất lỏng (`solid = false`, `liquid = true`, `opaque = false`).
 * Ghi đè `getTopHeight` trả về `0.88f` khi phía trên là không khí.
 */
public class LavaBlock extends LiquidBlock {

    public LavaBlock() {
        super(
                (byte) 8,
                "Lava",
                Texture.TILE_LAVA,
                new float[]{0.92f, 0.42f, 0.10f}
        );
    }
}
