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
import com.voxelgame.entity.Item;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.StatusEffectType;
import com.voxelgame.entity.mob.MountableMob;

/**
 * Hệ thống Kết xuất Giao diện Người dùng 2D & Hiệu ứng Tương tác 3D (UI & HUD Renderer),
 * triển khai giao diện {@link Disposable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Triển khai `Disposable` để thống nhất cơ chế giải phóng bộ đệm Mesh và {@link GuiTextureAtlas}.
 *    - Đóng gói toàn bộ thuật toán vẽ 2D Orthographic, sprite pixel-perfect Minecraft 1.21.11
 *      và bảng mã phông chữ ma trận điểm ảnh 5x7 đằng sau `render2DHUD(...)` và `renderTargetBlockOverlay3D(...)`.
 * 2. Tính Đóng gói (Encapsulation):
 *    - Truy xuất thông tin khối đang ngắm thông qua các phương thức getter đóng gói của `Raycast.HitResult`
 *      (`hit.isHit()`, `hit.getBlockX()`, `hit.getBlockY()`, `hit.getBlockZ()`) thay vì truy cập trường trực tiếp.
 * 3. Tính Đa hình (Polymorphism):
 *    - Sử dụng tile mặt nam trên atlas để vẽ biểu tượng của từng
 *      loại `Block` hoặc `Item` đang nằm trong 9 ô `Hotbar`.
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

    // Kích thước dự phòng của 9 ô Hotbar khi chưa có GuiTextureAtlas
    private static final float HOTBAR_SLOT_SIZE = 50.0f;
    private static final float HOTBAR_SLOT_PADDING = 4.0f;
    /** Một nền/khung chọn, tối đa sáu lượt mỗi ô và hai lượt tên khối. */
    private static final int HOTBAR_STYLES = 6 + Hotbar.SLOT_COUNT * 6;
    /** Dự trù cả nhãn khối/vật phẩm dài, bóng chữ và số lượng hai chữ số ở mọi ô. */
    private static final int HOTBAR_VERTEX_CAPACITY = 4096 * 6;
    private static final byte TEXTURE_NONE = 0;
    private static final byte TEXTURE_BLOCK_ATLAS = 1;
    private static final byte TEXTURE_ITEM_ATLAS = 2;
    private static final byte TEXTURE_GUI_ATLAS = 3;
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

    // Hộp mặc định cho đường gọi cũ và bộ đệm đỉnh dùng lại cho lớp phủ, viền và biểu tượng.
    private static final Block.Bounds DEFAULT_TARGET_BOUNDS = new Block.Bounds(0, 0, 0, 1, 1, 1);
    private final float[] rectVertices = new float[6 * 6];
    private final float[] boxVertices = new float[6 * 6 * 6];
    /** Bộ đệm đủ cho nhãn dài nhất của HUD và chữ 5×7 dày tối đa. */
    private final float[] textVertices = new float[32 * 5 * 7 * 36];
    private static final int[][] GLYPHS = createGlyphs();
    private static final String[] NUMBER_LABELS = new String[101];
    private static final String[] VOLUME_LABELS = new String[101];
    /** Đồng hồ và trạng thái thời tiết dùng bộ đệm ký tự, tránh tạo chuỗi theo frame. */
    private final StringBuilder environmentLabel = new StringBuilder(48);
    /** Nhãn trạng thái và mẫu pixel dùng lại khi vẽ tim và đùi thịt. */
    private final StringBuilder effectLabel = new StringBuilder(32);
    private static final int[] HEART_ROWS = {0b0110110, 0b1111111, 0b1111111, 0b0111110, 0b0011100, 0b0001000};
    private static final int[] FOOD_ROWS = {0b0001110, 0b0011111, 0b0011111, 0b0111110, 0b1111000, 0b1100000};
    /** Mỗi biểu tượng đại diện hai điểm, với hai pixel màn hình trên mỗi pixel mẫu (fallback). */
    private static final int VITAL_ICON_COUNT = 10;
    private static final float VITAL_PIXEL = 2f;
    private static final float VITAL_SPACING = 17f;
    /** Mỗi hàng bitmap có tối đa hai dải pixel; dự trù cả nền và phần màu. */
    private static final int VITAL_VERTEX_CAPACITY =
            VITAL_ICON_COUNT * (HEART_ROWS.length + FOOD_ROWS.length) * 2 * 2 * 6;
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
    private WorldTime worldTime;
    private WeatherManager weather;
    // === HOOK: TRACK B - FIELDS ===
    // Khai báo tài nguyên overlay nứt khối / ẩn crosshair của Track B tại đây.
    // === END HOOK: TRACK B - FIELDS ===

    // === HOOK: TRACK C - FIELDS ===
    // Khai báo GuiTextureAtlas, thanh Oxy, InventoryScreenRenderer, DebugOverlayRenderer tại đây.
    /** Atlas sprite HUD/Widget chuẩn Minecraft 1.21.11, khởi tạo lười khi có OpenGL Context. */
    private GuiTextureAtlas guiAtlas;
    private boolean guiAtlasFailed = false;

    /** Mẫu bitmap 7×6 dự phòng cho viền bọt khí (rỗng) và lõi bọt khí (đầy) trên thanh Oxy. */
    private static final int[] BUBBLE_OUTLINE_ROWS = {
            0b0011100,
            0b0100010,
            0b1000001,
            0b1000001,
            0b0100010,
            0b0011100
    };
    private static final int[] BUBBLE_FILL_ROWS = {
            0b0011100,
            0b0111110,
            0b1111111,
            0b1111111,
            0b0111110,
            0b0011100
    };
    private static final int OXYGEN_VERTEX_CAPACITY =
            VITAL_ICON_COUNT * BUBBLE_FILL_ROWS.length * 4 * 6;
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
    private InventoryScreenRenderer inventoryScreen;
    private DebugOverlayRenderer debugOverlay;
    private World debugWorld;

    /** Khởi tạo lười {@link GuiTextureAtlas} khi đã có OpenGL Context hợp lệ. */
    private GuiTextureAtlas ensureGuiAtlas() {
        if (guiAtlas == null && !guiAtlasFailed && Gdx.gl != null) {
            try {
                guiAtlas = new GuiTextureAtlas();
            } catch (RuntimeException | Error e) {
                guiAtlasFailed = true;
            }
        }
        return guiAtlas;
    }

    /** Tính hệ số phóng nguyên pixel-perfect (2 hoặc 3) phù hợp với chiều rộng cửa sổ. */
    private static int getHudScale(int windowWidth) {
        return Math.max(2, Math.min(3, windowWidth / 420));
    }

    /** Gắn bộ kết xuất màn hình túi đồ 36 ô để vẽ trong lượt HUD 2D. */
    public void setInventoryScreen(InventoryScreenRenderer inventoryScreen) {
        this.inventoryScreen = inventoryScreen;
    }

    /** Gắn bộ kết xuất màn hình thông số F3 và thế giới để vẽ trong lượt HUD 2D. */
    public void setDebugOverlay(DebugOverlayRenderer debugOverlay, World world) {
        this.debugOverlay = debugOverlay;
        this.debugWorld = world;
    }

    /** Kiểm tra ký tự có mẫu glyph 5x7 tích hợp (phục vụ màn hình F3 và kiểm thử). */
    public static boolean hasGlyph(char c) {
        char upper = Character.toUpperCase(c);
        return upper < GLYPHS.length && GLYPHS[upper] != null;
    }

    /** Vẽ hình chữ nhật 2D dùng lại bộ đệm của UIRenderer cho màn hình thông số F3. */
    void drawOverlayRect(ShaderProgram shader, float x, float y, float w, float h,
                         float r, float g, float b, float a) {
        this.currentShader = shader;
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", r, g, b, a);
        drawRect2D(x, y, w, h);
    }

    /** Vẽ dòng chữ 5x7 có bóng đổ cho màn hình thông số F3 (zero-GC). */
    void drawOverlayText(ShaderProgram shader, CharSequence text, float x, float y, float scale) {
        this.currentShader = shader;
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.0f, 0.0f, 0.0f, 0.85f);
        drawPixelText(text, x + 1.5f, y + 1.5f, scale);
        shader.setUniformVec4("uTintColor", 0.92f, 0.94f, 0.98f, 1.0f);
        drawPixelText(text, x, y, scale);
    }

    /** Vẽ hàng 10 bọt khí Oxy phía trên thanh đói bên phải bằng sprite {@link GuiTextureAtlas} (zero-GC). */
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

    /** Dựng lưới 10 bọt khí từ {@code air.png}, {@code air_bursting.png}, {@code air_empty.png}. */
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
            writeQuad(textVertices, length, x, y + iconSize, 0f, x + iconSize, y + iconSize, 0f,
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

    /** Dựng lại hình học 10 bọt khí dạng bitmap dự phòng khi không có GuiTextureAtlas. */
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

    /** Ghi các dải pixel bọt khí từ phải sang trái thẳng hàng với thanh đói (dự phòng). */
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
                    writeQuad(textVertices, offset, x0, y1, 0f, x1, y1, 0f, x1, y0, 0f,
                            x0, y0, 0f, 0f, 0f, 1f, 1f);
                    offset += 36;
                }
            }
        }
        return offset;
    }
    // === END HOOK: TRACK C - FIELDS ===

    static {
        for (int i = 0; i < NUMBER_LABELS.length; i++) {
            NUMBER_LABELS[i] = Integer.toString(i);
            VOLUME_LABELS[i] = "VOLUME: " + i + "%";
        }
    }

    /** Bốn hộp màn tạm dùng chung cho vẽ và kiểm tra click trên luồng chính. */
    private final float[] resumeButtonBounds = new float[4];
    private final float[] volumeMinusBounds = new float[4];
    private final float[] volumeSliderBounds = new float[4];
    private final float[] volumePlusBounds = new float[4];
    private final Matrix4 modelMatrix = new Matrix4();

    public UIRenderer() {
        // Khởi tạo Mesh động cho UI với dung lượng ban đầu 4096 đỉnh
        uiMesh = new Mesh(false, 4096, 0, VERTEX_ATTRIBUTES);
    }

    /** Gắn đồng hồ và thời tiết để hiển thị trong lượt vẽ HUD hiện có. */
    public void setEnvironment(WorldTime time, WeatherManager weatherManager) {
        worldTime = time;
        weather = weatherManager;
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
        renderTargetBlockOverlay3D(shader, hit, miningProgress, DEFAULT_TARGET_BOUNDS);
    }

    /** Vẽ lớp phủ đào và ngắm đúng hộp chọn cục bộ của block đang trúng tia. */
    public void renderTargetBlockOverlay3D(
            ShaderProgram shader,
            Raycast.HitResult hit,
            float miningProgress,
            Block.Bounds bounds
    ) {
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

        // Phóng to nhẹ khối bọc ngoài thêm 0.004f mỗi bên để không bị trùng mặt phẳng (Z-Fighting) với Block gốc
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
            // Khi đang giữ chuột trái đập block: Vẽ lớp phủ nứt/tối dần và co giãn theo phần trăm tiến trình
            float alpha = Math.min(0.68f, 0.18f + miningProgress * 0.50f);
            shader.setUniformVec4("uTintColor", 0.08f, 0.08f, 0.08f, alpha);
            draw3DBoxSolid(x0, y0, z0, x1, y1, z1);

            // Vẽ thêm lõi chỉ báo vết nứt co dần ở tâm mỗi mặt
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
            // Hai dải ngang phủ góc. Hai dải dọc chỉ phủ phần giữa để màu không chồng ở góc.
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
        render2DHUD(shader, textureAtlas, null, camera, player, audioManager,
                windowWidth, windowHeight, miningProgress, isPaused, mouseX, mouseY);
    }

    /** Nạp chồng nhận cả atlas khối lẫn atlas vật phẩm để vẽ đầy đủ mọi ô Hotbar. */
    public void render2DHUD(
            ShaderProgram shader,
            Texture textureAtlas,
            ItemTextureAtlas itemAtlas,
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
                itemAtlas,
                camera,
                hotbar,
                audioManager,
                windowWidth,
                windowHeight,
                miningProgress,
                isPaused,
                mouseX,
                mouseY,
                player
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
        render2DHUD(shader, textureAtlas, null, camera, hotbar, audioManager, windowWidth, windowHeight,
                miningProgress, isPaused, mouseX, mouseY, null);
    }

    /** Kết xuất HUD dùng chung và vẽ chỉ số sinh tồn khi có người chơi. */
    private void render2DHUD(ShaderProgram shader, Texture textureAtlas, ItemTextureAtlas itemAtlas,
                             Camera camera, Hotbar hotbar, AudioManager audioManager, int windowWidth,
                             int windowHeight, float miningProgress, boolean isPaused, double mouseX,
                             double mouseY, Player player) {
        this.currentShader = shader;
        GuiTextureAtlas atlas = ensureGuiAtlas();
        boolean mounted = player != null && player.getMount() instanceof MountableMob;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.enable2DUIMode(camera.getOrthoMatrix(windowWidth, windowHeight));

        // 1. Luôn vẽ thanh 9 ô Hotbar ở cạnh dưới màn hình
        if (hotbar != null) {
            renderHotbar(shader, textureAtlas, itemAtlas, atlas, hotbar, windowWidth, windowHeight, mounted);
        }
        if (player != null) {
            renderVitals(player, atlas, windowWidth, windowHeight);
            if (player.getMount() instanceof MountableMob mount) {
                if (atlas != null) {
                    renderTexturedMountJumpBar(atlas, mount, windowWidth, windowHeight);
                } else {
                    renderMountVitals(mount, windowWidth, windowHeight);
                }
            }
        }

        if (!isPaused) {
            if (player != null) {
                renderStatusEffects(player, windowWidth);
            }
            if (worldTime != null && weather != null) {
                renderEnvironment();
            }
            // 2. Khi đang chơi: Vẽ tâm ngắm Crosshair ở chính giữa màn hình
            renderCrosshair(shader, atlas, windowWidth, windowHeight);

            // 3. Nếu đang giữ chuột trái đập một Block: Vẽ thanh Tiến trình Đập Block ngay dưới tâm ngắm
            if (miningProgress > 0.001f) {
                renderMiningProgressBar(shader, windowWidth, windowHeight, miningProgress);
            }
            // === HOOK: TRACK C - INGAME HUD ===
            // Vẽ thanh Oxy (10 bóng khí), DebugOverlayRenderer (F3) và InventoryScreenRenderer (E) tại đây.
            if (player != null && (player.isEyeInWater() || player.getAirTicks() < Player.AIR_MAX_TICKS)) {
                renderOxygenBar(player, windowWidth, windowHeight);
            }
            if (debugOverlay != null && debugOverlay.isVisible()) {
                debugOverlay.render(shader, this, debugWorld, player, camera, windowWidth, windowHeight);
            }
            if (inventoryScreen != null && inventoryScreen.isOpen() && player != null) {
                inventoryScreen.render(shader, player.getInventory(), textureAtlas, itemAtlas, atlas,
                        windowWidth, windowHeight, (float) mouseX, (float) mouseY);
            }
            // === END HOOK: TRACK C - INGAME HUD ===
        } else {
            // 4. Khi bấm ESC tạm dừng: Vẽ Menu Tạm dừng (Pause Menu) với ô Resume và điều chỉnh Âm lượng
            renderPauseMenu(shader, atlas, audioManager, windowWidth, windowHeight, mouseX, mouseY);
        }

        shader.enable3DWorldMode();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    /** Vẽ dải tim người chơi (thường/độc), đùi thịt (thường/đói) và tim thú cưỡi (khi đang cưỡi). */
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

    /** Dựng lưới sprite tim, đùi thịt và tim thú cưỡi từ {@link GuiTextureAtlas} (zero-GC). */
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

        // 1. Thanh máu 10 tim bên trái (nền container trước, lớp tim đầy/nửa đè lên sau)
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

        // 2. Thanh đói 10 đùi thịt bên phải (từ phải sang trái, đổi sang màu xanh khi dính HUNGER)
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

        // 3. Nếu đang cưỡi thú: vẽ tim thú cưỡi phía trên thanh đói bên phải
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

    /** Ghi nhanh 1 quad sprite UV vào mảng đỉnh tái sử dụng. */
    private static int writeSpriteQuad(float[] buffer, int offset, float x, float y,
                                       float w, float h, float[] uv) {
        if (offset + 36 > buffer.length) {
            return offset;
        }
        writeQuad(buffer, offset, x, y + h, 0f, x + w, y + h, 0f,
                x + w, y, 0f, x, y, 0f, uv[0], uv[1], uv[2], uv[3]);
        return offset + 36;
    }

    /** Vẽ thanh nhảy / hồi lướt thú cưỡi chuẩn 182 × 5 từ {@link GuiTextureAtlas}. */
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

    /** Hiển thị máu và tiến độ nhảy hoặc hồi lướt dạng dự phòng khi chưa có GuiTextureAtlas. */
    private void renderMountVitals(MountableMob mount, int width, int height) {
        float y = height - HOTBAR_SLOT_SIZE - HOTBAR_SLOT_PADDING * 2f - 75f;
        float left = width * 0.5f - 100f;
        drawMountMeter("MOUNT HP", left, y, mount.getHealth() / mount.getMaxHealth(), 0.94f, 0.29f, 0.35f);
        float cooldown = mount.dashCooldownRemaining();
        float progress = cooldown > 0f ? 1f - cooldown / 2.75f : mount.chargeProgress();
        drawMountMeter(mount.isDashMount() ? "DASH" : "JUMP", left + 106f, y, progress,
                cooldown > 0f ? 0.30f : 0.97f, 0.70f, cooldown > 0f ? 0.95f : 0.25f);
    }

    /** Vẽ thanh bằng bộ đệm UI dùng lại, bỏ lượt vẽ phần tô khi giá trị bằng không. */
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

    /** Ghi nền, tim và thịt dạng bitmap dự phòng khi không có GuiTextureAtlas. */
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

    /** Gộp từng dải pixel liên tiếp thành một quad, cắt nửa biểu tượng khi còn một điểm. */
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
                    writeQuad(textVertices, offset, x0, y1, 0f, x1, y1, 0f, x1, y0, 0f,
                            x0, y0, 0f, 0f, 0f, 1f, 1f);
                    offset += 36;
                }
            }
        }
        return offset;
    }

    /** Vẽ tên và số giây làm tròn lên của từng hiệu ứng ở góc trên phải. */
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

    /** Vẽ hai dòng góc trên trái bằng font 5 × 7 có sẵn. */
    private void renderEnvironment() {
        String biomeName = switch (weather.biome()) {
            case SNOWY_PLAINS -> "SNOWY PLAINS";
            case FOREST -> "FOREST";
            case PLAINS -> "PLAINS";
            case FLOWER_FOREST -> "FLOWER FOREST";
            case DESERT -> "DESERT";
        };
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

    /** Bóng chữ giữ nhãn dễ đọc trên mây sáng và nền tuyết. */
    private void drawEnvironmentLine(CharSequence label, float y) {
        currentShader.setUniform1i("uUseTexture", 0);
        currentShader.setUniformVec4("uTintColor", 0f, 0f, 0f, 0.8f);
        drawPixelText(label, 11.5f, y + 1.5f, 1.5f);
        currentShader.setUniformVec4("uTintColor", 1f, 1f, 1f, 0.95f);
        drawPixelText(label, 10f, y, 1.5f);
    }

    /** Vẽ tâm ngắm Crosshair 15 × 15 chuẩn Minecraft từ {@link GuiTextureAtlas} (có fallback). */
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

    /**
     * Vẽ giao diện 9 ô Thanh Công Cụ Nhanh (Hotbar) ở cạnh dưới màn hình cho cả {@link Block} và {@link Item}.
     */
    private void renderHotbar(
            ShaderProgram shader,
            Texture textureAtlas,
            ItemTextureAtlas itemAtlas,
            GuiTextureAtlas guiAtlas,
            Hotbar hotbar,
            int windowWidth,
            int windowHeight,
            boolean mounted
    ) {
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

        // Chuyển đổi atlas chỉ khi loại texture thay đổi để giữ chi phí draw call tối thiểu.
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

    /** Dựng lưới Hotbar chuẩn Minecraft 182 × 22 + khung chọn 24 × 23 từ {@link GuiTextureAtlas}. */
    private void rebuildTexturedHotbar(Hotbar hotbar, int windowWidth, int windowHeight, boolean mounted) {
        int scale = getHudScale(windowWidth);
        float totalWidth = 182.0f * scale;
        float barHeight = 22.0f * scale;
        float startX = Math.round((windowWidth - totalWidth) * 0.5f);
        float startY = windowHeight - barHeight - 12.0f;

        // 1. Vẽ tấm nền 9 ô hotbar.png (182 × 22) và khung chọn hotbar_selection.png (24 × 23)
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

        // 2. Vẽ số phím (1..9), biểu tượng khối/vật phẩm và số lượng từng ô
        float slotInner = 16.0f * scale;
        float keyLabelScale = Math.max(1.0f, scale * 0.45f);
        float countScale = Math.max(1.4f, scale * 0.60f);

        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            float slotX = startX + (3.0f + i * 20.0f) * scale;
            float slotY = startY + 3.0f * scale;

            // Số thứ tự phím (1..9) nhỏ ở góc trái-trên của ô
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

                // Số lượng item ở góc phải-dưới của ô
                String countStr = NUMBER_LABELS[count];
                float textW = countStr.length() * 6.0f * countScale;
                float tx = slotX + slotInner - textW - 1.0f;
                float ty = slotY + slotInner - 7.0f * countScale - 1.0f;

                beginHotbarStyle(TEXTURE_NONE, 0.05f, 0.05f, 0.05f, 0.95f);
                drawPixelText(countStr, tx + 1.5f, ty + 1.5f, countScale);

                beginHotbarStyle(TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
                drawPixelText(countStr, tx, ty, countScale);
            }
        }

        // 3. Hiển thị tên loại Block/Item đang cầm phía trên các dải chỉ số
        Item selectedItem = hotbar.getSelectedItem();
        if (!selectedItem.isNone() && hotbar.getSelectedCount() > 0) {
            String label = selectedItem.getDisplayName();
            float nameScale = Math.max(1.5f, scale * 0.68f);
            float labelW = label.length() * 6.0f * nameScale;
            float lx = (windowWidth - labelW) * 0.5f;
            float ly = startY - (mounted ? 40.0f : 29.0f) * scale;

            beginHotbarStyle(TEXTURE_NONE, 0.0f, 0.0f, 0.0f, 0.78f);
            drawPixelText(label, lx + 2.0f, ly + 2.0f, nameScale);
            beginHotbarStyle(TEXTURE_NONE, 1.0f, 0.96f, 0.72f, 1.0f);
            drawPixelText(label, lx, ly, nameScale);
        }
    }

    /** Ghi lại hình học Hotbar dạng dự phòng khi chưa có GuiTextureAtlas. */
    private void rebuildFallbackHotbar(Hotbar hotbar, int windowWidth, int windowHeight) {
        float totalWidth = Hotbar.SLOT_COUNT * HOTBAR_SLOT_SIZE + (Hotbar.SLOT_COUNT + 1) * HOTBAR_SLOT_PADDING;
        float barHeight = HOTBAR_SLOT_SIZE + HOTBAR_SLOT_PADDING * 2.0f;
        float startX = (windowWidth - totalWidth) * 0.5f;
        float startY = windowHeight - barHeight - 16.0f;

        // 1. Vẽ tấm nền tổng của thanh Hotbar
        beginHotbarStyle(TEXTURE_NONE, 0.10f, 0.11f, 0.14f, 0.82f);
        drawRect2D(startX - 4.0f, startY - 4.0f, totalWidth + 8.0f, barHeight + 8.0f);

        // 2. Vẽ từng ô từ 0 đến 8 (tương ứng phím 1 đến 9)
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            float slotX = startX + HOTBAR_SLOT_PADDING + i * (HOTBAR_SLOT_SIZE + HOTBAR_SLOT_PADDING);
            float slotY = startY + HOTBAR_SLOT_PADDING;

            boolean isSelected = (i == hotbar.getSelectedSlot());

            if (isSelected) {
                beginHotbarStyle(TEXTURE_NONE, 1.0f, 0.92f, 0.32f, 1.0f);
                drawRect2D(slotX - 3.0f, slotY - 3.0f, HOTBAR_SLOT_SIZE + 6.0f, HOTBAR_SLOT_SIZE + 6.0f);
            }

            if (isSelected) {
                beginHotbarStyle(TEXTURE_NONE, 0.28f, 0.30f, 0.35f, 0.95f);
            } else {
                beginHotbarStyle(TEXTURE_NONE, 0.18f, 0.19f, 0.23f, 0.90f);
            }
            drawRect2D(slotX, slotY, HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE);

            // Số thứ tự phím (1..9) nhỏ ở góc trái-trên của ô
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

                // Vẽ số lượng item hiện có ở góc phải-dưới của ô
                String countStr = NUMBER_LABELS[count];
                float textScale = 2.0f;
                float textW = countStr.length() * 6.0f * textScale;
                float tx = slotX + HOTBAR_SLOT_SIZE - textW - 3.0f;
                float ty = slotY + HOTBAR_SLOT_SIZE - 7.0f * textScale - 3.0f;

                beginHotbarStyle(TEXTURE_NONE, 0.05f, 0.05f, 0.05f, 0.95f);
                drawPixelText(countStr, tx + 1.5f, ty + 1.5f, textScale);

                beginHotbarStyle(TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
                drawPixelText(countStr, tx, ty, textScale);
            }
        }

        // Hiển thị tên loại Block/Item đang cầm ngay phía trên thanh Hotbar
        Item selectedItem = hotbar.getSelectedItem();
        if (!selectedItem.isNone() && hotbar.getSelectedCount() > 0) {
            String label = selectedItem.getDisplayName();
            float scale = 2.0f;
            float labelW = label.length() * 6.0f * scale;
            float lx = (windowWidth - labelW) * 0.5f;
            float ly = startY - 24.0f;

            beginHotbarStyle(TEXTURE_NONE, 0.0f, 0.0f, 0.0f, 0.75f);
            drawPixelText(label, lx + 2.0f, ly + 2.0f, scale);
            beginHotbarStyle(TEXTURE_NONE, 1.0f, 0.96f, 0.72f, 1.0f);
            drawPixelText(label, lx, ly, scale);
        }
    }

    /** Lưu màu và nguồn texture cho một khoảng đỉnh liên tiếp. */
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
    // 3. VẼ MENU TẠM DỪNG (PAUSE MENU: NÚT RESUME & THANH ÂM LƯỢNG)
    // =========================================================================

    private void renderPauseMenu(
            ShaderProgram shader,
            GuiTextureAtlas atlas,
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
        if (atlas != null) {
            drawWidgetSprite(shader, atlas, hoverResume
                    ? GuiTextureAtlas.SpriteKey.WIDGET_BUTTON_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_BUTTON, resumeBounds);
        } else {
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", hoverResume ? 1.0f : 0.75f, hoverResume ? 0.95f : 0.78f, 0.40f, 1.0f);
            drawRect2D(resumeBounds[0] - 2.0f, resumeBounds[1] - 2.0f, resumeBounds[2] + 4.0f, resumeBounds[3] + 4.0f);
            shader.setUniformVec4("uTintColor",
                    hoverResume ? 0.26f : 0.20f,
                    hoverResume ? 0.58f : 0.44f,
                    hoverResume ? 0.30f : 0.24f, 1.0f);
            drawRect2D(resumeBounds[0], resumeBounds[1], resumeBounds[2], resumeBounds[3]);
        }

        String resumeLabel = "RESUME GAME";
        float resScale = 2.4f;
        float resW = resumeLabel.length() * 6.0f * resScale;
        float resX = resumeBounds[0] + (resumeBounds[2] - resW) * 0.5f;
        float resY = resumeBounds[1] + (resumeBounds[3] - 7.0f * resScale) * 0.5f;
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.12f, 0.12f, 0.12f, 0.92f);
        drawPixelText(resumeLabel, resX + 2.0f, resY + 2.0f, resScale);
        shader.setUniformVec4("uTintColor", hoverResume ? 1.0f : 0.96f, hoverResume ? 1.0f : 0.96f,
                hoverResume ? 0.65f : 0.96f, 1.0f);
        drawPixelText(resumeLabel, resX, resY, resScale);

        // 5. Khu vực Điều chỉnh Âm lượng Nhạc nền (Volume Control: Nút [-], Thanh trượt, Nút [+])
        int volPercent = (audioManager != null) ? audioManager.getVolumePercent() : 70;
        String volLabel = VOLUME_LABELS[volPercent];
        float volLabelScale = 2.0f;
        float volLabelW = volLabel.length() * 6.0f * volLabelScale;
        shader.setUniformVec4("uTintColor", 0.88f, 0.90f, 0.95f, 1.0f);
        drawPixelText(volLabel, (windowWidth - volLabelW) * 0.5f, panelY + 138.0f, volLabelScale);

        // Nút Giảm âm lượng [-]
        float[] minusBounds = getVolumeMinusBounds(windowWidth, windowHeight);
        boolean hoverMinus = isPointInRect(mouseX, mouseY, minusBounds);
        drawPauseSquareButton(shader, atlas, minusBounds, hoverMinus, "-");

        // Thanh trượt Volume (Slider Track & Handle)
        float[] sliderBounds = getVolumeSliderBounds(windowWidth, windowHeight);
        float volRatio = (audioManager != null) ? audioManager.getVolume() : 0.7f;
        boolean hoverSlider = getSliderVolumeAtCursor(mouseX, mouseY, windowWidth, windowHeight) >= 0.0f;

        if (atlas != null) {
            drawWidgetSprite(shader, atlas, hoverSlider
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
            drawTexturedRect2D(knobX, knobY, knobW, knobH, handleUv[0], handleUv[1], handleUv[2], handleUv[3]);
            atlas.unbind();
        } else {
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.08f, 0.09f, 0.12f, 1.0f);
            drawRect2D(sliderBounds[0] - 2.0f, sliderBounds[1] - 2.0f, sliderBounds[2] + 4.0f, sliderBounds[3] + 4.0f);

            shader.setUniformVec4("uTintColor", 0.24f, 0.65f, 0.92f, 1.0f);
            drawRect2D(sliderBounds[0], sliderBounds[1], sliderBounds[2] * volRatio, sliderBounds[3]);

            float knobW = 12.0f;
            float knobX = sliderBounds[0] + sliderBounds[2] * volRatio - knobW * 0.5f;
            knobX = Math.max(sliderBounds[0], Math.min(sliderBounds[0] + sliderBounds[2] - knobW, knobX));
            shader.setUniformVec4("uTintColor", 1.0f, 0.95f, 0.50f, 1.0f);
            drawRect2D(knobX, sliderBounds[1] - 5.0f, knobW, sliderBounds[3] + 10.0f);
        }

        // Nút Tăng âm lượng [+]
        float[] plusBounds = getVolumePlusBounds(windowWidth, windowHeight);
        boolean hoverPlus = isPointInRect(mouseX, mouseY, plusBounds);
        drawPauseSquareButton(shader, atlas, plusBounds, hoverPlus, "+");

        // 6. Dòng hướng dẫn bấm ESC lần 2 để thoát game
        String quitHint = "PRESS ESC AGAIN TO QUIT";
        float hintScale = 1.6f;
        float hintW = quitHint.length() * 6.0f * hintScale;
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.75f, 0.78f, 0.84f, 0.90f);
        drawPixelText(quitHint, (windowWidth - hintW) * 0.5f, panelY + PANEL_HEIGHT - 28.0f, hintScale);
    }

    /** Vẽ một widget hình chữ nhật từ {@link GuiTextureAtlas} khớp đúng khung hit-test. */
    private void drawWidgetSprite(ShaderProgram shader, GuiTextureAtlas atlas,
                                  GuiTextureAtlas.SpriteKey key, float[] bounds) {
        float[] uv = GuiTextureAtlas.uvOf(key);
        atlas.bindHudAtlas();
        shader.setUniform1i("uUseTexture", 1);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        drawTexturedRect2D(bounds[0], bounds[1], bounds[2], bounds[3], uv[0], uv[1], uv[2], uv[3]);
        atlas.unbind();
    }

    /** Vẽ nút vuông [-]/[+] trên Pause Menu bằng widget Minecraft hoặc hình chữ nhật dự phòng. */
    private void drawPauseSquareButton(ShaderProgram shader, GuiTextureAtlas atlas,
                                       float[] bounds, boolean hovered, String symbol) {
        if (atlas != null) {
            drawWidgetSprite(shader, atlas, hovered
                    ? GuiTextureAtlas.SpriteKey.WIDGET_BUTTON_HIGHLIGHTED
                    : GuiTextureAtlas.SpriteKey.WIDGET_BUTTON, bounds);
        } else {
            shader.setUniform1i("uUseTexture", 0);
            shader.setUniformVec4("uTintColor", 0.75f, 0.78f, 0.82f, 1.0f);
            drawRect2D(bounds[0] - 2.0f, bounds[1] - 2.0f, bounds[2] + 4.0f, bounds[3] + 4.0f);
            shader.setUniformVec4("uTintColor", hovered ? 0.42f : 0.26f, hovered ? 0.45f : 0.29f, 0.36f, 1.0f);
            drawRect2D(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", 0.12f, 0.12f, 0.12f, 0.92f);
        drawPixelText(symbol, bounds[0] + 16.5f, bounds[1] + 12.5f, 2.8f);
        shader.setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        drawPixelText(symbol, bounds[0] + 15.0f, bounds[1] + 11.0f, 2.8f);
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

    /** Vẽ hình chữ nhật bằng màu đồng nhất của shader. */
    private void drawRect2D(float x, float y, float w, float h) {
        drawTexturedRect2D(x, y, w, h, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    /** Cập nhật bộ đệm sáu đỉnh dùng lại cho hình chữ nhật và biểu tượng. */
    private void drawTexturedRect2D(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
        writeQuad(rectVertices, 0, x, y + h, 0.0f, x + w, y + h, 0.0f,
                x + w, y, 0.0f, x, y, 0.0f, u0, v0, u1, v1);
        uploadAndDrawTriangles(rectVertices);
    }

    /** Ghi sáu mặt hộp vào bộ đệm sử dụng lại cho ngắm và đào block. */
    private void draw3DBoxSolid(float x0, float y0, float z0, float x1, float y1, float z1) {
        writeQuad(boxVertices, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 0, 0, 0);
        writeQuad(boxVertices, 36, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, 0, 0, 0);
        writeQuad(boxVertices, 72, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 0, 0);
        writeQuad(boxVertices, 108, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, 0, 0);
        writeQuad(boxVertices, 144, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 0, 0, 0, 0);
        writeQuad(boxVertices, 180, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 0, 0, 0, 0);
        uploadAndDrawTriangles(boxVertices);
    }

    /** Ghi bốn góc thành hai tam giác, với cùng thứ tự thuộc tính cho UI 2D và lớp phủ 3D. */
    private static void writeQuad(float[] data, int offset, float ax, float ay, float az,
                                  float bx, float by, float bz, float cx, float cy, float cz,
                                  float dx, float dy, float dz, float u0, float v0, float u1, float v1) {
        writeVertex(data, offset, ax, ay, az, u0, v1);
        writeVertex(data, offset + 6, bx, by, bz, u1, v1);
        writeVertex(data, offset + 12, cx, cy, cz, u1, v0);
        writeVertex(data, offset + 18, ax, ay, az, u0, v1);
        writeVertex(data, offset + 24, cx, cy, cz, u1, v0);
        writeVertex(data, offset + 30, dx, dy, dz, u0, v0);
    }

    /** Ghi vị trí và UV với độ sáng đầy đủ, vì giao diện tự chọn màu riêng. */
    private static void writeVertex(float[] data, int offset, float x, float y, float z, float u, float v) {
        data[offset] = x;
        data[offset + 1] = y;
        data[offset + 2] = z;
        data[offset + 3] = u;
        data[offset + 4] = v;
        data[offset + 5] = 1.0f;
    }

    private void drawPixelText(CharSequence text, float startX, float startY, float pixelScale) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int length = 0;
        float cursorX = startX;

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            int[] glyph = c < GLYPHS.length ? GLYPHS[c] : null;
            if (glyph != null) {
                for (int row = 0; row < 7; row++) {
                    int rowBits = glyph[row];
                    for (int col = 0; col < 5; col++) {
                        if (((rowBits >> (4 - col)) & 1) == 1) {
                            float px = cursorX + col * pixelScale;
                            float py = startY + row * pixelScale;
                            float right = px + pixelScale;
                            float bottom = py + pixelScale;
                            if (length == textVertices.length) {
                                uploadAndDrawTriangles(textVertices, length);
                                length = 0;
                            }
                            writeQuad(textVertices, length, px, bottom, 0, right, bottom, 0,
                                    right, py, 0, px, py, 0, 0, 0, 1, 1);
                            length += 36;
                        }
                    }
                }
            }
            cursorX += 6.0f * pixelScale;
        }

        uploadAndDrawTriangles(textVertices, length);
    }

    private void uploadAndDrawTriangles(float[] vertices) {
        uploadAndDrawTriangles(vertices, vertices.length);
    }

    /** Chỉ tải phần đỉnh có dữ liệu trong bộ đệm tái sử dụng. */
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

    /** Tạo mỗi bitmap một lần thay vì cấp phát lại cho từng chữ. */
    private static int[][] createGlyphs() {
        int[][] glyphs = new int[128][];
        for (char c = 0; c < glyphs.length; c++) {
            glyphs[c] = createGlyph5x7(c);
        }
        return glyphs;
    }

    /** Sơ đồ bit gốc của font tích hợp. */
    private static int[] createGlyph5x7(char c) {
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
            case '.' -> new int[]{0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b01100, 0b01100};
            case '/' -> new int[]{0b00001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000};
            case ',' -> new int[]{0b00000, 0b00000, 0b00000, 0b00000, 0b00110, 0b00100, 0b01000};
            case '(' -> new int[]{0b00010, 0b00100, 0b01000, 0b01000, 0b01000, 0b00100, 0b00010};
            case ')' -> new int[]{0b01000, 0b00100, 0b00010, 0b00010, 0b00010, 0b00100, 0b01000};
            case '=' -> new int[]{0b00000, 0b00000, 0b11111, 0b00000, 0b11111, 0b00000, 0b00000};
            default -> null;
        };
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
        // === HOOK: TRACK B - DISPOSE ===
        // Giải phóng tài nguyên bổ sung của Track B tại đây.
        // === END HOOK: TRACK B - DISPOSE ===

        // === HOOK: TRACK C - DISPOSE ===
        // Giải phóng GuiTextureAtlas và UI màn hình của Track C tại đây.
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
        // === END HOOK: TRACK C - DISPOSE ===
    }
}
