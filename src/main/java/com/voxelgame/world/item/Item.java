package com.voxelgame.world.item;

import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.ItemTextureAtlas;
import com.voxelgame.world.effect.StatusEffectType;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.entity.projectile.ThrownEggEntity;
import com.voxelgame.world.entity.projectile.ThrownEnderPearlEntity;
import com.voxelgame.world.level.World;
import com.voxelgame.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lớp trừu tượng Flyweight bất biến đại diện cho mọi Vật phẩm (Item) trong trò chơi:
 * - Cung cấp các phương thức đa hình cho tốc độ đào, cận chiến, tương tác mob Lượt 1,
 *   sử dụng tức thời và sử dụng liên tục (kéo cung, tụ lực giáo, ăn, uống).
 * - Bao bọc tự động toàn bộ các {@link Block} thành {@link BlockItem}.
 * - Định nghĩa đầy đủ đúng thứ tự 56 vật phẩm độc lập chuẩn Minecraft 1.21.11 để giữ nguyên UV trên atlas.
 */
public abstract class Item {

    /** Hành động giữ chuột phải liên tục mà một vật phẩm có thể kích hoạt. */
    public enum UseAction {
        /** Không có hành động giữ chuột phải liên tục. */
        NONE,
        /** Kéo dây cung. */
        BOW,
        /** Hạ giáo sắt lao tới (Charge). */
        SPEAR,
        /** Ăn thức ăn trong 1,6 giây. */
        EAT,
        /** Uống thuốc hoặc sữa trong 1,6 giây. */
        DRINK
    }

    /** Bảng ánh xạ O(1) từ ID Block sang Item tương ứng. */
    private static final Item[] BY_BLOCK_ID = new Item[256];
    /** Danh sách các vật phẩm độc lập (không gắn Block thường) dùng để dựng ItemTextureAtlas. */
    private static final List<Item> STANDALONE_ITEMS = new ArrayList<>();
    private static int nextStandaloneId = 256;
    private static int nextAtlasTileIndex = 0;

    /** Vật phẩm rỗng đại diện cho ô trống hoặc Block.AIR. */
    public static final Item NONE = new SimpleItem(0, "Air", 0, null, -1, 0x000000);

    static {
        BY_BLOCK_ID[Byte.toUnsignedInt(Block.AIR.getId())] = NONE;
        for (int id = 1; id < 256; id++) {
            Block block = Block.fromId((byte) id);
            if (block != Block.AIR) {
                BY_BLOCK_ID[id] = new BlockItem(id, block);
            }
        }
    }

    // =========================================================================
    // 56 VẬT PHẨM ĐỘC LẬP CHUẨN MINECRAFT 1.21.11 (GIỮ NGUYÊN THỨ TỰ ATLAS)
    // =========================================================================

    public static final Item LEATHER = simple("Leather", 64, "leather", 0x9E5934);
    public static final Item BEEF = food("Raw Beef", 64, "beef", 0xC44943, 3, 1.8f);
    public static final Item COOKED_BEEF = food("Steak", 64, "cooked_beef", 0x7E4427, 8, 12.8f);
    public static final Item MUTTON = food("Raw Mutton", 64, "mutton", 0xC6554C, 2, 1.2f);
    public static final Item COOKED_MUTTON = food("Cooked Mutton", 64, "cooked_mutton", 0x874B32, 6, 9.6f);
    public static final Item PORKCHOP = food("Raw Porkchop", 64, "porkchop", 0xE69593, 3, 1.8f);
    public static final Item COOKED_PORKCHOP = food("Cooked Porkchop", 64, "cooked_porkchop", 0xBA7049, 8, 12.8f);
    public static final Item CHICKEN = food("Raw Chicken", 64, "chicken", 0xE2B2A2,
            2, 1.2f, StatusEffectType.HUNGER, 30.0f, 0, 0.30f);
    public static final Item COOKED_CHICKEN = food("Cooked Chicken", 64, "cooked_chicken", 0xB8733A, 6, 7.2f);
    public static final Item FEATHER = simple("Feather", 64, "feather", 0xEFEFEF);
    public static final Item EGG = throwable("Egg", 16, "egg", 0xEADCC2, ThrownEggEntity::new);
    public static final Item RABBIT = food("Raw Rabbit", 64, "rabbit", 0xD39D8E, 3, 1.8f);
    public static final Item COOKED_RABBIT = food("Cooked Rabbit", 64, "cooked_rabbit", 0xA86948, 5, 6.0f);
    public static final Item RABBIT_HIDE = simple("Rabbit Hide", 64, "rabbit_hide", 0xC59E6E);
    public static final Item RABBIT_FOOT = simple("Rabbit Foot", 64, "rabbit_foot", 0xC6A27E);
    public static final Item COD = food("Raw Cod", 64, "cod", 0xB7987A, 2, 0.4f);
    public static final Item COOKED_COD = food("Cooked Cod", 64, "cooked_cod", 0xC8A57E, 5, 6.0f);
    public static final Item SALMON = food("Raw Salmon", 64, "salmon", 0xB84A44, 2, 0.4f);
    public static final Item COOKED_SALMON = food("Cooked Salmon", 64, "cooked_salmon", 0xD36757, 6, 9.6f);
    public static final Item GOAT_HORN = registerStandalone(
            new GoatHornItem(nextStandaloneId++, "Goat Horn", 1, "goat_horn", nextAtlasTileIndex++, 0xB9A58B));
    public static final Item ROTTEN_FLESH = food("Rotten Flesh", 64, "rotten_flesh", 0x8A5638,
            4, 0.8f, StatusEffectType.HUNGER, 30.0f, 0, 0.80f);
    public static final Item IRON_INGOT = simple("Iron Ingot", 64, "iron_ingot", 0xD8D8D8);
    public static final Item CARROT = food("Carrot", 64, "carrot", 0xE57A1F, 3, 3.6f);
    public static final Item POTATO = food("Potato", 64, "potato", 0xC89B54, 1, 0.6f);
    public static final Item BAKED_POTATO = food("Baked Potato", 64, "baked_potato", 0xD4A153, 5, 6.0f);
    public static final Item IRON_SPEAR = registerStandalone(
            new SpearItem(nextStandaloneId++, "Iron Spear", 1, "iron_spear", nextAtlasTileIndex++, 0xC6C9CE));
    public static final Item BONE = simple("Bone", 64, "bone", 0xE4E1CF);
    public static final Item ARROW = simple("Arrow", 64, "arrow", 0x9B7653);
    public static final Item BOW = registerStandalone(
            new BowItem(nextStandaloneId++, "Bow", 1, "bow", nextAtlasTileIndex++, 0x8F6B43));
    public static final Item TIPPED_ARROW_SLOWNESS = simple("Arrow of Slowness", 64, "tipped_arrow_slowness", 0x8BAFE0);
    public static final Item TIPPED_ARROW_WEAKNESS = simple("Arrow of Weakness", 64, "tipped_arrow_weakness", 0x484D48);
    public static final Item GUNPOWDER = simple("Gunpowder", 64, "gunpowder", 0x727272);
    public static final Item MUSIC_DISC_13 = simple("Music Disc 13", 1, "music_disc_13", 0xD4BE47);
    public static final Item MUSIC_DISC_CAT = simple("Music Disc Cat", 1, "music_disc_cat", 0x5CC94B);
    public static final Item STRING = simple("String", 64, "string", 0xEBEBEB);
    public static final Item SPIDER_EYE = food("Spider Eye", 64, "spider_eye", 0x922739,
            2, 3.2f, StatusEffectType.POISON, 4.0f, 0, 1.0f);
    public static final Item ENDER_PEARL = throwable("Ender Pearl", 16, "ender_pearl", 0x349E88,
            ThrownEnderPearlEntity::new);
    public static final Item GLASS_BOTTLE = simple("Glass Bottle", 64, "glass_bottle", 0xB8DCE6);
    public static final Item GLOWSTONE_DUST = simple("Glowstone Dust", 64, "glowstone_dust", 0xF5D25B);
    public static final Item REDSTONE = simple("Redstone Dust", 64, "redstone", 0xD41B1B);
    public static final Item SUGAR = simple("Sugar", 64, "sugar", 0xF4F4F4);
    public static final Item STICK = simple("Stick", 64, "stick", 0x89673D);
    public static final Item POTION_HEALING = potion("Potion of Healing", "potion_healing", 0xF82423,
            false, GLASS_BOTTLE);
    public static final Item POTION_FIRE_RESISTANCE = potion("Potion of Fire Resistance", "potion_fire_resistance",
            0xFF9900, false, GLASS_BOTTLE);
    public static final Item POTION_WATER_BREATHING = potion("Potion of Water Breathing", "potion_water_breathing",
            0x98DAC0, false, GLASS_BOTTLE);
    public static final Item POTION_SWIFTNESS = potion("Potion of Swiftness", "potion_swiftness", 0x33EBFF,
            false, GLASS_BOTTLE);
    public static final Item WHITE_WOOL = wool("White Wool", "white_wool", 0xECECEC, Block.WHITE_WOOL);
    public static final Item LIGHT_GRAY_WOOL = wool("Light Gray Wool", "light_gray_wool", 0x9E9E97,
            Block.LIGHT_GRAY_WOOL);
    public static final Item GRAY_WOOL = wool("Gray Wool", "gray_wool", 0x4A4E52, Block.GRAY_WOOL);
    public static final Item BLACK_WOOL = wool("Black Wool", "black_wool", 0x1D1E23, Block.BLACK_WOOL);
    public static final Item BROWN_WOOL = wool("Brown Wool", "brown_wool", 0x7A4B2E, Block.BROWN_WOOL);
    public static final Item PINK_WOOL = wool("Pink Wool", "pink_wool", 0xEC8EAC, Block.PINK_WOOL);
    public static final Item SHEARS = registerStandalone(
            new ShearsItem(nextStandaloneId++, "Shears", 1, "shears", nextAtlasTileIndex++, 0xC4C4C4));
    public static final Item FLINT_AND_STEEL = registerStandalone(
            new FlintAndSteelItem(nextStandaloneId++, "Flint and Steel", 1, "flint_and_steel",
                    nextAtlasTileIndex++, 0xB4B8BD));
    public static final Item BUCKET = registerStandalone(
            new BucketItem(nextStandaloneId++, "Bucket", 16, "bucket", nextAtlasTileIndex++, 0xB8BFC6));
    public static final Item MILK_BUCKET = potion("Milk Bucket", "milk_bucket", 0xEFEFEF, true, BUCKET);

    private static final List<Item> STANDALONE_VIEW = Collections.unmodifiableList(STANDALONE_ITEMS);

    private final int id;
    private final String displayName;
    private final int maxStackSize;
    private final Block block;
    private final String textureName;
    private final int atlasTileIndex;
    private final boolean cubeItem;
    private final int fallbackRgb;

    /** Khởi tạo bất biến các thuộc tính nền cho mọi lớp con vật phẩm. */
    protected Item(int id, String displayName, int maxStackSize, Block block, String textureName,
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

    /** Đăng ký một vật phẩm độc lập vào danh sách dựng ItemTextureAtlas. */
    private static <T extends Item> T registerStandalone(T item) {
        STANDALONE_ITEMS.add(item);
        return item;
    }

    /** Tạo vật phẩm nguyên liệu đơn giản và đăng ký vào atlas vật phẩm. */
    private static Item simple(String displayName, int maxStackSize, String textureName, int fallbackRgb) {
        return registerStandalone(new SimpleItem(nextStandaloneId++, displayName, maxStackSize,
                textureName, nextAtlasTileIndex++, fallbackRgb));
    }

    /** Tạo món ăn thuần túy không có hiệu ứng phụ và đăng ký vào atlas vật phẩm. */
    private static Item food(String displayName, int maxStackSize, String textureName, int fallbackRgb,
                             int nutrition, float saturation) {
        return registerStandalone(new FoodItem(nextStandaloneId++, displayName, maxStackSize,
                textureName, nextAtlasTileIndex++, fallbackRgb,
                new FoodRegistry.FoodSpec(nutrition, saturation, null, 0.0f, 0, 0.0f)));
    }

    /** Tạo món ăn có hiệu ứng phụ theo xác suất và đăng ký vào atlas vật phẩm. */
    private static Item food(String displayName, int maxStackSize, String textureName, int fallbackRgb,
                             int nutrition, float saturation, StatusEffectType effect,
                             float durationSeconds, int amplifier, float effectChance) {
        return registerStandalone(new FoodItem(nextStandaloneId++, displayName, maxStackSize,
                textureName, nextAtlasTileIndex++, fallbackRgb,
                new FoodRegistry.FoodSpec(nutrition, saturation, effect, durationSeconds, amplifier, effectChance)));
    }

    /** Tạo vật phẩm đồ uống (thuốc Witch hoặc xô sữa) và đăng ký vào atlas vật phẩm. */
    private static Item potion(String displayName, String textureName, int fallbackRgb,
                               boolean clearsEffects, Item returnContainer) {
        return registerStandalone(new PotionItem(nextStandaloneId++, displayName, 1,
                textureName, nextAtlasTileIndex++, fallbackRgb, clearsEffects, returnContainer));
    }

    /** Tạo vật phẩm ném đạn đạo và đăng ký vào atlas vật phẩm. */
    private static Item throwable(String displayName, int maxStackSize, String textureName, int fallbackRgb,
                                  ThrowableItem.ProjectileFactory factory) {
        return registerStandalone(new ThrowableItem(nextStandaloneId++, displayName, maxStackSize,
                textureName, nextAtlasTileIndex++, fallbackRgb, factory));
    }

    /** Tạo vật phẩm len cừu dạng khối lập phương mini, liên kết hai chiều với {@link Block}. */
    private static Item wool(String displayName, String textureName, int fallbackRgb, Block woolBlock) {
        BlockItem item = registerStandalone(new BlockItem(nextStandaloneId++, displayName, 64, woolBlock,
                textureName, nextAtlasTileIndex++, true, fallbackRgb));
        BY_BLOCK_ID[Byte.toUnsignedInt(woolBlock.getId())] = item;
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

    // =========================================================================
    // HỢP ĐỒNG ĐA HÌNH CHO HÀNH VI SỬ DỤNG, CẬN CHIẾN, ĐÀO VÀ TƯƠNG TÁC MOB
    // =========================================================================

    /** Trả về hành động giữ chuột phải liên tục của vật phẩm (mặc định {@link UseAction#NONE}). */
    public UseAction getUseAction() {
        return UseAction.NONE;
    }

    /** Trả về thời lượng tiêu chuẩn (giây) cho hành động giữ chuột phải. */
    public float getUseDurationSeconds() {
        return 0.0f;
    }

    /** Kiểm tra điều kiện cho phép bắt đầu sử dụng liên tục (ví dụ còn mũi tên hoặc chưa no). */
    public boolean canStartContinuousUse(World world, Player player) {
        return getUseAction() != UseAction.NONE;
    }

    /** Cho biết vật phẩm có làm chậm tốc độ di chuyển người chơi khi đang giữ chuột phải không. */
    public boolean slowsPlayerWhileUsing() {
        return true;
    }

    /** Tầm đánh cận chiến chuột trái khi cầm vật phẩm này (mặc định 3,0 block). */
    public float getMeleeReach() {
        return ItemUseHandler.DEFAULT_MELEE_REACH;
    }

    /** Sát thương cận chiến cơ sở khi cầm vật phẩm này (mặc định 1,0 HP). */
    public float getMeleeDamage() {
        return ItemUseHandler.DEFAULT_MELEE_DAMAGE;
    }

    /** Thời gian hồi chiêu cận chiến khi cầm vật phẩm này (mặc định 0,6 giây). */
    public float getMeleeCooldown() {
        return ItemUseHandler.DEFAULT_MELEE_COOLDOWN;
    }

    /** Hệ số tốc độ đào khối khi cầm vật phẩm này (mặc định 1,0×; {@link ShearsItem} trả 5,0× cho lá/len). */
    public float getMiningSpeedMultiplier(Block block) {
        return 1.0f;
    }

    /**
     * Lượt 1 điều phối tương tác mob: hành vi công cụ phổ quát tác động lên mọi {@link MobEntity}
     * (ví dụ {@link FlintAndSteelItem} đốt cháy mob 8 giây).
     */
    public boolean onInteractMob(World world, Player player, MobEntity target) {
        return false;
    }

    /**
     * Xử lý sử dụng tức thời khi nhấn chuột phải mà không cần giữ lâu
     * (ví dụ ném {@link ThrowableItem} hoặc thổi {@link GoatHornItem}).
     */
    public boolean onInstantUse(World world, Player player, Camera camera, ItemUseHandler handler) {
        return false;
    }

    /** Hook hoàn tất tiêu thụ sau khi giữ chuột phải đủ thời lượng (mặc định không làm gì). */
    public void onConsumeComplete(World world, Player player) {
    }

    /** Hook khi nhả chuột phải từ trạng thái sử dụng liên tục (ví dụ {@link BowItem} bắn tên). */
    public void onReleaseUse(World world, Player player, Camera camera, float useElapsed, ItemUseHandler handler) {
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
