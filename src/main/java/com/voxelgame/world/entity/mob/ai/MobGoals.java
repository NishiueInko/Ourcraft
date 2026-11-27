package com.voxelgame.world.entity.mob.ai;

import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.mob.MobAttitude;
import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.mob.MobType;
import com.voxelgame.world.entity.mob.monster.Zombie;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.math.Vector3;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Các mục tiêu nền tảng để lớp hành vi của từng loài ghép theo nhu cầu. */
public final class MobGoals {
    /** Số ô đích ngẫu nhiên được thử tối đa mỗi lần tìm đường đi lang thang hoặc bỏ chạy. */
    private static final int DESTINATION_ATTEMPTS = 12;
    /** Lớp tập hợp các mục tiêu không có trạng thái dùng chung giữa cá thể. */
    private MobGoals() {
    }

    /** Gắn các hành vi chung theo ưu tiên, để lớp cụ thể có thể bổ sung hành vi đặc thù. */
    public static void addDefaults(MobGoalSelector selector, MobType type) {
        addDefaults(selector, type, true);
    }

    /** Cho phép loài có cách tấn công riêng giữ hành vi chung và tự chọn mục tiêu chiến đấu. */
    public static void addDefaults(MobGoalSelector selector, MobType type, boolean includeMelee) {
        selector.add(new FloatInWaterGoal(0));
        if (type.burnsInSunlight()) {
            selector.add(new SunlightShelterGoal(1));
        }
        if (type.attitude() == MobAttitude.PASSIVE) {
            selector.add(new PanicGoal(2));
        } else if (includeMelee) {
            selector.add(new MeleeAttackGoal(4, 1.2f, 2f, 0.95f, 1f, 20f));
        }
        switch (type) {
            case SKELETON, STRAY -> selector.add(new FleeEntityGoal(3, 6f, 1.25f,
                    entity -> entity instanceof MobEntity animal && animal.type() == MobType.WOLF));
            case RABBIT, SNOW_RABBIT -> {
                selector.add(new FleeEntityGoal(3, 8f, 1.25f, entity ->
                        (entity instanceof Player player && !holdsItem(player, MobGoals::isRabbitLure))
                        || (entity instanceof MobEntity animal
                        && (animal.type() == MobType.WOLF || animal.type().category() == MobCategory.MONSTER))));
                selector.add(new TemptFollowGoal(5, MobGoals::isRabbitLure, 10f, 0.8f));
            }
            case SNOW_FOX -> selector.add(new FleeEntityGoal(3, 8f, 1.25f,
                    entity -> entity instanceof Player player && !player.isSneaking()));
            case CAMEL -> selector.add(new TemptFollowGoal(5, Block.CACTUS, 10f, 0.85f));
            default -> {
                // Các loài còn lại dùng nhóm mục tiêu chung ở trên.
            }
        }
        selector.add(new WanderGoal(6, 9, 0.7f));
        selector.add(new LookAtPlayerGoal(7, 8f));
        selector.add(new RandomLookAroundGoal(8));
    }

    /** Đọc khoảng cách bình phương đến tâm đã được chép vào bộ đệm. */
    private static float distanceSquared(MobEntity mob, Vector3 center) {
        float dx = center.x - mob.x();
        float dy = center.y - (mob.y() + mob.getHeight() * 0.5f);
        float dz = center.z - mob.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Vật phẩm dụ thỏ mặc định: bồ công anh hoặc cà rốt. */
    private static boolean isRabbitLure(Item item) {
        return item == Item.CARROT || item == Item.of(Block.DANDELION);
    }

    /** Xem đúng ô Hotbar đang chọn của người chơi và chỉ nhận vật phẩm thỏa điều kiện còn số lượng. */
    private static boolean holdsItem(Player player, Predicate<Item> matches) {
        return player.getHotbar().getSelectedCount() > 0 && matches.test(player.getHotbar().getSelectedItem());
    }

    /** Đổi tọa độ sang ô voxel, kể cả phía ngoài mép đang kiểm tra. */
    private static int floor(float value) {
        return (int) Math.floor(value);
    }

    /** Cấu hình quyền điều khiển và móc vòng đời mặc định cho mục tiêu đơn giản. */
    private abstract static class Goal implements MobGoal {
        private final int priority;
        private final int controls;

        /** Lưu các thuộc tính cố định dùng trong bộ chọn. */
        private Goal(int priority, int controls) {
            this.priority = priority;
            this.controls = controls;
        }

        /** Đọc mức ưu tiên lúc đăng ký. */
        @Override
        public final int priority() {
            return priority;
        }

        /** Đọc nhóm kênh mục tiêu cần điều khiển. */
        @Override
        public final int controls() {
            return controls;
        }

        /** Hành vi đơn giản giữ quyền khi điều kiện kích hoạt vẫn đúng. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return canStart(world, mob);
        }

        /** Mục tiêu chỉ thay điều khiển trong tick có thể dùng móc khởi động rỗng. */
        @Override
        public void start(World world, MobEntity mob) {
        }

        /** Mục tiêu chỉ điều khiển một nhịp có thể dùng móc kết thúc rỗng. */
        @Override
        public void stop(World world, MobEntity mob) {
        }
    }

    /** Hành vi có sở hữu đường đi và bỏ yêu cầu điều hướng khi bị thay thế. */
    private abstract static class MovementGoal extends Goal {
        /** Giữ kênh di chuyển và các kênh bổ sung do hành vi sử dụng. */
        private MovementGoal(int priority, int extraControls) {
            super(priority, MOVE | extraControls);
        }

        /** Dọn điểm đích trước khi giao kênh di chuyển cho hành vi khác. */
        @Override
        public void stop(World world, MobEntity mob) {
            mob.stopMoving();
        }
    }

    /** Bộ đệm tọa độ đích và tập phép chọn ô hợp lệ dùng lại nhiều lượt. */
    private static final class Destination {
        private float x;
        private float y;
        private float z;

        /** Giữ tâm ngang và cao độ chân của ô đích. */
        private void set(int x, float y, int z) {
            this.x = x + 0.5f;
            this.y = y;
            this.z = z + 0.5f;
        }

        /** Chọn ô ngẫu nhiên hợp lệ, có thể ưu tiên hướng rời khỏi một mối đe dọa. */
        private boolean sample(World world, MobEntity mob, int radius, float awayX, float awayZ) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int baseX = floor(mob.x());
            int baseY = floor(mob.y());
            int baseZ = floor(mob.z());
            boolean directional = Math.abs(awayX) + Math.abs(awayZ) > 0.001f;
            float bestAlignment = 0f;
            boolean found = false;
            for (int i = 0; i < DESTINATION_ATTEMPTS; i++) {
                int dx = random.nextInt(-radius, radius + 1);
                int dz = random.nextInt(-radius, radius + 1);
                int distance = dx * dx + dz * dz;
                if (distance < 4 || distance > radius * radius) {
                    continue;
                }
                float alignment = dx * awayX + dz * awayZ;
                if (directional && alignment <= bestAlignment) {
                    continue;
                }
                float feetY = mob.navigator().findStandableY(world, baseX + dx, baseY, baseZ + dz);
                if (Float.isFinite(feetY)) {
                    set(baseX + dx, feetY, baseZ + dz);
                    if (!directional) {
                        return true;
                    }
                    bestAlignment = alignment;
                    found = true;
                }
            }
            return found;
        }

        /** Nhận đích khi tâm đã vào gần ô và cao độ đã khớp địa hình của điểm đến. */
        private boolean reached(MobEntity mob) {
            float dx = x - mob.x();
            float dz = z - mob.z();
            return dx * dx + dz * dz <= 0.16f && Math.abs(y - mob.y()) <= 1f;
        }

        /** Giao tọa độ đích cho bộ điều hướng chịu trách nhiệm tránh vật cản. */
        private void move(World world, MobEntity mob, float speed) {
            mob.moveToward(world, x, y, z, speed);
        }
    }

    /** Nhấc người khi ở trong nước, đồng thời vẫn cho mục tiêu khác điều khiển hướng ngang. */
    public static final class FloatInWaterGoal extends Goal {
        /** Thiết lập ưu tiên của kênh bơi. */
        public FloatInWaterGoal(int priority) {
            super(priority, JUMP);
        }

        /** Bơi nổi khi phần cần kiểm tra của thực thể đang ngập nước. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            return mob.inWater();
        }

        /** Yêu cầu lực nổi trong đúng nhịp vật lý hiện tại của mob. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            mob.requestFloat();
        }
    }

    /** Tìm chỗ có mái hoặc nước gần nhất theo từng nhóm nhỏ ô đã sắp theo khoảng cách. */
    public static final class SunlightShelterGoal extends MovementGoal {
        /** Bán kính tìm kiếm, ngân sách mỗi lần xét và thời gian tối đa cho một đường chạy. */
        private static final int SEARCH_RADIUS = 7;
        private static final int CELLS_PER_SEARCH = 20;
        private static final float MAX_ROUTE_SECONDS = 5f;
        private static final float CLOSE_COMBAT_RANGE_SQUARED = 8f * 8f;
        private static final int[] OFFSETS = sortedOffsets();

        private final Destination destination = new Destination();
        private int originX;
        private int originY;
        private int originZ;
        private int nextOffset;
        private float remaining;
        private boolean searching;
        private final Vector3 combatTarget = new Vector3();

        /** Gắn hành vi tìm nơi trú tránh cháy nắng. */
        public SunlightShelterGoal(int priority) {
            super(priority, 0);
        }

        /** Chuẩn bị danh sách ô theo bình phương khoảng cách, chỉ cấp phát khi nạp lớp. */
        private static int[] sortedOffsets() {
            int limit = SEARCH_RADIUS * SEARCH_RADIUS;
            int[] pairs = new int[2 * ((2 * SEARCH_RADIUS + 1) * (2 * SEARCH_RADIUS + 1) - 1)];
            int size = 0;
            for (int distance = 1; distance <= limit; distance++) {
                for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                    for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                        if (dx * dx + dz * dz == distance) {
                            pairs[size++] = dx;
                            pairs[size++] = dz;
                        }
                    }
                }
            }
            return Arrays.copyOf(pairs, size);
        }

        /** Chia lượt tìm bóng râm ra nhiều nhịp, tránh quét cả vùng trong một frame. */
        @Override
        public float retrySeconds() {
            return 0.10f + ThreadLocalRandom.current().nextFloat() * 0.04f;
        }

        /** Chọn ô râm mát hoặc nước đầu tiên có thể dùng theo thứ tự gần đến xa. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            if (!needsShelter(world, mob)) {
                searching = false;
                return false;
            }
            int mobX = floor(mob.x());
            int mobY = floor(mob.y());
            int mobZ = floor(mob.z());
            if (!searching || Math.abs(mobX - originX) > 2 || Math.abs(mobY - originY) > 1
                    || Math.abs(mobZ - originZ) > 2) {
                originX = mobX;
                originY = mobY;
                originZ = mobZ;
                nextOffset = 0;
                searching = true;
            }
            for (int i = 0; i < CELLS_PER_SEARCH && nextOffset < OFFSETS.length; i++) {
                int x = originX + OFFSETS[nextOffset++];
                int z = originZ + OFFSETS[nextOffset++];
                float feetY = findShelter(world, mob, x, z);
                if (Float.isFinite(feetY)) {
                    destination.set(x, feetY, z);
                    return true;
                }
            }
            searching = nextOffset < OFFSETS.length;
            return false;
        }

        /** Bỏ cột trống sớm, rồi xác minh nước hoặc mái che tại cao độ đứng thực. */
        private float findShelter(World world, MobEntity mob, int x, int z) {
            boolean water = false;
            for (int y = originY; y >= originY - 3 && !water; y--) {
                water = world.getBlock(x, y, z) == Block.WATER.getId();
            }
            float headOffset = mob.getHeight();
            if (!water && !world.hasWeatherRoof(x, originY - 3 + headOffset, z)) {
                return Float.NaN;
            }
            float feetY = mob.navigator().findStandableY(world, x, originY, z);
            if (!Float.isFinite(feetY)) {
                return Float.NaN;
            }
            return world.getBlock(x, floor(feetY + 0.1f), z) == Block.WATER.getId()
                    || world.hasWeatherRoof(x, feetY + headOffset, z) ? feetY : Float.NaN;
        }

        /** Nhả kênh di chuyển ngay khi hết phơi nắng hoặc cần đánh mục tiêu ở gần. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return remaining > 0f && !destination.reached(mob) && needsShelter(world, mob);
        }

        /** Zombie đang áp sát mục tiêu ưu tiên cận chiến, kể cả khi còn ở ngoài nắng. */
        private boolean needsShelter(World world, MobEntity mob) {
            if (!mob.burnsInSunlight() || mob.getFireTimer() <= 0f || !mob.isSunExposed(world)) {
                return false;
            }
            LivingEntity target = mob.target();
            return !(mob instanceof Zombie) || target == null || !target.isAlive()
                    || distanceSquared(mob, target.copyCenterPosition(combatTarget)) > CLOSE_COMBAT_RANGE_SQUARED;
        }

        /** Giới hạn thời gian cố bám một chỗ trú bị chặn bởi địa hình. */
        @Override
        public void start(World world, MobEntity mob) {
            remaining = MAX_ROUTE_SECONDS;
        }

        /** Tiếp tục chạy tới bóng râm hoặc điểm nước đã chọn. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            remaining -= dt;
            destination.move(world, mob, 1.1f);
        }
    }

    /** Chạy nhanh hơn 25% trong cửa sổ hoảng loạn do MobEntity tạo khi trúng đòn. */
    public static final class PanicGoal extends MovementGoal {
        /** Bán kính điểm chạy tán loạn. */
        private static final int RADIUS = 7;
        private final Destination destination = new Destination();
        private final Vector3 attackerCenter = new Vector3();

        /** Gắn ưu tiên phản ứng bị thương cho mob hiền. */
        public PanicGoal(int priority) {
            super(priority, 0);
        }

        /** Cách một khoảng ngắn trước khi xét lại địa hình không có ô chạy hợp lệ. */
        @Override
        public float retrySeconds() {
            return 0.15f;
        }

        /** Chọn hướng tháo chạy sau một đòn đánh đã trúng. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            return mob.panicTime() > 0f && findEscape(world, mob);
        }

        /** Giữ trạng thái chạy trong suốt cửa sổ hoảng loạn. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return mob.panicTime() > 0f;
        }

        /** Đổi đích nếu đã đến nơi mà cửa sổ hoảng loạn vẫn còn. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            if (!destination.reached(mob) || findEscape(world, mob)) {
                destination.move(world, mob, 1.25f);
            } else {
                mob.stopMoving();
            }
        }

        /** Ưu tiên rời khỏi nguồn đánh và chọn hướng bất kỳ nếu đường đó bị chặn. */
        private boolean findEscape(World world, MobEntity mob) {
            Entity attacker = mob.lastAttacker();
            if (attacker != null) {
                attacker.copyCenterPosition(attackerCenter);
                if (destination.sample(world, mob, RADIUS, mob.x() - attackerCenter.x, mob.z() - attackerCenter.z)) {
                    return true;
                }
            }
            return destination.sample(world, mob, RADIUS, 0f, 0f);
        }
    }

    /** Bỏ chạy khỏi thực thể thỏa điều kiện của loài, kiểm tra đối tượng còn sống trong bán kính. */
    public static final class FleeEntityGoal extends MovementGoal {
        private final float rangeSquared;
        private final int escapeRadius;
        private final float speed;
        private final Predicate<LivingEntity> matches;
        private final Vector3 center = new Vector3();
        private final Destination destination = new Destination();
        private LivingEntity threat;
        private float repathDelay;

        /** Cấu hình loại thực thể bị sợ, bán kính phát hiện và hệ số tốc độ chạy. */
        public FleeEntityGoal(int priority, float range, float speedMultiplier, Predicate<LivingEntity> matches) {
            super(priority, 0);
            rangeSquared = range * range;
            escapeRadius = Math.max(3, Math.min(10, (int) Math.ceil(range)));
            speed = speedMultiplier;
            this.matches = Objects.requireNonNull(matches);
        }

        /** Giới hạn tần suất truy vấn danh sách thực thể khi đang ở trạng thái bình thường. */
        @Override
        public float retrySeconds() {
            return 0.20f + ThreadLocalRandom.current().nextFloat() * 0.10f;
        }

        /** Tìm mối đe dọa gần nhất và ô đích nằm về hướng ngược lại. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            LivingEntity closest = null;
            float bestDistance = rangeSquared;
            Player player = world.getPlayer();
            if (player != null && player.isAlive() && matches.test(player)) {
                player.copyCenterPosition(center);
                float distance = distanceSquared(mob, center);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    closest = player;
                }
            }
            for (int i = 0, count = world.entityCount(); i < count; i++) {
                if (!(world.entityAt(i) instanceof LivingEntity other) || other == mob || other == player
                        || !other.isAlive() || !matches.test(other)) {
                    continue;
                }
                other.copyCenterPosition(center);
                float distance = distanceSquared(mob, center);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    closest = other;
                }
            }
            if (closest == null) {
                return false;
            }
            closest.copyCenterPosition(center);
            if (!destination.sample(world, mob, escapeRadius, mob.x() - center.x, mob.z() - center.z)) {
                return false;
            }
            threat = closest;
            return true;
        }

        /** Dừng nếu thực thể không còn đáng sợ, ví dụ người chơi vừa bắt đầu cúi rón rén. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            if (threat == null || !threat.isAlive() || !matches.test(threat)) {
                return false;
            }
            threat.copyCenterPosition(center);
            return distanceSquared(mob, center) <= rangeSquared;
        }

        /** Bắt đầu nhịp cập nhật lại đích thoát hiểm. */
        @Override
        public void start(World world, MobEntity mob) {
            repathDelay = 0.45f;
        }

        /** Làm mới hướng tháo chạy để tránh chạy về phía đối tượng đang tiếp cận. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            repathDelay -= dt;
            if (repathDelay <= 0f) {
                threat.copyCenterPosition(center);
                destination.sample(world, mob, escapeRadius, mob.x() - center.x, mob.z() - center.z);
                repathDelay = 0.45f;
            }
            if (destination.reached(mob)) {
                mob.stopMoving();
            } else {
                destination.move(world, mob, speed);
            }
        }

        /** Bỏ tham chiếu mục tiêu cũ và trả điều khiển cho bộ chọn. */
        @Override
        public void stop(World world, MobEntity mob) {
            threat = null;
            super.stop(world, mob);
        }
    }

    /** Bám mục tiêu đã chọn qua bộ điều hướng và đánh cận chiến khi có đường đánh thông. */
    public static final class MeleeAttackGoal extends MovementGoal {
        private final float speed;
        private final float damage;
        private final float reach;
        private final float cooldownSeconds;
        private final float rangeSquared;
        private final Vector3 targetCenter = new Vector3();
        private final Vector3 origin = new Vector3();
        private final Vector3 direction = new Vector3();
        private final Raycast.HitResult occluder = Raycast.HitResult.miss();
        private double nextAttack;

        /** Cấu hình tốc độ bám, sát thương, tầm từ mép hộp, hồi chiêu và giới hạn đuổi. */
        public MeleeAttackGoal(int priority, float speedMultiplier, float damage, float reach,
                               float cooldownSeconds, float maxPursuitRange) {
            super(priority, LOOK);
            speed = speedMultiplier;
            this.damage = damage;
            this.reach = reach;
            this.cooldownSeconds = cooldownSeconds;
            rangeSquared = maxPursuitRange * maxPursuitRange;
        }

        /** Nhận mục tiêu còn sống trong khoảng truy đuổi của hành vi hiện tại. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            LivingEntity target = mob.target();
            if (target == null || target == mob || !target.isAlive()) {
                return false;
            }
            target.copyCenterPosition(targetCenter);
            return distanceSquared(mob, targetCenter) <= rangeSquared;
        }

        /** Bám mục tiêu qua điểm chân và chỉ đánh khi tia không bị địa hình chắn. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            LivingEntity target = mob.target();
            if (target == null || !target.isAlive()) {
                return;
            }
            target.copyCenterPosition(targetCenter);
            float targetY = targetCenter.y - target.getHeight() * 0.5f;
            mob.lookAt(targetCenter.x, targetY + target.getHeight() * 0.8f, targetCenter.z);
            float dx = targetCenter.x - mob.x();
            float dz = targetCenter.z - mob.z();
            float horizontalSquared = dx * dx + dz * dz;
            float allowed = reach + mob.getHalfWidth() + target.getHalfWidth();
            if (horizontalSquared > allowed * allowed || !inVerticalReach(mob, target, targetY)
                    || !canHit(world, mob)) {
                mob.moveToward(world, targetCenter.x, targetY, targetCenter.z, speed);
                return;
            }
            mob.stopMoving();
            double now = mob.ageSeconds();
            if (now >= nextAttack) {
                nextAttack = now + cooldownSeconds;
                float push = horizontalSquared > 0.000001f ? 2f / (float) Math.sqrt(horizontalSquared) : 0f;
                mob.strikeMelee(world, target, damage, dx * push, dz * push);
            }
        }

        /** Giới hạn cận chiến theo khoảng cách dọc giữa hai mép hộp va chạm. */
        private boolean inVerticalReach(MobEntity mob, LivingEntity target, float targetY) {
            return targetY - (mob.y() + mob.getHeight()) <= reach
                    && mob.y() - (targetY + target.getHeight()) <= reach;
        }

        /** Dùng bộ đệm tia sẵn có để ngăn đòn đánh xuyên tường. */
        private boolean canHit(World world, MobEntity mob) {
            origin.set(mob.x(), mob.y() + mob.getHeight() * 0.62f, mob.z());
            direction.set(targetCenter).sub(origin);
            float length = direction.len();
            return length <= 0.0001f || !Raycast.castOccluder(world, origin, direction, length, occluder).isHit();
        }
    }

    /** Đi theo người chơi đang chọn một hoặc nhiều loại vật phẩm đã cấu hình. */
    public static final class TemptFollowGoal extends MovementGoal {
        /** Khoảng cách ngang mà mob dừng lại để giữ một khoảng trống trước người chơi. */
        private static final float STOP_DISTANCE_SQUARED = 2.25f;
        private final Predicate<Item> lureMatches;
        private final float rangeSquared;
        private final float speed;
        private final Vector3 center = new Vector3();

        /** Gắn vật phẩm khối dụ theo loài, bán kính phản ứng và tốc độ đi theo. */
        public TemptFollowGoal(int priority, Block lure, float range, float speedMultiplier) {
            this(priority, item -> item == Item.of(Objects.requireNonNull(lure)), range, speedMultiplier);
        }

        /** Gắn một vật phẩm {@link Item} cụ thể làm mồi dụ, bán kính phản ứng và tốc độ đi theo. */
        public TemptFollowGoal(int priority, Item lure, float range, float speedMultiplier) {
            this(priority, item -> item == Objects.requireNonNull(lure), range, speedMultiplier);
        }

        /** Gắn bộ lọc {@link Predicate} trên vật phẩm cầm tay để hỗ trợ nhiều loại mồi dụ cùng lúc. */
        public TemptFollowGoal(int priority, Predicate<Item> lureMatches, float range, float speedMultiplier) {
            super(priority, LOOK);
            this.lureMatches = Objects.requireNonNull(lureMatches);
            rangeSquared = range * range;
            speed = speedMultiplier;
        }

        /** Chỉ phản ứng với vật phẩm trên ô Hotbar đang được chọn của người chơi sống. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            Player player = world.getPlayer();
            if (player == null || !player.isAlive() || !holdsItem(player, lureMatches)) {
                return false;
            }
            player.copyCenterPosition(center);
            return distanceSquared(mob, center) <= rangeSquared;
        }

        /** Tiến tới người chơi, rồi dừng trong khoảng ngắn để tránh ép vào hộp va chạm. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            Player player = world.getPlayer();
            player.copyCenterPosition(center);
            float feetY = center.y - player.getHeight() * 0.5f;
            mob.lookAt(center.x, feetY + player.getHeight() * 0.8f, center.z);
            float dx = center.x - mob.x();
            float dz = center.z - mob.z();
            if (dx * dx + dz * dz <= STOP_DISTANCE_SQUARED && Math.abs(feetY - mob.y()) <= 1f) {
                mob.stopMoving();
            } else {
                mob.moveToward(world, center.x, feetY, center.z, speed);
            }
        }
    }

    /** Đi lang thang đến một ô hợp lệ và nghỉ ngẫu nhiên giữa những lượt đi. */
    public static final class WanderGoal extends MovementGoal {
        /** Khoảng nghỉ đầu lệch pha giữa các cá thể vừa được tạo trong cùng bầy. */
        private static final float MIN_INITIAL_WAIT = 0.75f;
        private final Destination destination = new Destination();
        private final int radius;
        private final float speed;
        private double nextMove = MIN_INITIAL_WAIT + ThreadLocalRandom.current().nextFloat() * 2f;
        private double deadline;

        /** Cấu hình bán kính chọn điểm và hệ số tốc độ của lượt đi. */
        public WanderGoal(int priority, int radius, float speedMultiplier) {
            super(priority, 0);
            this.radius = Math.max(2, radius);
            speed = speedMultiplier;
        }

        /** Thử tìm đường khi đã hết nghỉ, lùi lần thử sau nếu địa hình quá hẹp. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            double now = mob.ageSeconds();
            if (now < nextMove) {
                return false;
            }
            if (!destination.sample(world, mob, radius, 0f, 0f)) {
                nextMove = now + 2f;
                return false;
            }
            return true;
        }

        /** Tiếp tục tới điểm đã chọn cho đến lúc chạm đích hoặc hết thời hạn đi. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return mob.ageSeconds() < deadline && !destination.reached(mob);
        }

        /** Cho lượt đi đủ thời gian theo tốc độ và bán kính đã cấu hình. */
        @Override
        public void start(World world, MobEntity mob) {
            deadline = mob.ageSeconds() + radius / Math.max(0.5f, mob.moveSpeed() * speed) + 2f;
        }

        /** Chuyển yêu cầu tiến về đích cho bộ điều hướng nhẹ. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            destination.move(world, mob, speed);
        }

        /** Nghỉ từ hai đến năm giây sau khi dừng hoặc nhường chỗ cho hành vi khác. */
        @Override
        public void stop(World world, MobEntity mob) {
            nextMove = mob.ageSeconds() + 2f + ThreadLocalRandom.current().nextFloat() * 3f;
            super.stop(world, mob);
        }
    }

    /** Theo dõi người chơi trong khoảng ngắn, để hành vi nhìn quanh có thời gian hoạt động. */
    public static final class LookAtPlayerGoal extends Goal {
        private final float rangeSquared;
        private final Vector3 center = new Vector3();
        private float eyeY;
        private double lookUntil;
        private double nextLook;

        /** Đặt bán kính để mob có thể quan sát người chơi. */
        public LookAtPlayerGoal(int priority, float range) {
            super(priority, LOOK);
            rangeSquared = range * range;
        }

        /** Bắt đầu quan sát khi đã hết nghỉ và người chơi ở trong phạm vi. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            return mob.ageSeconds() >= nextLook && playerInRange(world, mob);
        }

        /** Dừng khi hết nhịp nhìn hoặc người chơi rời vùng quan sát. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return mob.ageSeconds() < lookUntil && playerInRange(world, mob);
        }

        /** Chép vị trí người chơi vào bộ đệm để dùng tiếp trong nhịp xoay đầu. */
        private boolean playerInRange(World world, MobEntity mob) {
            Player player = world.getPlayer();
            if (player == null || !player.isAlive()) {
                return false;
            }
            player.copyCenterPosition(center);
            eyeY = center.y + player.getHeight() * 0.3f;
            return distanceSquared(mob, center) <= rangeSquared;
        }

        /** Giới hạn một lượt quan sát trong hai đến năm giây. */
        @Override
        public void start(World world, MobEntity mob) {
            lookUntil = mob.ageSeconds() + 2f + ThreadLocalRandom.current().nextFloat() * 3f;
        }

        /** Nội suy đầu về phía người chơi qua kênh hướng nhìn của MobEntity. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            mob.lookAt(center.x, eyeY, center.z);
        }

        /** Chừa một khoảng nghỉ trước khi nhìn lại. */
        @Override
        public void stop(World world, MobEntity mob) {
            nextLook = mob.ageSeconds() + 1f + ThreadLocalRandom.current().nextFloat() * 2f;
        }
    }

    /** Chọn một hướng ngang tự nhiên và giữ ngắn hạn khi không theo dõi mục tiêu khác. */
    public static final class RandomLookAroundGoal extends Goal {
        private float lookX;
        private float lookY;
        private float lookZ;
        private double lookUntil;
        private double nextLook = 0.5f + ThreadLocalRandom.current().nextFloat() * 2f;

        /** Đặt mức ưu tiên thấp cho chuyển động nhìn quanh khi rảnh. */
        public RandomLookAroundGoal(int priority) {
            super(priority, LOOK);
        }

        /** Chỉ chọn hướng mới khi đã đến hết khoảng nghỉ. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            return mob.ageSeconds() >= nextLook;
        }

        /** Duy trì hướng ngẫu nhiên đến hết thời gian nhìn. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return mob.ageSeconds() < lookUntil;
        }

        /** Chọn một điểm xa vài block và hơi lệch độ cao đầu của mob. */
        @Override
        public void start(World world, MobEntity mob) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double angle = random.nextDouble(2 * Math.PI);
            lookX = (float) Math.cos(angle) * 5f;
            lookY = mob.getHeight() * 0.8f + random.nextFloat() * 0.8f - 0.4f;
            lookZ = (float) Math.sin(angle) * 5f;
            lookUntil = mob.ageSeconds() + 0.75f + random.nextFloat() * 1.5f;
        }

        /** Giữ hướng tương đối khi mob đồng thời di chuyển đến điểm khác. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            mob.lookAt(mob.x() + lookX, mob.y() + lookY, mob.z() + lookZ);
        }

        /** Hoãn lượt nhìn mới trong một đến bốn giây sau khi dừng. */
        @Override
        public void stop(World world, MobEntity mob) {
            nextLook = mob.ageSeconds() + 1f + ThreadLocalRandom.current().nextFloat() * 3f;
        }
    }
}
