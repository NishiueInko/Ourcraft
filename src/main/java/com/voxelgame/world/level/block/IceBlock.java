package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Băng là khối trơn bán trong suốt, có thể thành nước khi vỡ. */
final class IceBlock extends Block {
    /** Đăng ký băng với độ cứng và màu lạnh. */
    IceBlock() {
        super((byte) 23, "Ice", 0.75f, true, false, false, new float[]{0.56f, 0.76f, 0.95f});
    }

    /** Dùng một tile cho tất cả mặt băng. */
    @Override
    public int getTileIndex(Face face) {
        return Texture.TILE_ICE;
    }

    /** Băng được hòa trộn sau lớp đục. */
    @Override
    public RenderLayer getRenderLayer() {
        return RenderLayer.TRANSLUCENT;
    }

    /** Băng giữ đà ngang mạnh hơn nền thường. */
    @Override
    public float getFriction() {
        return 0.98f;
    }

    /** Đập có nền thì để lại nước, còn ở lơ lửng thì để lại không khí. */
    @Override
    public Block getBreakReplacement(World world, int x, int y, int z) {
        Block below = fromId(world.getBlock(x, y - 1, z));
        return below == WATER || below.getCollisionBounds(world.getBlockState(x, y - 1, z)) != null
                ? WATER : AIR;
    }

    /** Đập tay không không sinh vật phẩm băng. */
    @Override
    public void onBreak(World world, int x, int y, int z) {
    }
}
