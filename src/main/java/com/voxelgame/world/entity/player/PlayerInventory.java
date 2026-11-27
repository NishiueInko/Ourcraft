package com.voxelgame.world.entity.player;

import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;

/**
 * Quản lý túi đồ đầy đủ 36 ô của {@link Player}:
 * - Các ô {@code 0..8} liên kết trực tiếp với thanh {@link Hotbar} 9 ô.
 * - Các ô {@code 9..35} là 27 ô balo mở rộng (3 hàng × 9 cột).
 *
 * Bảo toàn 100% tương thích ngược với các hệ thống chỉ gọi {@code player.getHotbar()}.
 */
public class PlayerInventory {

    /** Số ô thuộc thanh công cụ nhanh Hotbar (chỉ số 0..8). */
    public static final int HOTBAR_SLOTS = Hotbar.SLOT_COUNT;
    /** Số ô thuộc phần balo mở rộng (chỉ số 9..35). */
    public static final int BACKPACK_SLOTS = 27;
    /** Tổng số ô của toàn bộ túi đồ người chơi (36 ô). */
    public static final int TOTAL_SLOTS = HOTBAR_SLOTS + BACKPACK_SLOTS;

    private final Hotbar hotbar;
    private final Item[] backpackItems = new Item[BACKPACK_SLOTS];
    private final int[] backpackCounts = new int[BACKPACK_SLOTS];

    /**
     * Khởi tạo túi đồ 36 ô cùng một {@link Hotbar} mới gắn kèm.
     */
    public PlayerInventory() {
        this(new Hotbar());
    }

    /**
     * Khởi tạo túi đồ 36 ô bao bọc một {@link Hotbar} có sẵn cho 9 ô đầu tiên.
     *
     * @param hotbar thanh công cụ 9 ô cần liên kết
     */
    public PlayerInventory(Hotbar hotbar) {
        this.hotbar = (hotbar != null) ? hotbar : new Hotbar();
        for (int i = 0; i < BACKPACK_SLOTS; i++) {
            backpackItems[i] = Item.NONE;
            backpackCounts[i] = 0;
        }
        this.hotbar.attachOwner(this);
    }

    /**
     * Trả về thanh {@link Hotbar} 9 ô đang chia sẻ dữ liệu với các ô {@code 0..8}.
     *
     * @return đối tượng Hotbar của túi đồ
     */
    public Hotbar getHotbar() {
        return hotbar;
    }

    /**
     * Đọc loại vật phẩm tại một ô bất kỳ trong 36 ô túi đồ.
     *
     * @param slot chỉ số ô (0..35)
     * @return {@link Item} tại ô đó, hoặc {@link Item#NONE} nếu trống / ngoài phạm vi
     */
    public Item getItem(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOTS) {
            return Item.NONE;
        }
        if (slot < HOTBAR_SLOTS) {
            return hotbar.getSlotItem(slot);
        }
        return backpackItems[slot - HOTBAR_SLOTS];
    }

    /**
     * Đọc số lượng vật phẩm tại một ô bất kỳ trong 36 ô túi đồ.
     *
     * @param slot chỉ số ô (0..35)
     * @return số lượng hiện có, hoặc 0 nếu trống / ngoài phạm vi
     */
    public int getCount(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOTS) {
            return 0;
        }
        if (slot < HOTBAR_SLOTS) {
            return hotbar.getSlotCount(slot);
        }
        return backpackCounts[slot - HOTBAR_SLOTS];
    }

    /**
     * Thiết lập trực tiếp loại {@link Block} và số lượng cho một ô trong 36 ô túi đồ.
     *
     * @param slot chỉ số ô (0..35)
     * @param block loại khối cần đặt
     * @param count số lượng cần đặt
     */
    public void setSlot(int slot, Block block, int count) {
        setSlot(slot, Item.of(block), count);
    }

    /**
     * Thiết lập trực tiếp {@link Item} và số lượng cho một ô trong 36 ô túi đồ.
     *
     * @param slot chỉ số ô (0..35)
     * @param item vật phẩm cần đặt
     * @param count số lượng (tự giới hạn theo {@code maxStackSize})
     */
    public void setSlot(int slot, Item item, int count) {
        if (slot < 0 || slot >= TOTAL_SLOTS) {
            return;
        }
        if (slot < HOTBAR_SLOTS) {
            hotbar.setSlot(slot, item, count);
            return;
        }
        int backpackIndex = slot - HOTBAR_SLOTS;
        if (item == null || item.isNone() || count <= 0) {
            backpackItems[backpackIndex] = Item.NONE;
            backpackCounts[backpackIndex] = 0;
        } else {
            backpackItems[backpackIndex] = item;
            backpackCounts[backpackIndex] = Math.min(item.getMaxStackSize(), count);
        }
    }

    /**
     * Thêm một lượng {@link Block} vào túi đồ 36 ô (ưu tiên Hotbar trước rồi tới 27 ô balo).
     *
     * @param block loại khối cần thêm
     * @param amount số lượng cần thêm
     * @return true nếu thêm được ít nhất 1 đơn vị
     */
    public boolean addItem(Block block, int amount) {
        return addItem(Item.of(block), amount);
    }

    /**
     * Thêm một lượng {@link Item} vào túi đồ 36 ô:
     * ưu tiên gộp vào các ô cùng loại chưa đầy stack, sau đó đặt vào ô trống đầu tiên.
     *
     * @param item vật phẩm cần thêm
     * @param amount số lượng cần thêm
     * @return true nếu thêm được ít nhất 1 đơn vị
     */
    public boolean addItem(Item item, int amount) {
        return addItemUpTo(item, amount) > 0;
    }

    /**
     * Thêm tối đa {@code amount} đơn vị {@link Item} vào túi đồ 36 ô và trả về số lượng thực tế đã nhận.
     * Giúp {@link ItemEntity} xử lý nhặt từng phần khi túi gần đầy mà không làm mất vật phẩm dư.
     *
     * @param item vật phẩm cần thêm
     * @param amount số lượng mong muốn thêm
     * @return số đơn vị đã được nạp vào túi (0 nếu túi không còn chỗ)
     */
    public int addItemUpTo(Item item, int amount) {
        if (item == null || item.isNone() || amount <= 0) {
            return 0;
        }

        int maxStack = item.getMaxStackSize();
        if (maxStack <= 0) {
            return 0;
        }

        int remaining = amount;

        // Bước 1: Cộng dồn vào các ô cùng loại chưa đầy giới hạn stack (từ ô 0..35)
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (getItem(i) == item) {
                int current = getCount(i);
                if (current < maxStack) {
                    int spaceLeft = maxStack - current;
                    int toAdd = Math.min(spaceLeft, remaining);
                    setSlot(i, item, current + toAdd);
                    remaining -= toAdd;
                    if (remaining == 0) {
                        return amount;
                    }
                }
            }
        }

        // Bước 2: Đặt phần còn lại vào các ô trống từ trái sang phải (0..8 Hotbar rồi 9..35 balo)
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (getItem(i).isNone() || getCount(i) <= 0) {
                int toAdd = Math.min(maxStack, remaining);
                setSlot(i, item, toAdd);
                remaining -= toAdd;
                if (remaining == 0) {
                    return amount;
                }
            }
        }

        return amount - remaining;
    }

    /**
     * Tiêu thụ tối đa {@code amount} đơn vị vật phẩm tại ô chỉ định.
     *
     * @param slot chỉ số ô (0..35)
     * @param amount số lượng cần lấy ra
     * @return số đơn vị thực tế đã tiêu thụ (0 nếu ô trống hoặc không hợp lệ)
     */
    public int consumeFromSlot(int slot, int amount) {
        if (slot < 0 || slot >= TOTAL_SLOTS || amount <= 0) {
            return 0;
        }
        Item current = getItem(slot);
        int currentCount = getCount(slot);
        if (current.isNone() || currentCount <= 0) {
            return 0;
        }
        int taken = Math.min(amount, currentCount);
        int remaining = currentCount - taken;
        setSlot(slot, remaining > 0 ? current : Item.NONE, remaining);
        return taken;
    }
}
