package com.voxelgame.client.gui;

import com.voxelgame.client.render.ItemTextureAtlas;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.client.render.Texture;
import com.voxelgame.world.entity.player.PlayerInventory;
import com.voxelgame.world.item.CreativeCatalog;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.Chunk;
import com.voxelgame.world.level.block.Block;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;

/**
 * Màn hình Kho Vật Phẩm Creative (bật/tắt bằng phím {@code E} khi ở chế độ Creative),
 * kế thừa {@link Screen} và tách biệt hoàn toàn với {@link InventoryScreen} của Survival.
 *
 * <p>Bố cục trực quan chuẩn Minecraft:</p>
 * <ul>
 *   <li>5 tab phân loại phía trên ({@code ALL}, {@code BLOCKS}, {@code COMBAT}, {@code FOOD}, {@code ITEMS})
 *       đọc trực tiếp từ {@link CreativeCatalog} bất biến.</li>
 *   <li>Lưới danh mục 9 cột × 5 hàng ({@value #VISIBLE_CATALOG_SLOTS} ô) kèm thanh cuộn ({@code ▲}/{@code ▼}
 *       và cuộn chuột).</li>
 *   <li>Hàng 9 ô Hotbar bên dưới đồng bộ trực tiếp với {@link PlayerInventory} (ô {@code 0..8})
 *       và ô Thùng rác {@code [X]} (click xóa đồ đang cầm; {@code Shift+Click} dọn sạch Hotbar).</li>
 * </ul>
 *
 * <p>Thiết kế Zero-GC: tái sử dụng hoàn toàn mảng đỉnh tĩnh và {@link Mesh} (chỉ dựng lại khi đổi tab,
 * cuộn trang, đổi Hotbar hoặc đổi kích thước cửa sổ); giải phóng đầy đủ GPU trong {@link #dispose()}.</p>
 */
public class CreativeInventoryScreen extends Screen {

    /** Mã nút chuột trái và chuột phải. */
    public static final int LEFT_CLICK = Input.Buttons.LEFT;
    public static final int RIGHT_CLICK = Input.Buttons.RIGHT;

    /** Kích thước lưới danh mục Creative: 9 cột × 5 hàng. */
    public static final int COLS = 9;
    public static final int ROWS = 5;
    public static final int VISIBLE_CATALOG_SLOTS = COLS * ROWS;

    /** Các thông số bố cục theo đơn vị pixel gốc (trước khi nhân hệ số phóng {@code scale}). */
    private static final float PANEL_W = 196.0f;
    private static final float PANEL_H = 152.0f;
    private static final float TAB_BAR_H = 16.0f;
    private static final float TAB_GAP = 2.0f;
    private static final float GRID_X = 8.0f;
    private static final float GRID_Y = 24.0f;
    private static final float SLOT_PITCH = 18.0f;
    private static final float SLOT_INNER = 16.0f;
    private static final float SCROLL_X = 174.0f;
    private static final float SCROLL_W = 14.0f;
    private static final float SCROLL_BTN_H = 14.0f;
    private static final float HOTBAR_X = 8.0f;
    private static final float HOTBAR_Y = 122.0f;
    private static final float TRASH_X = 174.0f;
    private static final float TRASH_Y = 122.0f;

    /** Cấu hình đỉnh đồng nhất với HudRenderer và InventoryScreen: aPos (3), aTexCoord (2), aShade (1). */
    private static final VertexAttributes VERTEX_ATTRIBUTES = new VertexAttributes(
            new VertexAttribute(Usage.Position, 3, "aPos"),
            new VertexAttribute(Usage.TextureCoordinates, 2, "aTexCoord"),
            new VertexAttribute(Usage.Generic, 1, "aShade")
    );

    /** Nguồn texture cho từng lượt vẽ trong Mesh. */
    private static final byte TEXTURE_NONE = 0;
    private static final byte TEXTURE_BLOCK_ATLAS = 1;
    private static final byte TEXTURE_ITEM_ATLAS = 2;

    /** Dung lượng đỉnh tĩnh cho lưới nền và lớp phủ động (zero-GC). */
    private static final int GRID_VERTEX_CAPACITY = 24576;
    private static final int OVERLAY_VERTEX_CAPACITY = 8192;
    private static final int MAX_PASSES = 16;

    /** Mảng các tab dựng sẵn để duyệt không cấp phát rác. */
    private static final CreativeCatalog.Tab[] TABS = CreativeCatalog.Tab.values();

    /** Bảng nhãn số lượng 0..64 dựng sẵn (zero-GC). */
    private static final String[] NUMBER_LABELS = new String[65];

    static {
        for (int i = 0; i < NUMBER_LABELS.length; i++) {
            NUMBER_LABELS[i] = Integer.toString(i);
        }
    }

    /** Tab danh mục hiện tại và dòng cuộn đầu tiên đang hiển thị. */
    private CreativeCatalog.Tab activeTab = CreativeCatalog.Tab.ALL;
    private int scrollRow = 0;

    /** Vật phẩm và số lượng đang bám theo con trỏ chuột. */
    private Item cursorItem = Item.NONE;
    private int cursorCount = 0;

    /** Bộ đệm Mesh tĩnh cho khung, 5 tab, lưới 45 ô, thanh cuộn, Hotbar 9 ô và Thùng rác. */
    private Mesh gridMesh;
    private final float[] gridVertices = new float[GRID_VERTEX_CAPACITY * 6];
    private int gridFloatLength = 0;
    private int gridPassCount = 0;
    private final int[] gridPassStart = new int[MAX_PASSES];
    private final int[] gridPassCountVerts = new int[MAX_PASSES];
    private final byte[] gridPassTexSource = new byte[MAX_PASSES];
    private final float[] gridPassTints = new float[MAX_PASSES * 4];

    /** Trạng thái lưu đệm của 9 ô Hotbar và khung để phát hiện cần dựng lại Mesh. */
    private final Item[] cachedHotbarItems = new Item[PlayerInventory.HOTBAR_SLOTS];
    private final int[] cachedHotbarCounts = new int[PlayerInventory.HOTBAR_SLOTS];
    private CreativeCatalog.Tab cachedTab = null;
    private int cachedScrollRow = -1;
    private int cachedScreenW = -1;
    private int cachedScreenH = -1;
    private boolean dirty = true;

    /** Bộ đệm Mesh động cho ô hover, vật phẩm trên con trỏ và tooltip tên vật phẩm. */
    private Mesh overlayMesh;
    private final float[] overlayVertices = new float[OVERLAY_VERTEX_CAPACITY * 6];
    private int overlayFloatLength = 0;
    private int overlayPassCount = 0;
    private final int[] overlayPassStart = new int[MAX_PASSES];
    private final int[] overlayPassCountVerts = new int[MAX_PASSES];
    private final byte[] overlayPassTexSource = new byte[MAX_PASSES];
    private final float[] overlayPassTints = new float[MAX_PASSES * 4];

    /** Bộ đệm UV tái sử dụng cho vật phẩm độc lập (zero-GC). */
    private final float[] itemUvBuffer = new float[4];

    /** Khởi tạo màn hình Kho Creative (cấp phát lười GPU Mesh để tương thích kiểm thử CPU headless). */
    public CreativeInventoryScreen() {
        for (int i = 0; i < PlayerInventory.HOTBAR_SLOTS; i++) {
            cachedHotbarItems[i] = Item.NONE;
            cachedHotbarCounts[i] = 0;
        }
    }

    /** Mở Kho Creative không tạm dừng mô phỏng thế giới (chuẩn Minecraft). */
    @Override
    public boolean pausesWorld() {
        return false;
    }

    @Override
    protected void onOpen() {
        dirty = true;
    }

    @Override
    protected void onClose() {
        cursorItem = Item.NONE;
        cursorCount = 0;
        dirty = true;
    }

    /** Trả về tab danh mục hiện đang được chọn. */
    public CreativeCatalog.Tab getActiveTab() {
        return activeTab;
    }

    /** Chuyển sang {@code tab} mới và đặt lại vị trí cuộn về đầu trang. */
    public void setActiveTab(CreativeCatalog.Tab tab) {
        if (tab == null) {
            return;
        }
        if (this.activeTab != tab) {
            this.activeTab = tab;
            this.scrollRow = 0;
            this.dirty = true;
        }
    }

    /** Trả về chỉ số hàng cuộn hiện tại (bắt đầu từ 0). */
    public int getScrollRow() {
        return scrollRow;
    }

    /** Trả về chỉ số hàng cuộn tối đa của tab hiện tại. */
    public int getMaxScrollRow() {
        int count = CreativeCatalog.getItemCount(activeTab);
        int totalRows = (count + COLS - 1) / COLS;
        return Math.max(0, totalRows - ROWS);
    }

    /** Cuộn danh sách vật phẩm lên/xuống theo bước cuộn chuột (dương = cuộn xuống, âm = cuộn lên). */
    public void scroll(float deltaRows) {
        int step = deltaRows > 0f ? 1 : (deltaRows < 0f ? -1 : 0);
        if (step == 0) {
            return;
        }
        int next = Math.max(0, Math.min(getMaxScrollRow(), scrollRow + step));
        if (next != scrollRow) {
            scrollRow = next;
            dirty = true;
        }
    }

    /** Trả về loại vật phẩm hiện đang bám theo con trỏ chuột. */
    public Item getCursorItem() {
        return cursorItem;
    }

    /** Trả về số lượng vật phẩm hiện đang bám theo con trỏ chuột. */
    public int getCursorCount() {
        return cursorCount;
    }

    /** Tính hệ số phóng nguyên pixel-perfect (1..4) theo kích thước cửa sổ. */
    private float getPanelScale(int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) {
            return 2.0f;
        }
        int scaleW = screenW / 240;
        int scaleH = screenH / 200;
        return Math.max(1, Math.min(4, Math.min(scaleW, scaleH)));
    }

    private float getPanelX(int screenW, float scale) {
        return Math.round((screenW - PANEL_W * scale) * 0.5f);
    }

    private float getPanelY(int screenH, float scale) {
        return Math.round((screenH - PANEL_H * scale) * 0.5f);
    }

    /** Trả về chỉ số tab (0..4) dưới con trỏ chuột, hoặc {@code -1} nếu không trúng. */
    public int getTabAt(float mouseX, float mouseY, int screenW, int screenH) {
        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float tabW = ((PANEL_W - (TABS.length - 1) * TAB_GAP) / TABS.length) * scale;
        float tabH = TAB_BAR_H * scale;
        float tabY = panelY + 4.0f * scale;
        if (mouseY < tabY || mouseY > tabY + tabH) {
            return -1;
        }
        for (int i = 0; i < TABS.length; i++) {
            float tx = panelX + i * (tabW + TAB_GAP * scale);
            if (mouseX >= tx && mouseX <= tx + tabW) {
                return i;
            }
        }
        return -1;
    }

    /** Trả về chỉ số ô trong lưới hiển thị (0..44) dưới con trỏ chuột, hoặc {@code -1} nếu không trúng. */
    public int getCatalogSlotAt(float mouseX, float mouseY, int screenW, int screenH) {
        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float slotSize = SLOT_INNER * scale;
        for (int r = 0; r < ROWS; r++) {
            float sy = panelY + (GRID_Y + r * SLOT_PITCH) * scale;
            if (mouseY < sy || mouseY > sy + slotSize) {
                continue;
            }
            for (int c = 0; c < COLS; c++) {
                float sx = panelX + (GRID_X + c * SLOT_PITCH) * scale;
                if (mouseX >= sx && mouseX <= sx + slotSize) {
                    return r * COLS + c;
                }
            }
        }
        return -1;
    }

    /** Trả về chỉ số ô Hotbar (0..8) dưới con trỏ chuột, hoặc {@code -1} nếu không trúng. */
    public int getHotbarSlotAt(float mouseX, float mouseY, int screenW, int screenH) {
        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float slotSize = SLOT_INNER * scale;
        float hy = panelY + HOTBAR_Y * scale;
        if (mouseY < hy || mouseY > hy + slotSize) {
            return -1;
        }
        for (int c = 0; c < PlayerInventory.HOTBAR_SLOTS; c++) {
            float hx = panelX + (HOTBAR_X + c * SLOT_PITCH) * scale;
            if (mouseX >= hx && mouseX <= hx + slotSize) {
                return c;
            }
        }
        return -1;
    }

    /** Kiểm tra tọa độ chuột có nằm trong ô Thùng rác {@code [X]} hay không. */
    public boolean isTrashSlotAt(float mouseX, float mouseY, int screenW, int screenH) {
        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float tx = panelX + TRASH_X * scale;
        float ty = panelY + TRASH_Y * scale;
        float size = SLOT_INNER * scale;
        return mouseX >= tx && mouseX <= tx + size && mouseY >= ty && mouseY <= ty + size;
    }

    /** Kiểm tra chuột có trúng nút cuộn lên (-1), nút cuộn xuống (+1), hay không (0). */
    private int getScrollButtonAt(float mouseX, float mouseY, int screenW, int screenH) {
        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float sx = panelX + SCROLL_X * scale;
        float sw = SCROLL_W * scale;
        if (mouseX < sx || mouseX > sx + sw) {
            return 0;
        }
        float upY = panelY + GRID_Y * scale;
        float btnH = SCROLL_BTN_H * scale;
        if (mouseY >= upY && mouseY <= upY + btnH) {
            return -1;
        }
        float gridBottom = panelY + (GRID_Y + (ROWS - 1) * SLOT_PITCH + SLOT_INNER) * scale;
        if (mouseY >= gridBottom - btnH && mouseY <= gridBottom) {
            return 1;
        }
        return 0;
    }

    /**
     * Xử lý click chuột trên Kho Creative:
     * - Click tab: chuyển nhóm vật phẩm.
     * - Click nút cuộn {@code ▲/▼}: cuộn lưới danh mục.
     * - Click ô Kho: chuột trái lấy full stack, chuột phải lấy 1, {@code Shift+Click} thêm thẳng vào Hotbar.
     * - Click ô Hotbar: đặt/gộp/hoán đổi đồ từ con trỏ ({@code Shift+Click} xóa nhanh ô đó).
     * - Click Thùng rác {@code [X]}: xóa đồ trên con trỏ ({@code Shift+Click} dọn sạch cả 9 ô Hotbar).
     *
     * @return {@code true} nếu có thao tác hợp lệ được xử lý
     */
    public boolean handleClick(PlayerInventory inv, float mouseX, float mouseY,
                               int screenW, int screenH, int button, boolean shiftHeld) {
        if (!open || inv == null) {
            return false;
        }

        // 1. Kiểm tra click vào thanh 5 Tab
        int tabIdx = getTabAt(mouseX, mouseY, screenW, screenH);
        if (tabIdx >= 0 && tabIdx < TABS.length) {
            setActiveTab(TABS[tabIdx]);
            return true;
        }

        // 2. Kiểm tra click nút cuộn lên/xuống
        int scrollDir = getScrollButtonAt(mouseX, mouseY, screenW, screenH);
        if (scrollDir != 0) {
            scroll(scrollDir);
            return true;
        }

        // 3. Kiểm tra click ô Thùng rác [X]
        if (isTrashSlotAt(mouseX, mouseY, screenW, screenH)) {
            if (shiftHeld) {
                for (int s = 0; s < PlayerInventory.HOTBAR_SLOTS; s++) {
                    inv.setSlot(s, Item.NONE, 0);
                }
            }
            cursorItem = Item.NONE;
            cursorCount = 0;
            dirty = true;
            return true;
        }

        // 4. Kiểm tra click ô trong lưới danh mục Creative
        int catalogSlot = getCatalogSlotAt(mouseX, mouseY, screenW, screenH);
        if (catalogSlot >= 0) {
            int itemIndex = (scrollRow * COLS) + catalogSlot;
            Item catalogItem = CreativeCatalog.getItem(activeTab, itemIndex);
            if (catalogItem == null || catalogItem.isNone()) {
                // Click vùng trống trong danh mục khi đang cầm đồ sẽ xóa đồ đang cầm (chuẩn Creative)
                if (!cursorItem.isNone() && cursorCount > 0) {
                    cursorItem = Item.NONE;
                    cursorCount = 0;
                    dirty = true;
                    return true;
                }
                return false;
            }
            return handleCatalogSlotClick(inv, catalogItem, button, shiftHeld);
        }

        // 5. Kiểm tra click vào 9 ô Hotbar bên dưới
        int hotbarSlot = getHotbarSlotAt(mouseX, mouseY, screenW, screenH);
        if (hotbarSlot >= 0) {
            return handleHotbarSlotClick(inv, hotbarSlot, button, shiftHeld);
        }

        return false;
    }

    /** Xử lý thao tác lấy vật phẩm từ một ô hợp lệ trong danh mục Creative. */
    private boolean handleCatalogSlotClick(PlayerInventory inv, Item catalogItem, int button, boolean shiftHeld) {
        int maxStack = Math.max(1, catalogItem.getMaxStackSize());
        if (shiftHeld) {
            // Đưa thẳng 1 full stack vào ô trống hoặc cộng dồn trên Hotbar (0..8)
            addToHotbarDirect(inv, catalogItem, maxStack);
            dirty = true;
            return true;
        }
        if (button == RIGHT_CLICK) {
            if (cursorItem == catalogItem && cursorCount > 0) {
                cursorCount = Math.min(maxStack, cursorCount + 1);
            } else {
                cursorItem = catalogItem;
                cursorCount = 1;
            }
            dirty = true;
            return true;
        }
        if (button == LEFT_CLICK) {
            if (cursorItem == catalogItem && cursorCount >= maxStack) {
                // Nhấn lại đúng món đã đầy stack sẽ bỏ khỏi con trỏ
                cursorItem = Item.NONE;
                cursorCount = 0;
            } else {
                cursorItem = catalogItem;
                cursorCount = maxStack;
            }
            dirty = true;
            return true;
        }
        return false;
    }

    /** Xử lý đặt/gộp/hoán đổi hoặc xóa nhanh trên một ô Hotbar (0..8). */
    private boolean handleHotbarSlotClick(PlayerInventory inv, int slot, int button, boolean shiftHeld) {
        Item slotItem = inv.getItem(slot);
        int slotCount = inv.getCount(slot);

        if (shiftHeld) {
            inv.setSlot(slot, Item.NONE, 0);
            dirty = true;
            return true;
        }

        boolean hasCursor = cursorItem != null && !cursorItem.isNone() && cursorCount > 0;
        boolean hasSlot = slotItem != null && !slotItem.isNone() && slotCount > 0;

        if (button == LEFT_CLICK) {
            if (!hasCursor) {
                if (!hasSlot) {
                    return false;
                }
                cursorItem = slotItem;
                cursorCount = slotCount;
                inv.setSlot(slot, Item.NONE, 0);
            } else if (!hasSlot) {
                inv.setSlot(slot, cursorItem, cursorCount);
                cursorItem = Item.NONE;
                cursorCount = 0;
            } else if (slotItem == cursorItem) {
                int maxStack = Math.max(1, slotItem.getMaxStackSize());
                int space = Math.max(0, maxStack - slotCount);
                int moved = Math.min(space, cursorCount);
                inv.setSlot(slot, slotItem, slotCount + moved);
                cursorCount -= moved;
                if (cursorCount <= 0) {
                    cursorItem = Item.NONE;
                    cursorCount = 0;
                }
            } else {
                inv.setSlot(slot, cursorItem, cursorCount);
                cursorItem = slotItem;
                cursorCount = slotCount;
            }
            dirty = true;
            return true;
        }

        if (button == RIGHT_CLICK) {
            if (!hasCursor) {
                if (!hasSlot) {
                    return false;
                }
                int take = (slotCount + 1) / 2;
                int remain = slotCount - take;
                cursorItem = slotItem;
                cursorCount = take;
                inv.setSlot(slot, remain > 0 ? slotItem : Item.NONE, remain);
                dirty = true;
                return true;
            }
            if (!hasSlot) {
                inv.setSlot(slot, cursorItem, 1);
                cursorCount--;
                if (cursorCount <= 0) {
                    cursorItem = Item.NONE;
                    cursorCount = 0;
                }
                dirty = true;
                return true;
            }
            if (slotItem == cursorItem && slotCount < Math.max(1, slotItem.getMaxStackSize())) {
                inv.setSlot(slot, slotItem, slotCount + 1);
                cursorCount--;
                if (cursorCount <= 0) {
                    cursorItem = Item.NONE;
                    cursorCount = 0;
                }
                dirty = true;
                return true;
            }
        }
        return false;
    }

    /** Thêm trực tiếp vật phẩm vào hàng Hotbar (0..8), ưu tiên gộp stack cùng loại rồi tới ô trống. */
    private static void addToHotbarDirect(PlayerInventory inv, Item item, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, item.getMaxStackSize());
        for (int s = 0; s < PlayerInventory.HOTBAR_SLOTS && remaining > 0; s++) {
            if (inv.getItem(s) == item && inv.getCount(s) > 0 && inv.getCount(s) < maxStack) {
                int add = Math.min(remaining, maxStack - inv.getCount(s));
                inv.setSlot(s, item, inv.getCount(s) + add);
                remaining -= add;
            }
        }
        for (int s = 0; s < PlayerInventory.HOTBAR_SLOTS && remaining > 0; s++) {
            if (inv.getCount(s) <= 0 || inv.getItem(s).isNone()) {
                int add = Math.min(remaining, maxStack);
                inv.setSlot(s, item, add);
                remaining -= add;
            }
        }
    }

    /**
     * Kết xuất toàn bộ Kho Creative trong chế độ 2D Orthographic (không kèm font VT323).
     */
    public void render(ShaderProgram shader, PlayerInventory inv, Texture blockAtlas,
                       ItemTextureAtlas itemAtlas, int screenW, int screenH,
                       float mouseX, float mouseY) {
        render(shader, inv, blockAtlas, itemAtlas, null, screenW, screenH, mouseX, mouseY);
    }

    /**
     * Kết xuất toàn bộ Kho Creative trong chế độ 2D Orthographic cùng font {@link Vt323FontRenderer}.
     */
    public void render(ShaderProgram shader, PlayerInventory inv, Texture blockAtlas,
                       ItemTextureAtlas itemAtlas, Vt323FontRenderer font, int screenW, int screenH,
                       float mouseX, float mouseY) {
        if (!open || shader == null || inv == null || Gdx.gl == null) {
            return;
        }
        boolean useVt323 = font != null && font.isReady();
        if (needsGridRebuild(inv, screenW, screenH)) {
            rebuildGridMesh(inv, !useVt323, screenW, screenH);
            syncCache(inv, screenW, screenH);
        }
        rebuildOverlayMesh(inv, !useVt323, screenW, screenH, mouseX, mouseY);

        if (gridMesh != null && gridPassCount > 0) {
            renderMeshPasses(shader, gridMesh, gridPassCount, gridPassStart, gridPassCountVerts,
                    gridPassTexSource, gridPassTints, blockAtlas, itemAtlas);
        }
        if (useVt323) {
            renderGridVt323Text(shader, font, inv, screenW, screenH);
        }
        if (overlayMesh != null && overlayPassCount > 0) {
            renderMeshPasses(shader, overlayMesh, overlayPassCount, overlayPassStart, overlayPassCountVerts,
                    overlayPassTexSource, overlayPassTints, blockAtlas, itemAtlas);
        }
        if (useVt323) {
            renderOverlayVt323Text(shader, font, inv, screenW, screenH, mouseX, mouseY);
        }
    }

    /** Vẽ nhãn 5 tab, nút cuộn +/-, nút thùng rác X và số lượng stack Hotbar bằng font VT323. */
    private void renderGridVt323Text(ShaderProgram shader, Vt323FontRenderer font, PlayerInventory inv,
                                     int screenW, int screenH) {
        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float panelW = PANEL_W * scale;
        float slotSize = SLOT_INNER * scale;
        float tabW = ((PANEL_W - (TABS.length - 1) * TAB_GAP) / TABS.length) * scale;
        float tabH = TAB_BAR_H * scale;
        float tabY = panelY + 4.0f * scale;

        float tabPxH = Math.max(13.0f, scale * 5.6f);
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i].getLabel();
            float tx = panelX + i * (tabW + TAB_GAP * scale);
            float lw = font.measureWidth(label, tabPxH);
            font.drawTextWithShadow(shader, label, tx + (tabW - lw) * 0.5f,
                    tabY + (tabH - tabPxH) * 0.5f, tabPxH, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        float scrollX = panelX + SCROLL_X * scale;
        float scrollW = SCROLL_W * scale;
        float scrollTop = panelY + GRID_Y * scale;
        float scrollTotalH = ((ROWS - 1) * SLOT_PITCH + SLOT_INNER) * scale;
        float btnH = SCROLL_BTN_H * scale;
        float btnPxH = Math.max(14.0f, scale * 6.0f);

        font.drawCenteredTextWithShadow(shader, "-", scrollX + scrollW * 0.5f,
                scrollTop + (btnH - btnPxH) * 0.5f, btnPxH, 1.0f, 1.0f, 1.0f, 1.0f);
        font.drawCenteredTextWithShadow(shader, "+", scrollX + scrollW * 0.5f,
                scrollTop + scrollTotalH - btnH + (btnH - btnPxH) * 0.5f, btnPxH, 1.0f, 1.0f, 1.0f, 1.0f);
        font.drawCenteredTextWithShadow(shader, "X", panelX + TRASH_X * scale + slotSize * 0.5f,
                panelY + TRASH_Y * scale + (slotSize - btnPxH) * 0.5f, btnPxH, 1.0f, 1.0f, 1.0f, 1.0f);

        float hy = panelY + HOTBAR_Y * scale;
        float countPxH = Math.max(14.0f, scale * 6.2f);
        for (int s = 0; s < PlayerInventory.HOTBAR_SLOTS; s++) {
            int count = inv.getCount(s);
            if (count > 1 && !inv.getItem(s).isNone()) {
                int clamped = Math.max(0, Math.min(NUMBER_LABELS.length - 1, count));
                String countStr = NUMBER_LABELS[clamped];
                float hx = panelX + (HOTBAR_X + s * SLOT_PITCH) * scale;
                float tw = font.measureWidth(countStr, countPxH);
                font.drawTextWithShadow(shader, countStr,
                        hx + slotSize - tw - 1.5f, hy + slotSize - countPxH - 1.0f,
                        countPxH, 1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }

    /** Vẽ số lượng stack trên con trỏ và tooltip vật phẩm bằng font VT323. */
    private void renderOverlayVt323Text(ShaderProgram shader, Vt323FontRenderer font, PlayerInventory inv,
                                        int screenW, int screenH, float mouseX, float mouseY) {
        float scale = getPanelScale(screenW, screenH);
        float slotSize = SLOT_INNER * scale;
        boolean hasCursor = cursorItem != null && !cursorItem.isNone() && cursorCount > 0;
        if (hasCursor && cursorCount > 1) {
            int clamped = Math.max(0, Math.min(NUMBER_LABELS.length - 1, cursorCount));
            String countStr = NUMBER_LABELS[clamped];
            float countPxH = Math.max(14.0f, scale * 6.2f);
            float boxX = mouseX - slotSize * 0.5f;
            float boxY = mouseY - slotSize * 0.5f;
            float tw = font.measureWidth(countStr, countPxH);
            font.drawTextWithShadow(shader, countStr,
                    boxX + slotSize - tw - 1.5f, boxY + slotSize - countPxH - 1.0f,
                    countPxH, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        if (!hasCursor) {
            int hoveredCatalog = getCatalogSlotAt(mouseX, mouseY, screenW, screenH);
            int hoveredHotbar = hoveredCatalog < 0 ? getHotbarSlotAt(mouseX, mouseY, screenW, screenH) : -1;
            boolean hoveredTrash = hoveredCatalog < 0 && hoveredHotbar < 0
                    && isTrashSlotAt(mouseX, mouseY, screenW, screenH);

            Item hoveredItem = Item.NONE;
            if (hoveredCatalog >= 0) {
                hoveredItem = CreativeCatalog.getItem(activeTab, scrollRow * COLS + hoveredCatalog);
            } else if (hoveredHotbar >= 0 && inv.getCount(hoveredHotbar) > 0) {
                hoveredItem = inv.getItem(hoveredHotbar);
            }

            String tooltipLabel = null;
            if (hoveredItem != null && !hoveredItem.isNone()) {
                tooltipLabel = hoveredItem.getDisplayName();
            } else if (hoveredTrash) {
                tooltipLabel = "Clear / Shift: Clear All";
            }

            if (tooltipLabel != null) {
                float tipPxH = 19.0f;
                float textW = font.measureWidth(tooltipLabel, tipPxH);
                float padX = 10.0f;
                float padY = 6.0f;
                float tipW = textW + padX * 2.0f;
                float tipH = tipPxH + padY * 2.0f;
                float tipX = Math.max(4.0f, Math.min(screenW - tipW - 4.0f, mouseX + 14.0f));
                float tipY = Math.max(4.0f, Math.min(screenH - tipH - 4.0f, mouseY - tipH - 8.0f));

                drawSolidQuad(shader, tipX - 2f, tipY - 2f, tipW + 4f, tipH + 4f, 0.45f, 0.48f, 0.78f, 0.96f);
                drawSolidQuad(shader, tipX, tipY, tipW, tipH, 0.08f, 0.09f, 0.14f, 0.96f);
                font.drawTextWithShadow(shader, tooltipLabel, tipX + padX, tipY + padY, tipPxH, 1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }

    /** Vẽ hình chữ nhật đồng nhất ngắn gọn cho khung tooltip VT323. */
    private void drawSolidQuad(ShaderProgram shader, float x, float y, float w, float h,
                               float r, float g, float b, float a) {
        if (overlayMesh == null) {
            overlayMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, false,
                    OVERLAY_VERTEX_CAPACITY, 0, VERTEX_ATTRIBUTES);
        }
        BitmapFont.writeQuad(overlayVertices, 0, x, y + h, 0f, x + w, y + h, 0f,
                x + w, y, 0f, x, y, 0f, 0f, 0f, 1f, 1f);
        overlayMesh.setVertices(overlayVertices, 0, 36);
        shader.setUniform1i("uUseTexture", 0);
        shader.setUniformVec4("uTintColor", r, g, b, a);
        overlayMesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 0, 6);
    }

    private boolean needsGridRebuild(PlayerInventory inv, int screenW, int screenH) {
        if (dirty || gridMesh == null || screenW != cachedScreenW || screenH != cachedScreenH
                || activeTab != cachedTab || scrollRow != cachedScrollRow) {
            return true;
        }
        for (int i = 0; i < PlayerInventory.HOTBAR_SLOTS; i++) {
            if (inv.getItem(i) != cachedHotbarItems[i] || inv.getCount(i) != cachedHotbarCounts[i]) {
                return true;
            }
        }
        return false;
    }

    private void syncCache(PlayerInventory inv, int screenW, int screenH) {
        cachedScreenW = screenW;
        cachedScreenH = screenH;
        cachedTab = activeTab;
        cachedScrollRow = scrollRow;
        for (int i = 0; i < PlayerInventory.HOTBAR_SLOTS; i++) {
            cachedHotbarItems[i] = inv.getItem(i);
            cachedHotbarCounts[i] = inv.getCount(i);
        }
        dirty = false;
    }

    /** Dựng lưới tĩnh: nền tối, khung panel, 5 tab, 45 ô danh mục, thanh cuộn, 9 ô Hotbar và Thùng rác. */
    private void rebuildGridMesh(PlayerInventory inv, boolean bakeLegacyText, int screenW, int screenH) {
        gridFloatLength = 0;
        gridPassCount = 0;

        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float panelW = PANEL_W * scale;
        float panelH = PANEL_H * scale;
        float slotSize = SLOT_INNER * scale;

        // 1. Lớp phủ tối nền sau toàn màn hình
        int pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.0f, 0.0f, 0.0f, 0.56f);
        gridFloatLength = appendRect(gridVertices, gridFloatLength, 0f, 0f, screenW, screenH);
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // 2. Viền ngoài sáng + nền chính panel Creative
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.72f, 0.74f, 0.78f, 0.98f);
        gridFloatLength = appendRect(gridVertices, gridFloatLength,
                panelX - 2f * scale, panelY - 2f * scale, panelW + 4f * scale, panelH + 4f * scale);
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.78f, 0.78f, 0.78f, 0.98f);
        gridFloatLength = appendRect(gridVertices, gridFloatLength, panelX, panelY, panelW, panelH);
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // 3. Nền các Tab không chọn & Tab đang chọn
        float tabW = ((PANEL_W - (TABS.length - 1) * TAB_GAP) / TABS.length) * scale;
        float tabH = TAB_BAR_H * scale;
        float tabY = panelY + 4.0f * scale;

        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.45f, 0.45f, 0.48f, 0.98f);
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i] != activeTab) {
                float tx = panelX + i * (tabW + TAB_GAP * scale);
                gridFloatLength = appendRect(gridVertices, gridFloatLength, tx, tabY, tabW, tabH);
            }
        }
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.24f, 0.46f, 0.78f, 1.0f);
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i] == activeTab) {
                float tx = panelX + i * (tabW + TAB_GAP * scale);
                gridFloatLength = appendRect(gridVertices, gridFloatLength, tx, tabY, tabW, tabH);
            }
        }
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // 4. Nền 45 ô danh mục + 9 ô Hotbar + rãnh thanh cuộn
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.54f, 0.54f, 0.54f, 1.0f);
        for (int r = 0; r < ROWS; r++) {
            float sy = panelY + (GRID_Y + r * SLOT_PITCH) * scale;
            for (int c = 0; c < COLS; c++) {
                float sx = panelX + (GRID_X + c * SLOT_PITCH) * scale;
                gridFloatLength = appendRect(gridVertices, gridFloatLength, sx, sy, slotSize, slotSize);
            }
        }
        float hy = panelY + HOTBAR_Y * scale;
        for (int c = 0; c < PlayerInventory.HOTBAR_SLOTS; c++) {
            float hx = panelX + (HOTBAR_X + c * SLOT_PITCH) * scale;
            gridFloatLength = appendRect(gridVertices, gridFloatLength, hx, hy, slotSize, slotSize);
        }
        float scrollX = panelX + SCROLL_X * scale;
        float scrollW = SCROLL_W * scale;
        float scrollTop = panelY + GRID_Y * scale;
        float scrollTotalH = ((ROWS - 1) * SLOT_PITCH + SLOT_INNER) * scale;
        gridFloatLength = appendRect(gridVertices, gridFloatLength, scrollX, scrollTop, scrollW, scrollTotalH);
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // 5. Nút cuộn ▲ / ▼ và con chạy thanh cuộn
        float btnH = SCROLL_BTN_H * scale;
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.34f, 0.36f, 0.42f, 1.0f);
        gridFloatLength = appendRect(gridVertices, gridFloatLength, scrollX, scrollTop, scrollW, btnH);
        gridFloatLength = appendRect(gridVertices, gridFloatLength,
                scrollX, scrollTop + scrollTotalH - btnH, scrollW, btnH);
        int maxScroll = getMaxScrollRow();
        float trackY = scrollTop + btnH + 2f * scale;
        float trackH = Math.max(8f * scale, scrollTotalH - 2f * btnH - 4f * scale);
        float thumbH = maxScroll <= 0 ? trackH : Math.max(10f * scale, trackH / (maxScroll + 1));
        float thumbY = maxScroll <= 0 ? trackY : trackY + (trackH - thumbH) * ((float) scrollRow / maxScroll);
        gridFloatLength = appendRect(gridVertices, gridFloatLength,
                scrollX + 2f * scale, thumbY, scrollW - 4f * scale, thumbH);
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // 6. Ô Thùng rác [X] màu đỏ trầm
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_NONE, 0.72f, 0.24f, 0.24f, 1.0f);
        gridFloatLength = appendRect(gridVertices, gridFloatLength,
                panelX + TRASH_X * scale, panelY + TRASH_Y * scale, slotSize, slotSize);
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // 7. Nhãn chữ trên 5 Tab, mũi tên +/- trên nút cuộn và chữ X trên Thùng rác (chỉ khi fallback 5×7)
        if (bakeLegacyText) {
            float tabTextScale = Math.max(1.0f, scale * 0.45f);
            pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                    gridFloatLength, TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
            for (int i = 0; i < TABS.length; i++) {
                String label = TABS[i].getLabel();
                float tx = panelX + i * (tabW + TAB_GAP * scale);
                float lw = BitmapFont.measureWidth(label, tabTextScale);
                float lh = BitmapFont.measureHeight(tabTextScale);
                gridFloatLength = BitmapFont.appendTextVertices(gridVertices, gridFloatLength, label,
                        tx + (tabW - lw) * 0.5f, tabY + (tabH - lh) * 0.5f, tabTextScale, gridVertices.length);
            }
            float btnTextScale = Math.max(1.0f, scale * 0.50f);
            float minusW = BitmapFont.measureWidth("-", btnTextScale);
            float plusW = BitmapFont.measureWidth("+", btnTextScale);
            float xW = BitmapFont.measureWidth("X", btnTextScale);
            float btnTextH = BitmapFont.measureHeight(btnTextScale);
            gridFloatLength = BitmapFont.appendTextVertices(gridVertices, gridFloatLength, "-",
                    scrollX + (scrollW - minusW) * 0.5f, scrollTop + (btnH - btnTextH) * 0.5f,
                    btnTextScale, gridVertices.length);
            gridFloatLength = BitmapFont.appendTextVertices(gridVertices, gridFloatLength, "+",
                    scrollX + (scrollW - plusW) * 0.5f, scrollTop + scrollTotalH - btnH + (btnH - btnTextH) * 0.5f,
                    btnTextScale, gridVertices.length);
            gridFloatLength = BitmapFont.appendTextVertices(gridVertices, gridFloatLength, "X",
                    panelX + TRASH_X * scale + (slotSize - xW) * 0.5f,
                    panelY + TRASH_Y * scale + (slotSize - btnTextH) * 0.5f,
                    btnTextScale, gridVertices.length);
            endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);
        }

        // 8. Vẽ biểu tượng khối (Block Atlas) cho cả lưới danh mục & 9 ô Hotbar
        Item[] catalog = CreativeCatalog.getItems(activeTab);
        int baseIdx = scrollRow * COLS;

        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_BLOCK_ATLAS, 1f, 1f, 1f, 1f);
        for (int i = 0; i < VISIBLE_CATALOG_SLOTS; i++) {
            int idx = baseIdx + i;
            if (idx >= catalog.length) {
                break;
            }
            Item item = catalog[idx];
            if (item != null && !item.isNone() && item.isBlock()) {
                float sx = panelX + (GRID_X + (i % COLS) * SLOT_PITCH) * scale;
                float sy = panelY + (GRID_Y + (i / COLS) * SLOT_PITCH) * scale;
                gridFloatLength = appendItemIcon(gridVertices, gridFloatLength, item, sx, sy, slotSize);
            }
        }
        for (int s = 0; s < PlayerInventory.HOTBAR_SLOTS; s++) {
            Item item = inv.getItem(s);
            if (item != null && !item.isNone() && inv.getCount(s) > 0 && item.isBlock()) {
                float hx = panelX + (HOTBAR_X + s * SLOT_PITCH) * scale;
                gridFloatLength = appendItemIcon(gridVertices, gridFloatLength, item, hx, hy, slotSize);
            }
        }
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // 9. Vẽ biểu tượng vật phẩm độc lập (Item Atlas) cho cả lưới danh mục & 9 ô Hotbar
        pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                gridFloatLength, TEXTURE_ITEM_ATLAS, 1f, 1f, 1f, 1f);
        for (int i = 0; i < VISIBLE_CATALOG_SLOTS; i++) {
            int idx = baseIdx + i;
            if (idx >= catalog.length) {
                break;
            }
            Item item = catalog[idx];
            if (item != null && !item.isNone() && !item.isBlock()) {
                float sx = panelX + (GRID_X + (i % COLS) * SLOT_PITCH) * scale;
                float sy = panelY + (GRID_Y + (i / COLS) * SLOT_PITCH) * scale;
                gridFloatLength = appendItemIcon(gridVertices, gridFloatLength, item, sx, sy, slotSize);
            }
        }
        for (int s = 0; s < PlayerInventory.HOTBAR_SLOTS; s++) {
            Item item = inv.getItem(s);
            if (item != null && !item.isNone() && inv.getCount(s) > 0 && !item.isBlock()) {
                float hx = panelX + (HOTBAR_X + s * SLOT_PITCH) * scale;
                gridFloatLength = appendItemIcon(gridVertices, gridFloatLength, item, hx, hy, slotSize);
            }
        }
        endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

        // 10. Số lượng stack trên 9 ô Hotbar (chỉ khi fallback 5×7)
        if (bakeLegacyText) {
            float countScale = Math.max(1.2f, scale * 0.55f);
            pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                    gridFloatLength, TEXTURE_NONE, 0.05f, 0.05f, 0.05f, 0.95f);
            for (int s = 0; s < PlayerInventory.HOTBAR_SLOTS; s++) {
                int count = inv.getCount(s);
                if (count > 1 && !inv.getItem(s).isNone()) {
                    float hx = panelX + (HOTBAR_X + s * SLOT_PITCH) * scale;
                    gridFloatLength = appendSlotCountText(gridVertices, gridFloatLength, count,
                            hx, hy, slotSize, countScale, 1.5f, gridVertices.length);
                }
            }
            endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);

            pass = beginPass(gridPassStart, gridPassTexSource, gridPassTints, gridPassCount++,
                    gridFloatLength, TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
            for (int s = 0; s < PlayerInventory.HOTBAR_SLOTS; s++) {
                int count = inv.getCount(s);
                if (count > 1 && !inv.getItem(s).isNone()) {
                    float hx = panelX + (HOTBAR_X + s * SLOT_PITCH) * scale;
                    gridFloatLength = appendSlotCountText(gridVertices, gridFloatLength, count,
                            hx, hy, slotSize, countScale, 0f, gridVertices.length);
                }
            }
            endPass(gridPassStart, gridPassCountVerts, pass, gridFloatLength);
        }

        if (gridMesh == null) {
            gridMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, false,
                    GRID_VERTEX_CAPACITY, 0, VERTEX_ATTRIBUTES);
        }
        gridMesh.setVertices(gridVertices, 0, gridFloatLength);
    }

    /** Dựng lớp phủ động gồm ô sáng khi hover, vật phẩm trên con trỏ và tooltip tên vật phẩm. */
    private void rebuildOverlayMesh(PlayerInventory inv, boolean bakeLegacyText, int screenW, int screenH,
                                    float mouseX, float mouseY) {
        overlayFloatLength = 0;
        overlayPassCount = 0;

        float scale = getPanelScale(screenW, screenH);
        float panelX = getPanelX(screenW, scale);
        float panelY = getPanelY(screenH, scale);
        float slotSize = SLOT_INNER * scale;

        int hoveredCatalog = getCatalogSlotAt(mouseX, mouseY, screenW, screenH);
        int hoveredHotbar = hoveredCatalog < 0 ? getHotbarSlotAt(mouseX, mouseY, screenW, screenH) : -1;
        boolean hoveredTrash = hoveredCatalog < 0 && hoveredHotbar < 0
                && isTrashSlotAt(mouseX, mouseY, screenW, screenH);

        boolean hasCursor = cursorItem != null && !cursorItem.isNone() && cursorCount > 0;
        Item hoveredItem = Item.NONE;
        if (hoveredCatalog >= 0) {
            hoveredItem = CreativeCatalog.getItem(activeTab, scrollRow * COLS + hoveredCatalog);
        } else if (hoveredHotbar >= 0 && inv.getCount(hoveredHotbar) > 0) {
            hoveredItem = inv.getItem(hoveredHotbar);
        }

        String tooltipLabel = null;
        if (!hasCursor) {
            if (hoveredItem != null && !hoveredItem.isNone()) {
                tooltipLabel = hoveredItem.getDisplayName();
            } else if (hoveredTrash) {
                tooltipLabel = "CLEAR / SHIFT: CLEAR ALL";
            }
        }

        if (hoveredCatalog < 0 && hoveredHotbar < 0 && !hoveredTrash && !hasCursor) {
            return;
        }

        // Ô sáng hover
        if (hoveredCatalog >= 0 || hoveredHotbar >= 0 || hoveredTrash) {
            float hx;
            float hy;
            if (hoveredCatalog >= 0) {
                hx = panelX + (GRID_X + (hoveredCatalog % COLS) * SLOT_PITCH) * scale;
                hy = panelY + (GRID_Y + (hoveredCatalog / COLS) * SLOT_PITCH) * scale;
            } else if (hoveredHotbar >= 0) {
                hx = panelX + (HOTBAR_X + hoveredHotbar * SLOT_PITCH) * scale;
                hy = panelY + HOTBAR_Y * scale;
            } else {
                hx = panelX + TRASH_X * scale;
                hy = panelY + TRASH_Y * scale;
            }
            int pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 0.38f);
            overlayFloatLength = appendRect(overlayVertices, overlayFloatLength, hx, hy, slotSize, slotSize);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);
        }

        // Vật phẩm đang bám theo con trỏ chuột
        if (hasCursor) {
            byte texSource = cursorItem.isBlock() ? TEXTURE_BLOCK_ATLAS : TEXTURE_ITEM_ATLAS;
            int pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, texSource, 1f, 1f, 1f, 1f);
            float boxX = mouseX - slotSize * 0.5f;
            float boxY = mouseY - slotSize * 0.5f;
            overlayFloatLength = appendItemIcon(overlayVertices, overlayFloatLength,
                    cursorItem, boxX, boxY, slotSize);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);

            if (bakeLegacyText && cursorCount > 1) {
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

        // Tooltip tên vật phẩm khi rê chuột (chỉ khi fallback 5×7)
        if (bakeLegacyText && tooltipLabel != null) {
            float tipScale = 1.6f;
            float textW = BitmapFont.measureWidth(tooltipLabel, tipScale);
            float textH = BitmapFont.measureHeight(tipScale);
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
            overlayFloatLength = BitmapFont.appendTextVertices(overlayVertices, overlayFloatLength, tooltipLabel,
                    tipX + padX + 1.5f, tipY + padY + 1.5f, tipScale, overlayVertices.length);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);

            pass = beginPass(overlayPassStart, overlayPassTexSource, overlayPassTints, overlayPassCount++,
                    overlayFloatLength, TEXTURE_NONE, 1.0f, 1.0f, 1.0f, 1.0f);
            overlayFloatLength = BitmapFont.appendTextVertices(overlayVertices, overlayFloatLength, tooltipLabel,
                    tipX + padX, tipY + padY, tipScale, overlayVertices.length);
            endPass(overlayPassStart, overlayPassCountVerts, pass, overlayFloatLength);
        }

        if (overlayMesh == null) {
            overlayMesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, false,
                    OVERLAY_VERTEX_CAPACITY, 0, VERTEX_ATTRIBUTES);
        }
        overlayMesh.setVertices(overlayVertices, 0, overlayFloatLength);
    }

    /** Vẽ lần lượt các lượt pass trong Mesh, giảm thiểu chuyển đổi texture. */
    private void renderMeshPasses(ShaderProgram shader, Mesh mesh, int passCount,
                                  int[] passStart, int[] passVertCount, byte[] passTexSource,
                                  float[] passTints, Texture blockAtlas, ItemTextureAtlas itemAtlas) {
        com.badlogic.gdx.graphics.glutils.ShaderProgram program = shader.getGdxShader();
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
            }
            int tint = i * 4;
            boolean useTexture = (source == TEXTURE_BLOCK_ATLAS && blockAtlas != null)
                    || (source == TEXTURE_ITEM_ATLAS && itemAtlas != null);
            shader.setUniform1i("uUseTexture", useTexture ? 1 : 0);
            shader.setUniformVec4("uTintColor", passTints[tint], passTints[tint + 1],
                    passTints[tint + 2], passTints[tint + 3]);
            mesh.render(program, GL20.GL_TRIANGLES, passStart[i], vertCount, false);
        }
        mesh.unbind(program);
        if (boundSource == TEXTURE_ITEM_ATLAS && itemAtlas != null) {
            itemAtlas.unbind();
        } else if (boundSource == TEXTURE_BLOCK_ATLAS && blockAtlas != null) {
            blockAtlas.unbind();
        }
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

    private int appendSlotCountText(float[] buffer, int offset, int count,
                                    float slotX, float slotY, float slotSize,
                                    float textScale, float shadowOffset, int capacity) {
        int clamped = Math.max(0, Math.min(NUMBER_LABELS.length - 1, count));
        String countStr = NUMBER_LABELS[clamped];
        float textW = BitmapFont.measureWidth(countStr, textScale);
        float tx = slotX + slotSize - textW - 1.0f + shadowOffset;
        float ty = slotY + slotSize - BitmapFont.measureHeight(textScale) - 1.0f + shadowOffset;
        return BitmapFont.appendTextVertices(buffer, offset, countStr, tx, ty, textScale, capacity);
    }

    private int appendRect(float[] buffer, int offset, float x, float y, float w, float h) {
        return appendTexturedRect(buffer, offset, x, y, w, h, 0f, 0f, 1f, 1f);
    }

    private int appendTexturedRect(float[] buffer, int offset, float x, float y, float w, float h,
                                   float u0, float v0, float u1, float v1) {
        if (offset + 36 > buffer.length) {
            return offset;
        }
        BitmapFont.writeQuad(buffer, offset, x, y + h, 0f, x + w, y + h, 0f,
                x + w, y, 0f, x, y, 0f, u0, v0, u1, v1);
        return offset + 36;
    }

    /** Giải phóng toàn bộ tài nguyên GPU ({@link Mesh}) theo giao ước {@link com.badlogic.gdx.utils.Disposable}. */
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
    }
}
