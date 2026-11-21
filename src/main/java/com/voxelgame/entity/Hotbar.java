package com.voxelgame.entity;

/**
 * Quản lý hệ thống 9 ô Thanh Công Cụ Nhanh (Hotbar HUD) thuộc sở hữu của {@link Player} (Composition).
 *
 * THỂ HIỆN TÍNH ĐÓNG GÓI (ENCAPSULATION):
 * - Toàn bộ mảng lưu trữ loại khối (`slotTypes`) và số lượng (`slotCounts`) được khai báo `private final`,
 *   ngăn chặn mọi truy cập hoặc chỉnh sửa trực tiếp từ bên ngoài làm sai lệch giới hạn cộng dồn (64 item/ô).
 */
public class Hotbar {

    // Tổng số ô trên thanh Hotbar
    public static final int SLOT_COUNT = 9;
    // Số lượng cộng dồn tối đa trong 1 ô (64 blocks/stack chuẩn Minecraft)
    public static final int MAX_STACK_SIZE = 64;

    private final Block[] slotTypes = new Block[SLOT_COUNT];
    private final int[] slotCounts = new int[SLOT_COUNT];

    // Chỉ số ô đang được người chơi chọn hiện tại (0..8, tương ứng phím 1..9)
    private int selectedSlot = 0;

    public Hotbar() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slotTypes[i] = Block.AIR;
            slotCounts[i] = 0;
        }

        // Trang bị sẵn 5 loại Block cơ bản ở 5 ô đầu để người chơi thử nghiệm ngay khi vào thế giới
        setSlot(0, Block.DIRT, 16);
        setSlot(1, Block.GRASS, 16);
        setSlot(2, Block.STONE, 16);
        setSlot(3, Block.OAK_LOG, 12);
        setSlot(4, Block.OAK_LEAVES, 16);
    }

    /**
     * Thiết lập trực tiếp loại Block và số lượng cho một ô cụ thể.
     *
     * @param slotIndex chỉ số ô (0..8)
     * @param type loại Block
     * @param count số lượng (tối đa 64)
     */
    public void setSlot(int slotIndex, Block type, int count) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return;
        }
        if (type == null || type == Block.AIR || count <= 0) {
            slotTypes[slotIndex] = Block.AIR;
            slotCounts[slotIndex] = 0;
        } else {
            slotTypes[slotIndex] = type;
            slotCounts[slotIndex] = Math.min(MAX_STACK_SIZE, count);
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
     * Thêm một số lượng Block vừa nhặt được (từ ItemEntity rơi ra trên mặt đất) vào thanh Hotbar.
     * Ưu tiên gộp vào các ô cùng loại chưa đầy 64, sau đó mới xếp vào ô trống đầu tiên.
     *
     * @param type loại Block nhặt được
     * @param amount số lượng cần thêm
     * @return true nếu đã thêm được ít nhất 1 item vào Hotbar
     */
    public boolean addItem(Block type, int amount) {
        if (type == null || type == Block.AIR || amount <= 0) {
            return false;
        }

        int remaining = amount;

        // Bước 1: Ưu tiên cộng dồn vào các ô đang chứa sẵn cùng loại Block và chưa đầy 64
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slotTypes[i] == type && slotCounts[i] < MAX_STACK_SIZE) {
                int spaceLeft = MAX_STACK_SIZE - slotCounts[i];
                int toAdd = Math.min(spaceLeft, remaining);
                slotCounts[i] += toAdd;
                remaining -= toAdd;
                if (remaining == 0) {
                    return true;
                }
            }
        }

        // Bước 2: Nếu vẫn còn dư, đặt vào ô trống đầu tiên từ trái sang phải
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slotTypes[i] == Block.AIR || slotCounts[i] <= 0) {
                slotTypes[i] = type;
                int toAdd = Math.min(MAX_STACK_SIZE, remaining);
                slotCounts[i] = toAdd;
                remaining -= toAdd;
                if (remaining == 0) {
                    return true;
                }
            }
        }

        return remaining < amount;
    }

    /**
     * Tiêu thụ 1 Block ở ô đang chọn khi người chơi click chuột phải để đặt Block.
     *
     * @return loại Block vừa tiêu thụ, hoặc Block.AIR nếu ô trống
     */
    public Block consumeSelectedItem() {
        Block currentType = slotTypes[selectedSlot];
        int currentCount = slotCounts[selectedSlot];

        if (currentType == Block.AIR || currentCount <= 0) {
            return Block.AIR;
        }

        slotCounts[selectedSlot] = currentCount - 1;
        if (slotCounts[selectedSlot] <= 0) {
            slotCounts[selectedSlot] = 0;
            slotTypes[selectedSlot] = Block.AIR;
        }

        return currentType;
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public Block getSelectedBlock() {
        return slotTypes[selectedSlot];
    }

    public Block getSelectedBlockType() {
        return slotTypes[selectedSlot];
    }

    public int getSelectedCount() {
        return slotCounts[selectedSlot];
    }

    public Block getSlotBlock(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) {
            return Block.AIR;
        }
        return slotTypes[slotIndex];
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
