package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/**
 * Khối Đá Nền Bedrock (ID = 4): Nằm ở tầng đáy cùng (y = 0) của thế giới, độ cứng -1.0f (không thể phá hủy).
 */
public class BedrockBlock extends Block {

    public BedrockBlock() {
        super(
                (byte) 4,
                "Bedrock",
                -1.0f,
                true,
                false,
                true,
                new float[]{0.25f, 0.25f, 0.25f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_BEDROCK;
    }

    @Override
    public void onBreak(World world, int x, int y, int z) {
        // Bedrock không thể bị phá hủy trong mọi trường hợp
    }
}
