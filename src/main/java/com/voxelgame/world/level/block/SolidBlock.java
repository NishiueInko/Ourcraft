package com.voxelgame.world.level.block;

/**
 * Lớp trừu tượng trung gian cho các khối rắn đục chuẩn ({@code solid = true}, {@code liquid = false}, {@code opaque = true}).
 * Gom chung cấu hình vật liệu và hỗ trợ hai dạng: khối dùng chung một ô texture cho cả 6 mặt
 * hoặc khối nhiều mặt tự ghi đè {@link #getTileIndex(Face)}.
 */
public abstract class SolidBlock extends Block {

    /** Chỉ số ô texture dùng chung cho cả 6 mặt (hoặc -1 nếu lớp con tự quy định từng mặt). */
    private final int uniformTile;

    /**
     * Khởi tạo khối rắn đục dùng chung một ô texture cho cả 6 mặt.
     *
     * @param id          mã định danh block
     * @param displayName tên hiển thị trên giao diện
     * @param hardness    độ cứng khi đào tay (giây; âm nếu không phá được)
     * @param uniformTile chỉ số tile trong Texture Atlas dùng cho cả 6 mặt
     * @param iconColor   màu RGB đại diện
     */
    protected SolidBlock(byte id, String displayName, float hardness, int uniformTile, float[] iconColor) {
        super(id, displayName, hardness, true, false, true, iconColor);
        this.uniformTile = uniformTile;
    }

    /**
     * Khởi tạo khối rắn đục có họa tiết khác nhau theo từng mặt (lớp con ghi đè {@link #getTileIndex(Face)}).
     *
     * @param id          mã định danh block
     * @param displayName tên hiển thị trên giao diện
     * @param hardness    độ cứng khi đào tay (giây; âm nếu không phá được)
     * @param iconColor   màu RGB đại diện
     */
    protected SolidBlock(byte id, String displayName, float hardness, float[] iconColor) {
        this(id, displayName, hardness, 0, iconColor);
    }

    /** Mặc định trả về ô texture đồng nhất cho cả 6 mặt; lớp con nhiều mặt có thể ghi đè. */
    @Override
    public int getTileIndex(Face face) {
        return uniformTile;
    }
}
