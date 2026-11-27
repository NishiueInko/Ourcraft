package com.voxelgame.client.gui;

import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.ItemTextureAtlas;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.client.render.Texture;
import com.voxelgame.world.effect.StatusEffectType;
import com.voxelgame.world.entity.mob.MountableMob;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.gamemode.GameMode;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.Chunk;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.WorldTime;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.weather.WeatherManager;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/**
 * Hệ thống Kết xuất HUD trong lúc chơi (In-Game HUD & Target Block Overlay 3D),
 * triển khai {@link Disposable}.
 *
 * Chịu trách nhiệm riêng biệt theo nguyên lý SRP:
 * - Crosshair 15×15, Thanh Hotbar 9 ô, Thanh máu/đói/Oxy/thú cưỡi.
 * - Nhãn hiệu ứng trạng thái góc trên-phải và nhãn Biome/Thời tiết/Ngày-giờ góc trên-trái.
 * - Lớp phủ chất lỏng, viền đỏ Cactus và Overlay 3D quanh khối đang ngắm/đào.
 * - Chia sẻ bộ đệm hình học 2D zero-GC và {@link GuiTextureAtlas} cho {@link PauseScreen}
 *   và {@link DebugOverlayRenderer}.
 */
public class HudRenderer implements Disposable {

    /** Cấu hình thuộc tính đỉnh libGDX: aPos (3), aTexCoord (2), aShade (1). */
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade")
    );

    /** Kích thước dự phòng của 9 ô Hotbar khi chưa có GuiTextureAtlas. */
    private static final float HOTBAR_SLOT_SIZE = 50.0f;
    private static final float HOTBAR_SLOT_PADDING = 4.0f;
    private static final int HOTBAR_STYLES = 6 + Hotbar.SLOT_COUNT * 6;
    private static final int HOTBAR_VERTEX_CAPACITY = 4096 * 6;
    private static final byte TEXTURE_NONE = 0;
    private static final byte TEXTURE_BLOCK_ATLAS = 1;
    private static final byte TEXTURE_ITEM_ATLAS = 2;
    private static final byte TEXTURE_GUI_ATLAS = 3;

    /** Hộp mặc định cho đường gọi overlay 3D cũ. */
    private static final Block.Bounds DEFAULT_TARGET_BOUNDS = new Block.Bounds(0, 0, 0, 1, 1, 1);

    /** Bảng số lượng 0..100 tạo sẵn để tránh cấp phát String trong Game Loop. */
    private static final String[] NUMBER_LABELS = new String[101];

    /** Mẫu bitmap 7×6 dự phòng cho tim, đùi thịt và bọt khí Oxy. */
    private static final int[] HEART_ROWS = {0b0110110, 0b1111111, 0b1111111, 0b0111110, 0b0011100, 0b0001000};
    private static final int[] FOOD_ROWS = {0b0001110, 0b0011111, 0b0011111, 0b0111110, 0b1111000, 0b1100000};
    private static final int[] BUBBLE_OUTLINE_ROWS = {
            0b0011100, 0b0100010, 0b1000001, 0b1000001, 0b0100010, 0b0011100
    };
    private static final int[] BUBBLE_FILL_ROWS = {
            0b0011100, 0b0111110, 0b1111111, 0b1111111, 0b0111110, 0b0011100
    };
    private static final int VITAL_ICON_COUNT = 10;
    private static final float VITAL_PIXEL = 2f;
    private static final float VITAL_SPACING = 17f;
    private static final int VITAL_VERTEX_CAPACITY =
            VITAL_ICON_COUNT * (HEART_ROWS.length + FOOD_ROWS.length) * 2 * 2 * 6;
    private static final int OXYGEN_VERTEX_CAPACITY =
            VITAL_ICON_COUNT * BUBBLE_FILL_ROWS.length * 4 * 6;

    static {
        for (int i = 0; i < NUMBER_LABELS.length; i++) {
            NUMBER_LABELS[i] = Integer.toString(i);
        }
    }

    private Mesh uiMesh;
    private ShaderProgram currentShader;

    private final float[] hotbarVertices = new float[HOTBAR_VERTEX_CAPACITY * 6];
    private final float[] hotbarTints = new float[HOTBAR_STYLES * 4];
    private final byte[] hotbarTextureSource = new byte[HOTBAR_STYLES];
    private final int[] hotbarEnd = new int[HOTBAR_STYLES];
    private final Item[] hotbarItems = new Item[Hotbar.SLOT_COUNT];
    private final int[] hotbarCounts = new int[Hotbar.SLOT_COUNT];
    private Mesh hotbarMesh;
    private int hotbarStyleCount;
    private int hotbarLength;
    private int hotbarWidth = -1;
    private int hotbarHeight = -1;
    private int hotbarSelection = -1;
    private boolean hotbarUsedGuiAtlas = false;
    private boolean hotbarMounted = false;
    private boolean recordingHotbar;

    private final float[] rectVertices = new float[6 * 6];
    private final float[] boxVertices = new float[6 * 6 * 6];
    private final float[] textVertices = new float[32 * 5 * 7 * 36];
    private final StringBuilder environmentLabel = new StringBuilder(48);
    private final StringBuilder effectLabel = new StringBuilder(32);
    private final Matrix4 modelMatrix = new Matrix4();

    private Mesh vitalMesh;
    private int vitalWidth = -1;
    private int vitalHeight = -1;
    private int vitalHealth = -1;
    private int vitalFood = -1;
    private boolean vitalPoisoned = false;
    private boolean vitalHungerEffect = false;
    private boolean vitalMounted = false;
    private int vitalMountHp = -1;
    private int vitalMountMaxHp = -1;
    private boolean vitalUsedGuiAtlas = false;
    private int healthVertex;
    private int foodVertex;
    private int vitalVertexCount;

    private Mesh oxygenMesh;
    private int oxygenWidth = -1;
    private int oxygenHeight = -1;
    private int oxygenFullBubbles = -1;
    private boolean oxygenPopping = false;
    private boolean oxygenMounted = false;
    private boolean oxygenUsedGuiAtlas = false;
    private int oxygenOutlineVertexCount;
    private int oxygenFullVertexCount;
    private int oxygenTotalVertexCount;

    private GuiTextureAtlas guiAtlas;
    private boolean guiAtlasFailed = false;

    /** Thời lượng hiển thị thông báo ngắn khi chuyển đổi chế độ chơi bằng F2 (giây). */
    private static final float MODE_TOAST_DURATION = 2.5f;

    private WorldTime worldTime;
    private WeatherManager weather;
    private InventoryScreen inventoryScreen;
    private CreativeInventoryScreen creativeInventoryScreen;
    private DebugOverlayRenderer debugOverlay;
    private World debugWorld;
    private String modeToastLabel;
    private float modeToastTimer;

    /**
     * Khởi tạo bộ kết xuất HUD. Chỉ cấp phát {@link Mesh} trên GPU khi có OpenGL Context
     * để tương thích an toàn với các bài kiểm thử CPU headless.
     */
    public HudRenderer() {
        if (Gdx.gl != null) {
            uiMesh = new Mesh(false, 4096, 0, VERTEX_ATTRIBUTES);
        }
    }

    /** Gắn đồng hồ và thời tiết để hiển thị nhãn môi trường góc trên-trái. */
    public void setEnvironment(WorldTime time, WeatherManager weatherManager) {
        this.worldTime = time;
        this.weather = weatherManager;
    }

    /** Gắn màn hình túi đồ 36 ô để kết xuất trong lượt HUD 2D. */
    public void setInventoryScreen(InventoryScreen inventoryScreen) {
        this.inventoryScreen = inventoryScreen;
    }

    /** Gắn màn hình Kho Creative để kết xuất trong lượt HUD 2D khi ở chế độ Creative. */
    public void setCreativeInventoryScreen(CreativeInventoryScreen creativeInventoryScreen) {
        this.creativeInventoryScreen = creativeInventoryScreen;
    }

    /** Kích hoạt thông báo ngắn phía trên Hotbar khi vừa chuyển đổi chế độ chơi ({@code F2}). */
    public void showGameModeToast(GameMode mode) {
        if (mode == null) {
            return;
        }
        this.modeToastLabel = mode.getToastLabel();
        this.modeToastTimer = MODE_TOAST_DURATION;
    }

    /** Giảm bộ đếm thời gian thông báo chế độ chơi mỗi khung hình (zero-GC). */
    public void updateToast(float deltaTime) {
        if (modeToastTimer > 0.0f && deltaTime > 0.0f) {
            modeToastTimer = Math.max(0.0f, modeToastTimer - deltaTime);
        }
    }

    /** Trả về nhãn thông báo chế độ hiện tại nếu đang hiển thị, hoặc {@code null}. */
    public String getActiveModeToastLabel() {
        return modeToastTimer > 0.0f ? modeToastLabel : null;
    }

    /** Gắn màn hình thông số F3 và thế giới để kết xuất trong lượt HUD 2D. */
    public void setDebugOverlay(DebugOverlayRenderer debugOverlay, World world) {
        this.debugOverlay = debugOverlay;
        this.debugWorld = world;
    }

    /** Khởi tạo lười {@link GuiTextureAtlas} khi đã có OpenGL Context hợp lệ. */
    GuiTextureAtlas ensureGuiAtlas() {
        if (guiAtlas == null && !guiAtlasFailed && Gdx.gl != null) {
            try {
                guiAtlas = new GuiTextureAtlas();
            } catch (RuntimeException | Error e) {
                guiAtlasFailed = true;
            }
        }
        return guiAtlas;
    }

    /** Cập nhật shader đang dùng cho các lệnh vẽ 2D dùng chung (PauseScreen / DebugOverlay). */
    void setCurrentShader(ShaderProgram shader) {
        this.currentShader = shader;
    }

    /** Tính hệ số phóng nguyên pixel-perfect (2 hoặc 3) phù hợp với chiều rộng cửa sổ. */
    private static int getHudScale(int windowWidth) {
        return Math.max(2, Math.min(3, windowWidth / 420));
    }

    /** Vẽ hình chữ nhật 2D dùng lại bộ đệm của HudRenderer cho màn hình thông số F3. */
    void drawOverlayRect(ShaderProgram shader, float x, float y, float w, float h,
                         float r, float g, float b, float a) {
        this.currentShader = shader;
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", r, g, b, a);
        drawRect2D(x, y, w, h);
    }

    /** Vẽ dòng chữ 5×7 có bóng đổ cho màn hình thông số F3 (zero-GC). */
    void drawOverlayText(ShaderProgram shader, CharSequence text, float x, float y, float scale) {
        this.currentShader = shader;
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.0f, 0.0f, 0.0f, 0.85f);
        drawPixelText(text, x + 1.5f, y + 1.5f, scale);
        shader.setUniformVec4("uTintColor", 0.92f, 0.94f, 0.98f, 1.0f);
        drawPixelText(text, x, y, scale);
    }

    // =========================================================================
    // 1. VẼ OVERLAY 3D KHỐI ĐANG NGẮM / ĐÀO
    // =========================================================================

    /** Vẽ lớp vỏ bọc quanh Block đang nhìn với hộp mặc định 1×1×1. */
    public void renderTargetBlockOverlay3D(ShaderProgram shader, Raycast.HitResult hit, float miningProgress) {
        renderTargetBlockOverlay3D(shader, hit, miningProgress, DEFAULT_TARGET_BOUNDS);
    }

    /** Vẽ lớp phủ đào và ngắm đúng hộp chọn cục bộ của block đang trúng tia. */
    public void renderTargetBlockOverlay3D(ShaderProgram shader, Raycast.HitResult hit,
                                           float miningProgress, Block.Bounds bounds) {
        if (hit == null || !hit.isHit() || bounds == null) {
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

        float expand = 0.004f;
        float minX = bx + bounds.minX();
        float minY = by + bounds.minY();
        float minZ = bz + bounds.minZ();
        float maxX = bx + bounds.maxX();
        float maxY = by + bounds.maxY();
        float maxZ = bz + bounds.maxZ();
        float x0 = minX - expand;
        float y0 = minY - expand;
        float z0 = minZ - expand;
        float x1 = maxX + expand;
        float y1 = maxY + expand;
        float z1 = maxZ + expand;

        modelMatrix.idt();
        shader.setUniformMatrix4f("uModel", modelMatrix);

        if (miningProgress > 0.001f) {
            float alpha = Math.min(0.68f, 0.18f + miningProgress * 0.50f);
            shader.setUniformVec4("uTintColor", 0.08f, 0.08f, 0.08f, alpha);
            draw3DBoxSolid(x0, y0, z0, x1, y1, z1);

            float crackRatio = Math.min(1.0f, miningProgress);
            float pad = (1.0f - crackRatio) * 0.45f;
            float padX = pad * (maxX - minX);
            float padY = pad * (maxY - minY);
            float padZ = pad * (maxZ - minZ);
            shader.setUniformVec4("uTintColor", 1.0f, 0.92f, 0.25f, 0.35f);
            draw3DBoxSolid(
                    minX + padX - expand * 2,
                    minY + padY - expand * 2,
                    minZ + padZ - expand * 2,
                    maxX - padX + expand * 2,
                    maxY - padY + expand * 2,
                    maxZ - padZ + expand * 2
            );
        } else {
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 0.09f);
            draw3DBoxSolid(x0, y0, z0, x1, y1, z1);
        }

        shader.setUniform1i("uUseTexture", 1);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);

        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    // =========================================================================
    // 2. HIỆU ỨNG MÀN HÌNH & HUD TRONG LÚC CHƠI
    // =========================================================================

    /** Vẽ viền đỏ mờ dần theo cường độ 0–1 ngay trước lượt vẽ HUD. */
    public void renderCactusFlash(ShaderProgram shader, Camera camera, int windowWidth, int windowHeight,
                                  float intensity) {
        renderScreenEffects(shader, camera, windowWidth, windowHeight, Camera.LiquidView.AIR, intensity);
    }

    /** Phủ màu chất lỏng sau cảnh 3D, rồi vẽ phản hồi va gai trước HUD. */
    public void renderScreenEffects(ShaderProgram shader, Camera camera, int windowWidth, int windowHeight,
                                    Camera.LiquidView liquid, float intensity) {
        if (liquid.tintAlpha == 0f && intensity <= 0f) {
            return;
        }

        currentShader = shader;
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shader.enable2DUIMode(camera.getOrthoMatrix(windowWidth, windowHeight));
        shader.setUniform1i("uUseTexture", 0);
        if (liquid.tintAlpha > 0f) {
            shader.setUniformVec4("uTintColor", liquid.red, liquid.green, liquid.blue, liquid.tintAlpha);
            drawRect2D(0f, 0f, windowWidth, windowHeight);
        }
        if (intensity > 0f) {
            shader.setUniformVec4("uTintColor", 0.9f, 0.04f, 0.04f, 0.55f * Math.min(1.0f, intensity));
            float thickness = 0.07f * Math.min(windowWidth, windowHeight);
            drawRect2D(0.0f, 0.0f, windowWidth, thickness);
            drawRect2D(0.0f, windowHeight - thickness, windowWidth, thickness);
            drawRect2D(0.0f, thickness, thickness, windowHeight - 2.0f * thickness);
            drawRect2D(windowWidth - thickness, thickness, thickness, windowHeight - 2.0f * thickness);
        }

        shader.enable3DWorldMode();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    /**
     * Kết xuất phần HUD cơ sở luôn hiện (Hotbar, Tim, Đói, Thú cưỡi) trong chế độ 2D Orthographic.
     */
    void renderBaseHud(ShaderProgram shader, Texture textureAtlas, ItemTextureAtlas itemAtlas,
                       GuiTextureAtlas atlas, Hotbar hotbar, Player player,
                       int windowWidth, int windowHeight) {
        this.currentShader = shader;
        boolean mounted = player != null && player.getMount() instanceof MountableMob;

        if (hotbar != null) {
            renderHotbar(shader, textureAtlas, itemAtlas, atlas, hotbar, windowWidth, windowHeight, mounted);
        }
        if (player != null) {
            if (player.getGameMode().showsSurvivalHud()) {
                renderVitals(player, atlas, windowWidth, windowHeight);
            }
            if (player.getMount() instanceof MountableMob mount) {
                if (atlas != null) {
                    renderTexturedMountJumpBar(atlas, mount, windowWidth, windowHeight);
                } else {
                    renderMountVitals(mount, windowWidth, windowHeight);
                }
            }
        }
    }

    /**
     * Kết xuất phần HUD chỉ hiển thị khi không Pause (Hiệu ứng, Môi trường, Crosshair,
     * Thanh đào, Oxy, Debug F3 và InventoryScreen nếu đang mở).
     */
    void renderInGameOverlays(ShaderProgram shader, UIRenderer uiFacade, Texture textureAtlas,
                              ItemTextureAtlas itemAtlas, GuiTextureAtlas atlas, Camera camera,
                              Player player, int windowWidth, int windowHeight,
                              float miningProgress, double mouseX, double mouseY) {
        this.currentShader = shader;
        if (player != null) {
            renderStatusEffects(player, windowWidth);
        }
        if (worldTime != null && weather != null) {
            renderEnvironment();
        }
        renderCrosshair(shader, atlas, windowWidth, windowHeight);

        if (miningProgress > 0.001f) {
            renderMiningProgressBar(shader, windowWidth, windowHeight, miningProgress);
        }
        if (player != null && player.getGameMode().showsSurvivalHud()
                && (player.isEyeInWater() || player.getAirTicks() < Player.AIR_MAX_TICKS)) {
            renderOxygenBar(player, windowWidth, windowHeight);
        }
        if (modeToastTimer > 0.0f && modeToastLabel != null) {
            renderModeToast(shader, windowWidth, windowHeight);
        }
        if (debugOverlay != null && debugOverlay.isVisible()) {
            debugOverlay.render(shader, uiFacade, debugWorld, player, camera, windowWidth, windowHeight);
        }
        if (creativeInventoryScreen != null && creativeInventoryScreen.isOpen() && player != null) {
            creativeInventoryScreen.render(shader, player.getInventory(), textureAtlas, itemAtlas,
                    windowWidth, windowHeight, (float) mouseX, (float) mouseY);
        } else if (inventoryScreen != null && inventoryScreen.isOpen() && player != null) {
            inventoryScreen.render(shader, player.getInventory(), textureAtlas, itemAtlas, atlas,
                    windowWidth, windowHeight, (float) mouseX, (float) mouseY);
        }
    }

    /** Vẽ thông báo ngắn (toast) phía trên Hotbar khi vừa đổi chế độ chơi bằng phím {@code F2}. */
    private void renderModeToast(ShaderProgram shader, int windowWidth, int windowHeight) {
        float scale = 2.0f;
        float textW = BitmapFont.measureWidth(modeToastLabel, scale);
        float textH = BitmapFont.measureHeight(scale);
        float padX = 10.0f;
        float padY = 6.0f;
        float boxW = textW + padX * 2.0f;
        float boxH = textH + padY * 2.0f;
        float x = Math.round((windowWidth - boxW) * 0.5f);
        float y = windowHeight - 132.0f;
        float alpha = Math.min(1.0f, modeToastTimer * 1.5f);

        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.06f, 0.08f, 0.12f, 0.78f * alpha);
        drawRect2D(x, y, boxW, boxH);
        shader.setUniformVec4("uTintColor", 0.05f, 0.05f, 0.05f, 0.90f * alpha);
        drawPixelText(modeToastLabel, x + padX + 1.5f, y + padY + 1.5f, scale);
        shader.setUniformVec4("uTintColor", 1.0f, 0.95f, 0.45f, alpha);
        drawPixelText(modeToastLabel, x + padX, y + padY, scale);
    }

    // =========================================================================
    // 3. THANH OXY, TIM, ĐÓI, THÚ CƯỠI, HIỆU ỨNG & MÔI TRƯỜNG
    // =========================================================================

    private void renderOxygenBar(Player player, int windowWidth, int windowHeight) {
        GuiTextureAtlas atlas = ensureGuiAtlas();
        boolean useGui = atlas != null;
        int airTicks = Math.max(0, Math.min(Player.AIR_MAX_TICKS, player.getAirTicks()));
        int fullBubbles = airTicks / 30;
        boolean popping = airTicks > 0 && (airTicks % 30) != 0 && fullBubbles < VITAL_ICON_COUNT;
        boolean mounted = player.getMount() instanceof MountableMob;

        if (windowWidth != oxygenWidth || windowHeight != oxygenHeight
                || fullBubbles != oxygenFullBubbles || popping != oxygenPopping
                || mounted != oxygenMounted || useGui != oxygenUsedGuiAtlas) {
            if (useGui) {
                rebuildTexturedOxygenBar(windowWidth, windowHeight, fullBubbles, popping, mounted);
            } else {
                rebuildFallbackOxygenBar(windowWidth, windowHeight, fullBubbles, popping, mounted);
            }
        }

        if (useGui) {
            atlas.bindHudAtlas();
            currentShader.setUniform1i("uUseTexture", 1);
            currentShader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            oxygenMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES, 0, oxygenTotalVertexCount);
            atlas.unbind();
        } else {
            currentShader.setUniform1i("uUseTexture", 0);
            currentShader.setUniformVec4("uTintColor", 0.08f, 0.22f, 0.38f, 0.90f);
            oxygenMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES, 0, oxygenOutlineVertexCount);
            if (oxygenFullVertexCount > oxygenOutlineVertexCount) {
                currentShader.setUniformVec4("uTintColor", 0.25f, 0.76f, 0.98f, 1.0f);
                oxygenMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES,
                        oxygenOutlineVertexCount, oxygenFullVertexCount - oxygenOutlineVertexCount);
            }
            if (oxygenTotalVertexCount > oxygenFullVertexCount) {
                currentShader.setUniformVec4("uTintColor", 0.65f, 0.92f, 1.0f, 0.85f);
                oxygenMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES,
                        oxygenFullVertexCount, oxygenTotalVertexCount - oxygenFullVertexCount);
            }
        }
    }

    private void rebuildTexturedOxygenBar(int windowWidth, int windowHeight, int fullBubbles,
                                          boolean popping, boolean mounted) {
        if (oxygenMesh == null) {
            oxygenMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true,
                    OXYGEN_VERTEX_CAPACITY, 0, VERTEX_ATTRIBUTES);
        }
        int scale = getHudScale(windowWidth);
        float hotbarW = 182.0f * scale;
        float hotbarH = 22.0f * scale;
        float hotbarX = Math.round((windowWidth - hotbarW) * 0.5f);
        float hotbarY = windowHeight - hotbarH - 12.0f;
        float vitalsY = hotbarY - (mounted ? 18.0f : 12.0f) * scale;
        float y = vitalsY - (mounted ? 20.0f : 10.0f) * scale;
        float iconSize = 9.0f * scale;
        float iconStep = 8.0f * scale;
        float rightMostX = hotbarX + hotbarW - iconSize;

        float[] fullUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.AIR);
        float[] burstUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.AIR_BURSTING);
        float[] emptyUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.AIR_EMPTY);

        int length = 0;
        for (int i = 0; i < VITAL_ICON_COUNT; i++) {
            float x = rightMostX - i * iconStep;
            float[] uv;
            if (i < fullBubbles) {
                uv = fullUv;
            } else if (i == fullBubbles && popping) {
                uv = burstUv;
            } else {
                uv = emptyUv;
            }
            BitmapFont.writeQuad(textVertices, length, x, y + iconSize, 0f, x + iconSize, y + iconSize, 0f,
                    x + iconSize, y, 0f, x, y, 0f, uv[0], uv[1], uv[2], uv[3]);
            length += 36;
        }

        oxygenTotalVertexCount = length / 6;
        oxygenOutlineVertexCount = oxygenTotalVertexCount;
        oxygenFullVertexCount = oxygenTotalVertexCount;
        oxygenMesh.setVertices(textVertices, 0, length);
        oxygenWidth = windowWidth;
        oxygenHeight = windowHeight;
        oxygenFullBubbles = fullBubbles;
        oxygenPopping = popping;
        oxygenMounted = mounted;
        oxygenUsedGuiAtlas = true;
    }

    private void rebuildFallbackOxygenBar(int windowWidth, int windowHeight, int fullBubbles,
                                          boolean popping, boolean mounted) {
        float halfBarWidth = (Hotbar.SLOT_COUNT * HOTBAR_SLOT_SIZE
                + (Hotbar.SLOT_COUNT + 1) * HOTBAR_SLOT_PADDING) * 0.5f;
        float span = (VITAL_ICON_COUNT - 1) * VITAL_SPACING + 7f * VITAL_PIXEL;
        float inset = Math.max(4f, windowWidth * 0.5f - halfBarWidth);
        float foodY = windowHeight - HOTBAR_SLOT_SIZE - HOTBAR_SLOT_PADDING * 2f - 58f;
        float y = mounted ? (foodY - 33f) : (foodY - 17f);
        float foodX = windowWidth - inset - span;
        if (oxygenMesh == null) {
            oxygenMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true,
                    OXYGEN_VERTEX_CAPACITY, 0, VERTEX_ATTRIBUTES);
        }
        int length = writeBubbleIcons(BUBBLE_OUTLINE_ROWS, foodX, y, 0, VITAL_ICON_COUNT, 0);
        oxygenOutlineVertexCount = length / 6;
        length = writeBubbleIcons(BUBBLE_FILL_ROWS, foodX, y, 0, fullBubbles, length);
        oxygenFullVertexCount = length / 6;
        if (popping) {
            length = writeBubbleIcons(BUBBLE_OUTLINE_ROWS, foodX, y, fullBubbles, 1, length);
        }
        oxygenTotalVertexCount = length / 6;
        oxygenMesh.setVertices(textVertices, 0, length);
        oxygenWidth = windowWidth;
        oxygenHeight = windowHeight;
        oxygenFullBubbles = fullBubbles;
        oxygenPopping = popping;
        oxygenMounted = mounted;
        oxygenUsedGuiAtlas = false;
    }

    private int writeBubbleIcons(int[] rows, float foodX, float y, int startIndex, int count, int offset) {
        for (int i = 0; i < count; i++) {
            int icon = startIndex + i;
            float iconX = foodX + (VITAL_ICON_COUNT - 1 - icon) * VITAL_SPACING;
            for (int row = 0; row < rows.length; row++) {
                int col = 0;
                while (col < 7) {
                    if ((rows[row] & (1 << (6 - col))) == 0) {
                        col++;
                        continue;
                    }
                    int first = col++;
                    while (col < 7 && (rows[row] & (1 << (6 - col))) != 0) {
                        col++;
                    }
                    float x0 = iconX + first * VITAL_PIXEL;
                    float x1 = iconX + col * VITAL_PIXEL;
                    float y0 = y + row * VITAL_PIXEL;
                    float y1 = y0 + VITAL_PIXEL;
                    BitmapFont.writeQuad(textVertices, offset, x0, y1, 0f, x1, y1, 0f, x1, y0, 0f,
                            x0, y0, 0f, 0f, 0f, 1f, 1f);
                    offset += 36;
                }
            }
        }
        return offset;
    }

    private void renderVitals(Player player, GuiTextureAtlas atlas, int windowWidth, int windowHeight) {
        int hp = Math.max(0, Math.min(20, (int) Math.ceil(player.getHealth())));
        int food = Math.max(0, Math.min(20, (int) Math.ceil(player.getHunger())));
        boolean poisoned = player.hasEffect(StatusEffectType.POISON);
        boolean hungerEffect = player.hasEffect(StatusEffectType.HUNGER);
        MountableMob mount = (player.getMount() instanceof MountableMob m) ? m : null;
        boolean mounted = mount != null;
        int mountHp = mounted ? Math.max(0, (int) Math.ceil(mount.getHealth())) : 0;
        int mountMaxHp = mounted ? Math.max(1, (int) Math.ceil(mount.getMaxHealth())) : 0;
        boolean useGui = atlas != null;

        if (useGui) {
            if (windowWidth != vitalWidth || windowHeight != vitalHeight
                    || hp != vitalHealth || food != vitalFood
                    || poisoned != vitalPoisoned || hungerEffect != vitalHungerEffect
                    || mounted != vitalMounted || mountHp != vitalMountHp
                    || mountMaxHp != vitalMountMaxHp || !vitalUsedGuiAtlas) {
                rebuildTexturedVitals(windowWidth, windowHeight, hp, food, poisoned, hungerEffect,
                        mounted, mountHp, mountMaxHp);
            }
            atlas.bindHudAtlas();
            currentShader.setUniform1i("uUseTexture", 1);
            currentShader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
            vitalMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES, 0, vitalVertexCount);
            atlas.unbind();
        } else {
            if (windowWidth != vitalWidth || windowHeight != vitalHeight
                    || hp != vitalHealth || food != vitalFood || vitalUsedGuiAtlas) {
                rebuildFallbackVitals(windowWidth, windowHeight, hp, food);
            }
            currentShader.setUniform1i("uUseTexture", 0);
            currentShader.setUniformVec4("uTintColor", 0.15f, 0.12f, 0.17f, 0.95f);
            vitalMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES, 0, healthVertex);
            currentShader.setUniformVec4("uTintColor", 0.94f, 0.29f, 0.35f, 1f);
            if (foodVertex > healthVertex) {
                vitalMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES,
                        healthVertex, foodVertex - healthVertex);
            }
            currentShader.setUniformVec4("uTintColor", 0.97f, 0.64f, 0.30f, 1f);
            if (vitalVertexCount > foodVertex) {
                vitalMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES,
                        foodVertex, vitalVertexCount - foodVertex);
            }
        }
    }

    private void rebuildTexturedVitals(int windowWidth, int windowHeight, int hp, int food,
                                       boolean poisoned, boolean hungerEffect,
                                       boolean mounted, int mountHp, int mountMaxHp) {
        if (vitalMesh == null) {
            vitalMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true, VITAL_VERTEX_CAPACITY,
                    0, VERTEX_ATTRIBUTES);
        }
        int scale = getHudScale(windowWidth);
        float hotbarW = 182.0f * scale;
        float hotbarH = 22.0f * scale;
        float hotbarX = Math.round((windowWidth - hotbarW) * 0.5f);
        float hotbarY = windowHeight - hotbarH - 12.0f;
        float vitalsY = hotbarY - (mounted ? 18.0f : 12.0f) * scale;
        float iconSize = 9.0f * scale;
        float iconStep = 8.0f * scale;
        float foodRightMostX = hotbarX + hotbarW - iconSize;

        int length = 0;

        float[] heartBgUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.HEART_CONTAINER);
        float[] heartFullUv = GuiTextureAtlas.uvOf(poisoned
                ? GuiTextureAtlas.SpriteKey.HEART_POISONED_FULL
                : GuiTextureAtlas.SpriteKey.HEART_FULL);
        float[] heartHalfUv = GuiTextureAtlas.uvOf(poisoned
                ? GuiTextureAtlas.SpriteKey.HEART_POISONED_HALF
                : GuiTextureAtlas.SpriteKey.HEART_HALF);
        for (int i = 0; i < VITAL_ICON_COUNT; i++) {
            float x = hotbarX + i * iconStep;
            length = writeSpriteQuad(textVertices, length, x, vitalsY, iconSize, iconSize, heartBgUv);
            int threshold = (i + 1) * 2;
            if (hp >= threshold) {
                length = writeSpriteQuad(textVertices, length, x, vitalsY, iconSize, iconSize, heartFullUv);
            } else if (hp == threshold - 1) {
                length = writeSpriteQuad(textVertices, length, x, vitalsY, iconSize, iconSize, heartHalfUv);
            }
        }

        float[] foodBgUv = GuiTextureAtlas.uvOf(hungerEffect
                ? GuiTextureAtlas.SpriteKey.FOOD_EMPTY_HUNGER
                : GuiTextureAtlas.SpriteKey.FOOD_EMPTY);
        float[] foodFullUv = GuiTextureAtlas.uvOf(hungerEffect
                ? GuiTextureAtlas.SpriteKey.FOOD_FULL_HUNGER
                : GuiTextureAtlas.SpriteKey.FOOD_FULL);
        float[] foodHalfUv = GuiTextureAtlas.uvOf(hungerEffect
                ? GuiTextureAtlas.SpriteKey.FOOD_HALF_HUNGER
                : GuiTextureAtlas.SpriteKey.FOOD_HALF);
        for (int i = 0; i < VITAL_ICON_COUNT; i++) {
            float x = foodRightMostX - i * iconStep;
            length = writeSpriteQuad(textVertices, length, x, vitalsY, iconSize, iconSize, foodBgUv);
            int threshold = (i + 1) * 2;
            if (food >= threshold) {
                length = writeSpriteQuad(textVertices, length, x, vitalsY, iconSize, iconSize, foodFullUv);
            } else if (food == threshold - 1) {
                length = writeSpriteQuad(textVertices, length, x, vitalsY, iconSize, iconSize, foodHalfUv);
            }
        }

        if (mounted) {
            float mountY = vitalsY - 10.0f * scale;
            int mountHearts = Math.max(1, Math.min(VITAL_ICON_COUNT, (mountMaxHp + 1) / 2));
            float[] mBgUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.HEART_VEHICLE_CONTAINER);
            float[] mFullUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.HEART_VEHICLE_FULL);
            float[] mHalfUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.HEART_VEHICLE_HALF);
            for (int i = 0; i < mountHearts; i++) {
                float x = foodRightMostX - i * iconStep;
                length = writeSpriteQuad(textVertices, length, x, mountY, iconSize, iconSize, mBgUv);
                int threshold = (i + 1) * 2;
                if (mountHp >= threshold) {
                    length = writeSpriteQuad(textVertices, length, x, mountY, iconSize, iconSize, mFullUv);
                } else if (mountHp == threshold - 1) {
                    length = writeSpriteQuad(textVertices, length, x, mountY, iconSize, iconSize, mHalfUv);
                }
            }
        }

        vitalVertexCount = length / 6;
        healthVertex = vitalVertexCount;
        foodVertex = vitalVertexCount;
        vitalMesh.setVertices(textVertices, 0, length);

        vitalWidth = windowWidth;
        vitalHeight = windowHeight;
        vitalHealth = hp;
        vitalFood = food;
        vitalPoisoned = poisoned;
        vitalHungerEffect = hungerEffect;
        vitalMounted = mounted;
        vitalMountHp = mountHp;
        vitalMountMaxHp = mountMaxHp;
        vitalUsedGuiAtlas = true;
    }

    private static int writeSpriteQuad(float[] buffer, int offset, float x, float y,
                                       float w, float h, float[] uv) {
        if (offset + 36 > buffer.length) {
            return offset;
        }
        BitmapFont.writeQuad(buffer, offset, x, y + h, 0f, x + w, y + h, 0f,
                x + w, y, 0f, x, y, 0f, uv[0], uv[1], uv[2], uv[3]);
        return offset + 36;
    }

    private void renderTexturedMountJumpBar(GuiTextureAtlas atlas, MountableMob mount,
                                            int windowWidth, int windowHeight) {
        int scale = getHudScale(windowWidth);
        float hotbarW = 182.0f * scale;
        float hotbarH = 22.0f * scale;
        float hotbarX = Math.round((windowWidth - hotbarW) * 0.5f);
        float hotbarY = windowHeight - hotbarH - 12.0f;
        float jumpY = hotbarY - 7.0f * scale;
        float jumpH = 5.0f * scale;

        float[] bgUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.JUMP_BAR_BACKGROUND);
        float cooldown = mount.dashCooldownRemaining();
        float progress = cooldown > 0f ? 1f - cooldown / 2.75f : mount.chargeProgress();
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));

        atlas.bindHudAtlas();
        currentShader.setUniform1i("uUseTexture", 1);
        currentShader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        drawTexturedRect2D(hotbarX, jumpY, hotbarW, jumpH, bgUv[0], bgUv[1], bgUv[2], bgUv[3]);

        if (clamped > 0.001f) {
            GuiTextureAtlas.SpriteKey fillKey = cooldown > 0f
                    ? GuiTextureAtlas.SpriteKey.JUMP_BAR_COOLDOWN
                    : GuiTextureAtlas.SpriteKey.JUMP_BAR_PROGRESS;
            float[] fillUv = GuiTextureAtlas.uvOf(fillKey);
            float uEnd = fillUv[0] + (fillUv[2] - fillUv[0]) * clamped;
            drawTexturedRect2D(hotbarX, jumpY, hotbarW * clamped, jumpH,
                    fillUv[0], fillUv[1], uEnd, fillUv[3]);
        }
        atlas.unbind();
    }

    private void renderMountVitals(MountableMob mount, int width, int height) {
        float y = height - HOTBAR_SLOT_SIZE - HOTBAR_SLOT_PADDING * 2f - 75f;
        float left = width * 0.5f - 100f;
        drawMountMeter("MOUNT HP", left, y, mount.getHealth() / mount.getMaxHealth(), 0.94f, 0.29f, 0.35f);
        float cooldown = mount.dashCooldownRemaining();
        float progress = cooldown > 0f ? 1f - cooldown / 2.75f : mount.chargeProgress();
        drawMountMeter(mount.isDashMount() ? "DASH" : "JUMP", left + 106f, y, progress,
                cooldown > 0f ? 0.30f : 0.97f, 0.70f, cooldown > 0f ? 0.95f : 0.25f);
    }

    private void drawMountMeter(String label, float x, float y, float progress, float r, float g, float b) {
        currentShader.setUniform1i("uUseTexture", 0);
        currentShader.setUniformVec4("uTintColor", 0.15f, 0.12f, 0.17f, 0.95f);
        drawRect2D(x, y, 94f, 8f);
        if (progress > 0f) {
            currentShader.setUniformVec4("uTintColor", r, g, b, 1f);
            drawRect2D(x + 1f, y + 1f, 92f * Math.min(1f, progress), 6f);
        }
        currentShader.setUniformVec4("uTintColor", 1f, 1f, 1f, 0.95f);
        drawPixelText(label, x, y - 9f, 1f);
    }

    private void rebuildFallbackVitals(int windowWidth, int windowHeight, int hp, int food) {
        float halfBarWidth = (Hotbar.SLOT_COUNT * HOTBAR_SLOT_SIZE
                + (Hotbar.SLOT_COUNT + 1) * HOTBAR_SLOT_PADDING) * 0.5f;
        float span = (VITAL_ICON_COUNT - 1) * VITAL_SPACING + 7f * VITAL_PIXEL;
        float inset = Math.max(4f, windowWidth * 0.5f - halfBarWidth);
        float y = windowHeight - HOTBAR_SLOT_SIZE - HOTBAR_SLOT_PADDING * 2f - 58f;
        float foodX = windowWidth - inset - span;
        if (vitalMesh == null) {
            vitalMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true, VITAL_VERTEX_CAPACITY,
                    0, VERTEX_ATTRIBUTES);
        }
        int length = writeVitalIcons(HEART_ROWS, inset, y, VITAL_ICON_COUNT * 2, 0);
        length = writeVitalIcons(FOOD_ROWS, foodX, y, VITAL_ICON_COUNT * 2, length);
        healthVertex = length / 6;
        length = writeVitalIcons(HEART_ROWS, inset, y, hp, length);
        foodVertex = length / 6;
        length = writeVitalIcons(FOOD_ROWS, foodX, y, food, length);
        vitalVertexCount = length / 6;
        vitalMesh.setVertices(textVertices, 0, length);
        vitalWidth = windowWidth;
        vitalHeight = windowHeight;
        vitalHealth = hp;
        vitalFood = food;
        vitalUsedGuiAtlas = false;
    }

    private int writeVitalIcons(int[] rows, float x, float y, int units, int offset) {
        for (int icon = 0; icon < VITAL_ICON_COUNT && units > icon * 2; icon++) {
            int width = units - icon * 2 >= 2 ? 7 : 4;
            float iconX = x + icon * VITAL_SPACING;
            for (int row = 0; row < rows.length; row++) {
                int col = 0;
                while (col < width) {
                    if ((rows[row] & (1 << (6 - col))) == 0) {
                        col++;
                        continue;
                    }
                    int first = col++;
                    while (col < width && (rows[row] & (1 << (6 - col))) != 0) {
                        col++;
                    }
                    float x0 = iconX + first * VITAL_PIXEL;
                    float x1 = iconX + col * VITAL_PIXEL;
                    float y0 = y + row * VITAL_PIXEL;
                    float y1 = y0 + VITAL_PIXEL;
                    BitmapFont.writeQuad(textVertices, offset, x0, y1, 0f, x1, y1, 0f, x1, y0, 0f,
                            x0, y0, 0f, 0f, 0f, 1f, 1f);
                    offset += 36;
                }
            }
        }
        return offset;
    }

    private void renderStatusEffects(Player player, int windowWidth) {
        float y = 10f;
        for (int i = 0; i < StatusEffectType.COUNT; i++) {
            StatusEffectType type = StatusEffectType.at(i);
            float remaining = player.getEffectDuration(type);
            if (remaining <= 0f) {
                continue;
            }
            effectLabel.setLength(0);
            effectLabel.append(type.name()).append(' ').append((int) Math.ceil(remaining)).append('S');
            float x = windowWidth - 10f - effectLabel.length() * 9f;
            currentShader.setUniform1i("uUseTexture", 0);
            currentShader.setUniformVec4("uTintColor", 0f, 0f, 0f, 0.8f);
            drawPixelText(effectLabel, x + 1.5f, y + 1.5f, 1.5f);
            currentShader.setUniformVec4("uTintColor", 1f, 1f, 1f, 0.95f);
            drawPixelText(effectLabel, x, y, 1.5f);
            y += 15f;
        }
    }

    private void renderEnvironment() {
        String biomeName = weather.biome().toBiome().getDisplayName();
        environmentLabel.setLength(0);
        environmentLabel.append(biomeName).append(" - ").append(weather.label());
        drawEnvironmentLine(environmentLabel, 10f);

        int minuteOfDay = worldTime.minuteOfDay();
        int hour = minuteOfDay / 60;
        int minute = minuteOfDay % 60;
        environmentLabel.setLength(0);
        environmentLabel.append("DAY ").append(worldTime.dayCount() + 1).append(" - ");
        environmentLabel.append((char) ('0' + hour / 10)).append((char) ('0' + hour % 10));
        environmentLabel.append(':').append((char) ('0' + minute / 10)).append((char) ('0' + minute % 10));
        drawEnvironmentLine(environmentLabel, 25f);
    }

    private void drawEnvironmentLine(CharSequence label, float y) {
        currentShader.setUniform1i("uUseTexture", 0);
        currentShader.setUniformVec4("uTintColor", 0f, 0f, 0f, 0.8f);
        drawPixelText(label, 11.5f, y + 1.5f, 1.5f);
        currentShader.setUniformVec4("uTintColor", 1f, 1f, 1f, 0.95f);
        drawPixelText(label, 10f, y, 1.5f);
    }

    private void renderCrosshair(ShaderProgram shader, GuiTextureAtlas atlas, int windowWidth, int windowHeight) {
        if (atlas != null) {
            int scale = getHudScale(windowWidth);
            float size = 15.0f * scale;
            float x = Math.round(windowWidth * 0.5f - size * 0.5f);
            float y = Math.round(windowHeight * 0.5f - size * 0.5f);
            float[] uv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.CROSSHAIR);
            atlas.bindHudAtlas();
            shader.setUniform1i("uUseTexture", 1);
            shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 0.92f);
            drawTexturedRect2D(x, y, size, size, uv[0], uv[1], uv[2], uv[3]);
            atlas.unbind();
            return;
        }

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

    // =========================================================================
    // 4. THANH CÔNG CỤ NHANH 9 Ô (HOTBAR)
    // =========================================================================

    private void renderHotbar(ShaderProgram shader, Texture textureAtlas, ItemTextureAtlas itemAtlas,
                              GuiTextureAtlas guiAtlas, Hotbar hotbar, int windowWidth,
                              int windowHeight, boolean mounted) {
        boolean useGui = guiAtlas != null;
        boolean changed = windowWidth != hotbarWidth || windowHeight != hotbarHeight
                || hotbar.getSelectedSlot() != hotbarSelection
                || useGui != hotbarUsedGuiAtlas
                || mounted != hotbarMounted;
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            Item item = hotbar.getSlotItem(i);
            int count = hotbar.getSlotCount(i);
            changed |= item != hotbarItems[i] || count != hotbarCounts[i];
            hotbarItems[i] = item;
            hotbarCounts[i] = count;
        }
        if (changed) {
            hotbarWidth = windowWidth;
            hotbarHeight = windowHeight;
            hotbarSelection = hotbar.getSelectedSlot();
            hotbarUsedGuiAtlas = useGui;
            hotbarMounted = mounted;
            hotbarLength = 0;
            hotbarStyleCount = 0;
            recordingHotbar = true;
            if (useGui) {
                rebuildTexturedHotbar(hotbar, windowWidth, windowHeight, mounted);
            } else {
                rebuildFallbackHotbar(hotbar, windowWidth, windowHeight);
            }
            recordingHotbar = false;
            if (hotbarMesh == null) {
                hotbarMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, true,
                        HOTBAR_VERTEX_CAPACITY, 0, VERTEX_ATTRIBUTES);
            }
            hotbarMesh.setVertices(hotbarVertices, 0, hotbarLength);
        }

        var program = shader.getGdxShader();
        int start = 0;
        byte boundSource = TEXTURE_NONE;
        hotbarMesh.bind(program);
        for (int i = 0; i < hotbarStyleCount; i++) {
            int end = hotbarEnd[i];
            if (end <= start) {
                continue;
            }
            byte source = hotbarTextureSource[i];
            if (source == TEXTURE_BLOCK_ATLAS && boundSource != TEXTURE_BLOCK_ATLAS) {
                textureAtlas.bind();
                boundSource = TEXTURE_BLOCK_ATLAS;
            } else if (source == TEXTURE_ITEM_ATLAS && itemAtlas != null && boundSource != TEXTURE_ITEM_ATLAS) {
                itemAtlas.bind();
                boundSource = TEXTURE_ITEM_ATLAS;
            } else if (source == TEXTURE_GUI_ATLAS && guiAtlas != null && boundSource != TEXTURE_GUI_ATLAS) {
                guiAtlas.bindHudAtlas();
                boundSource = TEXTURE_GUI_ATLAS;
            }
            int tint = i * 4;
            boolean useTexture = source == TEXTURE_BLOCK_ATLAS
                    || (source == TEXTURE_ITEM_ATLAS && itemAtlas != null)
                    || (source == TEXTURE_GUI_ATLAS && guiAtlas != null);
            shader.setUniform1i("uUseTexture", useTexture ? 1 : 0);
            shader.setUniformVec4("uTintColor", hotbarTints[tint], hotbarTints[tint + 1],
                    hotbarTints[tint + 2], hotbarTints[tint + 3]);
            hotbarMesh.render(program, GL20.GL_TRIANGLES, start, end - start, false);
            start = end;
        }
        hotbarMesh.unbind(program);
        if (boundSource == TEXTURE_GUI_ATLAS && guiAtlas != null) {
            guiAtlas.unbind();
        } else if (boundSource == TEXTURE_ITEM_ATLAS && itemAtlas != null) {
            itemAtlas.unbind();
        } else if (boundSource == TEXTURE_BLOCK_ATLAS) {
            textureAtlas.unbind();
        }
    }

    private void rebuildTexturedHotbar(Hotbar hotbar, int windowWidth, int windowHeight, boolean mounted) {
        int scale = getHudScale(windowWidth);
        float totalWidth = 182.0f * scale;
        float barHeight = 22.0f * scale;
        float startX = Math.round((windowWidth - totalWidth) * 0.5f);
        float startY = windowHeight - barHeight - 12.0f;

        float[] hotbarUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.HOTBAR);
        beginHotbarStyle(TEXTURE_GUI_ATLAS, 1.0f, 1.0f, 1.0f, 1.0f);
        drawTexturedRect2D(startX, startY, totalWidth, barHeight,
                hotbarUv[0], hotbarUv[1], hotbarUv[2], hotbarUv[3]);

        int selectedSlot = hotbar.getSelectedSlot();
        float[] selUv = GuiTextureAtlas.uvOf(GuiTextureAtlas.SpriteKey.HOTBAR_SELECTION);
        float selX = startX + (selectedSlot * 20.0f - 1.0f) * scale;
        float selY = startY - 1.0f * scale;
        drawTexturedRect2D(selX, selY, 24.0f * scale, 23.0f * scale,
                selUv[0], selUv[1], selUv[2], selUv[3]);

        float slotInner = 16.0f * scale;
        float keyLabelScale = Math.max(1.0f, scale * 0.45f);
        float countScale = Math.max(1.4f, scale * 0.60f);

        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            float slotX = startX + (3.0f + i * 20.0f) * scale;
            float slotY = startY + 3.0f * scale;

            beginHotbarStyle(TEXTURE_NONE, 0.78f, 0.80f, 0.85f, 0.78f);
            drawPixelText(NUMBER_LABELS[i + 1], slotX + 1.5f, slotY + 1.5f, keyLabelScale);

            Item item = hotbar.getSlotItem(i);
            int count = hotbar.getSlotCount(i);
            if (!item.isNone() && count > 0) {
                float iconWidth = slotInner;
                float iconHeight = slotInner;
                float[] uv;
                byte texSource;
                if (item.isBlock()) {
                    Block itemBlock = item.getBlock();
                    if (itemBlock.getGeometryType() == Block.GeometryType.CACTUS) {
                        Block.Bounds shape = itemBlock.getSelectionBounds(0);
                        iconWidth *= shape.maxX() - shape.minX();
                    } else if (itemBlock.getGeometryType() == Block.GeometryType.SNOW_LAYER) {
                        iconHeight *= itemBlock.getSelectionBounds(0).maxY();
                    }
                    uv = Chunk.tileUv(itemBlock.getTileIndex(Block.Face.SOUTH));
                    texSource = TEXTURE_BLOCK_ATLAS;
                } else {
                    uv = item.getTileUV();
                    texSource = TEXTURE_ITEM_ATLAS;
                }
                float iconX = slotX + (slotInner - iconWidth) * 0.5f;
                float iconY = slotY + (slotInner - iconHeight) * 0.5f;

                beginHotbarStyle(texSource, 1.0f, 1.0f, 1.0f, 1.0f);
                drawTexturedRect2D(iconX, iconY, iconWidth, iconHeight, uv[0], uv[1], uv[2], uv[3]);

                String countStr = NUMBER_LABELS[count];
                float textW = BitmapFont.measureWidth(countStr, countScale);
                float tx = slotX + slotInner - textW - 1.0f;
                float ty = slotY + slotInner - BitmapFont.measureHeight(countScale) - 1.0f;

                beginHotbarStyle(TEXTURE_NONE, 0.05f, 0.05f, 0.05f, 0.95f);
                drawPixelText(countStr, tx + 1.5f, ty + 1.5f, countScale);

                beginHotbarStyle(TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
                drawPixelText(countStr, tx, ty, countScale);
            }
        }

        Item selectedItem = hotbar.getSelectedItem();
        if (!selectedItem.isNone() && hotbar.getSelectedCount() > 0) {
            String label = selectedItem.getDisplayName();
            float nameScale = Math.max(1.5f, scale * 0.68f);
            float labelW = BitmapFont.measureWidth(label, nameScale);
            float lx = (windowWidth - labelW) * 0.5f;
            float ly = startY - (mounted ? 40.0f : 29.0f) * scale;

            beginHotbarStyle(TEXTURE_NONE, 0.0f, 0.0f, 0.0f, 0.78f);
            drawPixelText(label, lx + 2.0f, ly + 2.0f, nameScale);
            beginHotbarStyle(TEXTURE_NONE, 1.0f, 0.96f, 0.72f, 1.0f);
            drawPixelText(label, lx, ly, nameScale);
        }
    }

    private void rebuildFallbackHotbar(Hotbar hotbar, int windowWidth, int windowHeight) {
        float totalWidth = Hotbar.SLOT_COUNT * HOTBAR_SLOT_SIZE + (Hotbar.SLOT_COUNT + 1) * HOTBAR_SLOT_PADDING;
        float barHeight = HOTBAR_SLOT_SIZE + HOTBAR_SLOT_PADDING * 2.0f;
        float startX = (windowWidth - totalWidth) * 0.5f;
        float startY = windowHeight - barHeight - 16.0f;

        beginHotbarStyle(TEXTURE_NONE, 0.10f, 0.11f, 0.14f, 0.82f);
        drawRect2D(startX - 4.0f, startY - 4.0f, totalWidth + 8.0f, barHeight + 8.0f);

        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            float slotX = startX + HOTBAR_SLOT_PADDING + i * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_PADDING);
            float slotY = startY + HOTBAR_SLOT_PADDING;

            boolean isSelected = (i == hotbar.getSelectedSlot());
            if (isSelected) {
                beginHotbarStyle(TEXTURE_NONE, 1.0f, 0.92f, 0.32f, 1.0f);
                drawRect2D(slotX - 3.0f, slotY - 3.0f, HOTBAR_SLOT_SIZE + 6.0f, HOTBAR_SLOT_SIZE + 6.0f);
                beginHotbarStyle(TEXTURE_NONE, 0.28f, 0.30f, 0.35f, 0.95f);
            } else {
                beginHotbarStyle(TEXTURE_NONE, 0.18f, 0.19f, 0.23f, 0.90f);
            }
            drawRect2D(slotX, slotY, HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE);

            beginHotbarStyle(TEXTURE_NONE, 0.72f, 0.74f, 0.78f, 0.85f);
            drawPixelText(NUMBER_LABELS[i + 1], slotX + 4.0f, slotY + 4.0f, 1.5f);

            Item item = hotbar.getSlotItem(i);
            int count = hotbar.getSlotCount(i);
            if (!item.isNone() && count > 0) {
                float iconSize = 28.0f;
                float iconWidth = iconSize;
                float iconHeight = iconSize;
                float[] uv;
                byte texSource;
                if (item.isBlock()) {
                    Block itemBlock = item.getBlock();
                    if (itemBlock.getGeometryType() == Block.GeometryType.CACTUS) {
                        Block.Bounds shape = itemBlock.getSelectionBounds(0);
                        iconWidth *= shape.maxX() - shape.minX();
                    } else if (itemBlock.getGeometryType() == Block.GeometryType.SNOW_LAYER) {
                        iconHeight *= itemBlock.getSelectionBounds(0).maxY();
                    }
                    uv = Chunk.tileUv(itemBlock.getTileIndex(Block.Face.SOUTH));
                    texSource = TEXTURE_BLOCK_ATLAS;
                } else {
                    uv = item.getTileUV();
                    texSource = TEXTURE_ITEM_ATLAS;
                }
                float iconX = slotX + (HOTBAR_SLOT_SIZE - iconWidth) * 0.5f;
                float iconY = slotY + (HOTBAR_SLOT_SIZE - iconHeight) * 0.5f;

                beginHotbarStyle(texSource, 1.0f, 1.0f, 1.0f, 1.0f);
                drawTexturedRect2D(iconX, iconY, iconWidth, iconHeight, uv[0], uv[1], uv[2], uv[3]);

                String countStr = NUMBER_LABELS[count];
                float textScale = 2.0f;
                float textW = BitmapFont.measureWidth(countStr, textScale);
                float tx = slotX + HOTBAR_SLOT_SIZE - textW - 3.0f;
                float ty = slotY + HOTBAR_SLOT_SIZE - BitmapFont.measureHeight(textScale) - 3.0f;

                beginHotbarStyle(TEXTURE_NONE, 0.05f, 0.05f, 0.05f, 0.95f);
                drawPixelText(countStr, tx + 1.5f, ty + 1.5f, textScale);

                beginHotbarStyle(TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
                drawPixelText(countStr, tx, ty, textScale);
            }
        }

        Item selectedItem = hotbar.getSelectedItem();
        if (!selectedItem.isNone() && hotbar.getSelectedCount() > 0) {
            String label = selectedItem.getDisplayName();
            float scale = 2.0f;
            float labelW = BitmapFont.measureWidth(label, scale);
            float lx = (windowWidth - labelW) * 0.5f;
            float ly = startY - 24.0f;

            beginHotbarStyle(TEXTURE_NONE, 0.0f, 0.0f, 0.0f, 0.75f);
            drawPixelText(label, lx + 2.0f, ly + 2.0f, scale);
            beginHotbarStyle(TEXTURE_NONE, 1.0f, 0.96f, 0.72f, 1.0f);
            drawPixelText(label, lx, ly, scale);
        }
    }

    private void beginHotbarStyle(byte textureSource, float r, float g, float b, float a) {
        int style = hotbarStyleCount++;
        int tint = style * 4;
        hotbarTextureSource[style] = textureSource;
        hotbarTints[tint] = r;
        hotbarTints[tint + 1] = g;
        hotbarTints[tint + 2] = b;
        hotbarTints[tint + 3] = a;
        hotbarEnd[style] = hotbarLength / 6;
    }

    // =========================================================================
    // 5. CÁC HÀM VẼ HÌNH HỌC CƠ SỞ DÙNG CHUNG (RECT, 3D BOX, BITMAP FONT 5×7)
    // =========================================================================

    /** Vẽ hình chữ nhật bằng màu đồng nhất của shader. */
    void drawRect2D(float x, float y, float w, float h) {
        drawTexturedRect2D(x, y, w, h, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    /** Cập nhật bộ đệm sáu đỉnh dùng lại cho hình chữ nhật và biểu tượng. */
    void drawTexturedRect2D(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        BitmapFont.writeQuad(rectVertices, 0, x, y + h, 0.0f, x + w, y + h, 0.0f,
                x + w, y, 0.0f, x, y, 0.0f, u0, v0, u1, v1);
        uploadAndDrawTriangles(rectVertices);
    }

    /** Ghi sáu mặt hộp vào bộ đệm sử dụng lại cho ngắm và đào block. */
    private void draw3DBoxSolid(float x0, float y0, float z0, float x1, float y1, float z1) {
        BitmapFont.writeQuad(boxVertices, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 0, 0, 0);
        BitmapFont.writeQuad(boxVertices, 36, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, 0, 0, 0);
        BitmapFont.writeQuad(boxVertices, 72, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 0, 0);
        BitmapFont.writeQuad(boxVertices, 108, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, 0, 0);
        BitmapFont.writeQuad(boxVertices, 144, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 0, 0, 0, 0);
        BitmapFont.writeQuad(boxVertices, 180, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 0, 0, 0, 0);
        uploadAndDrawTriangles(boxVertices);
    }

    /** Vẽ chuỗi chữ 5×7 thông qua {@link BitmapFont} bằng bộ đệm đỉnh tái sử dụng. */
    void drawPixelText(CharSequence text, float startX, float startY, float pixelScale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int length = 0;
        float cursorX = startX;
        for (int i = 0; i < text.length(); i++) {
            int[] glyph = BitmapFont.getGlyph(text.charAt(i));
            if (glyph != null) {
                for (int row = 0; row < BitmapFont.GLYPH_HEIGHT; row++) {
                    int rowBits = glyph[row];
                    for (int col = 0; col < BitmapFont.GLYPH_WIDTH; col++) {
                        if (((rowBits >> (4 - col)) & 1) == 1) {
                            float px = cursorX + col * pixelScale;
                            float py = startY + row * pixelScale;
                            float right = px + pixelScale;
                            float bottom = py + pixelScale;
                            if (length == textVertices.length) {
                                uploadAndDrawTriangles(textVertices, length);
                                length = 0;
                            }
                            BitmapFont.writeQuad(textVertices, length, px, bottom, 0, right, bottom, 0,
                                    right, py, 0, px, py, 0, 0, 0, 1, 1);
                            length += 36;
                        }
                    }
                }
            }
            cursorX += BitmapFont.CHAR_ADVANCE * pixelScale;
        }
        uploadAndDrawTriangles(textVertices, length);
    }

    private void uploadAndDrawTriangles(float[] vertices) {
        uploadAndDrawTriangles(vertices, vertices.length);
    }

    private void uploadAndDrawTriangles(float[] vertices, int length) {
        if (currentShader == null || length == 0) {
            return;
        }
        if (recordingHotbar) {
            System.arraycopy(vertices, 0, hotbarVertices, hotbarLength, length);
            hotbarLength += length;
            hotbarEnd[hotbarStyleCount - 1] = hotbarLength / 6;
            return;
        }
        int count = length / 6;
        if (uiMesh == null || uiMesh.getMaxVertices() < count) {
            if (uiMesh != null) {
                uiMesh.dispose();
            }
            uiMesh = new Mesh(false, Math.max(count, 4096), 0, VERTEX_ATTRIBUTES);
        }
        uiMesh.setVertices(vertices, 0, length);
        uiMesh.render(currentShader.getGdxShader(), GL20.GL_TRIANGLES, 0, count);
    }

    @Override
    public void dispose() {
        if (hotbarMesh != null) {
            hotbarMesh.dispose();
            hotbarMesh = null;
        }
        if (vitalMesh != null) {
            vitalMesh.dispose();
            vitalMesh = null;
            vitalWidth = -1;
        }
        if (uiMesh != null) {
            uiMesh.dispose();
            uiMesh = null;
        }
        if (oxygenMesh != null) {
            oxygenMesh.dispose();
            oxygenMesh = null;
            oxygenWidth = -1;
        }
        if (guiAtlas != null) {
            guiAtlas.dispose();
            guiAtlas = null;
        }
        inventoryScreen = null;
        debugOverlay = null;
        debugWorld = null;
    }
}
