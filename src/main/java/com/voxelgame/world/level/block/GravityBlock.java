package com.voxelgame.world.level.block;

/**
 * Lớp trừu tượng trung gian cho các khối rắn chịu tác dụng trọng lực ({@code Sand}, {@code Gravel}).
 * Tự động đánh dấu {@link #isGravityBlock()} trả về {@code true} để kích hoạt rơi thành {@code FallingBlockEntity}
 * khi mất mặt đỡ bên dưới.
 */
public abstract class GravityBlock extends SolidBlock {

    /**
     * Khởi tạo khối chịu trọng lực với cùng một ô texture cho cả 6 mặt.
     *
     * @param id          mã định danh block
     * @param displayName tên hiển thị trên giao diện
     * @param hardness    độ cứng khi đào tay (giây)
     * @param uniformTile chỉ số tile trong Texture Atlas
     * @param iconColor   màu RGB đại diện
     */
    protected GravityBlock(byte id, String displayName, float hardness, int uniformTile, float[] iconColor) {
        super(id, displayName, hardness, uniformTile, iconColor);
    }

    /** Khối rơi theo trọng lực khi không còn va chạm nâng đỡ ở ô bên dưới. */
    @Override
    public boolean isGravityBlock() {
        return true;
    }
}
