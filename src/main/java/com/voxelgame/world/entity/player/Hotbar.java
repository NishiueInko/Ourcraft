package com.voxelgame.world.entity.player;

import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;

/**
 * Quản lý hệ thống 9 ô Thanh Công Cụ Nhanh (Hotbar HUD) thuộc sở hữu của {@link Player} (Composition).
 * Hỗ trợ đồng thời cả khối ({@link Block}) lẫn vật phẩm độc lập ({@link Item}) với đầy đủ giới hạn cộng dồn.
 */
public class Hotbar {

    /** Tổng số ô trên thanh Hotbar. */
    public static final int SLOT_COUNT = 9;
    /** Số lượng cộng dồn tối đa mặc định trong 1 ô (64 items/stack chuẩn Minecraft). */
    public static final int MAX_STACK_SIZE = 64;

    private final Item[] slotItems = new Item[SLOT_COUNT];
    private final int[] slotCounts = new int[SLOT_COUNT];
    /** Túi đồ 36 ô sở hữu Hotbar này (null nếu Hotbar được tạo độc lập trong test cũ). */
    private PlayerInventory owner;

    /** Chỉ số ô đang được người chơi chọn hiện tại (0..8, tương ứng phím 1..9). */
    private int selectedSlot = 0;

    public Hotbar() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slotItems[i] = Item.NONE;
            slotCounts[i] = 0;
        }

        // Trang bị sẵn 5 loại Block cơ bản ở 5 ô đầu (giữ nguyên hành vi cũ)
        setSlot(0, Block.DIRT, 16);
        setSlot(1, Block.GRASS, 16);
        setSlot(2, Block.STONE, 16);
        setSlot(3, Block.OAK_LOG, 12);
        setSlot(4, Block.OAK_LEAVES, 16);
        // Ô 6 (chỉ số 5) để trống; các ô 7–9 trang bị công cụ tương tác mob
        setSlot(6, Item.SHEARS, 1);
        setSlot(7, Item.FLINT_AND_STEEL, 1);
        setSlot(8, Item.BUCKET, 1);
    }

    /**
     * Thiết lập trực tiếp loại Block và số lượng cho một ô cụ thể.
     *
     * @param slotIndex chỉ số ô (0..8)
     * @param type loại Block
     * @param count số lượng (tối đa 64)
     */
    public void setSlot(int slotIndex, Block type, int count) {
        setSlot(slotIndex, Item.of(type), count);
    }

    /**
     * Thiết lập trực tiếp vật phẩm {@link Item} và số lượng cho một ô cụ thể.
     *
     * @param slotIndex chỉ số ô (0..8)
     * @param item vật phẩm cần đặt
     * @param count số lượng (giới hạn theo maxStackSize của vật phẩm)
     */
    public void setSlot(int slotIndex, Item item, int count) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return;
        }
        if (item == null || item.isNone() || count <= 0) {
            slotItems[slotIndex] = Item.NONE;
            slotCounts[slotIndex] = 0;
        } else {
            slotItems[slotIndex] = item;
            slotCounts[slotIndex] = Math.min(item.getMaxStackSize(), count);
        }
    }

    /**
     * Chọn ô trực tiếp theo chỉ số (0 đến 8, tương ứng khi bấm phím 1 đến 9).
     *
     * @param slotIndex chỉ số ô cần chọn (0..8)
     */
    public void selectSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < SLOT_COUNT) {
            this.selectedSlot = slotIndex;
        }
    }

    /**
     * Chuyển đổi ô đang chọn khi người chơi cuộn con lăn chuột (Mouse Scroll Callback).
     *
     * @param scrollYOffset lượng cuộn trục Y (dương: cuộn lên, âm: cuộn xuống)
     */
    public void scroll(double scrollYOffset) {
        if (Math.abs(scrollYOffset) < 0.01) {
            return;
        }
        int step = (scrollYOffset > 0) ? -1 : 1;
        this.selectedSlot = (this.selectedSlot + step + SLOT_COUNT) % SLOT_COUNT;
    }

    /**
     * Thêm một số lượng Block vừa nhặt được vào thanh Hotbar.
     *
     * @param type loại Block nhặt được
     * @param amount số lượng cần thêm
     * @return true nếu đã thêm được ít nhất 1 item vào Hotbar
     */
    public boolean addItem(Block type, int amount) {
        return addItem(Item.of(type), amount);
    }

    /**
     * Gắn {@link PlayerInventory} sở hữu để {@link #addItem} tự tràn sang 27 ô balo khi Hotbar đầy.
     *
     * @param owner túi đồ 36 ô chứa Hotbar này
     */
    void attachOwner(PlayerInventory owner) {
        this.owner = owner;
    }

    /**
     * Thêm một số lượng vật phẩm {@link Item} vừa nhặt được.
     * Nếu Hotbar thuộc về {@link PlayerInventory}, tự động chuyển tiếp qua toàn bộ 36 ô;
     * nếu đứng độc lập (như trong các test đơn vị), chỉ xử lý trong 9 ô Hotbar.
     *
     * @param item vật phẩm nhặt được
     * @param amount số lượng cần thêm
     * @return true nếu đã thêm được ít nhất 1 vật phẩm
     */
    public boolean addItem(Item item, int amount) {
        return addItemUpTo(item, amount) > 0;
    }

    /**
     * Thêm tối đa {@code amount} đơn vị {@link Item} và trả về số lượng thực tế đã thêm.
     *
     * @param item vật phẩm cần thêm
     * @param amount số lượng mong muốn
     * @return số đơn vị đã nạp được (0 nếu không còn chỗ)
     */
    public int addItemUpTo(Item item, int amount) {
        if (owner != null) {
            return owner.addItemUpTo(item, amount);
        }
        return addLocalItemUpTo(item, amount);
    }

    /** Thực thi thêm vật phẩm nội bộ trong phạm vi 9 ô khi Hotbar đứng độc lập. */
    private int addLocalItemUpTo(Item item, int amount) {
        if (item == null || item.isNone() || amount <= 0) {
            return 0;
        }

        int maxStack = item.getMaxStackSize();
        if (maxStack <= 0) {
            return 0;
        }
        int remaining = amount;

        // Bước 1: Ưu tiên cộng dồn vào các ô đang chứa sẵn cùng loại vật phẩm và chưa đầy stack
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slotItems[i] == item && slotCounts[i] < maxStack) {
                int spaceLeft = maxStack - slotCounts[i];
                int toAdd = Math.min(spaceLeft, remaining);
                slotCounts[i] += toAdd;
                remaining -= toAdd;
                if (remaining == 0) {
                    return amount;
                }
            }
        }

        // Bước 2: Nếu vẫn còn dư, đặt vào ô trống đầu tiên từ trái sang phải
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slotItems[i].isNone() || slotCounts[i] <= 0) {
                slotItems[i] = item;
                int toAdd = Math.min(maxStack, remaining);
                slotCounts[i] = toAdd;
                remaining -= toAdd;
                if (remaining == 0) {
                    return amount;
                }
            }
        }

        return amount - remaining;
    }

    /**
     * Tiêu thụ 1 Block ở ô đang chọn khi người chơi click chuột phải để đặt Block.
     *
     * @return loại Block vừa tiêu thụ, hoặc Block.AIR nếu ô trống hoặc đang cầm vật phẩm không phải Block
     */
    public Block consumeSelectedItem() {
        Item selected = slotItems[selectedSlot];
        if (!selected.isBlock() || slotCounts[selectedSlot] <= 0) {
            return Block.AIR;
        }
        consumeSelectedItemStack();
        return selected.getBlock();
    }

    /**
     * Tiêu thụ 1 đơn vị vật phẩm bất kỳ ở ô đang chọn (dùng cho cả Block lẫn Item độc lập).
     *
     * @return {@link Item} vừa tiêu thụ, hoặc {@link Item#NONE} nếu ô trống
     */
    public Item consumeSelectedItemStack() {
        Item current = slotItems[selectedSlot];
        int currentCount = slotCounts[selectedSlot];

        if (current.isNone() || currentCount <= 0) {
            return Item.NONE;
        }

        slotCounts[selectedSlot] = currentCount - 1;
        if (slotCounts[selectedSlot] <= 0) {
            slotCounts[selectedSlot] = 0;
            slotItems[selectedSlot] = Item.NONE;
        }

        return current;
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public Item getSelectedItem() {
        return slotItems[selectedSlot];
    }

    public Block getSelectedBlock() {
        return slotItems[selectedSlot].getBlock();
    }

    public Block getSelectedBlockType() {
        return getSelectedBlock();
    }

    public int getSelectedCount() {
        return slotCounts[selectedSlot];
    }

    public Item getSlotItem(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return Item.NONE;
        }
        return slotItems[slotIndex];
    }

    public Block getSlotBlock(int slotIndex) {
        return getSlotItem(slotIndex).getBlock();
    }

    public Block getSlotType(int slotIndex) {
        return getSlotBlock(slotIndex);
    }

    public int getSlotCount(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return 0;
        }
        return slotCounts[slotIndex];
    }
}
