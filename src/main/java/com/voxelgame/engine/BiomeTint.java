package com.voxelgame.engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.NumberUtils;

/**
 * Tính hệ số nhuộm màu động cho thảm cỏ và tán lá sồi theo 5 {@link BiomeType}
 * với cơ chế lấy mẫu trung bình 3×3 quanh cột để chuyển màu mượt tại ranh giới biome.
 * <p>
 * Lớp thuần tĩnh và không cấp phát bộ nhớ (zero-GC): toàn bộ kết quả được ghi trực tiếp
 * vào mảng {@code float[3] outRgb} do bên gọi tái sử dụng.
 */
public final class BiomeTint {

    /**
     * Màu trắng chuẩn đóng gói dạng float (0xFEFFFFFF theo quy ước {@link Color#WHITE_FLOAT_BITS})
     * dùng cho các khối/mặt không nhuộm biome nhằm tránh giá trị IEEE-754 NaN của 0xFFFFFFFF.
     */
    public static final float WHITE_PACKED_COLOR = Color.WHITE_FLOAT_BITS;

    /** Màu cỏ chuẩn của Plains đã được nhân sẵn vào atlas trong {@code Texture.PLAINS_TINT}. */
    private static final int PLAINS_GRASS_TINT = 0x91BD59;
    /** Màu lá chuẩn của Plains dùng làm gốc chuẩn hóa cho hệ số lá sồi tại Plains bằng (1, 1, 1). */
    private static final int PLAINS_FOLIAGE_TINT = 0x77AB2F;

    private static final float PLAINS_GRASS_R = ((PLAINS_GRASS_TINT >>> 16) & 0xFF);
    private static final float PLAINS_GRASS_G = ((PLAINS_GRASS_TINT >>> 8) & 0xFF);
    private static final float PLAINS_GRASS_B = (PLAINS_GRASS_TINT & 0xFF);

    private static final float PLAINS_FOLIAGE_R = ((PLAINS_FOLIAGE_TINT >>> 16) & 0xFF);
    private static final float PLAINS_FOLIAGE_G = ((PLAINS_FOLIAGE_TINT >>> 8) & 0xFF);
    private static final float PLAINS_FOLIAGE_B = (PLAINS_FOLIAGE_TINT & 0xFF);

    /** Bảng màu cỏ RGB 24-bit cho 5 biome, đánh chỉ số theo {@link BiomeType#ordinal()}. */
    private static final int[] GRASS_COLORS = new int[BiomeType.values().length];
    /** Bảng màu lá RGB 24-bit cho 5 biome, đánh chỉ số theo {@link BiomeType#ordinal()}. */
    private static final int[] FOLIAGE_COLORS = new int[BiomeType.values().length];

    static {
        registerBiomeColors(BiomeType.SNOWY_PLAINS, 0x80B497, 0x60A17B);
        registerBiomeColors(BiomeType.FOREST, 0x79C05A, 0x59AE30);
        registerBiomeColors(BiomeType.PLAINS, 0x91BD59, 0x77AB2F);
        registerBiomeColors(BiomeType.FLOWER_FOREST, 0x88BB67, 0x6BA941);
        registerBiomeColors(BiomeType.DESERT, 0xBFB755, 0xAEA42A);
    }

    private BiomeTint() {
    }

    /** Đăng ký cặp màu cỏ/lá cho một biome vào bảng tra tĩnh. */
    private static void registerBiomeColors(BiomeType biome, int grassRgb, int foliageRgb) {
        int index = biome.ordinal();
        GRASS_COLORS[index] = grassRgb;
        FOLIAGE_COLORS[index] = foliageRgb;
    }

    /**
     * Tính hệ số nhân màu cỏ tại cột {@code (x, z)} bằng lấy mẫu 3×3 qua {@link World#getBiome(int, int)},
     * chia bù theo {@code PLAINS_TINT (0x91BD59)} sao cho tại trung tâm Plains kết quả là {@code (1, 1, 1)}.
     *
     * @param world  thế giới nguồn dữ liệu biome (nếu {@code null} trả về {@code (1, 1, 1)})
     * @param x      tọa độ X thế giới
     * @param z      tọa độ Z thế giới
     * @param outRgb mảng {@code float[3]} nhận các kênh R, G, B
     */
    public static void grassMultiplier(World world, int x, int z, float[] outRgb) {
        sampleBlendedMultiplier(world, x, z, GRASS_COLORS,
                PLAINS_GRASS_R, PLAINS_GRASS_G, PLAINS_GRASS_B, outRgb);
    }

    /**
     * Tính hệ số nhân màu lá sồi tại cột {@code (x, z)} bằng lấy mẫu 3×3 qua {@link World#getBiome(int, int)},
     * chia bù theo màu lá Plains {@code (0x77AB2F)} sao cho tại trung tâm Plains kết quả là {@code (1, 1, 1)}.
     *
     * @param world  thế giới nguồn dữ liệu biome (nếu {@code null} trả về {@code (1, 1, 1)})
     * @param x      tọa độ X thế giới
     * @param z      tọa độ Z thế giới
     * @param outRgb mảng {@code float[3]} nhận các kênh R, G, B
     */
    public static void foliageMultiplier(World world, int x, int z, float[] outRgb) {
        sampleBlendedMultiplier(world, x, z, FOLIAGE_COLORS,
                PLAINS_FOLIAGE_R, PLAINS_FOLIAGE_G, PLAINS_FOLIAGE_B, outRgb);
    }

    /** Lấy trung bình 9 cột trong vùng 3×3 và chuẩn hóa theo màu gốc của Plains. */
    private static void sampleBlendedMultiplier(World world, int x, int z, int[] palette,
                                                float baseR, float baseG, float baseB, float[] outRgb) {
        if (world == null) {
            outRgb[0] = 1.0f;
            outRgb[1] = 1.0f;
            outRgb[2] = 1.0f;
            return;
        }
        int sumR = 0;
        int sumG = 0;
        int sumB = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int rgb = palette[world.getBiome(x + dx, z + dz).ordinal()];
                sumR += (rgb >>> 16) & 0xFF;
                sumG += (rgb >>> 8) & 0xFF;
                sumB += rgb & 0xFF;
            }
        }
        outRgb[0] = sumR / (9.0f * baseR);
        outRgb[1] = sumG / (9.0f * baseG);
        outRgb[2] = sumB / (9.0f * baseB);
    }

    /**
     * Đóng gói hệ số nhân RGB (có thể lớn hơn 1.0 ở Desert/Snowy Plains) thành 1 float
     * tương thích thuộc tính {@code Usage.ColorPacked} (4 byte RGBA chuẩn hóa).
     * <p>
     * Khi cả 3 kênh {@code <= 1.0f}, kênh alpha giữ mức tối đa ({@code >= 254/255}) để shader đọc trực tiếp RGB.
     * Khi có kênh {@code > 1.0f}, RGB được chia theo tỷ lệ lưu vào kênh alpha ({@code <= 252/255}) để shader
     * khôi phục chính xác {@code aColor.rgb / aColor.a} mà không bị kẹp mất sắc vàng Desert hay lam Snowy.
     *
     * @param rgb mảng {@code float[3]} chứa hệ số R, G, B
     * @return giá trị float đã đóng gói sẵn sàng ghi vào bộ đệm đỉnh
     */
    public static float packMultiplier(float[] rgb) {
        return packMultiplier(rgb[0], rgb[1], rgb[2]);
    }

    /** Đóng gói 3 kênh hệ số nhân R, G, B thành 1 float cho {@code aColor}. */
    public static float packMultiplier(float r, float g, float b) {
        float cr = Math.max(0.0f, r);
        float cg = Math.max(0.0f, g);
        float cb = Math.max(0.0f, b);
        float maxChannel = Math.max(cr, Math.max(cg, cb));

        if (maxChannel <= 1.0f) {
            int rByte = Math.round(cr * 255.0f);
            int gByte = Math.round(cg * 255.0f);
            int bByte = Math.round(cb * 255.0f);
            if (rByte >= 255 && gByte >= 255 && bByte >= 255) {
                return WHITE_PACKED_COLOR;
            }
            int packed = (255 << 24) | (bByte << 16) | (gByte << 8) | rByte;
            return NumberUtils.intToFloatColor(packed);
        }

        // Dùng kênh alpha <= 252 để mã hóa hệ số phóng đại (scale = 255 / aByte >= maxChannel).
        int aByte = Math.min(252, Math.max(1, (int) Math.floor(255.0f / maxChannel)));
        int rByte = Math.min(255, Math.round(cr * aByte));
        int gByte = Math.min(255, Math.round(cg * aByte));
        int bByte = Math.min(255, Math.round(cb * aByte));
        int packed = (aByte << 24) | (bByte << 16) | (gByte << 8) | rByte;
        return NumberUtils.intToFloatColor(packed);
    }
}
