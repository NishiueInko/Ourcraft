package com.voxelgame.entity;

import com.voxelgame.engine.ItemTextureAtlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lớp Flyweight bất biến đại diện cho mọi Vật phẩm (Item) trong trò chơi:
 * - Bao bọc tự động toàn bộ các {@link Block} để tương thích ngược với Hotbar và ItemEntity.
 * - Định nghĩa đầy đủ 56 vật phẩm độc lập chuẩn Minecraft 1.21.11 rơi từ mob và công cụ tương tác.
 */
public final class Item {

    /** Bảng ánh xạ O(1) từ ID Block sang Item tương ứng. */
    private static final Item[] BY_BLOCK_ID = new Item[256];
    /** Danh sách bất biến các vật phẩm độc lập (không gắn Block) dùng để dựng ItemTextureAtlas. */
    private static final List<Item> STANDALONE_ITEMS = new ArrayList<>();
    private static int nextStandaloneId = 256;
    private static int nextAtlasTileIndex = 0;

    /** Vật phẩm rỗng đại diện cho ô trống hoặc Block.AIR. */
    public static final Item NONE = new Item(0, "Air", 0, Block.AIR, null, -1, false, 0x000000);

    static {
        BY_BLOCK_ID[Byte.toUnsignedInt(Block.AIR.getId())] = NONE;
        for (int id = 1; id < 256; id++) {
            Block block = Block.fromId((byte) id);
            if (block != Block.AIR) {
                BY_BLOCK_ID[id] = new Item(id, block.getDisplayName(), Hotbar.MAX_STACK_SIZE,
                        block, null, -1, false, 0xFFFFFF);
            }
        }
    }

    // =========================================================================
    // 56 VẬT PHẨM ĐỘC LẬP CHUẨN MINECRAFT 1.21.11 (RƠI TỪ MOB & CÔNG CỤ)
    // =========================================================================

    public static final Item LEATHER = standalone("Leather", 64, "leather", 0x9E5934);
    public static final Item BEEF = standalone("Raw Beef", 64, "beef", 0xC44943);
    public static final Item COOKED_BEEF = standalone("Steak", 64, "cooked_beef", 0x7E4427);
    public static final Item MUTTON = standalone("Raw Mutton", 64, "mutton", 0xC6554C);
    public static final Item COOKED_MUTTON = standalone("Cooked Mutton", 64, "cooked_mutton", 0x874B32);
    public static final Item PORKCHOP = standalone("Raw Porkchop", 64, "porkchop", 0xE69593);
    public static final Item COOKED_PORKCHOP = standalone("Cooked Porkchop", 64, "cooked_porkchop", 0xBA7049);
    public static final Item CHICKEN = standalone("Raw Chicken", 64, "chicken", 0xE2B2A2);
    public static final Item COOKED_CHICKEN = standalone("Cooked Chicken", 64, "cooked_chicken", 0xB8733A);
    public static final Item FEATHER = standalone("Feather", 64, "feather", 0xEFEFEF);
    public static final Item EGG = standalone("Egg", 16, "egg", 0xEADCC2);
    public static final Item RABBIT = standalone("Raw Rabbit", 64, "rabbit", 0xD39D8E);
    public static final Item COOKED_RABBIT = standalone("Cooked Rabbit", 64, "cooked_rabbit", 0xA86948);
    public static final Item RABBIT_HIDE = standalone("Rabbit Hide", 64, "rabbit_hide", 0xC59E6E);
    public static final Item RABBIT_FOOT = standalone("Rabbit Foot", 64, "rabbit_foot", 0xC6A27E);
    public static final Item COD = standalone("Raw Cod", 64, "cod", 0xB7987A);
    public static final Item COOKED_COD = standalone("Cooked Cod", 64, "cooked_cod", 0xC8A57E);
    public static final Item SALMON = standalone("Raw Salmon", 64, "salmon", 0xB84A44);
    public static final Item COOKED_SALMON = standalone("Cooked Salmon", 64, "cooked_salmon", 0xD36757);
    public static final Item GOAT_HORN = standalone("Goat Horn", 1, "goat_horn", 0xB9A58B);
    public static final Item ROTTEN_FLESH = standalone("Rotten Flesh", 64, "rotten_flesh", 0x8A5638);
    public static final Item IRON_INGOT = standalone("Iron Ingot", 64, "iron_ingot", 0xD8D8D8);
    public static final Item CARROT = standalone("Carrot", 64, "carrot", 0xE57A1F);
    public static final Item POTATO = standalone("Potato", 64, "potato", 0xC89B54);
    public static final Item BAKED_POTATO = standalone("Baked Potato", 64, "baked_potato", 0xD4A153);
    public static final Item IRON_SPEAR = standalone("Iron Spear", 1, "iron_spear", 0xC6C9CE);
    public static final Item BONE = standalone("Bone", 64, "bone", 0xE4E1CF);
    public static final Item ARROW = standalone("Arrow", 64, "arrow", 0x9B7653);
    public static final Item BOW = standalone("Bow", 1, "bow", 0x8F6B43);
    public static final Item TIPPED_ARROW_SLOWNESS = standalone("Arrow of Slowness", 64, "tipped_arrow_slowness", 0x8BAFE0);
    public static final Item TIPPED_ARROW_WEAKNESS = standalone("Arrow of Weakness", 64, "tipped_arrow_weakness", 0x484D48);
    public static final Item GUNPOWDER = standalone("Gunpowder", 64, "gunpowder", 0x727272);
    public static final Item MUSIC_DISC_13 = standalone("Music Disc 13", 1, "music_disc_13", 0xD4BE47);
    public static final Item MUSIC_DISC_CAT = standalone("Music Disc Cat", 1, "music_disc_cat", 0x5CC94B);
    public static final Item STRING = standalone("String", 64, "string", 0xEBEBEB);
    public static final Item SPIDER_EYE = standalone("Spider Eye", 64, "spider_eye", 0x922739);
    public static final Item ENDER_PEARL = standalone("Ender Pearl", 16, "ender_pearl", 0x349E88);
    public static final Item GLASS_BOTTLE = standalone("Glass Bottle", 64, "glass_bottle", 0xB8DCE6);
    public static final Item GLOWSTONE_DUST = standalone("Glowstone Dust", 64, "glowstone_dust", 0xF5D25B);
    public static final Item REDSTONE = standalone("Redstone Dust", 64, "redstone", 0xD41B1B);
    public static final Item SUGAR = standalone("Sugar", 64, "sugar", 0xF4F4F4);
    public static final Item STICK = standalone("Stick", 64, "stick", 0x89673D);
    public static final Item POTION_HEALING = standalone("Potion of Healing", 1, "potion_healing", 0xF82423);
    public static final Item POTION_FIRE_RESISTANCE = standalone("Potion of Fire Resistance", 1, "potion_fire_resistance", 0xFF9900);
    public static final Item POTION_WATER_BREATHING = standalone("Potion of Water Breathing", 1, "potion_water_breathing", 0x98DAC0);
    public static final Item POTION_SWIFTNESS = standalone("Potion of Swiftness", 1, "potion_swiftness", 0x33EBFF);
    public static final Item WHITE_WOOL = wool("White Wool", "white_wool", 0xECECEC);
    public static final Item LIGHT_GRAY_WOOL = wool("Light Gray Wool", "light_gray_wool", 0x9E9E97);
    public static final Item GRAY_WOOL = wool("Gray Wool", "gray_wool", 0x4A4E52);
    public static final Item BLACK_WOOL = wool("Black Wool", "black_wool", 0x1D1E23);
    public static final Item BROWN_WOOL = wool("Brown Wool", "brown_wool", 0x7A4B2E);
    public static final Item PINK_WOOL = wool("Pink Wool", "pink_wool", 0xEC8EAC);
    public static final Item SHEARS = standalone("Shears", 1, "shears", 0xC4C4C4);
    public static final Item FLINT_AND_STEEL = standalone("Flint and Steel", 1, "flint_and_steel", 0xB4B8BD);
    public static final Item BUCKET = standalone("Bucket", 16, "bucket", 0xB8BFC6);
    public static final Item MILK_BUCKET = standalone("Milk Bucket", 1, "milk_bucket", 0xEFEFEF);

    private static final List<Item> STANDALONE_VIEW = Collections.unmodifiableList(STANDALONE_ITEMS);

    private final int id;
    private final String displayName;
    private final int maxStackSize;
    private final Block block;
    private final String textureName;
    private final int atlasTileIndex;
    private final boolean cubeItem;
    private final int fallbackRgb;

    /** Khởi tạo bất biến một thực thể Flyweight vật phẩm. */
    private Item(int id, String displayName, int maxStackSize, Block block, String textureName,
                 int atlasTileIndex, boolean cubeItem, int fallbackRgb) {
        this.id = id;
        this.displayName = displayName;
        this.maxStackSize = maxStackSize;
        this.block = block;
        this.textureName = textureName;
        this.atlasTileIndex = atlasTileIndex;
        this.cubeItem = cubeItem;
        this.fallbackRgb = fallbackRgb;
    }

    /** Tạo vật phẩm phẳng độc lập và đăng ký vào atlas vật phẩm. */
    private static Item standalone(String displayName, int maxStackSize, String textureName, int fallbackRgb) {
        Item item = new Item(nextStandaloneId++, displayName, maxStackSize, Block.AIR,
                textureName, nextAtlasTileIndex++, false, fallbackRgb);
        STANDALONE_ITEMS.add(item);
        return item;
    }

    /** Tạo vật phẩm len cừu dạng khối lập phương mini khi rơi trên mặt đất. */
    private static Item wool(String displayName, String textureName, int fallbackRgb) {
        Item item = new Item(nextStandaloneId++, displayName, 64, Block.AIR,
                textureName, nextAtlasTileIndex++, true, fallbackRgb);
        STANDALONE_ITEMS.add(item);
        return item;
    }

    /** Trả về Item bọc tương ứng với một Block, hoặc Item.NONE nếu block là null/AIR. */
    public static Item of(Block block) {
        if (block == null || block == Block.AIR) {
            return NONE;
        }
        Item item = BY_BLOCK_ID[Byte.toUnsignedInt(block.getId())];
        return item != null ? item : NONE;
    }

    /** Trả về danh sách bất biến tất cả vật phẩm độc lập để đóng gói vào ItemTextureAtlas. */
    public static List<Item> allStandaloneItems() {
        return STANDALONE_VIEW;
    }

    /** Tính tọa độ UV [u0, v0, u1, v1] trên lưới 8 × 8 của ItemTextureAtlas. */
    public float[] getTileUV() {
        if (atlasTileIndex < 0) {
            return block.getFaceUV(Block.Face.SOUTH);
        }
        int col = atlasTileIndex % ItemTextureAtlas.ATLAS_COLS;
        int row = atlasTileIndex / ItemTextureAtlas.ATLAS_COLS;
        float tileWidth = 1.0f / ItemTextureAtlas.ATLAS_COLS;
        float tileHeight = 1.0f / ItemTextureAtlas.ATLAS_ROWS;
        float eps = 0.0008f;
        return new float[]{
                col * tileWidth + eps,
                row * tileHeight + eps,
                (col + 1) * tileWidth - eps,
                (row + 1) * tileHeight - eps
        };
    }

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    public Block getBlock() {
        return block;
    }

    public boolean isBlock() {
        return block != null && block != Block.AIR;
    }

    public boolean isNone() {
        return this == NONE;
    }

    public String getTextureName() {
        return textureName;
    }

    public int getAtlasTileIndex() {
        return atlasTileIndex;
    }

    public boolean isCubeItem() {
        return cubeItem;
    }

    public int getFallbackRgb() {
        return fallbackRgb;
    }
}
