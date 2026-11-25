package com.voxelgame.entity.mob;

import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Cừu đồng bằng có một màu len cố định và định kỳ dừng chân ăn cỏ. */
public final class Sheep extends MobEntity {
    /** Sáu màu len tự nhiên và biên trên không bao gồm của mỗi khoảng sinh. */
    public enum WoolColor {
        WHITE(0xffffff, 81_836), LIGHT_GRAY(0xb5b7ad, 86_836), GRAY(0x575953, 91_836),
        BLACK(0x26252a, 96_836), BROWN(0x85543a, 99_836), PINK(0xed96af, 100_000);

        /** Bảng khoảng cố định và miền của bộ chọn, không sao chép mảng khi chọn màu. */
        private static final WoolColor[] COLORS = values();
        private static final int TOTAL_ROLLS = 100_000;
        private final int rgb;
        private final int upperBound;

        /** Gắn màu nhân lên len và khoảng tích lũy của biến thể. */
        WoolColor(int rgb, int upperBound) {
            this.rgb = rgb;
            this.upperBound = upperBound;
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
    }

    /** Màu len theo cá thể, model và lớp phủ vẫn được chia sẻ. */
    private final WoolColor woolColor;

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

    /** Giữ tư thế cúi đầu tới lúc ăn, dùng đường thay block bình thường để cập nhật cây cạnh. */
    private static final class GrazeGoal implements MobGoal {
        /** Thời gian cúi đầu, mốc gặm và tổng thời gian cho một lần ăn. */
        private static final float EASE_SECONDS = 0.35f;
        private static final float EAT_SECONDS = 0.9f;
        private static final float DURATION = 1.45f;
        /** Sai số nhỏ khi đọc mặt đỡ ở biên độ cao nguyên. */
        private static final float GROUND_EPSILON = 0.001f;
        private double nextAttempt = ThreadLocalRandom.current().nextFloat(3f, 8f);
        private float elapsed;
        private int foodX;
        private int foodY;
        private int foodZ;
        private Block food;

        /** Đặt ăn trước Wander nhưng cho phép chạy hoảng thay quyền. */
        @Override public int priority() { return 5; }
        /** Giữ cả chuyển động ngang và đầu để tránh tư thế xung đột. */
        @Override public int controls() { return MOVE | LOOK; }

        /** Chọn Short Grass ở chân, hoặc Grass Block tại mặt đỡ nếu không có bụi cỏ. */
        @Override
        public boolean canStart(World world, MobEntity sheep) {
            if (sheep.ageSeconds() < nextAttempt || !sheep.isOnGround() || sheep.inWater() || sheep.panicTime() > 0f) {
                return false;
            }
            scheduleNext(sheep);
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

        /** Nội suy góc cúi, xác nhận đúng khối tại mốc ăn rồi đổi trạng thái một lần. */
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
            }
        }

        /** Đưa đầu về tư thế thường lúc ăn xong hoặc bị goal ưu tiên hơn ngắt. */
        @Override
        public void stop(World world, MobEntity sheep) {
            sheep.mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }

        /** Tách các lần thử để cừu có thời gian lang thang giữa các lần ăn. */
        private void scheduleNext(MobEntity sheep) {
            nextAttempt = sheep.ageSeconds() + ThreadLocalRandom.current().nextFloat(7f, 13f);
        }

        /** Đổi tọa độ thành ô voxel. */
        private static int floor(float coordinate) {
            return (int) Math.floor(coordinate);
        }
    }
}
