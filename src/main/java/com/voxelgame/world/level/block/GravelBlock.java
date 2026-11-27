package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Khối Gravel của các biome mới. */
final class GravelBlock extends GravityBlock {
    /** Khởi tạo khối với kiểu vật liệu và độ cứng cố định. */
    GravelBlock() {
        super((byte) 10, "Gravel", 0.90f, Texture.TILE_GRAVEL, new float[]{0.48f, 0.45f, 0.43f});
    }
}
