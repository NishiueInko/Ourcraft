package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Item;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.PlayerInventory;

/**
 * Hệ thống Kết xuất và Tương tác Giao diện Túi đồ 36 ô (27 ô Balo + 9 ô Hotbar),
 * bật/tắt bằng phím {@code E} và triển khai {@link Disposable}.
 *
 * Đặc điểm kỹ thuật:
 * - Vẽ nền khung túi đồ pixel-perfect cắt từ {@code textures/gui/container/inventory.png}
 *   (khung 176 × 166 trên ảnh 256 × 256, đặt 27 ô balo tại (8, 84) và 9 ô Hotbar tại (8, 142))
 *   khớp 100% với {@link #getSlotAt(float, float, int, int)}.
 * - Có cơ chế fallback an toàn nếu {@link GuiTextureAtlas} chưa sẵn sàng hoặc {@code Gdx.gl == null}.
 * - Tái sử dụng bộ đệm {@link Mesh} và mảng đỉnh cấp phát trước để đảm bảo zero-GC trong Game Loop.
 * - Hỗ trợ thao tác chuẩn Minecraft: Chuột trái (nhặt/đặt/gộp/hoán đổi), Chuột phải (chia đôi / thả 1),
 *   {@code Shift + Chuột trái} (chuyển nhanh Balo &lt;-&gt; Hotbar ưu tiên gộp stack cùng loại trước ô trống).
 */
public class InventoryScreenRenderer implements Disposable {

    /** Mã nút chuột trái dùng cho tương tác ô túi đồ. */
    public static final int LEFT_CLICK = Input.Buttons.LEFT;
    /** Mã nút chuột phải dùng cho chia đôi stack hoặc đặt từng đơn vị. */
    public static final int RIGHT_CLICK = Input.Buttons.RIGHT;

    /** Cấu trúc lưới ô: 9 cột, 3 hàng balo (ô 9..35) và 1 hàng Hotbar (ô 0..8). */
    private static final int COLS = 9;
    private static final int BACKPACK_ROWS = 3;

    /** Kích thước gốc của khung túi đồ trong {@code inventory.png} và tọa độ từng ô (hệ 176 × 166). */
    private static final float INV_PANEL_W = GuiTextureAtlas.INVENTORY_PANEL_WIDTH;
    private static final float INV_PANEL_H = GuiTextureAtlas.INVENTORY_PANEL_HEIGHT;
    private static final float INV_BACKPACK_X = 8.0f;
    private static final float INV_BACKPACK_Y = 84.0f;
    private static final float INV_HOTBAR_X = 8.0f;
    private static final float INV_HOTBAR_Y = 142.0f;
    private static final float INV_SLOT_PITCH = 18.0f;
    private static final float INV_SLOT_INNER = 16.0f;

    /** Cấu hình đỉnh đồng nhất với UIRenderer: aPos (3), aTexCoord (2), aShade (1). */
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade")
    );

    /** Định danh nguồn texture cho từng đoạn vẽ trong Mesh. */
    private static final byte TEXTURE_NONE = 0;
    private static final byte TEXTURE_BLOCK_ATLAS = 1;
    private static final byte TEXTURE_ITEM_ATLAS = 2;
    private static final byte TEXTURE_GUI_INVENTORY = 3;

    /** Dung lượng đỉnh cho lưới nền/36 ô và lớp phủ con trỏ/tooltip. */
    private static final int GRID_VERTEX_CAPACITY = 16384;
    private static final int OVERLAY_VERTEX_CAPACITY = 8192;
    private static final int MAX_PASSES = 10;

    /** Chuỗi số lượng 0..64 tạo sẵn để không cấp phát String trong Game Loop. */
    private static final String[] NUMBER_LABELS = new String[65];
    /** Bảng mẫu ký tự 5x7 tích hợp sẵn cho tiêu đề, số lượng và tooltip. */
    private static final int[][] GLYPHS = createGlyphs();

    static {
        for (int i = 0; i < NUMBER_LABELS.length; i++) {
            NUMBER_LABELS[i] = Integer.toString(i);
        }
    }

    /** Trạng thái mở/đóng và vật phẩm đang bám theo con trỏ chuột. */
    private boolean open = false;
    private Item cursorItem = Item.NONE;
    private int cursorCount = 0;

    /** Atlas GUI dùng chung (hoặc tự cấp phát lười nếu gọi render 8 tham số). */
    private GuiTextureAtlas sharedGuiAtlas;
    private GuiTextureAtlas ownedGuiAtlas;
    private boolean ownedGuiAtlasFailed = false;

    /** Bộ đệm Mesh tĩnh cho khung 36 ô (chỉ dựng lại khi dữ liệu hoặc cửa sổ đổi). */
    private Mesh gridMesh;
    private final float[] gridVertices = new float[GRID_VERTEX_CAPACITY * 6];
    private int gridFloatLength = 0;
    private int gridPassCount = 0;
    private final int[] gridPassStart = new int[MAX_PASSES];
    private final int[] gridPassCountVerts = new int[MAX_PASSES];
    private final byte[] gridPassTexSource = new byte[MAX_PASSES];
    private final float[] gridPassTints = new float[MAX_PASSES * 4];

    /** Trạng thái lưu đệm của 36 ô để phát hiện thay đổi. */
    private final Item[] cachedItems = new Item[PlayerInventory.TOTAL_SLOTS];
    private final int[] cachedCounts = new int[PlayerInventory.TOTAL_SLOTS];
    private int cachedScreenW = -1;
    private int cachedScreenH = -1;
    private boolean cachedHasGuiTexture = false;
    private boolean dirty = true;

    /** Bộ đệm Mesh động cho ô đang hover, vật phẩm trên con trỏ và tooltip tên vật phẩm. */
    private Mesh overlayMesh;
    private final float[] overlayVertices = new float[OVERLAY_VERTEX_CAPACITY * 6];
    private int overlayFloatLength = 0;
    private int overlayPassCount = 0;
    private final int[] overlayPassStart = new int[MAX_PASSES];
    private final int[] overlayPassCountVerts = new int[MAX_PASSES];
    private final byte[] overlayPassTexSource = new byte[MAX_PASSES];
    private final float[] overlayPassTints = new float[MAX_PASSES * 4];

    /** Bộ đệm dùng lại cho UV và thả vật phẩm dư khi đóng túi đồ (zero-GC). */
    private final float[] itemUvBuffer = new float[4];
    private final Vector3 dropOriginBuffer = new Vector3();
    private final Vector3 dropDirectionBuffer = new Vector3();
    private final Vector3 dropVelocityBuffer = new Vector3();

    /**
     * Khởi tạo bộ kết xuất màn hình túi đồ. Tài nguyên GPU ({@link Mesh}) được cấp phát lười
     * trong lần {@link #render} đầu tiên để tương thích với kiểm thử CPU không có OpenGL.
     */
    public InventoryScreenRenderer() {
        for (int i = 0; i < PlayerInventory.TOTAL_SLOTS; i++) {
            cachedItems[i] = Item.NONE;
            cachedCounts[i] = 0;
        }
    }

    /** Gắn {@link GuiTextureAtlas} dùng chung từ {@link UIRenderer} để tránh nạp trùng texture trên VRAM. */
    public void setGuiAtlas(GuiTextureAtlas guiAtlas) {
        this.sharedGuiAtlas = guiAtlas;
        this.dirty = true;
    }

    /** Kiểm tra giao diện túi đồ hiện đang mở hay không. */
    public boolean isOpen() {
        return open;
    }

    /** Mở giao diện túi đồ 36 ô. */
    public void open() {
        open = true;
        dirty = true;
    }

    /** Đảo trạng thái mở/đóng đơn giản (không xử lý trả vật phẩm trên con trỏ). */
    public void toggle() {
        if (open) {
            close();
        } else {
            open();
        }
    }

    /**
     * Đảo trạng thái mở/đóng; khi đóng sẽ cố gắng trả vật phẩm đang cầm trên con trỏ về {@code inventory}.
     *
     * @param inventory túi đồ nhận lại vật phẩm nếu đang đóng
     */
    public void toggle(PlayerInventory inventory) {
        if (!open) {
            open();
            return;
        }
        if (inventory != null && !cursorItem.isNone() && cursorCount > 0) {
            inventory.addItemUpTo(cursorItem, cursorCount);
        }
        close();
    }

    /**
     * Đảo trạng thái mở/đóng đồng bộ với người chơi và thế giới.
     *
     * @param player người chơi sở hữu túi đồ
     * @param world  thế giới nhận vật phẩm rơi nếu kho đã đầy khi đóng
     */
    public void toggle(Player player, World world) {
        if (open) {
            close(player, world);
        } else {
            open();
        }
    }

    /** Đóng giao diện túi đồ và xóa vật phẩm tạm trên con trỏ. */
    public void close() {
        open = false;
        cursorItem = Item.NONE;
        cursorCount = 0;
        dirty = true;
    }

    /**
     * Đóng giao diện túi đồ; nếu còn vật phẩm trên con trỏ chuột thì trả lại vào túi đồ,
     * phần dư không đủ chỗ sẽ được ném ra thế giới trước mặt người chơi.
     *
     * @param player người chơi nhận lại vật phẩm hoặc làm gốc thả vật phẩm
     * @param world  thế giới để sinh {@link ItemEntity} nếu kho đầy
     */
    public void close(Player player, World world) {
        if (!cursorItem.isNone() && cursorCount > 0) {
            int remaining = cursorCount;
            if (player != null && player.getInventory() != null) {
                int added = player.getInventory().addItemUpTo(cursorItem, remaining);
                remaining -= added;
            }
            if (remaining > 0 && world != null && player != null) {
                if (!player.copyAim(dropOriginBuffer, dropDirectionBuffer)) {
                    player.copyPosition(dropOriginBuffer);
                    dropOriginBuffer.y += 1.5f;
                    dropDirectionBuffer.set(0f, 0f, -1f);
                }
                float spawnX = dropOriginBuffer.x + dropDirectionBuffer.x * 0.45f;
                float spawnY = dropOriginBuffer.y - 0.25f + dropDirectionBuffer.y * 0.25f;
                float spawnZ = dropOriginBuffer.z + dropDirectionBuffer.z * 0.45f;
                dropVelocityBuffer.set(
                        dropDirectionBuffer.x * 4.5f,
                        dropDirectionBuffer.y * 4.5f + 1.6f,
                        dropDirectionBuffer.z * 4.5f
                );
                world.spawnDroppedItem(new ItemEntity(spawnX, spawnY, spawnZ,
                        dropVelocityBuffer, cursorItem, remaining, 1.0f));
            }
        }
        close();
    }

    /** Trả về loại vật phẩm hiện đang bám theo con trỏ chuột. */
    public Item getCursorItem() {
        return cursorItem;
    }

    /** Trả về số lượng vật phẩm hiện đang bám theo con trỏ chuột. */
    public int getCursorCount() {
        return cursorCount;
    }

    /** Tính hệ số phóng nguyên pixel-perfect (1..4) theo kích thước cửa sổ hiện tại. */
    private float getPanelScale(int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) {
            return 2.0f;
        }
        int scaleW = screenW / 380;
        int scaleH = screenH / 240;
        return Math.max(1, Math.min(4, Math.min(scaleW, scaleH)));
    }

    /**
     * Xác định chỉ số ô túi đồ (0..35) nằm dưới tọa độ chuột màn hình (Top-Left),
     * khớp 100% với vị trí 27 ô balo (8, 84) và 9 ô Hotbar (8, 142) trên khung 176 × 166.
     */
    public int getSlotAt(float mouseX, float mouseY, int screenW, int screenH) {
        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float slotSize = INV_SLOT_INNER * scale;

        for (int slot = 0; slot < PlayerInventory.TOTAL_SLOTS; slot++) {
            float sx = getSlotX(panelX, scale, slot);
            float sy = getSlotY(panelY, scale, slot);
            if (mouseX >= sx && mouseX <= sx + slotSize && mouseY >= sy && mouseY <= sy + slotSize) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Xử lý sự kiện nhấn chuột trên màn hình túi đồ theo tọa độ pixel màn hình.
     */
    public boolean handleClick(PlayerInventory inv, float mouseX, float mouseY,
                               int screenW, int screenH, int button, boolean shiftDown) {
        if (!open || inv == null) {
            return false;
        }
        int slot = getSlotAt(mouseX, mouseY, screenW, screenH);
        if (slot >= 0) {
            handleSlotInteraction(inv, slot, button, shiftDown);
            return true;
        }
        float scale = getPanelScale(screenW, screenH);
        float panelW = INV_PANEL_W * scale;
        float panelH = INV_PANEL_H * scale;
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        return mouseX >= panelX && mouseX <= panelX + panelW
                && mouseY >= panelY && mouseY <= panelY + panelH;
    }

    /**
     * Thực thi tương tác trực tiếp trên một chỉ số ô cụ thể (0..35) độc lập với độ phân giải.
     */
    public boolean handleSlotInteraction(PlayerInventory inv, int slot, int button, boolean shiftDown) {
        if (inv == null || slot < 0 || slot >= PlayerInventory.TOTAL_SLOTS) {
            return false;
        }

        Item slotItem = inv.getItem(slot);
        int slotCount = inv.getCount(slot);
        boolean slotEmpty = slotItem == null || slotItem.isNone() || slotCount <= 0;
        boolean cursorEmpty = cursorItem == null || cursorItem.isNone() || cursorCount <= 0;

        // 1. Shift + Chuột trái: chuyển nhanh giữa Balo (9..35) và Hotbar (0..8)
        if (shiftDown && button == LEFT_CLICK) {
            if (slotEmpty) {
                return false;
            }
            int rangeStart = (slot >= PlayerInventory.HOTBAR_SLOTS) ? 0 : PlayerInventory.HOTBAR_SLOTS;
            int rangeEnd = (slot >= PlayerInventory.HOTBAR_SLOTS)
                    ? PlayerInventory.HOTBAR_SLOTS - 1
                    : PlayerInventory.TOTAL_SLOTS - 1;
            int remaining = quickMoveToRange(inv, slotItem, slotCount, rangeStart, rangeEnd);
            if (remaining != slotCount) {
                inv.setSlot(slot, remaining > 0 ? slotItem : Item.NONE, remaining);
                dirty = true;
                return true;
            }
            return false;
        }

        // 2. Chuột trái (không giữ Shift): nhặt, đặt, gộp cùng loại hoặc hoán đổi hai stack khác loại
        if (button == LEFT_CLICK) {
            if (cursorEmpty && slotEmpty) {
                return false;
            }
            if (cursorEmpty) {
                cursorItem = slotItem;
                cursorCount = slotCount;
                inv.setSlot(slot, Item.NONE, 0);
                dirty = true;
                return true;
            }
            if (slotEmpty) {
                int maxStack = cursorItem.getMaxStackSize();
                int placed = Math.min(maxStack, cursorCount);
                inv.setSlot(slot, cursorItem, placed);
                cursorCount -= placed;
                if (cursorCount <= 0) {
                    cursorItem = Item.NONE;
                    cursorCount = 0;
                }
                dirty = true;
                return true;
            }
            if (cursorItem == slotItem) {
                int maxStack = slotItem.getMaxStackSize();
                if (slotCount >= maxStack) {
                    return false;
                }
                int add = Math.min(maxStack - slotCount, cursorCount);
                inv.setSlot(slot, slotItem, slotCount + add);
                cursorCount -= add;
                if (cursorCount <= 0) {
                    cursorItem = Item.NONE;
                    cursorCount = 0;
                }
                dirty = true;
                return true;
            }
            // Khác loại: hoán đổi vật phẩm giữa con trỏ và ô
            Item prevCursorItem = cursorItem;
            int prevCursorCount = cursorCount;
            cursorItem = slotItem;
            cursorCount = slotCount;
            inv.setSlot(slot, prevCursorItem, prevCursorCount);
            dirty = true;
            return true;
        }

        // 3. Chuột phải: chia đôi stack khi con trỏ trống, hoặc thả 1 đơn vị khi con trỏ đang cầm đồ
        if (button == RIGHT_CLICK) {
            if (cursorEmpty && slotEmpty) {
                return false;
            }
            if (cursorEmpty) {
                int pick = (slotCount + 1) / 2;
                int remain = slotCount - pick;
                cursorItem = slotItem;
                cursorCount = pick;
                inv.setSlot(slot, remain > 0 ? slotItem : Item.NONE, remain);
                dirty = true;
                return true;
            }
            if (slotEmpty) {
                inv.setSlot(slot, cursorItem, 1);
                cursorCount -= 1;
                if (cursorCount <= 0) {
                    cursorItem = Item.NONE;
                    cursorCount = 0;
                }
                dirty = true;
                return true;
            }
            if (cursorItem == slotItem) {
                int maxStack = slotItem.getMaxStackSize();
                if (slotCount >= maxStack) {
                    return false;
                }
                inv.setSlot(slot, slotItem, slotCount + 1);
                cursorCount -= 1;
                if (cursorCount <= 0) {
                    cursorItem = Item.NONE;
                    cursorCount = 0;
                }
                dirty = true;
                return true;
            }
            // Khác loại: hoán đổi như chuột trái
            Item prevCursorItem = cursorItem;
            int prevCursorCount = cursorCount;
            cursorItem = slotItem;
            cursorCount = slotCount;
            inv.setSlot(slot, prevCursorItem, prevCursorCount);
            dirty = true;
            return true;
        }

        return false;
    }

    /**
     * Chuyển nhanh một lượng vật phẩm vào khoảng ô đích {@code [start..end]},
     * ưu tiên gộp vào các stack cùng loại chưa đầy trước rồi mới đặt vào ô trống.
     */
    private int quickMoveToRange(PlayerInventory inv, Item item, int amount, int start, int end) {
        int remaining = amount;
        int maxStack = item.getMaxStackSize();
        if (maxStack <= 0) {
            return amount;
        }

        // Lượt 1: gộp vào các ô cùng loại còn chỗ trống
        for (int i = start; i <= end && remaining > 0; i++) {
            if (inv.getItem(i) == item) {
                int current = inv.getCount(i);
                if (current < maxStack) {
                    int add = Math.min(maxStack - current, remaining);
                    inv.setSlot(i, item, current + add);
                    remaining -= add;
                }
            }
        }

        // Lượt 2: đặt phần còn lại vào các ô trống đầu tiên trong khoảng đích
        for (int i = start; i <= end && remaining > 0; i++) {
            Item target = inv.getItem(i);
            int targetCount = inv.getCount(i);
            if (target == null || target.isNone() || targetCount <= 0) {
                int add = Math.min(maxStack, remaining);
                inv.setSlot(i, item, add);
                remaining -= add;
            }
        }

        return remaining;
    }

    /** Lấy atlas GUI dùng chung hoặc tự khởi tạo lười khi có OpenGL Context. */
    private GuiTextureAtlas resolveGuiAtlas(GuiTextureAtlas explicitAtlas) {
        if (explicitAtlas != null) {
            return explicitAtlas;
        }
        if (sharedGuiAtlas != null) {
            return sharedGuiAtlas;
        }
        if (ownedGuiAtlas == null && !ownedGuiAtlasFailed && Gdx.gl != null) {
            try {
                ownedGuiAtlas = new GuiTextureAtlas();
            } catch (RuntimeException | Error e) {
                ownedGuiAtlasFailed = true;
            }
        }
        return ownedGuiAtlas;
    }

    /**
     * Kết xuất toàn bộ giao diện túi đồ 36 ô (tương thích chữ ký 8 tham số gốc).
     */
    public void render(ShaderProgram shader, PlayerInventory inv, Texture blockAtlas,
                       ItemTextureAtlas itemAtlas, int screenW, int screenH,
                       float mouseX, float mouseY) {
        render(shader, inv, blockAtlas, itemAtlas, null, screenW, screenH, mouseX, mouseY);
    }

    /**
     * Kết xuất toàn bộ giao diện túi đồ 36 ô với {@link GuiTextureAtlas} dùng chung.
     * Không cấp phát rác (zero-GC) trong mỗi khung hình.
     */
    public void render(ShaderProgram shader, PlayerInventory inv, Texture blockAtlas,
                       ItemTextureAtlas itemAtlas, GuiTextureAtlas guiAtlas,
                       int screenW, int screenH, float mouseX, float mouseY) {
        if (!open || shader == null || inv == null || screenW <= 0 || screenH <= 0 || Gdx.gl == null) {
            return;
        }

        GuiTextureAtlas activeGui = resolveGuiAtlas(guiAtlas);
        boolean hasGuiTexture = activeGui != null;

        // 1. Kiểm tra cache xem lưới 36 ô hoặc kích thước cửa sổ có thay đổi không
        boolean changed = dirty || screenW != cachedScreenW || screenH != cachedScreenH
                || hasGuiTexture != cachedHasGuiTexture;
        for (int i = 0; i < PlayerInventory.TOTAL_SLOTS; i++) {
            Item item = inv.getItem(i);
            int count = inv.getCount(i);
            if (item != cachedItems[i] || count != cachedCounts[i]) {
                cachedItems[i] = item;
                cachedCounts[i] = count;
                changed = true;
            }
        }

        if (changed) {
            cachedScreenW = screenW;
            cachedScreenH = screenH;
            cachedHasGuiTexture = hasGuiTexture;
            dirty = false;
            rebuildGridMesh(inv, hasGuiTexture, screenW, screenH);
        }

        // 2. Vẽ lưới nền 36 ô + biểu tượng + số lượng từ Mesh đã đệm
        renderPasses(shader, gridMesh, gridPassCount, gridPassStart, gridPassCountVerts,
                gridPassTexSource, gridPassTints, blockAtlas, itemAtlas, activeGui);

        // 3. Dựng và vẽ lớp phủ động theo con trỏ chuột (hover, item đang cầm, tooltip tên)
        rebuildOverlayMesh(inv, screenW, screenH, mouseX, mouseY);
        if (overlayFloatLength > 0) {
            renderPasses(shader, overlayMesh, overlayPassCount, overlayPassStart, overlayPassCountVerts,
                    overlayPassTexSource, overlayPassTints, blockAtlas, itemAtlas, activeGui);
        }
    }

    /** Dựng lại toàn bộ lưới tĩnh của khung túi đồ và 36 ô vào {@code gridMesh}. */
    private void rebuildGridMesh(PlayerInventory inv, boolean hasGuiTexture, int screenW, int screenH) {
        gridFloatLength = 0;
        gridPassCount = 0;

        float scale = getPanelScale(screenW, screenH);
        float panelW = INV_PANEL_W * scale;
        float panelH = INV_PANEL_H * scale;
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float slotSize = INV_SLOT_INNER * scale;

        // Pass 0: Màn tối phủ nền toàn màn hình
        int pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.04f, 0.05f, 0.08f, 0.65f);
        gridFloatLength = appendRect(gridVertices, gridFloatLength, 0f, 0f, screenW, screenH);
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        if (hasGuiTexture) {
            // Pass 1: Khung nền túi đồ Minecraft 1.21.11 cắt đúng 176 × 166 từ góc (0, 0) của inventory.png
            float[] invUv = GuiTextureAtlas.INVENTORY_PANEL_UV;
            pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                    gridFloatLength, TEXTURE_GUI_INVENTORY, 1f, 1f, 1f, 1f);
            gridFloatLength = appendTexturedRect(gridVertices, gridFloatLength,
                    panelX, panelY, panelW, panelH, invUv[0], invUv[1], invUv[2], invUv[3]);
            endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

            // Pass 2: Nhãn tiêu đề "INVENTORY" màu xám đậm đặc trưng trên khung inventory.png
            float titleScale = Math.max(1.0f, scale * 0.55f);
            pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                    gridFloatLength, TEXTURE_NONE, 0.25f, 0.25f, 0.25f, 1.0f);
            gridFloatLength = appendPixelText(gridVertices, gridFloatLength, "INVENTORY",
                    panelX + 97.0f * scale, panelY + 7.0f * scale, titleScale, gridVertices.length);
            endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);
        } else {
            // Fallback không có texture GUI: vẽ khung hộp thoại phẳng và 36 ô nền
            pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                    gridFloatLength, TEXTURE_NONE, 0.65f, 0.68f, 0.75f, 0.96f);
            gridFloatLength = appendRect(gridVertices, gridFloatLength,
                    panelX - 3f, panelY - 3f, panelW + 6f, panelH + 6f);
            endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

            pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                    gridFloatLength, TEXTURE_NONE, 0.14f, 0.16f, 0.21f, 0.96f);
            gridFloatLength = appendRect(gridVertices, gridFloatLength, panelX, panelY, panelW, panelH);
            endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

            pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                    gridFloatLength, TEXTURE_NONE, 0.23f, 0.25f, 0.31f, 0.96f);
            for (int s = 0; s < PlayerInventory.TOTAL_SLOTS; s++) {
                float sx = getSlotX(panelX, scale, s);
                float sy = getSlotY(panelY, scale, s);
                gridFloatLength = appendRect(gridVertices, gridFloatLength, sx, sy, slotSize, slotSize);
            }
            endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);
        }

        // Pass tiếp theo: Toàn bộ biểu tượng khối (Texture Block Atlas)
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_BLOCK_ATLAS, 1f, 1f, 1f, 1f);
        for (int s = 0; s < PlayerInventory.TOTAL_SLOTS; s++) {
            Item item = inv.getItem(s);
            int count = inv.getCount(s);
            if (item != null && !item.isNone() && count > 0 && item.isBlock()) {
                float sx = getSlotX(panelX, scale, s);
                float sy = getSlotY(panelY, scale, s);
                gridFloatLength = appendItemIcon(gridVertices, gridFloatLength, item, sx, sy, slotSize);
            }
        }
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // Pass tiếp theo: Toàn bộ biểu tượng vật phẩm độc lập (ItemTextureAtlas)
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_ITEM_ATLAS, 1f, 1f, 1f, 1f);
        for (int s = 0; s < PlayerInventory.TOTAL_SLOTS; s++) {
            Item item = inv.getItem(s);
            int count = inv.getCount(s);
            if (item != null && !item.isNone() && count > 0 && !item.isBlock()) {
                float sx = getSlotX(panelX, scale, s);
                float sy = getSlotY(panelY, scale, s);
                gridFloatLength = appendItemIcon(gridVertices, gridFloatLength, item, sx, sy, slotSize);
            }
        }
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // Pass tiếp theo: Bóng số lượng vật phẩm (> 1)
        float countScale = Math.max(1.2f, scale * 0.55f);
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.05f, 0.05f, 0.05f, 0.95f);
        for (int s = 0; s < PlayerInventory.TOTAL_SLOTS; s++) {
            Item item = inv.getItem(s);
            int count = inv.getCount(s);
            if (item != null && !item.isNone() && count > 1) {
                float sx = getSlotX(panelX, scale, s);
                float sy = getSlotY(panelY, scale, s);
                gridFloatLength = appendSlotCountText(gridVertices, gridFloatLength, count,
                        sx, sy, slotSize, countScale, 1.5f, gridVertices.length);
            }
        }
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // Pass tiếp theo: Chữ số lượng vật phẩm (> 1) màu trắng
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
        for (int s = 0; s < PlayerInventory.TOTAL_SLOTS; s++) {
            Item item = inv.getItem(s);
            int count = inv.getCount(s);
            if (item != null && !item.isNone() && count > 1) {
                float sx = getSlotX(panelX, scale, s);
                float sy = getSlotY(panelY, scale, s);
                gridFloatLength = appendSlotCountText(gridVertices, gridFloatLength, count,
                        sx, sy, slotSize, countScale, 0f, gridVertices.length);
            }
        }
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        if (gridMesh == null) {
            gridMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, false,
                    GRID_VERTEX_CAPACITY, 0, VERTEX_ATTRIBUTES);
        }
        gridMesh.setVertices(gridVertices, 0, gridFloatLength);
    }

    /** Dựng lớp phủ động gồm ô sáng khi hover, vật phẩm trên con trỏ và tooltip tên. */
    private void rebuildOverlayMesh(PlayerInventory inv, int screenW, int screenH,
                                    float mouseX, float mouseY) {
        overlayFloatLength = 0;
        overlayPassCount = 0;

        float scale = getPanelScale(screenW, screenH);
        float slotSize = INV_SLOT_INNER * scale;
        int hoveredSlot = getSlotAt(mouseX, mouseY, screenW, screenH);
        boolean hasCursorItem = cursorItem != null && !cursorItem.isNone() && cursorCount > 0;
        Item hoveredItem = (hoveredSlot >= 0) ? inv.getItem(hoveredSlot) : Item.NONE;
        int hoveredCount = (hoveredSlot >= 0) ? inv.getCount(hoveredSlot) : 0;
        boolean showTooltip = !hasCursorItem && hoveredSlot >= 0
                && hoveredItem != null && !hoveredItem.isNone() && hoveredCount > 0;

        if (hoveredSlot < 0 && !hasCursorItem) {
            return;
        }

        // Pass 0: Tô sáng trắng trong suốt chuẩn Minecraft trên ô đang trỏ chuột
        if (hoveredSlot >= 0) {
            float panelX = getPanelX(screenW, scale);
            float panelY = getPanelY(screenH, scale);
            float hx = getSlotX(panelX, scale, hoveredSlot);
            float hy = getSlotY(panelY, scale, hoveredSlot);

            int pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 0.42f);
            overlayFloatLength = appendRect(overlayVertices, overlayFloatLength, hx, hy, slotSize, slotSize);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);
        }

        // Pass 1: Biểu tượng vật phẩm đang bám theo con trỏ chuột
        if (hasCursorItem) {
            byte texSource = cursorItem.isBlock() ? TEXTURE_BLOCK_ATLAS : TEXTURE_ITEM_ATLAS;
            int pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, texSource, 1f, 1f, 1f, 1f);
            float boxX = mouseX - slotSize * 0.5f;
            float boxY = mouseY - slotSize * 0.5f;
            overlayFloatLength = appendItemIcon(overlayVertices, overlayFloatLength,
                    cursorItem, boxX, boxY, slotSize);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);

            if (cursorCount > 1) {
                float countScale = Math.max(1.2f, scale * 0.55f);
                pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                        overlayFloatLength, TEXTURE_NONE, 0.05f, 0.05f, 0.05f, 0.95f);
                overlayFloatLength = appendSlotCountText(overlayVertices, overlayFloatLength, cursorCount,
                        boxX, boxY, slotSize, countScale, 1.5f, overlayVertices.length);
                endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);

                pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                        overlayFloatLength, TEXTURE_NONE, 1f, 1f, 1f, 1f);
                overlayFloatLength = appendSlotCountText(overlayVertices, overlayFloatLength, cursorCount,
                        boxX, boxY, slotSize, countScale, 0f, overlayVertices.length);
                endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);
            }
        }

        // Pass tiếp theo: Tooltip hiển thị tên vật phẩm khi hover
        if (showTooltip) {
            String label = hoveredItem.getDisplayName();
            float tipScale = 1.6f;
            float textW = label.length() * 6.0f * tipScale;
            float textH = 7.0f * tipScale;
            float padX = 8.0f;
            float padY = 6.0f;
            float tipW = textW + padX * 2.0f;
            float tipH = textH + padY * 2.0f;
            float tipX = Math.max(4.0f, Math.min(screenW - tipW - 4.0f, mouseX + 14.0f));
            float tipY = Math.max(4.0f, Math.min(screenH - tipH - 4.0f, mouseY - tipH - 8.0f));

            int pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, TEXTURE_NONE, 0.45f, 0.48f, 0.78f, 0.96f);
            overlayFloatLength = appendRect(overlayVertices, overlayFloatLength,
                    tipX - 2f, tipY - 2f, tipW + 4f, tipH + 4f);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);

            pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, TEXTURE_NONE, 0.08f, 0.09f, 0.14f, 0.96f);
            overlayFloatLength = appendRect(overlayVertices, overlayFloatLength, tipX, tipY, tipW, tipH);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);

            pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, TEXTURE_NONE, 0.05f, 0.05f, 0.05f, 0.95f);
            overlayFloatLength = appendPixelText(overlayVertices, overlayFloatLength, label,
                    tipX + padX + 1.2f, tipY + padY + 1.2f, tipScale, overlayVertices.length);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);

            pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
            overlayFloatLength = appendPixelText(overlayVertices, overlayFloatLength, label,
                    tipX + padX, tipY + padY, tipScale, overlayVertices.length);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);
        }

        if (overlayFloatLength > 0) {
            if (overlayMesh == null) {
                overlayMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, false,
                        OVERLAY_VERTEX_CAPACITY, 0, VERTEX_ATTRIBUTES);
            }
            overlayMesh.setVertices(overlayVertices, 0, overlayFloatLength);
        }
    }

    /** Thực thi kết xuất tuần tự từng dải đỉnh và chỉ đổi atlas khi nguồn texture thay đổi. */
    private void renderPasses(ShaderProgram shader, Mesh mesh, int passCount,
                              int[] passStart, int[] passVertCount, byte[] passTexSource,
                              float[] passTints, Texture blockAtlas, ItemTextureAtlas itemAtlas,
                              GuiTextureAtlas guiAtlas) {
        if (mesh == null || passCount == 0) {
            return;
        }
        var program = shader.getGdxShader();
        byte boundSource = TEXTURE_NONE;
        mesh.bind(program);
        for (int i = 0; i < passCount; i++) {
            int vertCount = passVertCount[i];
            if (vertCount <= 0) {
                continue;
            }
            byte source = passTexSource[i];
            if (source == TEXTURE_BLOCK_ATLAS && blockAtlas != null && boundSource != TEXTURE_BLOCK_ATLAS) {
                blockAtlas.bind();
                boundSource = TEXTURE_BLOCK_ATLAS;
            } else if (source == TEXTURE_ITEM_ATLAS && itemAtlas != null && boundSource != TEXTURE_ITEM_ATLAS) {
                itemAtlas.bind();
                boundSource = TEXTURE_ITEM_ATLAS;
            } else if (source == TEXTURE_GUI_INVENTORY && guiAtlas != null && boundSource != TEXTURE_GUI_INVENTORY) {
                guiAtlas.bindInventoryTexture();
                boundSource = TEXTURE_GUI_INVENTORY;
            }
            int tint = i * 4;
            boolean useTexture = (source == TEXTURE_BLOCK_ATLAS && blockAtlas != null)
                    || (source == TEXTURE_ITEM_ATLAS && itemAtlas != null)
                    || (source == TEXTURE_GUI_INVENTORY && guiAtlas != null);
            shader.setUniform1i("uUseTexture", useTexture ? 1 : 0);
            shader.setUniformVec4("uTintColor", passTints[tint], passTints[tint + 1],
                    passTints[tint + 2], passTints[tint + 3]);
            mesh.render(program, GL20.GL_TRIANGLES, passStart[i], vertCount, false);
        }
        mesh.unbind(program);
        if (boundSource == TEXTURE_GUI_INVENTORY && guiAtlas != null) {
            guiAtlas.unbind();
        } else if (boundSource == TEXTURE_ITEM_ATLAS && itemAtlas != null) {
            itemAtlas.unbind();
        } else if (boundSource == TEXTURE_BLOCK_ATLAS && blockAtlas != null) {
            blockAtlas.unbind();
        }
    }

    private float getPanelX(int screenW, float scale) {
        return Math.round((screenW - INV_PANEL_W * scale) * 0.5f);
    }

    private float getPanelY(int screenH, float scale) {
        return Math.round((screenH - INV_PANEL_H * scale) * 0.5f);
    }

    private float getSlotX(float panelX, float scale, int slot) {
        int col = (slot < PlayerInventory.HOTBAR_SLOTS)
                ? slot
                : (slot - PlayerInventory.HOTBAR_SLOTS) % COLS;
        float baseX = (slot < PlayerInventory.HOTBAR_SLOTS) ? INV_HOTBAR_X : INV_BACKPACK_X;
        return panelX + (baseX + col * INV_SLOT_PITCH) * scale;
    }

    private float getSlotY(float panelY, float scale, int slot) {
        if (slot < PlayerInventory.HOTBAR_SLOTS) {
            return panelY + INV_HOTBAR_Y * scale;
        }
        int row = (slot - PlayerInventory.HOTBAR_SLOTS) / COLS;
        return panelY + (INV_BACKPACK_Y + row * INV_SLOT_PITCH) * scale;
    }

    private int beginPass(int[] starts, byte[] sources, float[] tints, int index,
                          int currentFloatLen, byte texSource, float r, float g, float b, float a) {
        starts[index] = currentFloatLen / 6;
        sources[index] = texSource;
        int t = index * 4;
        tints[t] = r;
        tints[t + 1] = g;
        tints[t + 2] = b;
        tints[t + 3] = a;
        return index;
    }

    private void endPass(int[] starts, int[] counts, int index, int newFloatLen) {
        counts[index] = (newFloatLen / 6) - starts[index];
    }

    /** Ghi biểu tượng khối hoặc vật phẩm độc lập vào bộ đệm khớp khít ô 16 × 16 đã phóng tỷ lệ. */
    private int appendItemIcon(float[] buffer, int offset, Item item, float slotX, float slotY, float slotSize) {
        float iconW = slotSize;
        float iconH = slotSize;
        float u0;
        float v0;
        float u1;
        float v1;

        if (item.isBlock()) {
            Block itemBlock = item.getBlock();
            if (itemBlock.getGeometryType() == Block.GeometryType.CACTUS) {
                Block.Bounds shape = itemBlock.getSelectionBounds(0);
                iconW *= shape.maxX() - shape.minX();
            } else if (itemBlock.getGeometryType() == Block.GeometryType.SNOW_LAYER) {
                iconH *= itemBlock.getSelectionBounds(0).maxY();
            }
            float[] uv = Chunk.tileUv(itemBlock.getTileIndex(Block.Face.SOUTH));
            u0 = uv[0];
            v0 = uv[1];
            u1 = uv[2];
            v1 = uv[3];
        } else {
            computeStandaloneUv(item.getAtlasTileIndex(), itemUvBuffer);
            u0 = itemUvBuffer[0];
            v0 = itemUvBuffer[1];
            u1 = itemUvBuffer[2];
            v1 = itemUvBuffer[3];
        }

        float iconX = slotX + (slotSize - iconW) * 0.5f;
        float iconY = slotY + (slotSize - iconH) * 0.5f;
        return appendTexturedRect(buffer, offset, iconX, iconY, iconW, iconH, u0, v0, u1, v1);
    }

    /** Tính tọa độ UV của vật phẩm độc lập trên ItemTextureAtlas 8x8 trực tiếp vào bộ đệm tái sử dụng. */
    private static void computeStandaloneUv(int tileIndex, float[] out) {
        if (tileIndex < 0) {
            out[0] = 0f;
            out[1] = 0f;
            out[2] = 1f;
            out[3] = 1f;
            return;
        }
        int col = tileIndex % ItemTextureAtlas.ATLAS_COLS;
        int row = tileIndex / ItemTextureAtlas.ATLAS_COLS;
        float tileW = 1.0f / ItemTextureAtlas.ATLAS_COLS;
        float tileH = 1.0f / ItemTextureAtlas.ATLAS_ROWS;
        float eps = 0.0008f;
        out[0] = col * tileW + eps;
        out[1] = row * tileH + eps;
        out[2] = (col + 1) * tileW - eps;
        out[3] = (row + 1) * tileH - eps;
    }

    /** Ghi chuỗi số lượng ở góc phải-dưới ô bằng mảng chuỗi tạo sẵn. */
    private int appendSlotCountText(float[] buffer, int offset, int count,
                                    float slotX, float slotY, float slotSize,
                                    float textScale, float shadowOffset, int capacity) {
        int clamped = Math.max(0, Math.min(NUMBER_LABELS.length - 1, count));
        String countStr = NUMBER_LABELS[clamped];
        float textW = countStr.length() * 6.0f * textScale;
        float tx = slotX + slotSize - textW - 1.0f + shadowOffset;
        float ty = slotY + slotSize - 7.0f * textScale - 1.0f + shadowOffset;
        return appendPixelText(buffer, offset, countStr, tx, ty, textScale, capacity);
    }

    private int appendRect(float[] buffer, int offset, float x, float y, float w, float h) {
        return appendTexturedRect(buffer, offset, x, y, w, h, 0f, 0f, 1f, 1f);
    }

    private int appendTexturedRect(float[] buffer, int offset, float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1) {
        if (offset + 36 > buffer.length) {
            return offset;
        }
        writeQuad(buffer, offset, x, y + h, 0f, x + w, y + h, 0f,
                x + w, y, 0f, x, y, 0f, u0, v0, u1, v1);
        return offset + 36;
    }

    private int appendPixelText(float[] buffer, int offset, CharSequence text,
                                float startX, float startY, float pixelScale, int capacity) {
        if (text == null || text.isEmpty()) {
            return offset;
        }
        float cursorX = startX;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            int[] glyph = c < GLYPHS.length ? GLYPHS[c] : null;
            if (glyph != null) {
                for (int row = 0; row < 7; row++) {
                    int rowBits = glyph[row];
                    for (int col = 0; col < 5; col++) {
                        if (((rowBits >> (4 - col)) & 1) == 1) {
                            if (offset + 36 > capacity) {
                                return offset;
                            }
                            float px = cursorX + col * pixelScale;
                            float py = startY + row * pixelScale;
                            writeQuad(buffer, offset, px, py + pixelScale, 0f,
                                    px + pixelScale, py + pixelScale, 0f,
                                    px + pixelScale, py, 0f, px, py, 0f, 0f, 0f, 1f, 1f);
                            offset += 36;
                        }
                    }
                }
            }
            cursorX += 6.0f * pixelScale;
        }
        return offset;
    }

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

    private static void writeVertex(float[] data, int offset, float x, float y, float z, float u, float v) {
        data[offset] = x;
        data[offset + 1] = y;
        data[offset + 2] = z;
        data[offset + 3] = u;
        data[offset + 4] = v;
        data[offset + 5] = 1.0f;
    }

    private static int[][] createGlyphs() {
        int[][] glyphs = new int[128][];
        for (char c = 0; c < glyphs.length; c++) {
            glyphs[c] = createGlyph5x7(c);
        }
        return glyphs;
    }

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
            default -> null;
        };
    }

    /**
     * Giải phóng bộ đệm {@link Mesh} và {@link GuiTextureAtlas} tự sở hữu (nếu có) trên GPU.
     */
    @Override
    public void dispose() {
        if (gridMesh != null) {
            gridMesh.dispose();
            gridMesh = null;
        }
        if (overlayMesh != null) {
            overlayMesh.dispose();
            overlayMesh = null;
        }
        if (ownedGuiAtlas != null) {
            ownedGuiAtlas.dispose();
            ownedGuiAtlas = null;
        }
        sharedGuiAtlas = null;
        cachedScreenW = -1;
        cachedScreenH = -1;
        cachedHasGuiTexture = false;
        dirty = true;
    }
}
