package com.voxelgame.client.gui;

import com.voxelgame.client.render.ShaderProgram;

/**
 * Màn hình Menu Tạm dừng ({@code ESC}), kế thừa {@link AudioSettingsMenuScreen}
 * để dùng chung hoàn toàn bố cục và phong cách với {@link SettingsScreen}.
 *
 * <p>Trên nền thế giới làm tối bán trong suốt, hiển thị tiêu đề vàng {@code Game Paused},
 * thanh kéo {@code Music}, thanh kéo {@code Sound Effects}, nút {@code Hotkeys},
 * nút {@code Credits}, nút {@code Resume Game} và nút {@code Back to Menu}.</p>
 */
public class PauseScreen extends AudioSettingsMenuScreen {

    /** Khung nút Resume Game và nút Back to Menu tái sử dụng (zero-GC). */
    private final float[] resumeButtonBounds = new float[4];
    private final float[] backToMenuButtonBounds = new float[4];

    @Override
    protected String getTitle() {
        return "Game Paused";
    }

    @Override
    protected float getBackdropAlpha() {
        return 0.78f;
    }

    @Override
    protected int getFooterButtonCount() {
        return 2;
    }

    /** Cập nhật khung hai nút chân trang {@code Resume Game} và {@code Back to Menu}. */
    private void layoutFooterBounds(int screenW, float footerStartY) {
        float bx = (screenW - WIDGET_WIDTH) * 0.5f;
        setBounds(resumeButtonBounds, bx, footerStartY, WIDGET_WIDTH, WIDGET_HEIGHT);
        setBounds(backToMenuButtonBounds, bx, footerStartY + WIDGET_HEIGHT + WIDGET_GAP,
                WIDGET_WIDTH, WIDGET_HEIGHT);
    }

    @Override
    protected void renderFooterButtons(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                                       Vt323FontRenderer font, int screenW, int screenH,
                                       float footerStartY, double mouseX, double mouseY) {
        layoutFooterBounds(screenW, footerStartY);
        drawButton(shader, hud, atlas, font, resumeButtonBounds, "Resume Game",
                isPointInRect(mouseX, mouseY, resumeButtonBounds));
        drawButton(shader, hud, atlas, font, backToMenuButtonBounds, "Back to Menu",
                isPointInRect(mouseX, mouseY, backToMenuButtonBounds));
    }

    @Override
    protected Action handleFooterClick(float mouseX, float mouseY, int screenW, int screenH,
                                       float footerStartY) {
        layoutFooterBounds(screenW, footerStartY);
        if (isPointInRect(mouseX, mouseY, resumeButtonBounds)) {
            return Action.BACK_OR_RESUME;
        }
        if (isPointInRect(mouseX, mouseY, backToMenuButtonBounds)) {
            return Action.BACK_TO_MAIN_MENU;
        }
        return Action.NONE;
    }

    // =========================================================================
    // CÁC PHƯƠNG THỨC HIT-TESTING TƯƠNG THÍCH NGƯỢC
    // =========================================================================

    /** Kiểm tra con trỏ chuột có nằm trong nút "Resume Game" hay không. */
    public boolean isInsideResumeButton(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        float footerStartY = layoutCoreBounds(windowWidth, windowHeight);
        layoutFooterBounds(windowWidth, footerStartY);
        return isPointInRect(mouseX, mouseY, resumeButtonBounds);
    }

    /** Kiểm tra con trỏ chuột có nằm trong nút giảm âm lượng cũ (trả {@code false} vì đã thay bằng slider). */
    public boolean isInsideVolumeMinus(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return false;
    }

    /** Kiểm tra con trỏ chuột có nằm trong nút tăng âm lượng cũ (trả {@code false} vì đã thay bằng slider). */
    public boolean isInsideVolumePlus(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return false;
    }

    /** Tính tỷ lệ âm lượng nhạc [0.0..1.0] nếu chuột nằm trên thanh kéo Music, hoặc {@code -1.0f}. */
    public float getSliderVolumeAtCursor(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        layoutCoreBounds(windowWidth, windowHeight);
        return computeSliderRatio(mouseX, mouseY, musicSliderBounds, false);
    }
}
