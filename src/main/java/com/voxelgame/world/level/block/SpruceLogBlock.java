package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Khối Spruce Log có tile riêng cho các mặt. */
final class SpruceLogBlock extends LogBlock {
    /** Khởi tạo vật liệu rắn và đục. */
    SpruceLogBlock() {
        super((byte) 24, "Spruce Log", 3.0f,
                Texture.TILE_SPRUCE_LOG_TOP, Texture.TILE_SPRUCE_WOOD,
                new float[]{0.39f, 0.27f, 0.16f});
    }
}
