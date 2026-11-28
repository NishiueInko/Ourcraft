package com.voxelgame.client.gui;

import com.voxelgame.client.render.ShaderProgram;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Disposable;

/**
 * Bộ kết xuất phông chữ TrueType **VT323** dùng chung toàn dự án thông qua {@code gdx-freetype}
 * và hệ thống {@link ShaderProgram} 2D (tọa độ Top-Left Y hướng xuống).
 *
 * <p>Thiết kế Zero-GC & Bảo toàn 60 FPS:</p>
 * <ul>
 *     <li>Nạp {@code fonts/VT323-Regular.ttf} đúng một lần khi khởi tạo OpenGL, sinh sẵn 2 atlas glyph
 *     ({@code bodyFont} cỡ 36px và {@code titleFont} cỡ 84px) chứa đầy đủ bảng ký tự ASCII và toàn bộ
 *     chữ cái tiếng Việt có dấu (phục vụ màn hình Credits, HUD, F3, Inventory, Menu).</li>
 *     <li>Mọi thao tác đo kích thước ({@link #measureWidth}, {@link #measureHeight}) và vẽ chuỗi
 *     ({@link #drawText}, {@link #drawTextWithShadow}, {@link #drawRotatedText}) đều nhận
 *     {@link CharSequence}, dùng lại duy nhất mảng đỉnh tĩnh {@code vertexBuffer} và {@link Mesh}
 *     trên VRAM — không tạo bất kỳ rác GC nào trong vòng lặp 60 FPS.</li>
 *     <li>Tự động bỏ qua an toàn khi chạy kiểm thử CPU không có ngữ cảnh OpenGL ({@code Gdx.gl == null}).</li>
 * </ul>
 */
public final class Vt323FontRenderer implements Disposable {

    /** Đường dẫn tài nguyên tệp font VT323 trong classpath. */
    public static final String FONT_RESOURCE_PATH = "fonts/VT323-Regular.ttf";

    /** Cỡ chữ chuẩn sinh sẵn trên atlas cho văn bản giao diện và tiêu đề lớn. */
    private static final int BODY_BASE_SIZE = 36;
    private static final int TITLE_BASE_SIZE = 84;

    /** Bộ ký tự đầy đủ gồm ASCII chuẩn và toàn bộ nguyên âm/phụ âm tiếng Việt có dấu. */
    private static final String ALL_CHARACTERS = FreeTypeFontGenerator.DEFAULT_CHARS
            + "ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝàáâãèéêìíòóôõùúý"
            + "ĂăĐđĨĩŨũƠơƯư"
            + "ẠạẢảẤấẦầẨẩẪẫẬậẮắẰằẲẳẴẵẶặ"
            + "ẸẹẺẻẼẽẾếỀềỂểỄễỆệ"
            + "ỈỉỊị"
            + "ỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợ"
            + "ỤụỦủỨứỪừỬửỮữỰự"
            + "ỲỳỴỵỶỷỸỹ"
            + "×–—…";

    /** Cấu hình thuộc tính đỉnh khớp với ShaderProgram 2D: aPos(3), aTexCoord(2), aShade(1). */
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade")
    );

    /** Sức chứa bộ đệm đỉnh cho tối đa 256 glyph trong một lần đẩy lên GPU (zero-GC). */
    private static final int MAX_QUADS_PER_BATCH = 256;
    private static final int FLOATS_PER_QUAD = 36;

    /** Hai bộ font VT323 (văn bản thường và tiêu đề lớn OurCraft). */
    private com.badlogic.gdx.graphics.g2d.BitmapFont bodyFont;
    private com.badlogic.gdx.graphics.g2d.BitmapFont titleFont;

    /** Lưới GPU và bộ đệm đỉnh tái sử dụng qua mọi khung hình. */
    private Mesh mesh;
    private final float[] vertexBuffer = new float[MAX_QUADS_PER_BATCH * FLOATS_PER_QUAD];

    private boolean ready = false;
    private boolean disposed = false;

    /**
     * Khởi tạo bộ kết xuất font VT323.
     * Nếu chưa có ngữ cảnh OpenGL (kiểm thử CPU), giữ trạng thái {@code ready = false} để fallback an toàn.
     */
    public Vt323FontRenderer() {
        if (Gdx.gl != null && Gdx.files != null) {
            initFonts();
        }
    }

    /** Kiểm tra font VT323 đã sẵn sàng trên GPU hay chưa. */
    public boolean isReady() {
        return ready && !disposed && bodyFont != null;
    }

    /** Nạp tệp {@code VT323-Regular.ttf} và tạo 2 atlas glyph tối ưu cho văn bản và tiêu đề. */
    private void initFonts() {
        FileHandle fontFile = Gdx.files.classpath(FONT_RESOURCE_PATH);
        if (!fontFile.exists()) {
            System.err.println("[Vt323FontRenderer] Không tìm thấy tài nguyên " + FONT_RESOURCE_PATH);
            return;
        }
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(fontFile);

            FreeTypeFontParameter bodyParam = new FreeTypeFontParameter();
            bodyParam.size = BODY_BASE_SIZE;
            bodyParam.characters = ALL_CHARACTERS;
            bodyParam.flip = true;
            bodyParam.magFilter = Texture.TextureFilter.Nearest;
            bodyParam.minFilter = Texture.TextureFilter.Linear;
            bodyParam.kerning = false;
            bodyFont = generator.generateFont(bodyParam);

            FreeTypeFontParameter titleParam = new FreeTypeFontParameter();
            titleParam.size = TITLE_BASE_SIZE;
            titleParam.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
            titleParam.flip = true;
            titleParam.magFilter = Texture.TextureFilter.Nearest;
            titleParam.minFilter = Texture.TextureFilter.Nearest;
            titleParam.kerning = false;
            titleFont = generator.generateFont(titleParam);

            mesh = new Mesh(false, MAX_QUADS_PER_BATCH * 6, 0, VERTEX_ATTRIBUTES);
            ready = true;
        } catch (RuntimeException | Error error) {
            System.err.println("[Vt323FontRenderer] Lỗi khởi tạo font VT323: " + error.getMessage());
            dispose();
        } finally {
            if (generator != null) {
                generator.dispose();
            }
        }
    }

    /**
     * Quy đổi hệ số {@code pixelScale} cũ của font 5×7 sang chiều cao hiển thị pixel tương đương cho VT323,
     * giúp các kích thước HUD/F3/Inventory tự nhiên và sắc nét.
     */
    public static float legacyScaleToPixelHeight(float legacyScale) {
        return Math.max(12.0f, legacyScale * 11.5f);
    }

    /** Chọn bộ font phù hợp (dùng {@code titleFont} cho cỡ lớn >= 52px để giữ cạnh sắc nét). */
    private com.badlogic.gdx.graphics.g2d.BitmapFont pickFont(float pixelHeight) {
        if (titleFont != null && pixelHeight >= 52.0f) {
            return titleFont;
        }
        return bodyFont;
    }

    /** Tính tỷ lệ phóng từ cỡ capHeight gốc của atlas sang chiều cao pixel yêu cầu. */
    private static float computeScale(BitmapFontData data, float pixelHeight) {
        float cap = Math.max(1.0f, data.capHeight);
        return pixelHeight / cap;
    }

    /** Đo chiều rộng hiển thị (pixel) của chuỗi ký tự theo chiều cao pixel {@code pixelHeight}. */
    public float measureWidth(CharSequence text, float pixelHeight) {
        if (!isReady() || text == null || text.isEmpty()) {
            return 0.0f;
        }
        com.badlogic.gdx.graphics.g2d.BitmapFont font = pickFont(pixelHeight);
        BitmapFontData data = font.getData();
        float scale = computeScale(data, pixelHeight);
        float width = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            Glyph glyph = resolveGlyph(data, text.charAt(i));
            if (glyph != null) {
                width += glyph.xadvance * scale;
            }
        }
        return width;
    }

    /** Đo chiều cao hiển thị chuẩn của dòng chữ theo {@code pixelHeight}. */
    public float measureHeight(float pixelHeight) {
        return pixelHeight;
    }

    /** Vẽ chuỗi chữ VT323 tại tọa độ góc trên-trái {@code (x, y)} với màu RGBA chỉ định. */
    public void drawText(ShaderProgram shader, CharSequence text, float x, float y,
                         float pixelHeight, float r, float g, float b, float a) {
        if (!isReady() || shader == null || text == null || text.isEmpty()) {
            return;
        }
        com.badlogic.gdx.graphics.g2d.BitmapFont font = pickFont(pixelHeight);
        BitmapFontData data = font.getData();
        float scale = computeScale(data, pixelHeight);

        shader.setUniform1i("uUseTexture", 1);
        shader.setUniformVec4("uTintColor", r, g, b, a);

        float cursorX = x;
        // Khi flip = true trong FreeTypeFontGenerator, glyph.yoffset tính từ đỉnh hộp dòng
        // (baseline nằm ở y + capHeight * scale, còn yoffset có giá trị âm theo chiều ngược hoặc dương từ đỉnh).
        // Ta căn theo data.ascent (âm) để phần đỉnh chữ hoa nằm đúng tại y.
        float topOffset = -data.ascent * scale;

        int currentPage = -1;
        int floatCount = 0;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            Glyph glyph = resolveGlyph(data, text.charAt(i));
            if (glyph == null) {
                continue;
            }
            if (glyph.width > 0 && glyph.height > 0) {
                if (glyph.page != currentPage) {
                    if (floatCount > 0) {
                        flushBatch(shader, font, currentPage, floatCount);
                        floatCount = 0;
                    }
                    currentPage = glyph.page;
                }
                if (floatCount + FLOATS_PER_QUAD > vertexBuffer.length) {
                    flushBatch(shader, font, currentPage, floatCount);
                    floatCount = 0;
                }

                float gx = cursorX + glyph.xoffset * scale;
                float gy = y + (glyph.yoffset * scale) - topOffset;
                float gw = glyph.width * scale;
                float gh = glyph.height * scale;

                float u0 = glyph.u;
                float u1 = glyph.u2;
                float vTop = Math.min(glyph.v, glyph.v2);
                float vBottom = Math.max(glyph.v, glyph.v2);

                BitmapFont.writeQuad(vertexBuffer, floatCount,
                        gx, gy + gh, 0.0f,
                        gx + gw, gy + gh, 0.0f,
                        gx + gw, gy, 0.0f,
                        gx, gy, 0.0f,
                        u0, vTop, u1, vBottom);
                floatCount += FLOATS_PER_QUAD;
            }
            cursorX += glyph.xadvance * scale;
        }

        if (floatCount > 0 && currentPage >= 0) {
            flushBatch(shader, font, currentPage, floatCount);
        }
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        shader.setUniform1i("uUseTexture", 0);
    }

    /** Vẽ chuỗi chữ VT323 kèm bóng đổ 2px (kiểu giao diện Minecraft). */
    public void drawTextWithShadow(ShaderProgram shader, CharSequence text, float x, float y,
                                   float pixelHeight, float r, float g, float b, float a) {
        float shadowOffset = Math.max(1.2f, pixelHeight * 0.08f);
        drawText(shader, text, x + shadowOffset, y + shadowOffset, pixelHeight,
                r * 0.18f, g * 0.18f, b * 0.18f, a * 0.90f);
        drawText(shader, text, x, y, pixelHeight, r, g, b, a);
    }

    /** Vẽ chuỗi chữ VT323 căn giữa ngang quanh {@code centerX} kèm bóng đổ. */
    public void drawCenteredTextWithShadow(ShaderProgram shader, CharSequence text,
                                           float centerX, float y, float pixelHeight,
                                           float r, float g, float b, float a) {
        float w = measureWidth(text, pixelHeight);
        drawTextWithShadow(shader, text, centerX - w * 0.5f, y, pixelHeight, r, g, b, a);
    }

    /**
     * Vẽ chuỗi chữ VT323 xoay quanh tâm {@code (centerX, centerY)} theo góc {@code angleDeg} (độ),
     * dùng cho dòng splash vàng nghiêng {@code "OOP Project!"} trên màn hình chính.
     */
    public void drawRotatedCenteredText(ShaderProgram shader, CharSequence text,
                                        float centerX, float centerY, float pixelHeight,
                                        float angleDeg, float r, float g, float b, float a) {
        if (!isReady() || shader == null || text == null || text.isEmpty()) {
            return;
        }
        com.badlogic.gdx.graphics.g2d.BitmapFont font = pickFont(pixelHeight);
        BitmapFontData data = font.getData();
        float scale = computeScale(data, pixelHeight);
        float totalW = measureWidth(text, pixelHeight);
        float totalH = pixelHeight;

        float rad = (float) Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        // Vẽ bóng đổ xoay trước, sau đó vẽ chữ chính
        drawRotatedPass(shader, font, data, text, centerX + 2.0f, centerY + 2.0f,
                totalW, totalH, scale, cos, sin, r * 0.22f, g * 0.22f, b * 0.05f, a * 0.92f);
        drawRotatedPass(shader, font, data, text, centerX, centerY,
                totalW, totalH, scale, cos, sin, r, g, b, a);
    }

    /** Một lượt vẽ chữ xoay 2D không cấp phát rác. */
    private void drawRotatedPass(ShaderProgram shader, com.badlogic.gdx.graphics.g2d.BitmapFont font,
                                 BitmapFontData data, CharSequence text,
                                 float centerX, float centerY, float totalW, float totalH,
                                 float scale, float cos, float sin,
                                 float r, float g, float b, float a) {
        shader.setUniform1i("uUseTexture", 1);
        shader.setUniformVec4("uTintColor", r, g, b, a);

        float localX = -totalW * 0.5f;
        float localTopY = -totalH * 0.5f;
        float topOffset = -data.ascent * scale;

        int currentPage = -1;
        int floatCount = 0;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            Glyph glyph = resolveGlyph(data, text.charAt(i));
            if (glyph == null) {
                continue;
            }
            if (glyph.width > 0 && glyph.height > 0) {
                if (glyph.page != currentPage) {
                    if (floatCount > 0) {
                        flushBatch(shader, font, currentPage, floatCount);
                        floatCount = 0;
                    }
                    currentPage = glyph.page;
                }
                if (floatCount + FLOATS_PER_QUAD > vertexBuffer.length) {
                    flushBatch(shader, font, currentPage, floatCount);
                    floatCount = 0;
                }

                float lx0 = localX + glyph.xoffset * scale;
                float ly0 = localTopY + (glyph.yoffset * scale) - topOffset;
                float lx1 = lx0 + glyph.width * scale;
                float ly1 = ly0 + glyph.height * scale;

                // Xoay 4 góc quanh (centerX, centerY)
                float ax = centerX + lx0 * cos - ly1 * sin;
                float ay = centerY + lx0 * sin + ly1 * cos;
                float bx = centerX + lx1 * cos - ly1 * sin;
                float by = centerY + lx1 * sin + ly1 * cos;
                float cx = centerX + lx1 * cos - ly0 * sin;
                float cy = centerY + lx1 * sin + ly0 * cos;
                float dx = centerX + lx0 * cos - ly0 * sin;
                float dy = centerY + lx0 * sin + ly0 * cos;

                float u0 = glyph.u;
                float u1 = glyph.u2;
                float vTop = Math.min(glyph.v, glyph.v2);
                float vBottom = Math.max(glyph.v, glyph.v2);

                BitmapFont.writeQuad(vertexBuffer, floatCount,
                        ax, ay, 0.0f,
                        bx, by, 0.0f,
                        cx, cy, 0.0f,
                        dx, dy, 0.0f,
                        u0, vTop, u1, vBottom);
                floatCount += FLOATS_PER_QUAD;
            }
            localX += glyph.xadvance * scale;
        }

        if (floatCount > 0 && currentPage >= 0) {
            flushBatch(shader, font, currentPage, floatCount);
        }
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        shader.setUniform1i("uUseTexture", 0);
    }

    /** Đẩy mẻ đỉnh glyph hiện tại lên GPU với texture page tương ứng. */
    private void flushBatch(ShaderProgram shader, com.badlogic.gdx.graphics.g2d.BitmapFont font,
                            int page, int floatCount) {
        Texture texture = font.getRegion(page).getTexture();
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        texture.bind(0);
        mesh.setVertices(vertexBuffer, 0, floatCount);
        mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 0, floatCount / 6);
    }

    /** Tra cứu glyph từ {@link BitmapFontData}, dự phòng dấu khoảng trắng nếu ký tự lạ. */
    private static Glyph resolveGlyph(BitmapFontData data, char ch) {
        Glyph g = data.getGlyph(ch);
        if (g == null && ch != ' ') {
            g = data.getGlyph('?');
        }
        return g;
    }

    /** Giải phóng toàn bộ atlas texture và lưới GPU theo giao ước {@link Disposable}. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        ready = false;
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (bodyFont != null) {
            bodyFont.dispose();
            bodyFont = null;
        }
        if (titleFont != null) {
            titleFont.dispose();
            titleFont = null;
        }
    }
}
