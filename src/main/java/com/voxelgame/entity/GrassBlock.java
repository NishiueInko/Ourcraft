package com.voxelgame.entity;

import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/**
 * Khối Cỏ (ID = 2): Thể hiện rõ nét Tính Đa hình (Polymorphism) trong OOP:
 * 1. Đa hình hiển thị (`getTileIndex`): Mặt trên là cỏ xanh (`TILE_GRASS_TOP`), mặt đáy là đất (`TILE_DIRT`),
 *    4 mặt xung quanh là viền cỏ + đất (`TILE_GRASS_SIDE`).
 * 2. Đa hình hành vi (`onBreak`): Khi bị đập vỡ bằng tay không, khối cỏ sẽ làm rơi ra khối Đất (`Block.DIRT`)
 *    đúng theo cơ chế chuẩn của Minecraft gốc.
 */
public class GrassBlock extends Block {

    public GrassBlock() {
        super(
                (byte) 2,
                "Grass Block",
                0.75f,
                true,
                false,
                true,
                new float[]{0.32f, 0.68f, 0.22f}
        );
    }

    @Override
    public int getTileIndex(Face face) {
        return switch (face) {
            case TOP -> Texture.TILE_GRASS_TOP;
            case BOTTOM -> Texture.TILE_DIRT;
            case NORTH, SOUTH, EAST, WEST -> Texture.TILE_GRASS_SIDE;
        };
    }

    @Override
    public void onBreak(World world, int x, int y, int z) {
        if (world != null) {
            // Đập vỡ khối cỏ bằng tay không sẽ rơi ra khối Đất (Block.DIRT)
            world.spawnDroppedItem(new ItemEntity(x + 0.5f, y + 0.5f, z + 0.5f, Block.DIRT));
        }
    }
}
