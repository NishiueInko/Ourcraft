package com.voxelgame.world.level.block;

/**
 * Lớp trừu tượng trung gian cho các khối thân gỗ ({@code Oak Log}, {@code Spruce Log}).
 * Gom chung ánh xạ 2 họa tiết: vân gỗ tròn cho mặt trên/đáy và vỏ cây dọc cho 4 mặt bên.
 */
public abstract class LogBlock extends SolidBlock {

    /** Chỉ số tile mặt cắt gỗ (TOP và BOTTOM). */
    private final int topBottomTile;
    /** Chỉ số tile vỏ cây 4 mặt bên (NORTH, SOUTH, EAST, WEST). */
    private final int sideTile;

    /**
     * Khởi tạo khối thân gỗ với độ cứng và cặp tile mặt cắt / vỏ cây.
     *
     * @param id            mã định danh block
     * @param displayName   tên hiển thị trên giao diện
     * @param hardness      độ cứng khi đào tay (giây)
     * @param topBottomTile chỉ số tile mặt trên và mặt đáy
     * @param sideTile      chỉ số tile 4 mặt bên
     * @param iconColor     màu RGB đại diện
     */
    protected LogBlock(byte id, String displayName, float hardness,
                       int topBottomTile, int sideTile, float[] iconColor) {
        super(id, displayName, hardness, iconColor);
        this.topBottomTile = topBottomTile;
        this.sideTile = sideTile;
    }

    /** Trả về tile vân gỗ cho mặt trên/đáy và tile vỏ cây cho 4 mặt bên. */
    @Override
    public int getTileIndex(Face face) {
        return switch (face) {
            case TOP, BOTTOM -> topBottomTile;
            case NORTH, SOUTH, EAST, WEST -> sideTile;
        };
    }
}
