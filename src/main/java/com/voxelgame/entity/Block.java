package com.voxelgame.entity;

import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.Texture;
import com.voxelgame.engine.World;

/**
 * Lớp trừu tượng đại diện cho một Khối vuông (Voxel Block), kế thừa từ {@link Entity}
 * và áp dụng mẫu thiết kế Flyweight (Flyweight Design Pattern) để tối ưu hóa bộ nhớ RAM.
 *
 * LƯU Ý KỸ THUẬT QUAN TRỌNG VỀ THỨ TỰ KHỞI TẠO STATIC (STATIC INITIALIZATION ORDER):
 * - Mảng tra cứu `BY_ID` bắt buộc phải được khai báo và cấp phát TRƯỚC các hằng số thực thể
 *   (`AIR`, `DIRT`, `GRASS`...). Khi mỗi hằng số con gọi constructor `super(...)` của `Block`,
 *   nó sẽ tự đăng ký chính mình vào `BY_ID[id & 0xFF] = this;` mà không gặp lỗi NullPointerException.
 */
public abstract class Block extends Entity {

    // 1. BẮT BUỘC KHAI BÁO BẢNG TRA CỨU FLYWEIGHT TRƯỚC TIÊN
    private static final Block[] BY_ID = new Block[16];

    // 2. KHỞI TẠO 9 HẰNG SỐ THỰC THỂ BLOCK DUY NHẤT (FLYWEIGHT SINGLETON INSTANCES)
    public static final Block AIR = new AirBlock();
    public static final Block DIRT = new DirtBlock();
    public static final Block GRASS = new GrassBlock();
    public static final Block STONE = new StoneBlock();
    public static final Block BEDROCK = new BedrockBlock();
    public static final Block OAK_LOG = new OakLogBlock();
    public static final Block OAK_LEAVES = new OakLeavesBlock();
    public static final Block WATER = new WaterBlock();
    public static final Block LAVA = new LavaBlock();

    /**
     * Liệt kê 6 hướng mặt của một khối lập phương 3D.
     */
    public enum Face {
        TOP,    // Mặt trên (+Y)
        BOTTOM, // Mặt dưới (-Y)
        NORTH,  // Mặt Bắc (-Z)
        SOUTH,  // Mặt Nam (+Z)
        EAST,   // Mặt Đông (+X)
        WEST    // Mặt Tây (-X)
    }

    // Các thuộc tính bất biến được đóng gói chặt chẽ (private final)
    private final byte id;
    private final String displayName;
    private final float hardness;      // Thời gian đập vỡ bằng tay không (giây). Âm (-1) là không thể phá hủy
    private final boolean solid;       // Có chặn va chạm vật lý AABB và tia Raycast không?
    private final boolean liquid;      // Có phải khối chất lỏng (Water / Lava) không?
    private final boolean opaque;      // Có che khuất hoàn toàn các mặt khối liền kề không?
    private final float[] iconColor;   // Màu RGB đại diện

    protected Block(
            byte id,
            String displayName,
            float hardness,
            boolean solid,
            boolean liquid,
            boolean opaque,
            float[] iconColor
    ) {
        super(0.5f, 1.0f);
        this.id = id;
        this.displayName = displayName;
        this.hardness = hardness;
        this.solid = solid;
        this.liquid = liquid;
        this.opaque = opaque;
        this.iconColor = (iconColor != null) ? iconColor.clone() : new float[]{0.0f, 0.0f, 0.0f};

        // Tự động đăng ký thực thể Flyweight vào bảng tra cứu O(1)
        BY_ID[id & 0xFF] = this;
    }

    /**
     * Tra cứu thực thể Block tương ứng từ mã ID (byte) lưu trong mảng 3D của Chunk với độ phức tạp O(1).
     * Nếu ID không hợp lệ, luôn trả về `Block.AIR` để tránh lỗi NullPointerException.
     *
     * @param id mã định danh byte của block
     * @return thực thể Block tương ứng hoặc Block.AIR
     */
    public static Block fromId(byte id) {
        int index = id & 0xFF;
        if (index >= 0 && index < BY_ID.length && BY_ID[index] != null) {
            return BY_ID[index];
        }
        return AIR;
    }

    /**
     * Ghi đè mặc định phương thức `update` của `Entity` thành hàm rỗng để 9 lớp con không phải lặp lại code.
     * Các khối địa hình tĩnh được quản lý tập trung theo từng Chunk.
     */
    @Override
    public void update(World world, float deltaTime) {
        // Không thực hiện cập nhật đơn lẻ trên từng ô địa hình tĩnh
    }

    /**
     * Ghi đè mặc định phương thức `render` của `Entity` thành hàm rỗng để 9 lớp con không phải lặp lại code.
     * Các mặt khối địa hình được gộp chung vào Mesh của Chunk (Batch Mesh Rendering).
     */
    @Override
    public void render(ShaderProgram shader) {
        // Không vẽ rời rạc từng khối địa hình tĩnh
    }

    /**
     * Phương thức trừu tượng buộc mỗi loại Block cụ thể tự định nghĩa chỉ số ô ảnh (Tile Index 0..15)
     * trên Texture Atlas cho từng mặt (TOP, BOTTOM, NORTH, SOUTH, EAST, WEST).
     *
     * @param face mặt lập phương cần lấy chỉ số tile
     * @return chỉ số tile (0..15) trên Texture Atlas
     */
    public abstract int getTileIndex(Face face);

    /**
     * Đa hình hành vi khi khối bị người chơi đập vỡ hoàn toàn:
     * Mặc định nếu khối có thể phá hủy (`isBreakable()`), sinh ra một `ItemEntity` mang chính loại Block đó.
     * Các lớp con (như `GrassBlock`, `BedrockBlock`, `WaterBlock`, `LavaBlock`) có thể ghi đè hành vi này.
     *
     * @param world thế giới voxel đang diễn ra hành động
     * @param x tọa độ nguyên trục X của khối bị phá
     * @param y tọa độ nguyên trục Y của khối bị phá
     * @param z tọa độ nguyên trục Z của khối bị phá
     */
    public void onBreak(World world, int x, int y, int z) {
        if (isBreakable() && world != null) {
            world.spawnDroppedItem(new ItemEntity(x + 0.5f, y + 0.5f, z + 0.5f, this));
        }
    }

    /**
     * Đa hình chiều cao bề mặt khối:
     * Mặc định các khối rắn có chiều cao `1.0f`. Các khối chất lỏng (`WaterBlock`, `LavaBlock`)
     * ghi đè phương thức này để hạ mặt thoáng xuống `0.88f` khi phía trên là không khí.
     *
     * @param world thế giới voxel
     * @param wx tọa độ X thế giới
     * @param wy tọa độ Y thế giới
     * @param wz tọa độ Z thế giới
     * @return chiều cao thực tế của mặt trên khối (0.0f đến 1.0f)
     */
    public float getTopHeight(World world, int wx, int wy, int wz) {
        return 1.0f;
    }

    /**
     * Tính tọa độ chất liệu UV [u0, v0, u1, v1] trên Texture Atlas cho một mặt cụ thể của Block.
     *
     * @param face mặt lập phương
     * @return mảng 4 phần tử float [u0, v0, u1, v1]
     */
    public float[] getFaceUV(Face face) {
        return getTileUV(getTileIndex(face));
    }

    /**
     * Tính trực tiếp tọa độ UV [u0, v0, u1, v1] từ chỉ số ô `tileIndex` trên Texture Atlas 4x4.
     * Áp dụng khoảng lùi eps = 0.0008f ở mép để triệt tiêu hiện tượng lem viền (Texture Bleeding).
     *
     * @param tileIndex chỉ số ô tile (0..15)
     * @return mảng 4 phần tử float [u0, v0, u1, v1]
     */
    public static float[] getTileUV(int tileIndex) {
        int col = tileIndex % Texture.ATLAS_COLS;
        int row = tileIndex / Texture.ATLAS_COLS;

        float tileWidth = 1.0f / Texture.ATLAS_COLS;
        float tileHeight = 1.0f / Texture.ATLAS_ROWS;

        // Khoảng lùi siêu nhỏ ở mép để triệt tiêu hiện tượng lem viền (Texture Bleeding) giữa các ô Atlas
        float eps = 0.0008f;

        float u0 = col * tileWidth + eps;
        float v0 = row * tileHeight + eps;
        float u1 = (col + 1) * tileWidth - eps;
        float v1 = (row + 1) * tileHeight - eps;

        return new float[]{u0, v0, u1, v1};
    }

    public byte getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getHardness() {
        return hardness;
    }

    /**
     * Kiểm tra xem người chơi có thể đập vỡ khối này bằng tay không hay không.
     * Khối rắn có độ cứng > 0 mới có thể đập vỡ.
     *
     * @return true nếu khối có thể bị đập vỡ
     */
    public boolean isBreakable() {
        return solid && hardness > 0.0f;
    }

    public boolean isSolid() {
        return solid;
    }

    public boolean isLiquid() {
        return liquid;
    }

    public boolean isOpaque() {
        return opaque;
    }

    public float[] getIconColor() {
        return iconColor.clone();
    }
}
