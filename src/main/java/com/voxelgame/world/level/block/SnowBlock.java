package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Khối Snow Block của các biome mới. */
final class SnowBlock extends SolidBlock {
    /** Khởi tạo khối với kiểu vật liệu và độ cứng cố định. */
    SnowBlock() {
        super((byte) 22, "Snow Block", 0.35f, Texture.TILE_SNOW, new float[]{0.94f, 0.96f, 1.0f});
    }
}
