package com.voxelgame.client.gui;

/**
 * Phông chữ ma trận điểm ảnh 5×7 dùng chung duy nhất cho toàn bộ tầng giao diện Client
 * ({@link HudRenderer}, {@link PauseScreen}, {@link InventoryScreen}, {@link DebugOverlayRenderer}).
 *
 * Thiết kế Zero-GC:
 * - Bảng {@link #GLYPHS} được khởi tạo tĩnh đúng một lần khi nạp lớp.
 * - Cung cấp các hàm ghi trực tiếp đỉnh quad 2D (6 float/đỉnh) vào mảng bộ đệm tái sử dụng,
 *   loại bỏ hoàn toàn mã nguồn trùng lặp giữa HUD và màn hình túi đồ.
 */
public final class BitmapFont {

    /** Chiều rộng ma trận điểm ảnh của mỗi ký tự (pixel gốc). */
    public static final int GLYPH_WIDTH = 5;
    /** Chiều cao ma trận điểm ảnh của mỗi ký tự (pixel gốc). */
    public static final int GLYPH_HEIGHT = 7;
    /** Bước tiến ngang giữa hai ký tự liên tiếp (5 pixel chữ + 1 pixel khoảng cách). */
    public static final float CHAR_ADVANCE = 6.0f;

    /** Bảng ánh xạ mã ASCII (0..127) sang 7 hàng bit 5-cột của từng ký tự. */
    private static final int[][] GLYPHS = createGlyphs();

    private BitmapFont() {
    }

    /** Kiểm tra ký tự có mẫu glyph 5×7 tích hợp hay không (không phân biệt hoa/thường). */
    public static boolean hasGlyph(char c) {
        char upper = Character.toUpperCase(c);
        return upper < GLYPHS.length && GLYPHS[upper] != null;
    }

    /** Lấy mảng 7 hàng bit của ký tự (hoặc {@code null} nếu là khoảng trắng / chưa hỗ trợ). */
    public static int[] getGlyph(char c) {
        char upper = Character.toUpperCase(c);
        return upper < GLYPHS.length ? GLYPHS[upper] : null;
    }

    /** Tính chiều rộng hiển thị (pixel) của chuỗi ký tự theo hệ số phóng {@code pixelScale}. */
    public static float measureWidth(CharSequence text, float pixelScale) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        return text.length() * CHAR_ADVANCE * pixelScale;
    }

    /** Tính chiều cao hiển thị (pixel) của một dòng chữ 5×7 theo hệ số phóng {@code pixelScale}. */
    public static float measureHeight(float pixelScale) {
        return GLYPH_HEIGHT * pixelScale;
    }

    /**
     * Ghi toàn bộ đỉnh hình học (2 tam giác / pixel bật) của chuỗi ký tự vào bộ đệm đỉnh dùng lại.
     *
     * @param buffer     mảng float đích (6 float mỗi đỉnh: x, y, z, u, v, shade)
     * @param offset     vị trí float bắt đầu ghi trong {@code buffer}
     * @param text       chuỗi cần vẽ
     * @param startX     tọa độ X góc trên-trái (pixel màn hình)
     * @param startY     tọa độ Y góc trên-trái (pixel màn hình)
     * @param pixelScale hệ số phóng mỗi điểm ảnh chữ
     * @param capacity   giới hạn số float tối đa được phép ghi vào {@code buffer}
     * @return vị trí {@code offset} mới sau khi ghi xong
     */
    public static int appendTextVertices(float[] buffer, int offset, CharSequence text,
                                         float startX, float startY, float pixelScale, int capacity) {
        if (text == null || text.isEmpty()) {
            return offset;
        }
        float cursorX = startX;
        for (int i = 0; i < text.length(); i++) {
            int[] glyph = getGlyph(text.charAt(i));
            if (glyph != null) {
                for (int row = 0; row < GLYPH_HEIGHT; row++) {
                    int rowBits = glyph[row];
                    for (int col = 0; col < GLYPH_WIDTH; col++) {
                        if (((rowBits >> (4 - col)) & 1) == 1) {
                            if (offset + 36 > capacity) {
                                return offset;
                            }
                            float px = cursorX + col * pixelScale;
                            float py = startY + row * pixelScale;
                            writeQuad(buffer, offset, px, py + pixelScale, 0f,
                                    px + pixelScale, py + pixelScale, 0f,
                                    px + pixelScale, py, 0f, px, py, 0f, 0f, 0f, 1f, 1f);
                            offset += 36;
                        }
                    }
                }
            }
            cursorX += CHAR_ADVANCE * pixelScale;
        }
        return offset;
    }

    /** Ghi bốn góc thành hai tam giác với cùng thứ tự thuộc tính (6 float/đỉnh) cho UI 2D và lớp phủ 3D. */
    public static void writeQuad(float[] data, int offset, float ax, float ay, float az,
                                 float bx, float by, float bz, float cx, float cy, float cz,
                                 float dx, float dy, float dz, float u0, float v0, float u1, float v1) {
        writeVertex(data, offset, ax, ay, az, u0, v1);
        writeVertex(data, offset + 6, bx, by, bz, u1, v1);
        writeVertex(data, offset + 12, cx, cy, cz, u1, v0);
        writeVertex(data, offset + 18, ax, ay, az, u0, v1);
        writeVertex(data, offset + 24, cx, cy, cz, u1, v0);
        writeVertex(data, offset + 30, dx, dy, dz, u0, v0);
    }

    /** Ghi vị trí (x, y, z), tọa độ UV và độ sáng đầy đủ {@code 1.0f} vào bộ đệm đỉnh UI. */
    public static void writeVertex(float[] data, int offset, float x, float y, float z, float u, float v) {
        data[offset] = x;
        data[offset + 1] = y;
        data[offset + 2] = z;
        data[offset + 3] = u;
        data[offset + 4] = v;
        data[offset + 5] = 1.0f;
    }

    /** Tạo toàn bộ bảng glyph một lần duy nhất thay vì cấp phát lại cho từng chữ. */
    private static int[][] createGlyphs() {
        int[][] glyphs = new int[128][];
        for (char c = 0; c < glyphs.length; c++) {
            glyphs[c] = createGlyph5x7(c);
        }
        return glyphs;
    }

    /** Sơ đồ bit 5×7 chuẩn của bộ chữ cái, chữ số và ký tự đặc biệt dùng trên HUD/F3/Menu. */
    private static int[] createGlyph5x7(char c) {
        return switch (c) {
            case 'A' -> new int[]{0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001};
            case 'B' -> new int[]{0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110};
            case 'C' -> new int[]{0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110};
            case 'D' -> new int[]{0b11100, 0b10010, 0b10001, 0b10001, 0b10001, 0b10010, 0b11100};
            case 'E' -> new int[]{0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111};
            case 'F' -> new int[]{0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000};
            case 'G' -> new int[]{0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01110};
            case 'H' -> new int[]{0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001};
            case 'I' -> new int[]{0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110};
            case 'J' -> new int[]{0b00111, 0b00010, 0b00010, 0b00010, 0b10010, 0b10010, 0b01100};
            case 'K' -> new int[]{0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001};
            case 'L' -> new int[]{0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111};
            case 'M' -> new int[]{0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001};
            case 'N' -> new int[]{0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001};
            case 'O' -> new int[]{0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110};
            case 'P' -> new int[]{0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000};
            case 'Q' -> new int[]{0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101};
            case 'R' -> new int[]{0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001};
            case 'S' -> new int[]{0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110};
            case 'T' -> new int[]{0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100};
            case 'U' -> new int[]{0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110};
            case 'V' -> new int[]{0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100};
            case 'W' -> new int[]{0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001};
            case 'X' -> new int[]{0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001};
            case 'Y' -> new int[]{0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100};
            case 'Z' -> new int[]{0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111};
            case '0' -> new int[]{0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110};
            case '1' -> new int[]{0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110};
            case '2' -> new int[]{0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111};
            case '3' -> new int[]{0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110};
            case '4' -> new int[]{0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010};
            case '5' -> new int[]{0b11111, 0b10000, 0b10000, 0b11110, 0b00001, 0b00001, 0b11110};
            case '6' -> new int[]{0b01110, 0b10000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110};
            case '7' -> new int[]{0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000};
            case '8' -> new int[]{0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110};
            case '9' -> new int[]{0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00001, 0b01110};
            case ':' -> new int[]{0b00000, 0b00100, 0b00100, 0b00000, 0b00100, 0b00100, 0b00000};
            case '%' -> new int[]{0b11001, 0b11010, 0b00010, 0b00100, 0b01000, 0b01011, 0b10011};
            case '-' -> new int[]{0b00000, 0b00000, 0b00000, 0b11111, 0b00000, 0b00000, 0b00000};
            case '+' -> new int[]{0b00000, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0b00000};
            case '.' -> new int[]{0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b01100, 0b01100};
            case '/' -> new int[]{0b00001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000};
            case ',' -> new int[]{0b00000, 0b00000, 0b00000, 0b00000, 0b00110, 0b00100, 0b01000};
            case '(' -> new int[]{0b00010, 0b00100, 0b01000, 0b01000, 0b01000, 0b00100, 0b00010};
            case ')' -> new int[]{0b01000, 0b00100, 0b00010, 0b00010, 0b00010, 0b00100, 0b01000};
            case '=' -> new int[]{0b00000, 0b00000, 0b11111, 0b00000, 0b11111, 0b00000, 0b00000};
            default -> null;
        };
    }
}
