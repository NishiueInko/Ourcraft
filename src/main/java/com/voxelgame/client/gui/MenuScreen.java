package com.voxelgame.client.gui;

import com.voxelgame.client.render.ShaderProgram;

/**
 * Lớp trừu tượng trung gian cho mọi màn hình Menu ({@code MainMenuScreen}, {@code SettingsScreen},
 * {@code PauseScreen}, {@code HotkeysScreen}, {@code CreditsScreen}), kế thừa {@link Screen}.
 *
 * <p>THỂ HIỆN CÁC TÍNH CHẤT OOP:</p>
 * <ul>
 *     <li><b>Kế thừa (Inheritance):</b> Gom toàn bộ tiện ích vẽ nút bấm kiểu Minecraft, thanh kéo Slider,
 *     tiêu đề màu vàng và kiểm tra va chạm chuột về một lớp cha duy nhất, tránh trùng lặp mã nguồn.</li>
 *     <li><b>Đóng gói (Encapsulation):</b> Che giấu hằng số kích thước chuẩn, màu sắc và logic vẽ sprite widget.</li>
 *     <li><b>Đa hình (Polymorphism):</b> Mọi lớp Menu con đều mặc định tạm dừng thế giới ({@link #pausesWorld()}
 *     trả {@code true}) và tự cài đặt bố cục cùng phản hồi tương tác riêng.</li>
 * </ul>
 */
public abstract class MenuScreen extends Screen {

    /** Chiều rộng và chiều cao chuẩn của nút bấm và thanh trượt kiểu Minecraft. */
    protected static final float WIDGET_WIDTH = 340.0f;
    protected static final float WIDGET_HEIGHT = 42.0f;
    protected static final float WIDGET_GAP = 14.0f;

    /** Mọi màn hình Menu đều tạm dừng vòng cập nhật thế giới khi đang mở. */
    @Override
    public boolean pausesWorld() {
        return true;
    }

    /** Vẽ lớp nền tối toàn màn hình (đặc hoàn toàn ở Main Menu/Settings, bán trong suốt ở Pause). */
    protected static void drawBackdrop(ShaderProgram shader, HudRenderer hud,
                                       int screenW, int screenH,
                                       float r, float g, float b, float a) {
        hud.setCurrentShader(shader);
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", r, g, b, a);
        hud.drawRect2D(0.0f, 0.0f, screenW, screenH);
    }

    /** Vẽ tiêu đề màu vàng nhạt có bóng đổ căn giữa ngang (kiểu tiêu đề "Settings" trong ảnh mẫu). */
    protected static void drawMenuTitle(ShaderProgram shader, HudRenderer hud, Vt323FontRenderer font,
                                        String title, float centerX, float y, float pixelHeight) {
        if (font != null && font.isReady()) {
            font.drawCenteredTextWithShadow(shader, title, centerX, y, pixelHeight,
                    1.0f, 0.96f, 0.56f, 1.0f);
        } else {
            float scale = Math.max(1.8f, pixelHeight / 12.0f);
            float w = BitmapFont.measureWidth(title, scale);
            float tx = centerX - w * 0.5f;
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.08f, 0.08f, 0.08f, 0.92f);
            hud.drawPixelText(title, tx + 2.0f, y + 2.0f, scale);
            shader.setUniformVec4("uTintColor", 1.0f, 0.96f, 0.56f, 1.0f);
            hud.drawPixelText(title, tx, y, scale);
        }
    }

    /** Vẽ nút bấm phong cách Minecraft (đá xám viền đen, sáng khi hover) cùng nhãn VT323 căn giữa. */
    protected static void drawButton(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                                     Vt323FontRenderer font, float[] bounds, String label, boolean hovered) {
        hud.setCurrentShader(shader);
        if (atlas != null) {
            drawWidgetSprite(shader, hud, atlas, hovered
                    ? GuiTextureAtlas.SpriteKey.WIDGET_BUTTON_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_BUTTON, bounds);
        } else {
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.04f, 0.04f, 0.05f, 1.0f);
            hud.drawRect2D(bounds[0] - 2.0f, bounds[1] - 2.0f, bounds[2] + 4.0f, bounds[3] + 4.0f);
            shader.setUniformVec4("uTintColor",
                    hovered ? 0.50f : 0.42f,
                    hovered ? 0.52f : 0.43f,
                    hovered ? 0.58f : 0.45f, 1.0f);
            hud.drawRect2D(bounds[0], bounds[1], bounds[2], bounds[3]);
        }

        float textPxH = 24.0f;
        float cx = bounds[0] + bounds[2] * 0.5f;
        float textR = hovered ? 1.0f : 0.96f;
        float textG = hovered ? 1.0f : 0.96f;
        float textB = hovered ? 0.65f : 0.96f;

        if (font != null && font.isReady()) {
            float ty = bounds[1] + (bounds[3] - textPxH) * 0.5f;
            font.drawCenteredTextWithShadow(shader, label, cx, ty, textPxH, textR, textG, textB, 1.0f);
        } else {
            float scale = 2.0f;
            float w = BitmapFont.measureWidth(label, scale);
            float tx = cx - w * 0.5f;
            float ty = bounds[1] + (bounds[3] - BitmapFont.measureHeight(scale)) * 0.5f;
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.10f, 0.10f, 0.10f, 0.92f);
            hud.drawPixelText(label, tx + 2.0f, ty + 2.0f, scale);
            shader.setUniformVec4("uTintColor", textR, textG, textB, 1.0f);
            hud.drawPixelText(label, tx, ty, scale);
        }
    }

    /**
     * Vẽ thanh trượt (Slider) phong cách Minecraft khớp ảnh Settings:
     * nền thanh tối, con chạy dọc trượt theo {@code ratio} [0..1] và nhãn chữ căn giữa ngay trên thanh.
     */
    protected static void drawSlider(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                                     Vt323FontRenderer font, float[] bounds, String label,
                                     float ratio, boolean hovered) {
        hud.setCurrentShader(shader);
        float clamped = Math.max(0.0f, Math.min(1.0f, ratio));
        float knobW = 14.0f;
        float knobX = bounds[0] + (bounds[2] - knobW) * clamped;

        if (atlas != null) {
            drawWidgetSprite(shader, hud, atlas, hovered
                    ? GuiTextureAtlas.SpriteKey.WIDGET_SLIDER_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_SLIDER, bounds);

            float[] handleUv = GuiTextureAtlas.uvOf(hovered
                    ? GuiTextureAtlas.SpriteKey.WIDGET_SLIDER_HANDLE_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_SLIDER_HANDLE);
            atlas.bindHudAtlas();
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            hud.drawTexturedRect2D(knobX, bounds[1], knobW, bounds[3],
                    handleUv[0], handleUv[1], handleUv[2], handleUv[3]);
            atlas.unbind();
        } else {
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.02f, 0.02f, 0.03f, 1.0f);
            hud.drawRect2D(bounds[0] - 2.0f, bounds[1] - 2.0f, bounds[2] + 4.0f, bounds[3] + 4.0f);
            shader.setUniformVec4("uTintColor", 0.14f, 0.14f, 0.16f, 1.0f);
            hud.drawRect2D(bounds[0], bounds[1], bounds[2], bounds[3]);
            shader.setUniformVec4("uTintColor", hovered ? 0.78f : 0.62f, hovered ? 0.80f : 0.64f,
                    hovered ? 0.86f : 0.68f, 1.0f);
            hud.drawRect2D(knobX, bounds[1], knobW, bounds[3]);
        }

        float textPxH = 24.0f;
        float cx = bounds[0] + bounds[2] * 0.5f;
        float textR = hovered ? 1.0f : 0.96f;
        float textG = hovered ? 1.0f : 0.96f;
        float textB = hovered ? 0.65f : 0.96f;
        if (font != null && font.isReady()) {
            float ty = bounds[1] + (bounds[3] - textPxH) * 0.5f;
            font.drawCenteredTextWithShadow(shader, label, cx, ty, textPxH, textR, textG, textB, 1.0f);
        } else {
            float scale = 2.0f;
            float w = BitmapFont.measureWidth(label, scale);
            float tx = cx - w * 0.5f;
            float ty = bounds[1] + (bounds[3] - BitmapFont.measureHeight(scale)) * 0.5f;
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.08f, 0.08f, 0.08f, 0.92f);
            hud.drawPixelText(label, tx + 2.0f, ty + 2.0f, scale);
            shader.setUniformVec4("uTintColor", textR, textG, textB, 1.0f);
            hud.drawPixelText(label, tx, ty, scale);
        }
    }

    /** Vẽ một sprite widget từ {@link GuiTextureAtlas} khớp đúng khung {@code bounds}. */
    protected static void drawWidgetSprite(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                                           GuiTextureAtlas.SpriteKey key, float[] bounds) {
        float[] uv = GuiTextureAtlas.uvOf(key);
        atlas.bindHudAtlas();
        shader.setUniform1i("uUseTexture", 1);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        hud.drawTexturedRect2D(bounds[0], bounds[1], bounds[2], bounds[3], uv[0], uv[1], uv[2], uv[3]);
        atlas.unbind();
    }

    /** Cập nhật 4 thành phần {@code [x, y, w, h]} của khung kiểm tra chuột mà không cấp phát mảng mới. */
    protected static float[] setBounds(float[] bounds, float x, float y, float width, float height) {
        bounds[0] = x;
        bounds[1] = y;
        bounds[2] = width;
        bounds[3] = height;
        return bounds;
    }

    /** Kiểm tra tọa độ con trỏ {@code (mx, my)} có nằm trong hình chữ nhật {@code rect} hay không. */
    protected static boolean isPointInRect(double mx, double my, float[] rect) {
        return mx >= rect[0] && mx <= (rect[0] + rect[2])
                && my >= rect[1] && my <= (rect[1] + rect[3]);
    }

    /** Tính tỷ lệ [0.0..1.0] theo vị trí X của chuột trên thanh trượt, trả {@code -1.0f} nếu ngoài vùng. */
    protected static float computeSliderRatio(double mx, double my, float[] rect, boolean allowVerticalOverflow) {
        boolean insideY = allowVerticalOverflow
                || (my >= rect[1] - 4.0f && my <= rect[1] + rect[3] + 4.0f);
        boolean insideX = allowVerticalOverflow
                || (mx >= rect[0] - 6.0f && mx <= rect[0] + rect[2] + 6.0f);
        if (!insideX || !insideY) {
            return -1.0f;
        }
        float ratio = (float) ((mx - rect[0]) / Math.max(1.0f, rect[2]));
        return Math.max(0.0f, Math.min(1.0f, ratio));
    }
}
