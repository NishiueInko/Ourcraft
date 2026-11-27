package com.voxelgame.world.level.block;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.World;

/** Hành vi và hình học dùng chung của thực vật hình chữ X. */
public abstract class PlantBlock extends Block {
    /** Hộp chọn ôm vùng hai mặt chéo. */
    private static final Bounds PLANT_BOUNDS = new Bounds(0.15f, 0, 0.15f, 0.85f, 1, 0.85f);
    /** Tile của cây khi nó không có nhiều nửa. */
    private final int tile;

    /** Khởi tạo thực vật không cản di chuyển và có thời gian phá rất ngắn. */
    PlantBlock(int id, String name, int tile) {
        super((byte) id, name, 0.05f, false, false, false, new float[]{0.45f, 0.67f, 0.25f});
        this.tile = tile;
    }

    /** Dùng một tile cho hai mặt phẳng. */
    @Override
    public int getTileIndex(Face face) {
        return tile;
    }

    /** Kết xuất hai mặt chéo có vùng khoét alpha. */
    @Override
    public GeometryType getGeometryType() {
        return GeometryType.CROSS_PLANT;
    }

    /** Bỏ pixel trong suốt ở kẽ thân và lá. */
    @Override
    public RenderLayer getRenderLayer() {
        return RenderLayer.CUTOUT;
    }

    /** Cho phép tia chọn thực vật dù nó không có va chạm. */
    @Override
    public Bounds getSelectionBounds(int state) {
        return PLANT_BOUNDS;
    }

    /** Nền thông thường của hoa và cỏ là Grass Block hoặc Dirt. */
    @Override
    public boolean canPlaceAt(World world, int x, int y, int z) {
        Block below = fromId(world.getBlock(x, y - 1, z));
        return below == GRASS || below == DIRT;
    }
}
