package com.voxelgame.client.gui;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.ShaderProgram;

/**
 * Màn hình thông tin nhóm thực hiện ({@code Credits}), kế thừa
 * {@link MenuScreen}.
 *
 * <p>
 * Hiển thị tiêu đề {@code Credits} và danh sách 4 thành viên có đầy đủ dấu
 * tiếng Việt được căn lề giữa
 * bằng font VT323, kèm nút {@code Back} để quay lại màn hình cha
 * ({@link SettingsScreen} hoặc
 * {@link PauseScreen}).
 * </p>
 */
public class CreditsScreen extends MenuScreen {

    /** Danh sách chính xác các thành viên nhóm (tất cả được căn lề giữa). */
    private static final String[] TEAM_MEMBERS = {
            "Nguyễn Tử Khuyến - 202516408",
            "Đặng Vương Trung Hiếu - 202516087",
            "Phạm Thế Dũng - 202516352",
            "Điền Hải Đăng - 202516334"
    };

    /** Khung nút Back tái sử dụng cho cả vẽ và kiểm tra click chuột. */
    private final float[] backButtonBounds = new float[4];

    /**
     * Độ mờ lớp nền (đồng bộ với màn hình cha: 1.0f từ Settings, 0.84f từ Pause).
     */
    private float backdropAlpha = 1.0f;

    /** Cập nhật độ mờ lớp nền cho khớp với màn hình cha vừa mở Credits. */
    public void setBackdropAlpha(float alpha) {
        this.backdropAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    /** Tính khung nút Back căn giữa phía dưới danh sách thành viên. */
    private float[] getBackButtonBounds(int screenW, int screenH) {
        float bx = (screenW - WIDGET_WIDTH) * 0.5f;
        float by = Math.min(screenH - WIDGET_HEIGHT - 36.0f, screenH * 0.74f);
        return setBounds(backButtonBounds, bx, by, WIDGET_WIDTH, WIDGET_HEIGHT);
    }

    /**
     * Kết xuất màn hình Credits với toàn bộ tên thành viên căn lề giữa bằng font
     * VT323.
     */
    public void render(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
            int screenW, int screenH, double mouseX, double mouseY) {
        if (shader == null || hud == null) {
            return;
        }
        Vt323FontRenderer font = hud.ensureFont();
        drawBackdrop(shader, hud, screenW, screenH, 0.04f, 0.05f, 0.07f, backdropAlpha);

        float centerX = screenW * 0.5f;
        float titleY = Math.max(48.0f, screenH * 0.20f);
        drawMenuTitle(shader, hud, font, "Credits", centerX, titleY, 38.0f);

        // Khung chứa danh sách thành viên căn giữa
        float linePxH = 30.0f;
        float lineStep = 44.0f;
        float listStartY = titleY + 76.0f;

        for (int i = 0; i < TEAM_MEMBERS.length; i++) {
            String member = TEAM_MEMBERS[i];
            float y = listStartY + i * lineStep;
            if (font != null && font.isReady()) {
                font.drawCenteredTextWithShadow(shader, member, centerX, y, linePxH,
                        0.95f, 0.96f, 0.98f, 1.0f);
            } else {
                float scale = 2.0f;
                float w = BitmapFont.measureWidth(member, scale);
                shader.setUniform1i("uUseTexture", 0);
                shader.setUniformVec4("uTintColor", 0.95f, 0.96f, 0.98f, 1.0f);
                hud.drawPixelText(member, centerX - w * 0.5f, y, scale);
            }
        }

        float[] backBounds = getBackButtonBounds(screenW, screenH);
        drawButton(shader, hud, atlas, font, backBounds, "Back",
                isPointInRect(mouseX, mouseY, backBounds));
    }

    /**
     * Kiểm tra nhấn chuột lên nút Back; trả {@code true} nếu cần quay lại màn hình
     * trước.
     */
    public boolean handleMouseDown(float mouseX, float mouseY, int screenW, int screenH,
            AudioManager audioManager) {
        if (isPointInRect(mouseX, mouseY, getBackButtonBounds(screenW, screenH))) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
            }
            return true;
        }
        return false;
    }
}
