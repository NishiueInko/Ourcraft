package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/**
 * Khối Đá Nền Bedrock (ID = 4): Nằm ở tầng đáy cùng (y = 0) của thế giới, độ cứng -1.0f (không thể phá hủy).
 */
public class BedrockBlock extends SolidBlock {

    public BedrockBlock() {
        super(
                (byte) 4,
                "Bedrock",
                -1.0f,
                Texture.TILE_BEDROCK,
                new float[]{0.25f, 0.25f, 0.25f}
        );
    }

    @Override
    public void onBreak(World world, int x, int y, int z) {
        // Bedrock không thể bị phá hủy trong mọi trường hợp
    }
}
