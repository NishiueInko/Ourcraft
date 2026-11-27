package com.voxelgame.world.entity.mob.animal;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.audio.MobSoundEvent;
import com.voxelgame.client.render.entity.MobVisualState;
import com.voxelgame.core.Interactable;
import com.voxelgame.world.entity.ItemEntity;
import com.voxelgame.world.entity.mob.ai.MobGoal;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.mob.PassiveMob;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Cừu đồng bằng có một màu len cố định, cắt lông bằng Shears và mọc lại lông khi ăn cỏ. */
public final class Sheep extends PassiveMob implements Interactable {
    /** Sáu màu len tự nhiên và biên trên không bao gồm của mỗi khoảng sinh. */
    public enum WoolColor {
        WHITE(0xffffff, 81_836, Item.WHITE_WOOL),
        LIGHT_GRAY(0xb5b7ad, 86_836, Item.LIGHT_GRAY_WOOL),
        GRAY(0x575953, 91_836, Item.GRAY_WOOL),
        BLACK(0x26252a, 96_836, Item.BLACK_WOOL),
        BROWN(0x85543a, 99_836, Item.BROWN_WOOL),
        PINK(0xed96af, 100_000, Item.PINK_WOOL);

        /** Bảng khoảng cố định và miền của bộ chọn, không sao chép mảng khi chọn màu. */
        private static final WoolColor[] COLORS = values();
        private static final int TOTAL_ROLLS = 100_000;
        private final int rgb;
        private final int upperBound;
        private final Item woolItem;

        /** Gắn màu nhân lên len, khoảng tích lũy và vật phẩm len tương ứng. */
        WoolColor(int rgb, int upperBound, Item woolItem) {
            this.rgb = rgb;
            this.upperBound = upperBound;
            this.woolItem = woolItem;
        }

        /** Đổi một giá trị đều trong 100.000 phần sang màu tự nhiên của cừu. */
        public static WoolColor fromRoll(int roll) {
            if (roll < 0 || roll >= TOTAL_ROLLS) {
                throw new IllegalArgumentException("Giá trị chọn màu len ngoài miền");
            }
            for (WoolColor color : COLORS) {
                if (roll < color.upperBound) {
                    return color;
                }
            }
            throw new IllegalStateException("Bảng màu len thiếu khoảng cuối");
        }

        /** Trả màu RGB chỉ dùng cho lớp len. */
        public int rgb() { return rgb; }

        /** Trả vật phẩm len tương ứng khi cắt lông hoặc khi cừu chết. */
        public Item woolItem() { return woolItem; }
    }

    /** Xác suất ăn cỏ mỗi tick (1/20s) chuẩn Minecraft: 1/1000 cho cừu lớn và 1/50 cho cừu con. */
    public static final int ADULT_GRAZE_CHANCE_DIVISOR = 1000;
    public static final int BABY_GRAZE_CHANCE_DIVISOR = 50;

    /** Màu len và trạng thái đã bị cắt lông theo cá thể, model vẫn được chia sẻ. */
    private final WoolColor woolColor;
    private boolean sheared;

    /** Chọn màu len khi sinh theo các xác suất tự nhiên. */
    public Sheep(float x, float y, float z) {
        this(x, y, z, WoolColor.fromRoll(ThreadLocalRandom.current().nextInt(WoolColor.TOTAL_ROLLS)));
    }

    /** Tạo một cá thể có màu được chỉ định, dùng cùng luồng hành vi và kết xuất. */
    public Sheep(float x, float y, float z, WoolColor woolColor) {
        super(MobType.SHEEP, x, y, z);
        this.woolColor = Objects.requireNonNull(woolColor);
        setMoveSpeed(2.3f);
        mobVisualState().setSheepWoolColor(woolColor.rgb());
        goals().add(new GrazeGoal());
    }

    /** Trả màu len tự nhiên đã chọn cho cá thể. */
    public WoolColor woolColor() { return woolColor; }

    /** Cho biết cừu đã bị cắt lông và đang chờ ăn cỏ để mọc lại hay chưa. */
    public boolean isSheared() { return sheared; }

    /** Xử lý Lượt 2 (Interactable): cắt 1–3 khối len khi người chơi cầm Shears. */
    @Override
    public boolean onInteract(World world, Player player, Item heldItem) {
        if (!isAlive() || isBaby() || sheared || player == null || !player.isAlive()) {
            return false;
        }
        if (heldItem == Item.SHEARS) {
            shear(world);
            return true;
        }
        return false;
    }

    /** Cắt lông cừu, ẩn lớp phủ len và rơi 1–3 khối len đúng màu tự nhiên. */
    public void shear(World world) {
        if (sheared) {
            return;
        }
        sheared = true;
        mobVisualState().setSheepSheared(true);
        if (world != null) {
            int count = ThreadLocalRandom.current().nextInt(1, 4);
            for (int i = 0; i < count; i++) {
                world.spawnDroppedItem(new ItemEntity(x(), y() + 0.5f, z(), woolColor.woolItem()));
            }
            AudioManager audio = world.getAudio();
            if (audio != null) {
                audio.playEffect(AudioManager.Effect.CLICK, 0.45f);
            }
            playSound(world, MobSoundEvent.AMBIENT);
        }
    }

    /** Mọc lại lớp lông sau khi cừu ăn cỏ thành công. */
    public void regrowWool() {
        if (sheared) {
            sheared = false;
            mobVisualState().setSheepSheared(false);
        }
    }

    /** Giữ tư thế cúi đầu tới lúc ăn, dùng đường thay block bình thường để cập nhật cây cạnh. */
    private static final class GrazeGoal implements MobGoal {
        /** Nhịp kiểm tra 1 tick (0,05s) và các mốc thời gian cúi đầu ăn cỏ. */
        private static final float TICK_SECONDS = 0.05f;
        private static final float EASE_SECONDS = 0.35f;
        private static final float EAT_SECONDS = 0.9f;
        private static final float DURATION = 1.45f;
        /** Sai số nhỏ khi đọc mặt đỡ ở biên độ cao nguyên. */
        private static final float GROUND_EPSILON = 0.001f;
        private float elapsed;
        private int foodX;
        private int foodY;
        private int foodZ;
        private Block food;

        /** Đặt ăn trước Wander nhưng cho phép chạy hoảng thay quyền. */
        @Override public int priority() { return 5; }
        /** Giữ cả chuyển động ngang và đầu để tránh tư thế xung đột. */
        @Override public int controls() { return MOVE | LOOK; }
        /** Rút thăm đúng theo nhịp 20 Hz chuẩn Minecraft, không phụ thuộc 60 FPS. */
        @Override public float retrySeconds() { return TICK_SECONDS; }

        /** Rút thăm 1/1000 (trưởng thành) hoặc 1/50 (con non), rồi kiểm tra Short Grass hoặc Grass Block dưới chân. */
        @Override
        public boolean canStart(World world, MobEntity sheep) {
            if (!sheep.isOnGround() || sheep.inWater() || sheep.panicTime() > 0f) {
                return false;
            }
            int divisor = sheep.isBaby() ? BABY_GRAZE_CHANCE_DIVISOR : ADULT_GRAZE_CHANCE_DIVISOR;
            if (ThreadLocalRandom.current().nextInt(divisor) != 0) {
                return false;
            }
            foodX = floor(sheep.x());
            foodY = floor(sheep.y() + GROUND_EPSILON);
            foodZ = floor(sheep.z());
            if (world.getBlock(foodX, foodY, foodZ) == Block.SHORT_GRASS.getId()) {
                food = Block.SHORT_GRASS;
                return true;
            }
            foodY = floor(sheep.y() - GROUND_EPSILON);
            if (world.getBlock(foodX, foodY, foodZ) == Block.GRASS.getId()) {
                food = Block.GRASS;
                return true;
            }
            return false;
        }

        /** Hủy tư thế nếu cừu bị xô khỏi ô ăn hoặc đang cần thoát hiểm. */
        @Override
        public boolean shouldContinue(World world, MobEntity sheep) {
            return elapsed < DURATION && sheep.isOnGround() && !sheep.inWater() && sheep.panicTime() <= 0f
                    && floor(sheep.x()) == foodX && floor(sheep.z()) == foodZ;
        }

        /** Bắt đầu cúi từ tư thế đứng và dừng lệnh di chuyển còn lại. */
        @Override
        public void start(World world, MobEntity sheep) {
            elapsed = 0f;
            sheep.stopMoving();
        }

        /** Nội suy góc cúi, xác nhận đúng khối tại mốc ăn rồi đổi trạng thái và mọc lại lông. */
        @Override
        public void tick(World world, MobEntity sheep, float dt) {
            float previous = elapsed;
            elapsed = Math.min(DURATION, elapsed + dt);
            float lower = Math.min(1f, elapsed / EASE_SECONDS);
            float raise = Math.min(1f, (DURATION - elapsed) / EASE_SECONDS);
            sheep.mobVisualState().setPose(MobVisualState.Pose.GRAZE, lower * raise);
            if (previous < EAT_SECONDS && elapsed >= EAT_SECONDS
                    && world.getBlock(foodX, foodY, foodZ) == food.getId()) {
                Block replacement = food == Block.SHORT_GRASS ? Block.AIR : Block.DIRT;
                world.setBlock(foodX, foodY, foodZ, replacement.getId());
                if (sheep instanceof Sheep actualSheep) {
                    actualSheep.regrowWool();
                }
            }
        }

        /** Đưa đầu về tư thế thường lúc ăn xong hoặc bị goal ưu tiên hơn ngắt. */
        @Override
        public void stop(World world, MobEntity sheep) {
            sheep.mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }

        /** Đổi tọa độ thành ô voxel. */
        private static int floor(float coordinate) {
            return (int) Math.floor(coordinate);
        }
    }
}
