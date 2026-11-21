package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Hệ thống Kết xuất Giao diện Người dùng 2D & Hiệu ứng Tương tác 3D (UI & HUD Renderer),
 * triển khai giao diện {@link Disposable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Triển khai `Disposable` để thống nhất cơ chế giải phóng bộ đệm Mesh của giao diện.
 *    - Đóng gói toàn bộ thuật toán vẽ 2D Orthographic và bảng mã phông chữ ma trận điểm ảnh 5x7
 *      đằng sau phương thức `render2DHUD(...)` và `renderTargetBlockOverlay3D(...)`.
 * 2. Tính Đóng gói (Encapsulation):
 *    - Truy xuất thông tin khối đang ngắm thông qua các phương thức getter đóng gói của `Raycast.HitResult`
 *      (`hit.isHit()`, `hit.getBlockX()`, `hit.getBlockY()`, `hit.getBlockZ()`) thay vì truy cập trường trực tiếp.
 * 3. Tính Đa hình (Polymorphism):
 *    - Sử dụng `itemType.getFaceUV(Block.Face.SOUTH)` để lấy đúng tọa độ UV trên Texture Atlas cho bất kỳ
 *      loại `Block` nào đang nằm trong 9 ô `Hotbar`.
 */
public class UIRenderer implements Disposable {

    // Cấu hình thuộc tính đỉnh libGDX: aPos (3), aTexCoord (2), aShade (1)
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade")
    );

    private Mesh uiMesh;
    private ShaderProgram currentShader;

    // Kích thước các thành phần trong Pause Menu (tính bằng Pixel màn hình)
    private static final float PANEL_WIDTH = 420.0f;
    private static final float PANEL_HEIGHT = 270.0f;

    private static final float RESUME_BTN_WIDTH = 280.0f;
    private static final float RESUME_BTN_HEIGHT = 46.0f;

    private static final float VOL_BTN_SIZE = 42.0f;
    private static final float SLIDER_WIDTH = 176.0f;
    private static final float SLIDER_HEIGHT = 24.0f;

    // Kích thước của 9 ô Hotbar ở cạnh dưới màn hình
    private static final float HOTBAR_SLOT_SIZE = 50.0f;
    private static final float HOTBAR_SLOT_PADDING = 4.0f;

    private final Matrix4 modelMatrix = new Matrix4();

    public UIRenderer() {
        // Khởi tạo Mesh động cho UI với dung lượng ban đầu 4096 đỉnh
        uiMesh = new Mesh(false, 4096, 0, VERTEX_ATTRIBUTES);
    }

    // =========================================================================
    // 1. VẼ HIỆU ỨNG KHỐI 3D ĐANG BỊ ĐẬP & KHUNG NGẮM BLOCK (3D OVERLAY)
    // =========================================================================

    /**
     * Vẽ lớp vỏ bọc quanh Block đang được người chơi nhìn vào và hiệu ứng tối/nứt dần theo tiến trình đập
     * (`miningProgress` từ 0.0f đến 1.0f), sử dụng các getter đóng gói của {@link Raycast.HitResult}.
     */
    public void renderTargetBlockOverlay3D(
            ShaderProgram shader,
            Raycast.HitResult hit,
            float miningProgress
    ) {
        if (hit == null || !hit.isHit()) {
            return;
        }

        this.currentShader = shader;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.setUniform1i("uUseTexture", 0);

        int bx = hit.getBlockX();
        int by = hit.getBlockY();
        int bz = hit.getBlockZ();

        // Phóng to nhẹ khối bọc ngoài thêm 0.004f mỗi bên để không bị trùng mặt phẳng (Z-Fighting) với Block gốc
        float expand = 0.004f;
        float x0 = bx - expand;
        float y0 = by - expand;
        float z0 = bz - expand;
        float x1 = bx + 1.0f + expand;
        float y1 = by + 1.0f + expand;
        float z1 = bz + 1.0f + expand;

        modelMatrix.idt();
        shader.setUniformMatrix4f("uModel", modelMatrix);

        if (miningProgress > 0.001f) {
            // Khi đang giữ chuột trái đập block: Vẽ lớp phủ nứt/tối dần và co giãn theo phần trăm tiến trình
            float alpha = Math.min(0.68f, 0.18f + miningProgress * 0.50f);
            shader.setUniformVec4("uTintColor", 0.08f, 0.08f, 0.08f, alpha);
            draw3DBoxSolid(x0, y0, z0, x1, y1, z1);

            // Vẽ thêm lõi chỉ báo vết nứt co dần ở tâm mỗi mặt
            float crackRatio = Math.min(1.0f, miningProgress);
            float pad = (1.0f - crackRatio) * 0.45f;
            shader.setUniformVec4("uTintColor", 1.0f, 0.92f, 0.25f, 0.35f);
            draw3DBoxSolid(
                    bx + pad - expand * 2,
                    by + pad - expand * 2,
                    bz + pad - expand * 2,
                    bx + 1.0f - pad + expand * 2,
                    by + 1.0f - pad + expand * 2,
                    bz + 1.0f - pad + expand * 2
            );
        } else {
            // Khi chỉ đang nhìn vào block: phủ lớp sáng mờ cực nhẹ để người chơi biết đang chọn trúng ô nào
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 0.09f);
            draw3DBoxSolid(x0, y0, z0, x1, y1, z1);
        }

        shader.setUniform1i("uUseTexture", 1);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);

        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // =========================================================================
    // 2. VẼ TOÀN BỘ GIAO DIỆN 2D (CROSSHAIR, 9 Ô HOTBAR, TIẾN TRÌNH ĐẬP & PAUSE MENU)
    // =========================================================================

    /**
     * Phương thức nạp chồng tiện ích nhận trực tiếp đối tượng {@link Player} và lấy `player.getHotbar()`.
     */
    public void render2DHUD(
            ShaderProgram shader,
            Texture textureAtlas,
            Camera camera,
            Player player,
            AudioManager audioManager,
            int windowWidth,
            int windowHeight,
            float miningProgress,
            boolean isPaused,
            double mouseX,
            double mouseY
    ) {
        Hotbar hotbar = (player != null) ? player.getHotbar() : null;
        render2DHUD(
                shader,
                textureAtlas,
                camera,
                hotbar,
                audioManager,
                windowWidth,
                windowHeight,
                miningProgress,
                isPaused,
                mouseX,
                mouseY
        );
    }

    /**
     * Kết xuất toàn bộ giao diện 2D HUD và Pause Menu lên màn hình bằng phép chiếu Trực giao (Orthographic).
     */
    public void render2DHUD(
            ShaderProgram shader,
            Texture textureAtlas,
            Camera camera,
            Hotbar hotbar,
            AudioManager audioManager,
            int windowWidth,
            int windowHeight,
            float miningProgress,
            boolean isPaused,
            double mouseX,
            double mouseY
    ) {
        this.currentShader = shader;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.enable2DUIMode(camera.getOrthoMatrix(windowWidth, windowHeight));

        // 1. Luôn vẽ thanh 9 ô Hotbar ở cạnh dưới màn hình
        if (hotbar != null) {
            renderHotbar(shader, textureAtlas, hotbar, windowWidth, windowHeight);
        }

        if (!isPaused) {
            // 2. Khi đang chơi: Vẽ tâm ngắm Crosshair ở chính giữa màn hình
            renderCrosshair(shader, windowWidth, windowHeight);

            // 3. Nếu đang giữ chuột trái đập một Block: Vẽ thanh Tiến trình Đập Block ngay dưới tâm ngắm
            if (miningProgress > 0.001f) {
                renderMiningProgressBar(shader, windowWidth, windowHeight, miningProgress);
            }
        } else {
            // 4. Khi bấm ESC tạm dừng: Vẽ Menu Tạm dừng (Pause Menu) với ô Resume và điều chỉnh Âm lượng
            renderPauseMenu(shader, audioManager, windowWidth, windowHeight, mouseX, mouseY);
        }

        shader.enable3DWorldMode();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private void renderCrosshair(ShaderProgram shader, int windowWidth, int windowHeight) {
        float cx = windowWidth * 0.5f;
        float cy = windowHeight * 0.5f;

        shader.setUniform1i("uUseTexture", 0);

        shader.setUniformVec4("uTintColor", 0.0f, 0.0f, 0.0f, 0.65f);
        drawRect2D(cx - 10.0f, cy - 2.0f, 20.0f, 4.0f);
        drawRect2D(cx - 2.0f, cy - 10.0f, 4.0f, 20.0f);

        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 0.92f);
        drawRect2D(cx - 8.0f, cy - 1.0f, 16.0f, 2.0f);
        drawRect2D(cx - 1.0f, cy - 8.0f, 2.0f, 16.0f);
    }

    private void renderMiningProgressBar(ShaderProgram shader, int windowWidth, int windowHeight, float progress) {
        float barW = 110.0f;
        float barH = 10.0f;
        float bx = (windowWidth - barW) * 0.5f;
        float by = windowHeight * 0.5f + 20.0f;

        shader.setUniform1i("uUseTexture", 0);

        shader.setUniformVec4("uTintColor", 0.08f, 0.08f, 0.10f, 0.85f);
        drawRect2D(bx - 2.0f, by - 2.0f, barW + 4.0f, barH + 4.0f);

        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        float r = 1.0f - clamped * 0.65f;
        float g = 0.72f + clamped * 0.25f;
        shader.setUniformVec4("uTintColor", r, g, 0.18f, 0.95f);
        drawRect2D(bx, by, barW * clamped, barH);
    }

    /**
     * Vẽ giao diện 9 ô Thanh Công Cụ Nhanh (Hotbar) ở cạnh dưới màn hình sử dụng lớp {@link Block}.
     */
    private void renderHotbar(
            ShaderProgram shader,
            Texture textureAtlas,
            Hotbar hotbar,
            int windowWidth,
            int windowHeight
    ) {
        float totalWidth = Hotbar.SLOT_COUNT * HOTBAR_SLOT_SIZE + (Hotbar.SLOT_COUNT + 1) * HOTBAR_SLOT_PADDING;
        float barHeight = HOTBAR_SLOT_SIZE + HOTBAR_SLOT_PADDING * 2.0f;
        float startX = (windowWidth - totalWidth) * 0.5f;
        float startY = windowHeight - barHeight - 16.0f;

        shader.setUniform1i("uUseTexture", 0);

        // 1. Vẽ tấm nền tổng của thanh Hotbar
        shader.setUniformVec4("uTintColor", 0.10f, 0.11f, 0.14f, 0.82f);
        drawRect2D(startX - 4.0f, startY - 4.0f, totalWidth + 8.0f, barHeight + 8.0f);

        // 2. Vẽ từng ô từ 0 đến 8 (tương ứng phím 1 đến 9)
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            float slotX = startX + HOTBAR_SLOT_PADDING + i * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_PADDING);
            float slotY = startY + HOTBAR_SLOT_PADDING;

            boolean isSelected = (i == hotbar.getSelectedSlot());

            if (isSelected) {
                shader.setUniform1i("uUseTexture", 0);
                shader.setUniformVec4("uTintColor", 1.0f, 0.92f, 0.32f, 1.0f);
                drawRect2D(slotX - 3.0f, slotY - 3.0f, HOTBAR_SLOT_SIZE + 6.0f, HOTBAR_SLOT_SIZE + 6.0f);
            }

            shader.setUniform1i("uUseTexture", 0);
            if (isSelected) {
                shader.setUniformVec4("uTintColor", 0.28f, 0.30f, 0.35f, 0.95f);
            } else {
                shader.setUniformVec4("uTintColor", 0.18f, 0.19f, 0.23f, 0.90f);
            }
            drawRect2D(slotX, slotY, HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE);

            // Số thứ tự phím (1..9) nhỏ ở góc trái-trên của ô
            shader.setUniformVec4("uTintColor", 0.72f, 0.74f, 0.78f, 0.85f);
            drawPixelText(String.valueOf(i + 1), slotX + 4.0f, slotY + 4.0f, 1.5f);

            Block itemBlock = hotbar.getSlotBlock(i);
            int count = hotbar.getSlotCount(i);

            if (itemBlock != Block.AIR && count > 0) {
                float iconSize = 28.0f;
                float iconX = slotX + (HOTBAR_SLOT_SIZE - iconSize) * 0.5f;
                float iconY = slotY + (HOTBAR_SLOT_SIZE - iconSize) * 0.5f;

                float[] uv = itemBlock.getFaceUV(Block.Face.SOUTH);
                textureAtlas.bind();
                shader.setUniform1i("uUseTexture", 1);
                shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
                drawTexturedRect2D(iconX, iconY, iconSize, iconSize, uv[0], uv[1], uv[2], uv[3]);
                textureAtlas.unbind();

                // Vẽ số lượng item hiện có ở góc phải-dưới của ô
                shader.setUniform1i("uUseTexture", 0);
                String countStr = String.valueOf(count);
                float textScale = 2.0f;
                float textW = countStr.length() * 6.0f * textScale;
                float tx = slotX + HOTBAR_SLOT_SIZE - textW - 3.0f;
                float ty = slotY + HOTBAR_SLOT_SIZE - 7.0f * textScale - 3.0f;

                shader.setUniformVec4("uTintColor", 0.05f, 0.05f, 0.05f, 0.95f);
                drawPixelText(countStr, tx + 1.5f, ty + 1.5f, textScale);

                shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
                drawPixelText(countStr, tx, ty, textScale);
            }
        }

        // Hiển thị tên loại Block đang cầm ngay phía trên thanh Hotbar
        Block selectedBlock = hotbar.getSelectedBlock();
        if (selectedBlock != Block.AIR && hotbar.getSelectedCount() > 0) {
            String label = selectedBlock.getDisplayName().toUpperCase();
            float scale = 2.0f;
            float labelW = label.length() * 6.0f * scale;
            float lx = (windowWidth - labelW) * 0.5f;
            float ly = startY - 24.0f;

            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.0f, 0.0f, 0.0f, 0.75f);
            drawPixelText(label, lx + 2.0f, ly + 2.0f, scale);
            shader.setUniformVec4("uTintColor", 1.0f, 0.96f, 0.72f, 1.0f);
            drawPixelText(label, lx, ly, scale);
        }
    }

    // =========================================================================
    // 3. VẼ MENU TẠM DỪNG (PAUSE MENU: NÚT RESUME & THANH ÂM LƯỢNG)
    // =========================================================================

    private void renderPauseMenu(
            ShaderProgram shader,
            AudioManager audioManager,
            int windowWidth,
            int windowHeight,
            double mouseX,
            double mouseY
    ) {
        shader.setUniform1i("uUseTexture", 0);

        // 1. Lớp màn tối bán trong suốt phủ toàn màn hình (Dim Backdrop)
        shader.setUniformVec4("uTintColor", 0.04f, 0.05f, 0.08f, 0.68f);
        drawRect2D(0.0f, 0.0f, windowWidth, windowHeight);

        // 2. Khung hộp thoại trung tâm (Pause Panel)
        float panelX = (windowWidth - PANEL_WIDTH) * 0.5f;
        float panelY = (windowHeight - PANEL_HEIGHT) * 0.5f;

        shader.setUniformVec4("uTintColor", 0.65f, 0.68f, 0.75f, 0.95f);
        drawRect2D(panelX - 3.0f, panelY - 3.0f, PANEL_WIDTH + 6.0f, PANEL_HEIGHT + 6.0f);

        shader.setUniformVec4("uTintColor", 0.14f, 0.16f, 0.21f, 0.96f);
        drawRect2D(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // 3. Tiêu đề "GAME PAUSED"
        String title = "GAME PAUSED";
        float titleScale = 3.2f;
        float titleW = title.length() * 6.0f * titleScale;
        float titleX = (windowWidth - titleW) * 0.5f;
        float titleY = panelY + 24.0f;

        shader.setUniformVec4("uTintColor", 0.05f, 0.05f, 0.05f, 0.90f);
        drawPixelText(title, titleX + 2.5f, titleY + 2.5f, titleScale);
        shader.setUniformVec4("uTintColor", 1.0f, 0.92f, 0.35f, 1.0f);
        drawPixelText(title, titleX, titleY, titleScale);

        // 4. Ô Nút bấm "RESUME GAME"
        float[] resumeBounds = getResumeButtonBounds(windowWidth, windowHeight);
        boolean hoverResume = isPointInRect(mouseX, mouseY, resumeBounds);

        shader.setUniformVec4("uTintColor", hoverResume ? 1.0f : 0.75f, hoverResume ? 0.95f : 0.78f, 0.40f, 1.0f);
        drawRect2D(resumeBounds[0] - 2.0f, resumeBounds[1] - 2.0f, resumeBounds[2] + 4.0f, resumeBounds[3] + 4.0f);

        if (hoverResume) {
            shader.setUniformVec4("uTintColor", 0.26f, 0.58f, 0.30f, 1.0f);
        } else {
            shader.setUniformVec4("uTintColor", 0.20f, 0.44f, 0.24f, 1.0f);
        }
        drawRect2D(resumeBounds[0], resumeBounds[1], resumeBounds[2], resumeBounds[3]);

        String resumeLabel = "RESUME GAME";
        float resScale = 2.4f;
        float resW = resumeLabel.length() * 6.0f * resScale;
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        drawPixelText(
                resumeLabel,
                resumeBounds[0] + (resumeBounds[2] - resW) * 0.5f,
                resumeBounds[1] + (resumeBounds[3] - 7.0f * resScale) * 0.5f,
                resScale
        );

        // 5. Khu vực Điều chỉnh Âm lượng Nhạc nền (Volume Control: Nút [-], Thanh trượt, Nút [+])
        int volPercent = (audioManager != null) ? audioManager.getVolumePercent() : 70;
        String volLabel = "MUSIC VOLUME: " + volPercent + "%";
        float volLabelScale = 2.0f;
        float volLabelW = volLabel.length() * 6.0f * volLabelScale;
        shader.setUniformVec4("uTintColor", 0.88f, 0.90f, 0.95f, 1.0f);
        drawPixelText(volLabel, (windowWidth - volLabelW) * 0.5f, panelY + 138.0f, volLabelScale);

        // Nút Giảm âm lượng [-]
        float[] minusBounds = getVolumeMinusBounds(windowWidth, windowHeight);
        boolean hoverMinus = isPointInRect(mouseX, mouseY, minusBounds);
        shader.setUniformVec4("uTintColor", 0.75f, 0.78f, 0.82f, 1.0f);
        drawRect2D(minusBounds[0] - 2.0f, minusBounds[1] - 2.0f, minusBounds[2] + 4.0f, minusBounds[3] + 4.0f);
        shader.setUniformVec4("uTintColor", hoverMinus ? 0.42f : 0.26f, hoverMinus ? 0.45f : 0.29f, 0.36f, 1.0f);
        drawRect2D(minusBounds[0], minusBounds[1], minusBounds[2], minusBounds[3]);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        drawPixelText("-", minusBounds[0] + 15.0f, minusBounds[1] + 11.0f, 2.8f);

        // Thanh trượt Volume (Slider Track & Fill)
        float[] sliderBounds = getVolumeSliderBounds(windowWidth, windowHeight);
        float volRatio = (audioManager != null) ? audioManager.getVolume() : 0.7f;

        shader.setUniformVec4("uTintColor", 0.08f, 0.09f, 0.12f, 1.0f);
        drawRect2D(sliderBounds[0] - 2.0f, sliderBounds[1] - 2.0f, sliderBounds[2] + 4.0f, sliderBounds[3] + 4.0f);

        shader.setUniformVec4("uTintColor", 0.24f, 0.65f, 0.92f, 1.0f);
        drawRect2D(sliderBounds[0], sliderBounds[1], sliderBounds[2] * volRatio, sliderBounds[3]);

        float knobW = 12.0f;
        float knobX = sliderBounds[0] + sliderBounds[2] * volRatio - knobW * 0.5f;
        knobX = Math.max(sliderBounds[0], Math.min(sliderBounds[0] + sliderBounds[2] - knobW, knobX));
        shader.setUniformVec4("uTintColor", 1.0f, 0.95f, 0.50f, 1.0f);
        drawRect2D(knobX, sliderBounds[1] - 5.0f, knobW, sliderBounds[3] + 10.0f);

        // Nút Tăng âm lượng [+]
        float[] plusBounds = getVolumePlusBounds(windowWidth, windowHeight);
        boolean hoverPlus = isPointInRect(mouseX, mouseY, plusBounds);
        shader.setUniformVec4("uTintColor", 0.75f, 0.78f, 0.82f, 1.0f);
        drawRect2D(plusBounds[0] - 2.0f, plusBounds[1] - 2.0f, plusBounds[2] + 4.0f, plusBounds[3] + 4.0f);
        shader.setUniformVec4("uTintColor", hoverPlus ? 0.42f : 0.26f, hoverPlus ? 0.45f : 0.29f, 0.36f, 1.0f);
        drawRect2D(plusBounds[0], plusBounds[1], plusBounds[2], plusBounds[3]);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        drawPixelText("+", plusBounds[0] + 15.0f, plusBounds[1] + 11.0f, 2.8f);

        // 6. Dòng hướng dẫn bấm ESC lần 2 để thoát game
        String quitHint = "PRESS ESC AGAIN TO QUIT";
        float hintScale = 1.6f;
        float hintW = quitHint.length() * 6.0f * hintScale;
        shader.setUniformVec4("uTintColor", 0.75f, 0.78f, 0.84f, 0.90f);
        drawPixelText(quitHint, (windowWidth - hintW) * 0.5f, panelY + PANEL_HEIGHT - 28.0f, hintScale);
    }

    // =========================================================================
    // CÁC HÀM KIỂM TRA TỌA ĐỘ CLICK CHUỘT TRONG PAUSE MENU (HIT-TESTING)
    // =========================================================================

    private float[] getResumeButtonBounds(int windowWidth, int windowHeight) {
        float panelY = (windowHeight - PANEL_HEIGHT) * 0.5f;
        float bx = (windowWidth - RESUME_BTN_WIDTH) * 0.5f;
        float by = panelY + 72.0f;
        return new float[]{bx, by, RESUME_BTN_WIDTH, RESUME_BTN_HEIGHT};
    }

    private float[] getVolumeMinusBounds(int windowWidth, int windowHeight) {
        float panelY = (windowHeight - PANEL_HEIGHT) * 0.5f;
        float totalRowW = VOL_BTN_SIZE + 12.0f + SLIDER_WIDTH + 12.0f + VOL_BTN_SIZE;
        float startX = (windowWidth - totalRowW) * 0.5f;
        float by = panelY + 168.0f;
        return new float[]{startX, by, VOL_BTN_SIZE, VOL_BTN_SIZE};
    }

    private float[] getVolumeSliderBounds(int windowWidth, int windowHeight) {
        float[] minus = getVolumeMinusBounds(windowWidth, windowHeight);
        float sx = minus[0] + VOL_BTN_SIZE + 12.0f;
        float sy = minus[1] + (VOL_BTN_SIZE - SLIDER_HEIGHT) * 0.5f;
        return new float[]{sx, sy, SLIDER_WIDTH, SLIDER_HEIGHT};
    }

    private float[] getVolumePlusBounds(int windowWidth, int windowHeight) {
        float[] slider = getVolumeSliderBounds(windowWidth, windowHeight);
        float[] minus = getVolumeMinusBounds(windowWidth, windowHeight);
        float px = slider[0] + SLIDER_WIDTH + 12.0f;
        return new float[]{px, minus[1], VOL_BTN_SIZE, VOL_BTN_SIZE};
    }

    private boolean isPointInRect(double mx, double my, float[] rect) {
        return mx >= rect[0] && mx <= (rect[0] + rect[2]) &&
               my >= rect[1] && my <= (rect[1] + rect[3]);
    }

    public boolean isInsideResumeButton(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return isPointInRect(mouseX, mouseY, getResumeButtonBounds(windowWidth, windowHeight));
    }

    public boolean isInsideVolumeMinus(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return isPointInRect(mouseX, mouseY, getVolumeMinusBounds(windowWidth, windowHeight));
    }

    public boolean isInsideVolumePlus(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return isPointInRect(mouseX, mouseY, getVolumePlusBounds(windowWidth, windowHeight));
    }

    public float getSliderVolumeAtCursor(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        float[] s = getVolumeSliderBounds(windowWidth, windowHeight);
        if (mouseX >= s[0] - 6.0f && mouseX <= s[0] + s[2] + 6.0f &&
            mouseY >= s[1] - 10.0f && mouseY <= s[1] + s[3] + 10.0f) {
            float ratio = (float) ((mouseX - s[0]) / s[2]);
            return Math.max(0.0f, Math.min(1.0f, ratio));
        }
        return -1.0f;
    }

    // =========================================================================
    // CÁC HÀM VẼ HÌNH HỌC CƠ SỞ (2D RECTANGLE, 3D BOX, 5x7 PIXEL FONT)
    // =========================================================================

    private void drawRect2D(float x, float y, float w, float h) {
        drawTexturedRect2D(x, y, w, h, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    private void drawTexturedRect2D(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        float[] verts = new float[]{
                x,     y + h, 0.0f, u0, v1, 1.0f,
                x + w, y + h, 0.0f, u1, v1, 1.0f,
                x + w, y,     0.0f, u1, v0, 1.0f,

                x,     y + h, 0.0f, u0, v1, 1.0f,
                x + w, y,     0.0f, u1, v0, 1.0f,
                x,     y,     0.0f, u0, v0, 1.0f
        };
        uploadAndDrawTriangles(verts);
    }

    private void draw3DBoxSolid(float x0, float y0, float z0, float x1, float y1, float z1) {
        float[] verts = new float[]{
                // Top (+Y)
                x0, y1, z1, 0, 0, 1,  x1, y1, z1, 0, 0, 1,  x1, y1, z0, 0, 0, 1,
                x0, y1, z1, 0, 0, 1,  x1, y1, z0, 0, 0, 1,  x0, y1, z0, 0, 0, 1,
                // Bottom (-Y)
                x0, y0, z0, 0, 0, 1,  x1, y0, z0, 0, 0, 1,  x1, y0, z1, 0, 0, 1,
                x0, y0, z0, 0, 0, 1,  x1, y0, z1, 0, 0, 1,  x0, y0, z1, 0, 0, 1,
                // Front (+Z)
                x0, y0, z1, 0, 0, 1,  x1, y0, z1, 0, 0, 1,  x1, y1, z1, 0, 0, 1,
                x0, y0, z1, 0, 0, 1,  x1, y1, z1, 0, 0, 1,  x0, y1, z1, 0, 0, 1,
                // Back (-Z)
                x1, y0, z0, 0, 0, 1,  x0, y0, z0, 0, 0, 1,  x0, y1, z0, 0, 0, 1,
                x1, y0, z0, 0, 0, 1,  x0, y1, z0, 0, 0, 1,  x1, y1, z0, 0, 0, 1,
                // Right (+X)
                x1, y0, z1, 0, 0, 1,  x1, y0, z0, 0, 0, 1,  x1, y1, z0, 0, 0, 1,
                x1, y0, z1, 0, 0, 1,  x1, y1, z0, 0, 0, 1,  x1, y1, z1, 0, 0, 1,
                // Left (-X)
                x0, y0, z0, 0, 0, 1,  x0, y0, z1, 0, 0, 1,  x0, y1, z1, 0, 0, 1,
                x0, y0, z0, 0, 0, 1,  x0, y1, z1, 0, 0, 1,  x0, y1, z0, 0, 0, 1
        };
        uploadAndDrawTriangles(verts);
    }

    private void drawPixelText(String text, float startX, float startY, float pixelScale) {
        if (text == null || text.isEmpty()) {
            return;
        }

        List<Float> verts = new ArrayList<>(text.length() * 90);
        float cursorX = startX;

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            int[] glyph = getGlyph5x7(c);
            if (glyph != null) {
                for (int row = 0; row < 7; row++) {
                    int rowBits = glyph[row];
                    for (int col = 0; col < 5; col++) {
                        if (((rowBits >> (4 - col)) & 1) == 1) {
                            float px = cursorX + col * pixelScale;
                            float py = startY + row * pixelScale;
                            addQuadToList(verts, px, py, pixelScale, pixelScale);
                        }
                    }
                }
            }
            cursorX += 6.0f * pixelScale;
        }

        if (verts.isEmpty()) {
            return;
        }

        float[] arr = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) {
            arr[i] = verts.get(i);
        }
        uploadAndDrawTriangles(arr);
    }

    private void addQuadToList(List<Float> list, float x, float y, float w, float h) {
        putV(list, x, y + h);
        putV(list, x + w, y + h);
        putV(list, x + w, y);

        putV(list, x, y + h);
        putV(list, x + w, y);
        putV(list, x, y);
    }

    private void putV(List<Float> list, float x, float y) {
        list.add(x);
        list.add(y);
        list.add(0.0f);
        list.add(0.0f);
        list.add(0.0f);
        list.add(1.0f);
    }

    private void uploadAndDrawTriangles(float[] vertices) {
        if (currentShader == null || vertices == null || vertices.length == 0) {
            return;
        }

        int count = vertices.length / 6;
        if (uiMesh == null || uiMesh.getMaxVertices() < count) {
            if (uiMesh != null) {
                uiMesh.dispose();
            }
            uiMesh = new Mesh(false, Math.max(count, 4096), 0, VERTEX_ATTRIBUTES);
        }

        uiMesh.setVertices(vertices, 0, vertices.length);
        uiMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES, 0, count);
    }

    private int[] getGlyph5x7(char c) {
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
            default -> null;
        };
    }

    @Override
    public void dispose() {
        if (uiMesh != null) {
            uiMesh.dispose();
            uiMesh = null;
        }
    }
}
