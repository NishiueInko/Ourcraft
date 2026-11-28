package com.voxelgame.client.gui;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.ShaderProgram;

/**
 * Lớp trừu tượng dùng chung bố cục cho cả {@link SettingsScreen} (từ Main Menu) và {@link PauseScreen}
 * (khi bấm {@code ESC} trong game), kế thừa {@link MenuScreen}.
 *
 * <p>Cung cấp đồng nhất 4 thành phần lõi xếp dọc căn giữa theo đúng ảnh mẫu Settings:</p>
 * <ol>
 *     <li>Thanh kéo {@code Music: XX%} (0–100%) điều khiển nhạc nền.</li>
 *     <li>Thanh kéo {@code Sound Effects: XX%} (0–100%) điều khiển toàn bộ hiệu ứng âm thanh.</li>
 *     <li>Nút {@code Hotkeys} mở màn hình danh sách phím tắt.</li>
 *     <li>Nút {@code Credits} mở màn hình danh sách thành viên nhóm.</li>
 * </ol>
 * <p>Các lớp con ghi đè đa hình tiêu đề ({@link #getTitle()}), độ mờ nền ({@link #getBackdropAlpha()})
 * và các nút chân trang ({@code Back} cho Settings; {@code Resume Game} + {@code Back to Menu} cho Pause).</p>
 */
public abstract class AudioSettingsMenuScreen extends MenuScreen {

    /** Định danh hành động trả về khi người dùng bấm nút trên màn hình Settings/Pause. */
    public enum Action {
        NONE,
        OPEN_HOTKEYS,
        OPEN_CREDITS,
        BACK_OR_RESUME,
        BACK_TO_MAIN_MENU
    }

    /** Trạng thái thanh trượt đang được người dùng kéo. */
    protected enum DragTarget {
        NONE,
        MUSIC,
        SFX
    }

    /** Bảng nhãn dựng sẵn "Music: 0%..100%" và "Sound Effects: 0%..100%" để đảm bảo zero-GC. */
    private static final String[] MUSIC_LABELS = new String[101];
    private static final String[] SFX_LABELS = new String[101];

    static {
        for (int i = 0; i <= 100; i++) {
            MUSIC_LABELS[i] = "Music: " + i + "%";
            SFX_LABELS[i] = "Sound Effects: " + i + "%";
        }
    }

    /** Bốn khung kiểm tra chuột tái sử dụng cho 2 thanh trượt và 2 nút phụ. */
    protected final float[] musicSliderBounds = new float[4];
    protected final float[] sfxSliderBounds = new float[4];
    protected final float[] hotkeysButtonBounds = new float[4];
    protected final float[] creditsButtonBounds = new float[4];

    /** Thanh trượt hiện đang được kéo (nếu có). */
    protected DragTarget activeDrag = DragTarget.NONE;

    /** Tiêu đề hiển thị ở đầu khối menu (ví dụ {@code "Settings"} hoặc {@code "Game Paused"}). */
    protected abstract String getTitle();

    /** Độ mờ của lớp nền tối phủ toàn màn hình (1.0f cho Settings, 0.78f cho Pause). */
    protected abstract float getBackdropAlpha();

    /** Số lượng nút chân trang (1 cho Settings, 2 cho Pause) dùng để căn giữa khối menu theo chiều dọc. */
    protected abstract int getFooterButtonCount();

    /** Vẽ các nút chân trang riêng của từng lớp con (Back hoặc Resume Game + Back to Menu). */
    protected abstract void renderFooterButtons(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                                                Vt323FontRenderer font, int screenW, int screenH,
                                                float footerStartY, double mouseX, double mouseY);

    /** Kiểm tra click chuột lên các nút chân trang riêng của từng lớp con. */
    protected abstract Action handleFooterClick(float mouseX, float mouseY, int screenW, int screenH,
                                                float footerStartY);

    /** Đặt lại trạng thái kéo thanh trượt khi đóng màn hình. */
    @Override
    protected void onClose() {
        activeDrag = DragTarget.NONE;
    }

    /** Tính tọa độ Y bắt đầu của tiêu đề để cả khối menu luôn căn giữa màn hình theo chiều dọc. */
    protected float computeTopY(int screenH) {
        float titleAreaH = 54.0f;
        float coreRowsH = 4 * WIDGET_HEIGHT + 3 * WIDGET_GAP;
        float footerGap = 24.0f;
        int footerCount = getFooterButtonCount();
        float footerH = footerCount * WIDGET_HEIGHT + Math.max(0, footerCount - 1) * WIDGET_GAP;
        float totalH = titleAreaH + coreRowsH + footerGap + footerH;
        return Math.max(28.0f, (screenH - totalH) * 0.5f);
    }

    /** Cập nhật toàn bộ khung hit-test của 4 thành phần lõi và trả về Y bắt đầu khu vực chân trang. */
    protected float layoutCoreBounds(int screenW, int screenH) {
        float topY = computeTopY(screenH);
        float bx = (screenW - WIDGET_WIDTH) * 0.5f;
        float y = topY + 54.0f;

        setBounds(musicSliderBounds, bx, y, WIDGET_WIDTH, WIDGET_HEIGHT);
        y += WIDGET_HEIGHT + WIDGET_GAP;

        setBounds(sfxSliderBounds, bx, y, WIDGET_WIDTH, WIDGET_HEIGHT);
        y += WIDGET_HEIGHT + WIDGET_GAP;

        setBounds(hotkeysButtonBounds, bx, y, WIDGET_WIDTH, WIDGET_HEIGHT);
        y += WIDGET_HEIGHT + WIDGET_GAP;

        setBounds(creditsButtonBounds, bx, y, WIDGET_WIDTH, WIDGET_HEIGHT);
        y += WIDGET_HEIGHT + 24.0f;

        return y;
    }

    /** Kết xuất toàn bộ giao diện Settings/Pause với font VT323 và bộ sprite widget Minecraft. */
    public void render(ShaderProgram shader, HudRenderer hud, GuiTextureAtlas atlas,
                       AudioManager audioManager, int screenW, int screenH,
                       double mouseX, double mouseY) {
        if (shader == null || hud == null) {
            return;
        }
        Vt323FontRenderer font = hud.ensureFont();
        drawBackdrop(shader, hud, screenW, screenH, 0.04f, 0.05f, 0.07f, getBackdropAlpha());

        float topY = computeTopY(screenH);
        float footerStartY = layoutCoreBounds(screenW, screenH);
        float centerX = screenW * 0.5f;

        // 1. Tiêu đề màu vàng căn giữa ("Settings" hoặc "Game Paused")
        drawMenuTitle(shader, hud, font, getTitle(), centerX, topY, 36.0f);

        // 2. Thanh trượt Music (0..100%)
        int musicPct = (audioManager != null) ? audioManager.getMusicVolumePercent() : 70;
        float musicRatio = (audioManager != null) ? audioManager.getMusicVolume() : 0.70f;
        boolean hoverMusic = activeDrag == DragTarget.MUSIC
                || isPointInRect(mouseX, mouseY, musicSliderBounds);
        drawSlider(shader, hud, atlas, font, musicSliderBounds,
                MUSIC_LABELS[Math.max(0, Math.min(100, musicPct))], musicRatio, hoverMusic);

        // 3. Thanh trượt Sound Effects (0..100%)
        int sfxPct = (audioManager != null) ? audioManager.getSfxVolumePercent() : 70;
        float sfxRatio = (audioManager != null) ? audioManager.getSfxVolume() : 0.70f;
        boolean hoverSfx = activeDrag == DragTarget.SFX
                || isPointInRect(mouseX, mouseY, sfxSliderBounds);
        drawSlider(shader, hud, atlas, font, sfxSliderBounds,
                SFX_LABELS[Math.max(0, Math.min(100, sfxPct))], sfxRatio, hoverSfx);

        // 4. Nút Hotkeys và nút Credits
        drawButton(shader, hud, atlas, font, hotkeysButtonBounds, "Hotkeys",
                isPointInRect(mouseX, mouseY, hotkeysButtonBounds));
        drawButton(shader, hud, atlas, font, creditsButtonBounds, "Credits",
                isPointInRect(mouseX, mouseY, creditsButtonBounds));

        // 5. Các nút chân trang đa hình do lớp con quyết định
        renderFooterButtons(shader, hud, atlas, font, screenW, screenH, footerStartY, mouseX, mouseY);
    }

    /**
     * Xử lý nhấn chuột trái trên màn hình Settings/Pause:
     * cập nhật ngay thanh kéo Music/SFX hoặc trả về {@link Action} tương ứng khi bấm nút.
     */
    public Action handleMouseDown(float mouseX, float mouseY, int screenW, int screenH,
                                  AudioManager audioManager) {
        float footerStartY = layoutCoreBounds(screenW, screenH);

        float musicRatio = computeSliderRatio(mouseX, mouseY, musicSliderBounds, false);
        if (musicRatio >= 0.0f) {
            activeDrag = DragTarget.MUSIC;
            if (audioManager != null) {
                audioManager.setMusicVolume(musicRatio);
            }
            return Action.NONE;
        }

        float sfxRatio = computeSliderRatio(mouseX, mouseY, sfxSliderBounds, false);
        if (sfxRatio >= 0.0f) {
            activeDrag = DragTarget.SFX;
            if (audioManager != null) {
                audioManager.setSfxVolume(sfxRatio);
            }
            return Action.NONE;
        }

        if (isPointInRect(mouseX, mouseY, hotkeysButtonBounds)) {
            playClick(audioManager);
            return Action.OPEN_HOTKEYS;
        }
        if (isPointInRect(mouseX, mouseY, creditsButtonBounds)) {
            playClick(audioManager);
            return Action.OPEN_CREDITS;
        }

        Action footerAction = handleFooterClick(mouseX, mouseY, screenW, screenH, footerStartY);
        if (footerAction != Action.NONE) {
            playClick(audioManager);
        }
        return footerAction;
    }

    /** Cập nhật giá trị thanh trượt liên tục khi người dùng giữ và kéo chuột. */
    public boolean handleMouseDrag(float mouseX, float mouseY, int screenW, int screenH,
                                   AudioManager audioManager) {
        if (activeDrag == DragTarget.NONE || audioManager == null) {
            return false;
        }
        layoutCoreBounds(screenW, screenH);
        if (activeDrag == DragTarget.MUSIC) {
            float ratio = computeSliderRatio(mouseX, mouseY, musicSliderBounds, true);
            if (ratio >= 0.0f) {
                audioManager.setMusicVolume(ratio);
            }
            return true;
        }
        if (activeDrag == DragTarget.SFX) {
            float ratio = computeSliderRatio(mouseX, mouseY, sfxSliderBounds, true);
            if (ratio >= 0.0f) {
                audioManager.setSfxVolume(ratio);
            }
            return true;
        }
        return false;
    }

    /** Kết thúc thao tác kéo thanh trượt khi nhả chuột. */
    public void handleMouseUp(AudioManager audioManager) {
        if (activeDrag == DragTarget.SFX && audioManager != null) {
            // Phát tiếng click mẫu sau khi thả thanh Sound Effects để người chơi nghe thử mức âm lượng mới
            audioManager.playEffect(AudioManager.Effect.CLICK, 0.40f);
        }
        activeDrag = DragTarget.NONE;
    }

    /** Phát âm thanh click nút tiêu chuẩn. */
    protected static void playClick(AudioManager audioManager) {
        if (audioManager != null) {
            audioManager.playEffect(AudioManager.Effect.CLICK, 0.36f);
        }
    }
}
