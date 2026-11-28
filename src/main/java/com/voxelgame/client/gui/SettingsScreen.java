package com.voxelgame.client.gui;

import com.voxelgame.client.render.ShaderProgram;

/**
 * Màn hình Cài đặt ({@code Settings}) mở từ màn hình chính {@link MainMenuScreen},
 * kế thừa {@link AudioSettingsMenuScreen}.
 *
 * <p>Hiển thị nền tối đặc, tiêu đề vàng {@code Settings}, 2 thanh kéo {@code Music} và
 * {@code Sound Effects}, nút {@code Hotkeys}, nút {@code Credits} và nút {@code Back}
 * ở chân trang đúng như bố cục mẫu.</p>
 */
public class SettingsScreen extends AudioSettingsMenuScreen {

    /** Khung nút Back tái sử dụng cho vẽ và kiểm tra click chuột. */
    private final float[] backButtonBounds = new float[4];

    @Override
    protected String getTitle() {
        return "Settings";
    }

    @Override
    protected float getBackdropAlpha() {
        return 1.0f;
    }

    @Override
    protected int getFooterButtonCount() {
        return 1;
    }

    @Override
    protected void renderFooterButtons(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                                       Vt323FontRenderer font, int screenW, int screenH,
                                       float footerStartY, double mouseX, double mouseY) {
        float bx = (screenW - WIDGET_WIDTH) * 0.5f;
        setBounds(backButtonBounds, bx, footerStartY, WIDGET_WIDTH, WIDGET_HEIGHT);
        drawButton(shader, hud, atlas, font, backButtonBounds, "Back",
                isPointInRect(mouseX, mouseY, backButtonBounds));
    }

    @Override
    protected Action handleFooterClick(float mouseX, float mouseY, int screenW, int screenH,
                                       float footerStartY) {
        float bx = (screenW - WIDGET_WIDTH) * 0.5f;
        setBounds(backButtonBounds, bx, footerStartY, WIDGET_WIDTH, WIDGET_HEIGHT);
        if (isPointInRect(mouseX, mouseY, backButtonBounds)) {
            return Action.BACK_OR_RESUME;
        }
        return Action.NONE;
    }
}
