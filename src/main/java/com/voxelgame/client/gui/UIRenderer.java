package com.voxelgame.client.gui;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.ItemTextureAtlas;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.client.render.Texture;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.gamemode.GameMode;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.WorldTime;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.weather.WeatherManager;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.Disposable;

/**
 * Lớp Facade điều phối tầng giao diện 2D & Overlay 3D của Client, triển khai {@link Disposable}.
 *
 * THỂ HIỆN CÁC MẪU THIẾT KẾ & TÍNH CHẤT OOP:
 * - **Facade Pattern:** Giữ giao diện công khai gọn gàng cho {@code VoxelGameApp} và các bài kiểm thử,
 *   ủy quyền toàn bộ công việc vẽ HUD sang {@link HudRenderer}, phần Pause Menu sang {@link PauseScreen},
 *   và tra cứu glyph 5×7 sang {@link BitmapFont}.
 * - **SRP (Single Responsibility Principle):** Tách bạch hoàn toàn vòng đời HUD trong game với màn hình
 *   tạm dừng kế thừa {@link Screen}.
 */
public class UIRenderer implements Disposable {

    /** Bộ kết xuất HUD trong game, hiệu ứng màn hình và Overlay 3D. */
    private final HudRenderer hudRenderer;
    /** Màn hình Pause Menu kế thừa {@link Screen}. */
    private final PauseScreen pauseScreen;

    /** Khởi tạo Facade UI cùng {@link HudRenderer} và {@link PauseScreen}. */
    public UIRenderer() {
        this.hudRenderer = new HudRenderer();
        this.pauseScreen = new PauseScreen();
    }

    /** Truy cập bộ kết xuất HUD nội bộ. */
    public HudRenderer getHudRenderer() {
        return hudRenderer;
    }

    /** Truy cập màn hình Pause Menu nội bộ. */
    public PauseScreen getPauseScreen() {
        return pauseScreen;
    }

    /** Gắn đồng hồ và thời tiết để hiển thị trong lượt vẽ HUD. */
    public void setEnvironment(WorldTime time, WeatherManager weatherManager) {
        hudRenderer.setEnvironment(time, weatherManager);
    }

    /** Gắn màn hình túi đồ 36 ô để vẽ trong lượt HUD 2D. */
    public void setInventoryScreen(InventoryScreen inventoryScreen) {
        hudRenderer.setInventoryScreen(inventoryScreen);
    }

    /** Gắn màn hình Kho Creative để vẽ trong lượt HUD 2D khi đang ở Creative. */
    public void setCreativeInventoryScreen(CreativeInventoryScreen creativeInventoryScreen) {
        hudRenderer.setCreativeInventoryScreen(creativeInventoryScreen);
    }

    /** Hiển thị thông báo ngắn khi chuyển đổi chế độ chơi ({@code F2}). */
    public void showGameModeToast(GameMode mode) {
        hudRenderer.showGameModeToast(mode);
    }

    /** Cập nhật thời gian hiển thị thông báo chế độ chơi mỗi khung hình. */
    public void updateToast(float deltaTime) {
        hudRenderer.updateToast(deltaTime);
    }

    /** Gắn màn hình thông số F3 và thế giới để vẽ trong lượt HUD 2D. */
    public void setDebugOverlay(DebugOverlayRenderer debugOverlay, World world) {
        hudRenderer.setDebugOverlay(debugOverlay, world);
    }

    /** Kiểm tra ký tự có mẫu glyph 5×7 tích hợp qua {@link BitmapFont}. */
    public static boolean hasGlyph(char c) {
        return BitmapFont.hasGlyph(c);
    }

    /** Vẽ hình chữ nhật 2D dùng lại bộ đệm cho {@link DebugOverlayRenderer}. */
    void drawOverlayRect(ShaderProgram shader, float x, float y, float w, float h,
                         float r, float g, float b, float a) {
        hudRenderer.drawOverlayRect(shader, x, y, w, h, r, g, b, a);
    }

    /** Vẽ dòng chữ 5×7 có bóng đổ cho {@link DebugOverlayRenderer}. */
    void drawOverlayText(ShaderProgram shader, CharSequence text, float x, float y, float scale) {
        hudRenderer.drawOverlayText(shader, text, x, y, scale);
    }

    /** Vẽ lớp vỏ bọc quanh Block đang ngắm với hộp mặc định 1×1×1. */
    public void renderTargetBlockOverlay3D(ShaderProgram shader, Raycast.HitResult hit, float miningProgress) {
        hudRenderer.renderTargetBlockOverlay3D(shader, hit, miningProgress);
    }

    /** Vẽ lớp phủ đào và ngắm đúng hộp chọn cục bộ của block đang trúng tia. */
    public void renderTargetBlockOverlay3D(ShaderProgram shader, Raycast.HitResult hit,
                                           float miningProgress, Block.Bounds bounds) {
        hudRenderer.renderTargetBlockOverlay3D(shader, hit, miningProgress, bounds);
    }

    /** Vẽ viền đỏ mờ dần theo cường độ 0–1 ngay trước lượt vẽ HUD. */
    public void renderCactusFlash(ShaderProgram shader, Camera camera, int windowWidth, int windowHeight,
                                  float intensity) {
        hudRenderer.renderCactusFlash(shader, camera, windowWidth, windowHeight, intensity);
    }

    /** Phủ màu chất lỏng sau cảnh 3D, rồi vẽ phản hồi va gai trước HUD. */
    public void renderScreenEffects(ShaderProgram shader, Camera camera, int windowWidth, int windowHeight,
                                    Camera.LiquidView liquid, float intensity) {
        hudRenderer.renderScreenEffects(shader, camera, windowWidth, windowHeight, liquid, intensity);
    }

    /** Nạp chồng tiện ích nhận trực tiếp {@link Player} (không kèm atlas vật phẩm). */
    public void render2DHUD(ShaderProgram shader, Texture textureAtlas, Camera camera, Player player,
                            AudioManager audioManager, int windowWidth, int windowHeight,
                            float miningProgress, boolean isPaused, double mouseX, double mouseY) {
        render2DHUD(shader, textureAtlas, null, camera, player, audioManager,
                windowWidth, windowHeight, miningProgress, isPaused, mouseX, mouseY);
    }

    /** Nạp chồng nhận cả atlas khối lẫn atlas vật phẩm để vẽ đầy đủ mọi ô Hotbar. */
    public void render2DHUD(ShaderProgram shader, Texture textureAtlas, ItemTextureAtlas itemAtlas,
                            Camera camera, Player player, AudioManager audioManager,
                            int windowWidth, int windowHeight, float miningProgress,
                            boolean isPaused, double mouseX, double mouseY) {
        Hotbar hotbar = (player != null) ? player.getHotbar() : null;
        render2DHUDInternal(shader, textureAtlas, itemAtlas, camera, hotbar, audioManager,
                windowWidth, windowHeight, miningProgress, isPaused, mouseX, mouseY, player);
    }

    /** Nạp chồng tương thích chỉ nhận {@link Hotbar} mà không có {@link Player}. */
    public void render2DHUD(ShaderProgram shader, Texture textureAtlas, Camera camera, Hotbar hotbar,
                            AudioManager audioManager, int windowWidth, int windowHeight,
                            float miningProgress, boolean isPaused, double mouseX, double mouseY) {
        render2DHUDInternal(shader, textureAtlas, null, camera, hotbar, audioManager,
                windowWidth, windowHeight, miningProgress, isPaused, mouseX, mouseY, null);
    }

    /** Điều phối trạng thái OpenGL 2D giữa {@link HudRenderer} và {@link PauseScreen}. */
    private void render2DHUDInternal(ShaderProgram shader, Texture textureAtlas, ItemTextureAtlas itemAtlas,
                                     Camera camera, Hotbar hotbar, AudioManager audioManager,
                                     int windowWidth, int windowHeight, float miningProgress,
                                     boolean isPaused, double mouseX, double mouseY, Player player) {
        GuiTextureAtlas atlas = hudRenderer.ensureGuiAtlas();

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.enable2DUIMode(camera.getOrthoMatrix(windowWidth, windowHeight));

        hudRenderer.renderBaseHud(shader, textureAtlas, itemAtlas, atlas, hotbar, player, windowWidth, windowHeight);

        if (!isPaused) {
            hudRenderer.renderInGameOverlays(shader, this, textureAtlas, itemAtlas, atlas, camera,
                    player, windowWidth, windowHeight, miningProgress, mouseX, mouseY);
        } else {
            pauseScreen.render(shader, hudRenderer, atlas, audioManager, windowWidth, windowHeight, mouseX, mouseY);
        }

        shader.enable3DWorldMode();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    /** Kiểm tra con trỏ chuột nằm trong nút "RESUME GAME" của {@link PauseScreen}. */
    public boolean isInsideResumeButton(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return pauseScreen.isInsideResumeButton(mouseX, mouseY, windowWidth, windowHeight);
    }

    /** Kiểm tra con trỏ chuột nằm trong nút giảm âm lượng [-] của {@link PauseScreen}. */
    public boolean isInsideVolumeMinus(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return pauseScreen.isInsideVolumeMinus(mouseX, mouseY, windowWidth, windowHeight);
    }

    /** Kiểm tra con trỏ chuột nằm trong nút tăng âm lượng [+] của {@link PauseScreen}. */
    public boolean isInsideVolumePlus(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return pauseScreen.isInsideVolumePlus(mouseX, mouseY, windowWidth, windowHeight);
    }

    /** Tính tỷ lệ âm lượng [0.0..1.0] theo vị trí chuột trên thanh trượt của {@link PauseScreen}. */
    public float getSliderVolumeAtCursor(double mouseX, double mouseY, int windowWidth, int windowHeight) {
        return pauseScreen.getSliderVolumeAtCursor(mouseX, mouseY, windowWidth, windowHeight);
    }

    /** Giải phóng toàn bộ tài nguyên GPU của {@link PauseScreen} và {@link HudRenderer}. */
    @Override
    public void dispose() {
        pauseScreen.dispose();
        hudRenderer.dispose();
    }
}
