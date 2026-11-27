package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Khối Spruce Leaves của các biome mới. */
public final class SpruceLeavesBlock extends LeavesBlock {
    /** Khởi tạo khối với kiểu vật liệu và độ cứng cố định. */
    SpruceLeavesBlock() {
        super((byte) 25, "Spruce Leaves", 0.30f, Texture.TILE_SPRUCE_LEAVES, new float[]{0.26f, 0.48f, 0.28f});
    }
}
