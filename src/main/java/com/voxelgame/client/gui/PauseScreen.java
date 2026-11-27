package com.voxelgame.client.gui;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.ShaderProgram;

/**
 * Màn hình Menu Tạm dừng ({@code ESC}), kế thừa {@link Screen}.
 *
 * Quản lý trọn vẹn bố cục pixel, kiểm tra tọa độ chuột (Hit-Testing) và kết xuất 2D cho:
 * - Khung nền bán trong suốt và hộp thoại trung tâm "GAME PAUSED".
 * - Nút bấm "RESUME GAME".
 * - Cụm điều chỉnh âm lượng nhạc nền (nút {@code [-]}, thanh trượt Slider, nút {@code [+]}).
 * - Dòng gợi ý nhấn {@code ESC} lần 2 để thoát game.
 */
public class PauseScreen extends Screen {

    /** Kích thước các thành phần trong Pause Menu (tính bằng pixel màn hình). */
    private static final float PANEL_WIDTH = 420.0f;
    private static final float PANEL_HEIGHT = 270.0f;
    private static final float RESUME_BTN_WIDTH = 280.0f;
    private static final float RESUME_BTN_HEIGHT = 46.0f;
    private static final float VOL_BTN_SIZE = 42.0f;
    private static final float SLIDER_WIDTH = 176.0f;
    private static final float SLIDER_HEIGHT = 24.0f;

    /** Bảng nhãn "VOLUME: 0%..100%" tạo sẵn nhằm tránh cấp phát String trong Game Loop. */
    private static final String[] VOLUME_LABELS = new String[101];

    static {
        for (int i = 0; i < VOLUME_LABELS.length; i++) {
            VOLUME_LABELS[i] = "VOLUME: " + i + "%";
        }
    }

    /** Bốn hộp giới hạn dùng lại cho cả vẽ và kiểm tra click chuột (zero-GC). */
    private final float[] resumeButtonBounds = new float[4];
    private final float[] volumeMinusBounds = new float[4];
    private final float[] volumeSliderBounds = new float[4];
    private final float[] volumePlusBounds = new float[4];

    /** Pause Menu luôn tạm dừng vòng cập nhật thế giới khi đang mở. */
    @Override
    public boolean pausesWorld() {
        return true;
    }

    /**
     * Kết xuất toàn bộ giao diện Pause Menu bằng bộ đệm dùng lại của {@link HudRenderer}.
     */
    public void render(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                       AudioManager audioManager, int windowWidth, int windowHeight,
                       double mouseX, double mouseY) {
        if (shader == null || hud == null) {
            return;
        }
        hud.setCurrentShader(shader);
        shader.setUniform1i("uUseTexture", 0);

        // 1. Lớp màn tối bán trong suốt phủ toàn màn hình (Dim Backdrop)
        shader.setUniformVec4("uTintColor", 0.04f, 0.05f, 0.08f, 0.68f);
        hud.drawRect2D(0.0f, 0.0f, windowWidth, windowHeight);

        // 2. Khung hộp thoại trung tâm (Pause Panel)
        float panelX = (windowWidth - PANEL_WIDTH) * 0.5f;
        float panelY = (windowHeight - PANEL_HEIGHT) * 0.5f;

        shader.setUniformVec4("uTintColor", 0.65f, 0.68f, 0.75f, 0.95f);
        hud.drawRect2D(panelX - 3.0f, panelY - 3.0f, PANEL_WIDTH + 6.0f, PANEL_HEIGHT + 6.0f);

        shader.setUniformVec4("uTintColor", 0.14f, 0.16f, 0.21f, 0.96f);
        hud.drawRect2D(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // 3. Tiêu đề "GAME PAUSED"
        String title = "GAME PAUSED";
        float titleScale = 3.2f;
        float titleW = BitmapFont.measureWidth(title, titleScale);
        float titleX = (windowWidth - titleW) * 0.5f;
        float titleY = panelY + 24.0f;

        shader.setUniformVec4("uTintColor", 0.05f, 0.05f, 0.05f, 0.90f);
        hud.drawPixelText(title, titleX + 2.5f, titleY + 2.5f, titleScale);
        shader.setUniformVec4("uTintColor", 1.0f, 0.92f, 0.35f, 1.0f);
        hud.drawPixelText(title, titleX, titleY, titleScale);

        // 4. Ô nút bấm "RESUME GAME"
        float[] resumeBounds = getResumeButtonBounds(windowWidth, windowHeight);
        boolean hoverResume = isPointInRect(mouseX, mouseY, resumeBounds);
        if (atlas != null) {
            drawWidgetSprite(shader, hud, atlas, hoverResume
                    ? GuiTextureAtlas.SpriteKey.WIDGET_BUTTON_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_BUTTON, resumeBounds);
        } else {
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", hoverResume ? 1.0f : 0.75f, hoverResume ? 0.95f : 0.78f, 0.40f, 1.0f);
            hud.drawRect2D(resumeBounds[0] - 2.0f, resumeBounds[1] - 2.0f,
                    resumeBounds[2] + 4.0f, resumeBounds[3] + 4.0f);
            shader.setUniformVec4("uTintColor",
                    hoverResume ? 0.26f : 0.20f,
                    hoverResume ? 0.58f : 0.44f,
                    hoverResume ? 0.30f : 0.24f, 1.0f);
            hud.drawRect2D(resumeBounds[0], resumeBounds[1], resumeBounds[2], resumeBounds[3]);
        }

        String resumeLabel = "RESUME GAME";
        float resScale = 2.4f;
        float resW = BitmapFont.measureWidth(resumeLabel, resScale);
        float resX = resumeBounds[0] + (resumeBounds[2] - resW) * 0.5f;
        float resY = resumeBounds[1] + (resumeBounds[3] - BitmapFont.measureHeight(resScale)) * 0.5f;
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.12f, 0.12f, 0.12f, 0.92f);
        hud.drawPixelText(resumeLabel, resX + 2.0f, resY + 2.0f, resScale);
        shader.setUniformVec4("uTintColor", hoverResume ? 1.0f : 0.96f, hoverResume ? 1.0f : 0.96f,
                hoverResume ? 0.65f : 0.96f, 1.0f);
        hud.drawPixelText(resumeLabel, resX, resY, resScale);

        // 5. Khu vực điều chỉnh âm lượng nhạc nền (nút [-], thanh trượt, nút [+])
        int volPercent = (audioManager != null) ? audioManager.getVolumePercent() : 70;
        String volLabel = VOLUME_LABELS[Math.max(0, Math.min(100, volPercent))];
        float volLabelScale = 2.0f;
        float volLabelW = BitmapFont.measureWidth(volLabel, volLabelScale);
        shader.setUniformVec4("uTintColor", 0.88f, 0.90f, 0.95f, 1.0f);
        hud.drawPixelText(volLabel, (windowWidth - volLabelW) * 0.5f, panelY + 138.0f, volLabelScale);

        float[] minusBounds = getVolumeMinusBounds(windowWidth, windowHeight);
        boolean hoverMinus = isPointInRect(mouseX, mouseY, minusBounds);
        drawPauseSquareButton(shader, hud, atlas, minusBounds, hoverMinus, "-");

        float[] sliderBounds = getVolumeSliderBounds(windowWidth, windowHeight);
        float volRatio = (audioManager != null) ? audioManager.getVolume() : 0.7f;
        boolean hoverSlider = getSliderVolumeAtCursor(mouseX, mouseY, windowWidth, windowHeight) >= 0.0f;

        if (atlas != null) {
            drawWidgetSprite(shader, hud, atlas, hoverSlider
                    ? GuiTextureAtlas.SpriteKey.WIDGET_SLIDER_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_SLIDER, sliderBounds);

            float knobW = 14.0f;
            float knobH = sliderBounds[3] + 6.0f;
            float knobX = sliderBounds[0] + (sliderBounds[2] - knobW) * volRatio;
            float knobY = sliderBounds[1] - 3.0f;
            float[] handleUv = GuiTextureAtlas.uvOf(hoverSlider
                    ? GuiTextureAtlas.SpriteKey.WIDGET_SLIDER_HANDLE_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_SLIDER_HANDLE);
            atlas.bindHudAtlas();
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            hud.drawTexturedRect2D(knobX, knobY, knobW, knobH,
                    handleUv[0], handleUv[1], handleUv[2], handleUv[3]);
            atlas.unbind();
        } else {
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.08f, 0.09f, 0.12f, 1.0f);
            hud.drawRect2D(sliderBounds[0] - 2.0f, sliderBounds[1] - 2.0f,
                    sliderBounds[2] + 4.0f, sliderBounds[3] + 4.0f);

            shader.setUniformVec4("uTintColor", 0.24f, 0.65f, 0.92f, 1.0f);
            hud.drawRect2D(sliderBounds[0], sliderBounds[1], sliderBounds[2] * volRatio, sliderBounds[3]);

            float knobW = 12.0f;
            float knobX = sliderBounds[0] + sliderBounds[2] * volRatio - knobW * 0.5f;
            knobX = Math.max(sliderBounds[0], Math.min(sliderBounds[0] + sliderBounds[2] - knobW, knobX));
            shader.setUniformVec4("uTintColor", 1.0f, 0.95f, 0.50f, 1.0f);
            hud.drawRect2D(knobX, sliderBounds[1] - 5.0f, knobW, sliderBounds[3] + 10.0f);
        }

        float[] plusBounds = getVolumePlusBounds(windowWidth, windowHeight);
        boolean hoverPlus = isPointInRect(mouseX, mouseY, plusBounds);
        drawPauseSquareButton(shader, hud, atlas, plusBounds, hoverPlus, "+");

        // 6. Dòng hướng dẫn bấm ESC lần 2 để thoát game
        String quitHint = "PRESS ESC AGAIN TO QUIT";
        float hintScale = 1.6f;
        float hintW = BitmapFont.measureWidth(quitHint, hintScale);
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.75f, 0.78f, 0.84f, 0.90f);
        hud.drawPixelText(quitHint, (windowWidth - hintW) * 0.5f, panelY + PANEL_HEIGHT - 28.0f, hintScale);
    }

    /** Vẽ một widget hình chữ nhật từ {@link GuiTextureAtlas} khớp đúng khung hit-test. */
    private void drawWidgetSprite(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                                  GuiTextureAtlas.SpriteKey key, float[] bounds) {
        float[] uv = GuiTextureAtlas.uvOf(key);
        atlas.bindHudAtlas();
        shader.setUniform1i("uUseTexture", 1);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        hud.drawTexturedRect2D(bounds[0], bounds[1], bounds[2], bounds[3], uv[0], uv[1], uv[2], uv[3]);
        atlas.unbind();
    }

    /** Vẽ nút vuông [-]/[+] trên Pause Menu bằng widget Minecraft hoặc hình chữ nhật dự phòng. */
    private void drawPauseSquareButton(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                                       float[] bounds, boolean hovered, String symbol) {
        if (atlas != null) {
            drawWidgetSprite(shader, hud, atlas, hovered
                    ? GuiTextureAtlas.SpriteKey.WIDGET_BUTTON_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_BUTTON, bounds);
        } else {
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.75f, 0.78f, 0.82f, 1.0f);
            hud.drawRect2D(bounds[0] - 2.0f, bounds[1] - 2.0f, bounds[2] + 4.0f, bounds[3] + 4.0f);
            shader.setUniformVec4("uTintColor", hovered ? 0.42f : 0.26f, hovered ? 0.45f : 0.29f, 0.36f, 1.0f);
            hud.drawRect2D(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.12f, 0.12f, 0.12f, 0.92f);
        hud.drawPixelText(symbol, bounds[0] + 16.5f, bounds[1] + 12.5f, 2.8f);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        hud.drawPixelText(symbol, bounds[0] + 15.0f, bounds[1] + 11.0f, 2.8f);
    }

    // =========================================================================
    // CÁC HÀM KIỂM TRA TỌA ĐỘ CLICK CHUỘT TRONG PAUSE MENU (HIT-TESTING)
    // =========================================================================

    private float[] getResumeButtonBounds(int windowWidth, int windowHeight) {
        float panelY = (windowHeight - PANEL_HEIGHT) * 0.5f;
        float bx = (windowWidth - RESUME_BTN_WIDTH) * 0.5f;
        float by = panelY + 72.0f;
        return setBounds(resumeButtonBounds, bx, by, RESUME_BTN_WIDTH, RESUME_BTN_HEIGHT);
    }

    private float[] getVolumeMinusBounds(int windowWidth, int windowHeight) {
        float panelY = (windowHeight - PANEL_HEIGHT) * 0.5f;
        float totalRowW = VOL_BTN_SIZE + 12.0f + SLIDER_WIDTH + 12.0f + VOL_BTN_SIZE;
        float startX = (windowWidth - totalRowW) * 0.5f;
        float by = panelY + 168.0f;
        return setBounds(volumeMinusBounds, startX, by, VOL_BTN_SIZE, VOL_BTN_SIZE);
    }

    private float[] getVolumeSliderBounds(int windowWidth, int windowHeight) {
        float[] minus = getVolumeMinusBounds(windowWidth, windowHeight);
        float sx = minus[0] + VOL_BTN_SIZE + 12.0f;
        float sy = minus[1] + (VOL_BTN_SIZE - SLIDER_HEIGHT) * 0.5f;
        return setBounds(volumeSliderBounds, sx, sy, SLIDER_WIDTH, SLIDER_HEIGHT);
    }

    private float[] getVolumePlusBounds(int windowWidth, int windowHeight) {
        float[] slider = getVolumeSliderBounds(windowWidth, windowHeight);
        float[] minus = getVolumeMinusBounds(windowWidth, windowHeight);
        float px = slider[0] + SLIDER_WIDTH + 12.0f;
        return setBounds(volumePlusBounds, px, minus[1], VOL_BTN_SIZE, VOL_BTN_SIZE);
    }

    /** Cập nhật bốn thành phần của hộp thử chuột mà không tạo mảng. */
    private static float[] setBounds(float[] bounds, float x, float y, float width, float height) {
        bounds[0] = x;
        bounds[1] = y;
        bounds[2] = width;
        bounds[3] = height;
        return bounds;
    }

    private static boolean isPointInRect(double mx, double my, float[] rect) {
        return mx >= rect[0] && mx <= (rect[0] + rect[2])
                && my >= rect[1] && my <= (rect[1] + rect[3]);
    }

    /** Kiểm tra con trỏ chuột có nằm trong nút "RESUME GAME" hay không. */
    public boolean isInsideResumeButton(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return isPointInRect(mouseX, mouseY, getResumeButtonBounds(windowWidth, windowHeight));
    }

    /** Kiểm tra con trỏ chuột có nằm trong nút giảm âm lượng [-] hay không. */
    public boolean isInsideVolumeMinus(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return isPointInRect(mouseX, mouseY, getVolumeMinusBounds(windowWidth, windowHeight));
    }

    /** Kiểm tra con trỏ chuột có nằm trong nút tăng âm lượng [+] hay không. */
    public boolean isInsideVolumePlus(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return isPointInRect(mouseX, mouseY, getVolumePlusBounds(windowWidth, windowHeight));
    }

    /** Tính tỷ lệ âm lượng [0.0..1.0] nếu chuột nằm trong vùng thanh trượt, hoặc {@code -1.0f} nếu ngoài vùng. */
    public float getSliderVolumeAtCursor(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        float[] s = getVolumeSliderBounds(windowWidth, windowHeight);
        if (mouseX >= s[0] - 6.0f && mouseX <= s[0] + s[2] + 6.0f
                && mouseY >= s[1] - 10.0f && mouseY <= s[1] + s[3] + 10.0f) {
            float ratio = (float) ((mouseX - s[0]) / s[2]);
            return Math.max(0.0f, Math.min(1.0f, ratio));
        }
        return -1.0f;
    }
}
