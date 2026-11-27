package com.voxelgame.world.item;

import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.block.WoolBlock;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Danh mục tĩnh bất biến tổng hợp toàn bộ khối và vật phẩm có thể lấy trong chế độ Creative,
 * phục vụ {@code CreativeInventoryScreen} để người chơi minh họa mọi nội dung của trò chơi.
 *
 * <p>Thiết kế Zero-GC:</p>
 * <ul>
 *   <li>Toàn bộ mảng vật phẩm của 5 tab ({@link Tab#ALL}, {@link Tab#BLOCKS}, {@link Tab#COMBAT},
 *       {@link Tab#FOOD}, {@link Tab#ITEMS}) được dựng một lần duy nhất trong khối {@code static}.</li>
 *   <li>Tự động loại bỏ {@link Block#AIR}, chất lỏng ({@link Block#WATER}, {@link Block#LAVA}) và
 *       khử trùng lặp 6 khối {@link WoolBlock} (vì 6 vật phẩm {@code Item.*_WOOL} đã liên kết 2 chiều).</li>
 *   <li>Bao gồm cả {@link Block#BEDROCK} và đầy đủ 56 vật phẩm độc lập chuẩn Minecraft 1.21.11.</li>
 * </ul>
 */
public final class CreativeCatalog {

    /** Năm nhóm danh mục hiển thị trên thanh tab của Kho Creative. */
    public enum Tab {
        /** Toàn bộ khối và vật phẩm trong trò chơi. */
        ALL("ALL"),
        /** Toàn bộ khối xây dựng, địa hình, thực vật, rương và 6 màu len. */
        BLOCKS("BLOCKS"),
        /** Vũ khí, cung tên, công cụ tương tác và lọ thuốc chiến đấu/tiện ích. */
        COMBAT("COMBAT"),
        /** Toàn bộ 19 loại thức ăn chuẩn Minecraft. */
        FOOD("FOOD"),
        /** Nguyên liệu rơi từ mob, đạn ném, đĩa nhạc, sừng dê và các vật phẩm khác. */
        ITEMS("ITEMS");

        /** Nhãn in hoa ngắn tương thích trực tiếp với font 5×7 của {@code BitmapFont}. */
        private final String label;

        Tab(String label) {
            this.label = label;
        }

        /** Trả về nhãn hiển thị trên nút tab. */
        public String getLabel() {
            return label;
        }
    }

    /** Bảng mảng tĩnh bất biến ánh xạ từng {@link Tab} sang danh sách vật phẩm (O(1), zero-GC). */
    private static final Map<Tab, Item[]> BY_TAB = buildCatalog();

    private CreativeCatalog() {
    }

    /** Trả về mảng tĩnh các vật phẩm thuộc {@code tab} (không cấp phát mới). */
    public static Item[] getItems(Tab tab) {
        if (tab == null) {
            return BY_TAB.get(Tab.ALL);
        }
        return BY_TAB.get(tab);
    }

    /** Trả về tổng số vật phẩm trong {@code tab}. */
    public static int getItemCount(Tab tab) {
        return getItems(tab).length;
    }

    /** Trả về vật phẩm tại chỉ số {@code index} trong {@code tab}, hoặc {@link Item#NONE} nếu ngoài biên. */
    public static Item getItem(Tab tab, int index) {
        Item[] items = getItems(tab);
        if (index < 0 || index >= items.length) {
            return Item.NONE;
        }
        return items[index];
    }

    /** Dựng một lần duy nhất toàn bộ 5 danh mục từ registry {@link Block} và {@link Item}. */
    private static Map<Tab, Item[]> buildCatalog() {
        List<Item> all = new ArrayList<>(96);
        List<Item> blocks = new ArrayList<>(36);
        List<Item> combat = new ArrayList<>(20);
        List<Item> food = new ArrayList<>(24);
        List<Item> misc = new ArrayList<>(32);

        // 1. Toàn bộ khối hợp lệ (bao gồm Bedrock; bỏ Air, Water, Lava và 6 WoolBlock tránh trùng với Item.*_WOOL)
        for (int id = 1; id < 256; id++) {
            Block block = Block.fromId((byte) id);
            if (block == null || block == Block.AIR || block.isLiquid() || block instanceof WoolBlock) {
                continue;
            }
            Item blockItem = Item.of(block);
            if (blockItem != null && !blockItem.isNone()) {
                all.add(blockItem);
                blocks.add(blockItem);
            }
        }

        // 2. Toàn bộ 56 vật phẩm độc lập (gồm 6 màu len đặt được thành khối)
        List<Item> standalone = Item.allStandaloneItems();
        for (int i = 0; i < standalone.size(); i++) {
            Item item = standalone.get(i);
            if (item == null || item.isNone()) {
                continue;
            }
            all.add(item);
            if (item.isBlock()) {
                blocks.add(item);
            } else if (item instanceof FoodItem) {
                food.add(item);
            } else if (isCombatOrTool(item)) {
                combat.add(item);
            } else {
                misc.add(item);
            }
        }

        EnumMap<Tab, Item[]> map = new EnumMap<>(Tab.class);
        map.put(Tab.ALL, all.toArray(new Item[0]));
        map.put(Tab.BLOCKS, blocks.toArray(new Item[0]));
        map.put(Tab.COMBAT, combat.toArray(new Item[0]));
        map.put(Tab.FOOD, food.toArray(new Item[0]));
        map.put(Tab.ITEMS, misc.toArray(new Item[0]));
        return map;
    }

    /** Phân loại nhóm vũ khí, cung tên, công cụ và thuốc uống cho tab {@link Tab#COMBAT}. */
    private static boolean isCombatOrTool(Item item) {
        return item instanceof BowItem
                || item instanceof SpearItem
                || item instanceof ToolItem
                || item instanceof PotionItem
                || item == Item.ARROW
                || item == Item.TIPPED_ARROW_SLOWNESS
                || item == Item.TIPPED_ARROW_WEAKNESS;
    }
}
