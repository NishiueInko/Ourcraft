package com.voxelgame.client.gui;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.ShaderProgram;

/**
 * Màn hình danh sách phím tắt ({@code Hotkeys}), kế thừa {@link MenuScreen}.
 *
 * <p>Hiển thị toàn bộ các phím điều khiển và phím tắt hiện có trong game theo bố cục 2 cột gọn gàng
 * bằng font VT323, kèm nút {@code Back} để quay lại màn hình cha ({@link SettingsScreen} hoặc
 * {@link PauseScreen}).</p>
 */
public class HotkeysScreen extends MenuScreen {

    /** Danh sách đầy đủ các phím tắt và chức năng hiện có trong dự án (mảng tĩnh zero-GC). */
    private static final String[][] HOTKEY_ENTRIES = {
            {"W / A / S / D", "Move"},
            {"Mouse", "Look Around"},
            {"Space", "Jump / Swim"},
            {"Space x2", "Toggle Fly (Creative)"},
            {"Left Shift", "Sneak / Descend / Dismount"},
            {"Left Ctrl", "Sprint / Sprint-Swim"},
            {"Left Click", "Break Block / Attack"},
            {"Right Click", "Place Block / Use / Interact"},
            {"1 - 9 / Scroll", "Select Hotbar Slot"},
            {"E", "Open Inventory / Creative Menu"},
            {"Q / Ctrl+Q", "Drop Item / Drop Stack"},
            {"ESC", "Pause / Back"},
            {"F1", "Cycle Speed (Creative)"},
            {"F2", "Toggle Creative / Survival"},
            {"F3", "Toggle Debug Overlay"},
            {"F5", "Cycle Camera View"},
            {"F6", "Cycle Weather"},
            {"F7", "Advance Time of Day"},
            {"F8", "Toggle 20x Time Speed"},
            {"Ctrl + C", "Next Biome (Creative)"}
    };

    /** Khung nút Back tái sử dụng cho cả vẽ và kiểm tra click chuột. */
    private final float[] backButtonBounds = new float[4];

    /** Độ mờ lớp nền (đồng bộ với màn hình cha: 1.0f từ Settings, 0.84f từ Pause). */
    private float backdropAlpha = 1.0f;

    /** Cập nhật độ mờ lớp nền cho khớp với màn hình cha vừa mở Hotkeys. */
    public void setBackdropAlpha(float alpha) {
        this.backdropAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    /** Tính khung nút Back căn giữa phía dưới màn hình. */
    private float[] getBackButtonBounds(int screenW, int screenH) {
        float bx = (screenW - WIDGET_WIDTH) * 0.5f;
        float by = Math.min(screenH - WIDGET_HEIGHT - 28.0f, screenH * 0.84f);
        return setBounds(backButtonBounds, bx, by, WIDGET_WIDTH, WIDGET_HEIGHT);
    }

    /** Kết xuất màn hình Hotkeys theo bố cục 2 cột rõ ràng bằng font VT323. */
    public void render(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                       int screenW, int screenH, double mouseX, double mouseY) {
        if (shader == null || hud == null) {
            return;
        }
        Vt323FontRenderer font = hud.ensureFont();
        drawBackdrop(shader, hud, screenW, screenH, 0.04f, 0.05f, 0.07f, backdropAlpha);

        float centerX = screenW * 0.5f;
        float titleY = Math.max(26.0f, screenH * 0.07f);
        drawMenuTitle(shader, hud, font, "Hotkeys", centerX, titleY, 36.0f);

        // Khung bảng 2 cột chứa 20 phím tắt (10 dòng mỗi cột)
        int rowsPerCol = (HOTKEY_ENTRIES.length + 1) / 2;
        float panelW = Math.min(screenW - 60.0f, 920.0f);
        float panelX = (screenW - panelW) * 0.5f;
        float panelY = titleY + 52.0f;
        float backY = getBackButtonBounds(screenW, screenH)[1];
        float panelH = Math.max(220.0f, backY - panelY - 22.0f);

        // Nền bảng tối nhẹ có viền mảnh giúp đọc dễ dàng
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.28f, 0.30f, 0.36f, 0.65f);
        hud.drawRect2D(panelX - 2.0f, panelY - 2.0f, panelW + 4.0f, panelH + 4.0f);
        shader.setUniformVec4("uTintColor", 0.08f, 0.09f, 0.13f, 0.88f);
        hud.drawRect2D(panelX, panelY, panelW, panelH);

        // Đường kẻ chia giữa 2 cột
        shader.setUniformVec4("uTintColor", 0.25f, 0.27f, 0.33f, 0.55f);
        hud.drawRect2D(centerX - 1.0f, panelY + 12.0f, 2.0f, panelH - 24.0f);

        float colW = panelW * 0.5f;
        float padX = 22.0f;
        float padY = 16.0f;
        float rowStep = (panelH - padY * 2.0f) / Math.max(1, rowsPerCol);
        float textPxH = Math.max(17.0f, Math.min(22.0f, rowStep * 0.68f));

        for (int i = 0; i < HOTKEY_ENTRIES.length; i++) {
            int col = i / rowsPerCol;
            int row = i % rowsPerCol;
            float colStartX = panelX + col * colW + padX;
            float colEndX = panelX + (col + 1) * colW - padX;
            float ry = panelY + padY + row * rowStep + (rowStep - textPxH) * 0.5f;

            String keyName = HOTKEY_ENTRIES[i][0];
            String desc = HOTKEY_ENTRIES[i][1];

            if (font != null && font.isReady()) {
                font.drawTextWithShadow(shader, keyName, colStartX, ry, textPxH,
                        1.0f, 0.94f, 0.52f, 1.0f);
                float descW = font.measureWidth(desc, textPxH);
                font.drawTextWithShadow(shader, desc, colEndX - descW, ry, textPxH,
                        0.92f, 0.94f, 0.98f, 1.0f);
            } else {
                float scale = 1.4f;
                shader.setUniform1i("uUseTexture", 0);
                shader.setUniformVec4("uTintColor", 1.0f, 0.94f, 0.52f, 1.0f);
                hud.drawPixelText(keyName, colStartX, ry, scale);
                float descW = BitmapFont.measureWidth(desc, scale);
                shader.setUniformVec4("uTintColor", 0.92f, 0.94f, 0.98f, 1.0f);
                hud.drawPixelText(desc, colEndX - descW, ry, scale);
            }
        }

        // Nút Back căn giữa phía dưới
        float[] backBounds = getBackButtonBounds(screenW, screenH);
        drawButton(shader, hud, atlas, font, backBounds, "Back",
                isPointInRect(mouseX, mouseY, backBounds));
    }

    /** Kiểm tra nhấn chuột lên nút Back; trả {@code true} nếu cần quay lại màn hình trước. */
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
