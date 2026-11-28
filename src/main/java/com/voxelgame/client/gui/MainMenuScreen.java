package com.voxelgame.client.gui;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.FirstPersonRenderer;
import com.voxelgame.client.render.ShaderProgram;

/**
 * Màn hình chính khi khởi chạy trò chơi ({@code MainMenuScreen}), kế thừa {@link MenuScreen}.
 *
 * <p>Bố cục đúng theo thiết kế mẫu:</p>
 * <ul>
 *     <li>Nền xám than tối ({@code #262A2D}).</li>
 *     <li>Tiêu đề lớn {@code OurCraft} màu trắng có khối đổ bóng nổi 3D và dòng splash vàng nghiêng
 *     {@code OOP Project!} phập phồng nhẹ kiểu Minecraft.</li>
 *     <li>Hai nút bấm kiểu Minecraft xếp dọc căn giữa: {@code Play} và {@code Settings}.</li>
 *     <li>Nhân vật 3D Steve đứng bên phải có hoạt ảnh thở nhẹ và quay đầu nhìn theo con trỏ chuột.</li>
 * </ul>
 */
public class MainMenuScreen extends MenuScreen {

    /** Hành động người dùng chọn trên màn hình chính. */
    public enum Action {
        NONE,
        PLAY,
        SETTINGS
    }

    /** Hai khung kiểm tra chuột tái sử dụng cho nút Play và nút Settings. */
    private final float[] playButtonBounds = new float[4];
    private final float[] settingsButtonBounds = new float[4];

    /** Bộ đếm thời gian hoạt ảnh cho splash text và mô hình Steve 3D. */
    private float animationTime = 0.0f;

    /** Cập nhật bộ đếm thời gian hoạt ảnh mỗi khung hình. */
    public void update(float deltaTime) {
        animationTime += deltaTime;
    }

    /** Cập nhật tọa độ khung 2 nút Play và Settings căn giữa màn hình. */
    private void layoutButtons(int screenW, int screenH) {
        float bx = (screenW - WIDGET_WIDTH) * 0.5f;
        float playY = screenH * 0.50f;
        setBounds(playButtonBounds, bx, playY, WIDGET_WIDTH, WIDGET_HEIGHT);
        setBounds(settingsButtonBounds, bx, playY + WIDGET_HEIGHT + 18.0f, WIDGET_WIDTH, WIDGET_HEIGHT);
    }

    /**
     * Kết xuất toàn bộ giao diện Main Menu: nền xám tối, logo OurCraft + splash "OOP Project!",
     * 2 nút Play/Settings và mô hình Steve 3D bên phải.
     */
    public void render(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                       FirstPersonRenderer steveRenderer, int screenW, int screenH,
                       double mouseX, double mouseY) {
        if (shader == null || hud == null) {
            return;
        }
        Vt323FontRenderer font = hud.ensureFont();

        // 1. Nền xám than đồng nhất (#262A2D)
        drawBackdrop(shader, hud, screenW, screenH, 0.15f, 0.165f, 0.175f, 1.0f);

        // 2. Tiêu đề "OurCraft" màu trắng có đổ bóng 3D dày giống ảnh mẫu
        float centerX = screenW * 0.5f;
        float titlePxH = Math.max(68.0f, Math.min(104.0f, screenH * 0.125f));
        float titleY = screenH * 0.19f;
        String title = "OurCraft";

        float titleWidth;
        if (font != null && font.isReady()) {
            titleWidth = font.measureWidth(title, titlePxH);
            float titleX = centerX - titleWidth * 0.5f;
            // Đổ bóng khối 3D xám đậm dày bên dưới
            for (int depth = 6; depth >= 1; depth--) {
                float shade = 0.28f + depth * 0.02f;
                font.drawText(shader, title, titleX + depth * 0.7f, titleY + depth * 1.4f,
                        titlePxH, shade, shade, shade + 0.02f, 1.0f);
            }
            font.drawText(shader, title, titleX, titleY, titlePxH, 0.96f, 0.97f, 0.99f, 1.0f);

            // 3. Dòng splash vàng nghiêng "OOP Project!" phập phồng nhẹ kiểu Minecraft
            float pulse = 1.0f + 0.07f * (float) Math.abs(Math.sin(animationTime * 3.4f));
            float splashPxH = 28.0f * pulse;
            float splashX = centerX + titleWidth * 0.44f;
            float splashY = titleY + titlePxH * 0.88f;
            font.drawRotatedCenteredText(shader, "OOP Project!", splashX, splashY,
                    splashPxH, -18.0f, 1.0f, 0.95f, 0.12f, 1.0f);
        } else {
            float scale = 7.0f;
            titleWidth = BitmapFont.measureWidth(title, scale);
            float titleX = centerX - titleWidth * 0.5f;
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.30f, 0.31f, 0.34f, 1.0f);
            hud.drawPixelText(title, titleX + 4.0f, titleY + 6.0f, scale);
            shader.setUniformVec4("uTintColor", 0.96f, 0.97f, 0.99f, 1.0f);
            hud.drawPixelText(title, titleX, titleY, scale);
        }

        // 4. Hai nút Play và Settings kiểu Minecraft xếp dọc căn giữa
        layoutButtons(screenW, screenH);
        drawButton(shader, hud, atlas, font, playButtonBounds, "Play",
                isPointInRect(mouseX, mouseY, playButtonBounds));
        drawButton(shader, hud, atlas, font, settingsButtonBounds, "Settings",
                isPointInRect(mouseX, mouseY, settingsButtonBounds));

        // 5. Mô hình nhân vật Steve 3D đứng bên phải (thở nhẹ + quay đầu nhìn theo chuột)
        if (steveRenderer != null) {
            steveRenderer.renderMenuSteve(shader, screenW, screenH,
                    (float) mouseX, (float) mouseY, animationTime);
        }
    }

    /** Kiểm tra nhấn chuột trái lên 2 nút Play hoặc Settings. */
    public Action handleMouseDown(float mouseX, float mouseY, int screenW, int screenH,
                                  AudioManager audioManager) {
        layoutButtons(screenW, screenH);
        if (isPointInRect(mouseX, mouseY, playButtonBounds)) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
            }
            return Action.PLAY;
        }
        if (isPointInRect(mouseX, mouseY, settingsButtonBounds)) {
            if (audioManager != null) {
                audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
            }
            return Action.SETTINGS;
        }
        return Action.NONE;
    }
}
