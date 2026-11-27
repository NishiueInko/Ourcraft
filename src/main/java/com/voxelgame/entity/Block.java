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
    private static final Block[] BY_ID = new Block[256];

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

    // Các ID mới nối tiếp bảng cũ để giữ cách diễn giải dữ liệu voxel.
    public static final Block SAND = new SandBlock();
    public static final Block GRAVEL = new GravelBlock();
    public static final Block SANDSTONE = new SandstoneBlock();
    public static final Block CACTUS = new CactusBlock();
    public static final Block DEAD_BUSH = new DeadBushBlock();
    public static final Block SHORT_GRASS = new ShortGrassBlock();
    public static final Block TALL_GRASS = new TallGrassBlock();
    public static final Block DANDELION = new FlowerBlock(16, "Dandelion", Texture.TILE_DANDELION);
    public static final Block POPPY = new FlowerBlock(17, "Poppy", Texture.TILE_POPPY);
    public static final Block CORNFLOWER = new FlowerBlock(18, "Cornflower", Texture.TILE_CORNFLOWER);
    public static final Block OXEYE_DAISY = new FlowerBlock(19, "Oxeye Daisy", Texture.TILE_OXEYE_DAISY);
    public static final Block SUGAR_CANE = new SugarCaneBlock();
    public static final Block SNOW_LAYER = new SnowLayerBlock();
    public static final Block SNOW_BLOCK = new SnowBlock();
    public static final Block ICE = new IceBlock();
    public static final Block SPRUCE_LOG = new SpruceLogBlock();
    public static final Block SPRUCE_LEAVES = new SpruceLeavesBlock();
    /** Rương gỗ trang trí được sinh trong tầng ngầm của kim tự tháp. */
    public static final Block CHEST = new DecorativeChestBlock();

    /** Sáu khối len cừu nối tiếp mã khối 27..32 để đặt xuống thế giới. */
    public static final Block WHITE_WOOL = new WoolBlock(27, "White Wool",
            Texture.TILE_WHITE_WOOL, new float[]{0.93f, 0.93f, 0.93f});
    public static final Block LIGHT_GRAY_WOOL = new WoolBlock(28, "Light Gray Wool",
            Texture.TILE_LIGHT_GRAY_WOOL, new float[]{0.62f, 0.62f, 0.59f});
    public static final Block GRAY_WOOL = new WoolBlock(29, "Gray Wool",
            Texture.TILE_GRAY_WOOL, new float[]{0.29f, 0.31f, 0.32f});
    public static final Block BLACK_WOOL = new WoolBlock(30, "Black Wool",
            Texture.TILE_BLACK_WOOL, new float[]{0.11f, 0.12f, 0.14f});
    public static final Block BROWN_WOOL = new WoolBlock(31, "Brown Wool",
            Texture.TILE_BROWN_WOOL, new float[]{0.48f, 0.29f, 0.18f});
    public static final Block PINK_WOOL = new WoolBlock(32, "Pink Wool",
            Texture.TILE_PINK_WOOL, new float[]{0.93f, 0.56f, 0.67f});

    /** Lớp kết xuất quyết định cách ghi chiều sâu và hòa trộn alpha. */
    public enum RenderLayer { OPAQUE, CUTOUT, TRANSLUCENT }

    /** Hình học cục bộ của ô voxel. */
    public enum GeometryType { FULL_CUBE, LIQUID, CACTUS, SNOW_LAYER, CROSS_PLANT }

    /** Giới hạn bất biến theo hệ tọa độ cục bộ của block. */
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        /** Kiểm tra giao của hai hộp, cho phép các mặt chỉ tiếp xúc. */
        public boolean intersects(int x, int y, int z, float left, float bottom, float back,
                                  float right, float top, float front) {
            return left < x + maxX && right > x + minX
                    && bottom < y + maxY && top > y + minY
                    && back < z + maxZ && front > z + minZ;
        }
    }

    /** Hộp dùng chung cho khối đầy. */
    public static final Bounds FULL_BOUNDS = new Bounds(0, 0, 0, 1, 1, 1);

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
        Block block = BY_ID[Byte.toUnsignedInt(id)];
        return block == null ? AIR : block;
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
     * Phương thức trừu tượng buộc mỗi loại Block cụ thể tự định nghĩa chỉ số ô ảnh
     * trên Texture Atlas cho từng mặt (TOP, BOTTOM, NORTH, SOUTH, EAST, WEST).
     *
     * @param face mặt lập phương cần lấy chỉ số tile
     * @return chỉ số tile trên Texture Atlas
     */
    public abstract int getTileIndex(Face face);

    /** Lấy tile phụ thuộc vào trạng thái của ô và khối ngay phía trên. */
    public int getTileIndex(Face face, int state, Block above) {
        return getTileIndex(face);
    }

    /** Cho phép chọn cây và lớp tuyết bên cạnh các khối rắn. */
    public boolean isSelectable() {
        return this != AIR && !liquid;
    }

    /** Kiểm tra ô hiện tại có thể nhường chỗ block mới hay không. */
    public boolean isReplaceable(int state) {
        return this == AIR || liquid;
    }

    /** Cho biết block có rơi khi mất mặt đỡ. */
    public boolean isGravityBlock() {
        return false;
    }

    /** Hệ số giữ đà trên bề mặt tiêu chuẩn. */
    public float getFriction() {
        return 0.6f;
    }

    /** Trả dạng hình học mặc định của khối rắn và chất lỏng. */
    public GeometryType getGeometryType() {
        return liquid ? GeometryType.LIQUID : GeometryType.FULL_CUBE;
    }

    /** Chọn pha vẽ. Nước được hòa trộn, dung nham giữ cách vẽ hiện tại. */
    public RenderLayer getRenderLayer() {
        return this == WATER ? RenderLayer.TRANSLUCENT : RenderLayer.OPAQUE;
    }

    /** Trả hộp vật lý hoặc null cho ô không cản thực thể. */
    public Bounds getCollisionBounds(int state) {
        return solid ? FULL_BOUNDS : null;
    }

    /** Trả hộp chọn hoặc null nếu tia phải đi xuyên qua. */
    public Bounds getSelectionBounds(int state) {
        return isSelectable() ? FULL_BOUNDS : null;
    }

    /** Giới hạn trạng thái của các khối không có metadata riêng. */
    public int normalizeState(int state) {
        return 0;
    }

    /** Kiểm tra điều kiện nền và ô kề trước khi đặt block. */
    public boolean canPlaceAt(World world, int x, int y, int z) {
        return true;
    }

    /** Áp dụng quy tắc tồn tại khi ô hoặc vùng lân cận bị sửa. */
    public void onNeighborChange(World world, int x, int y, int z) {
        if (!canPlaceAt(world, x, y, z)) {
            world.breakBlockAndSpawnDrop(x, y, z);
        }
    }

    /** Trả block sẽ chiếm ô sau khi đào hoàn tất. */
    public Block getBreakReplacement(World world, int x, int y, int z) {
        return AIR;
    }

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
     * Tính tọa độ UV [u0, v0, u1, v1] của tile trong atlas.
     * Xương rồng chỉ dùng vùng ảnh bên trong viền một pixel; mọi tile đều có khoảng lùi chống lem.
     *
     * @param tileIndex chỉ số ô tile trong atlas
     * @return mảng 4 phần tử float [u0, v0, u1, v1]
     */
    public static float[] getTileUV(int tileIndex) {
        int col = tileIndex % Texture.ATLAS_COLS;
        int row = tileIndex / Texture.ATLAS_COLS;

        float tileWidth = 1.0f / Texture.ATLAS_COLS;
        float tileHeight = 1.0f / Texture.ATLAS_ROWS;
        boolean cactusCap = tileIndex == Texture.TILE_CACTUS_TOP || tileIndex == Texture.TILE_CACTUS_BOTTOM;
        float insetU = cactusCap || tileIndex == Texture.TILE_CACTUS_SIDE ? 1.0f / Texture.ATLAS_WIDTH : 0f;
        float insetV = cactusCap ? 1.0f / Texture.ATLAS_HEIGHT : 0f;

        // Khoảng lùi siêu nhỏ ở mép để triệt tiêu hiện tượng lem viền (Texture Bleeding) giữa các ô Atlas
        float eps = 0.0008f;

        float u0 = col * tileWidth + insetU + eps;
        float v0 = row * tileHeight + insetV + eps;
        float u1 = (col + 1) * tileWidth - insetU - eps;
        float v1 = (row + 1) * tileHeight - insetV - eps;

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
        return isSelectable() && hardness > 0.0f;
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
