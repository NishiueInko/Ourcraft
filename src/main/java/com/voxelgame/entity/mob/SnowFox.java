package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobAppearance;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.Raycast;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.Player;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/** Cáo tuyết nghỉ ở nơi an toàn ban ngày, săn ban đêm và tránh người không rón rén. */
public final class SnowFox extends MobEntity {
    /** Tầm phát hiện người, tầm tìm con mồi và nhịp xét tầm nhìn và vật phẩm. */
    private static final float PLAYER_RANGE = 12f;
    private static final float PREY_RANGE = 16f;
    private static final float SIGHT_INTERVAL = 0.12f;
    private static final float PICKUP_INTERVAL = 0.3f;
    private static final float PICKUP_RANGE = 1.2f;
    /** Xác suất cáo con trong bầy. */
    private static final float BABY_PROBABILITY = 0.05f;

    /** Bộ đệm tia dùng cả cho nhận diện người và săn mồi. */
    private final Vector3 sightOrigin = new Vector3();
    private final Vector3 sightDirection = new Vector3();
    private final Vector3 playerCenter = new Vector3();
    private final Raycast.HitResult occluder = Raycast.HitResult.miss();
    private Player seenPlayer;
    private Player sightCandidate;
    private double nextSight;
    private double nextPickup;
    private boolean sleeping;

    /** Dựng cáo lông tuyết, chọn ngẫu nhiên cá thể non và thiết lập ưu tiên né người rồi mới săn. */
    public SnowFox(float x, float y, float z) {
        super(MobType.SNOW_FOX, x, y, z, 0f, 0, false);
        setMoveSpeed(3.5f);
        setBaby(ThreadLocalRandom.current().nextFloat() < BABY_PROBABILITY);
        goals().add(new MobGoals.FloatInWaterGoal(0));
        goals().add(new MobGoals.FleeEntityGoal(1, PLAYER_RANGE, 1.55f, entity -> entity == seenPlayer));
        goals().add(new MobGoals.MeleeAttackGoal(2, 1.1f, 2f, 0.75f, 1f, 16f));
        goals().add(new FoxPounceGoal());
        goals().add(new SleepGoal());
        goals().add(new MobGoals.WanderGoal(6, 9, 0.7f));
        goals().add(new MobGoals.LookAtPlayerGoal(7, 8f));
        goals().add(new MobGoals.RandomLookAroundGoal(8));
    }

    /** Cho biết cá thể đã đến nơi trú và đang cuộn tròn ngủ. */
    public boolean isSleeping() {
        return sleeping;
    }

    /** Cập nhật người gây sợ trước khi bộ chọn dừng ngủ hoặc hủy việc tiếp cận con mồi. */
    @Override
    protected void updateGoals(World world, float dt) {
        updatePlayerSight(world);
        super.updateGoals(world, dt);
        if (!sleeping && seenPlayer == null && mobVisualState().heldBlock() == null && ageSeconds() >= nextPickup) {
            nextPickup = ageSeconds() + PICKUP_INTERVAL;
            Block carried = world.takeNearbyDroppedBlock(x(), y() + getHeight() * 0.5f, z(), PICKUP_RANGE);
            if (carried != null) {
                mobVisualState().setHeldBlock(carried);
            }
        }
    }

    /** Trả vật phẩm trong miệng về thế giới khi chết, kể cả khi vòng đời kết thúc ngay. */
    @Override
    protected void finishLivingUpdate(World world) {
        if (!isAlive()) {
            setSleeping(false);
            Block carried = mobVisualState().heldBlock();
            if (carried != null) {
                mobVisualState().setHeldBlock(null);
                world.spawnDroppedItem(new ItemEntity(x(), y() + 0.25f, z(), carried));
            }
        }
        super.finishLivingUpdate(world);
    }

    /** Bỏ người đang rón rén ngay lập tức; kiểm tra tia định kỳ đối với người đã vào tầm. */
    private void updatePlayerSight(World world) {
        Player player = world.getPlayer();
        if (player == null || !player.isAlive() || player.isSneaking()) {
            sightCandidate = null;
            seenPlayer = null;
            return;
        }
        player.copyCenterPosition(playerCenter);
        float dx = playerCenter.x - x();
        float dy = playerCenter.y - (y() + getHeight() * 0.5f);
        float dz = playerCenter.z - z();
        if (dx * dx + dy * dy + dz * dz > PLAYER_RANGE * PLAYER_RANGE) {
            sightCandidate = null;
            seenPlayer = null;
            return;
        }
        if (player != sightCandidate || ageSeconds() >= nextSight) {
            sightCandidate = player;
            nextSight = ageSeconds() + SIGHT_INTERVAL;
            seenPlayer = canSee(world, playerCenter.x, playerCenter.y, playerCenter.z) ? player : null;
        }
    }

    /** Kiểm tra đường nhìn từ cao độ đầu cáo đến mục tiêu. */
    private boolean canSee(World world, float x, float y, float z) {
        sightOrigin.set(x(), y() + getHeight() * 0.75f, z());
        sightDirection.set(x, y, z).sub(sightOrigin);
        float distance = sightDirection.len();
        return distance < 0.0001f
                || !Raycast.castOccluder(world, sightOrigin, sightDirection, distance, occluder).isHit();
    }

    /** Dùng cùng ngưỡng độ cao mặt trời với hành vi tiếp xúc ánh nắng. */
    private static boolean daylight(World world) {
        return world.worldTime().sunY() > 0.05f;
    }

    /** Chuyển đồng thời tư thế ngủ và tấm ảnh da, giữ hình thể của cá thể hiện tại. */
    private void setSleeping(boolean sleeping) {
        if (this.sleeping == sleeping) {
            return;
        }
        this.sleeping = sleeping;
        mobVisualState().setAppearance(sleeping ? MobAppearance.SNOW_FOX_SLEEP : MobAppearance.SNOW_FOX);
        mobVisualState().setPose(sleeping ? MobVisualState.Pose.SLEEP : MobVisualState.Pose.IDLE, sleeping ? 1f : 0f);
    }

    /** Cho phép ngủ trên tuyết hoặc ngay dưới tán vân sam trong độ cao hữu hạn. */
    private static boolean shelteredBySnowOrSpruce(World world, int x, float feetY, int z) {
        int feet = (int) Math.floor(feetY + 0.08f);
        int ground = (int) Math.floor(feetY - 0.08f);
        byte surface = world.getBlock(x, ground, z);
        if (surface == Block.SNOW_BLOCK.getId() || surface == Block.SNOW_LAYER.getId()
                || world.getBlock(x, feet, z) == Block.SNOW_LAYER.getId()) {
            return true;
        }
        for (int y = feet + 1; y <= feet + 7; y++) {
            if (world.getBlock(x, y, z) == Block.SPRUCE_LEAVES.getId()) {
                return true;
            }
        }
        return false;
    }

    /** Tìm chỗ ngủ theo khoảng cách; mục tiêu chỉ quét một phần nhỏ bán kính trong mỗi nhịp. */
    private final class SleepGoal implements MobGoal {
        /** Bán kính ô có thể ngủ và ngân sách xét trong một lượt. */
        private static final int RADIUS = 6;
        private static final int SEARCH_BUDGET = 20;
        private static final int[] OFFSETS = nearbyOffsets();
        private int searchX = Integer.MIN_VALUE;
        private int searchY;
        private int searchZ;
        private int cursor;
        private float restX;
        private float restY;
        private float restZ;
        private double arriveBefore;

        /** Chuẩn bị thứ tự ô từ gần đến xa khi lớp mục tiêu được nạp. */
        private static int[] nearbyOffsets() {
            int diameter = 2 * RADIUS + 1;
            int[] pairs = new int[diameter * diameter * 2];
            int length = 0;
            for (int distance = 1; distance <= RADIUS * RADIUS; distance++) {
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        if (dx * dx + dz * dz == distance) {
                            pairs[length++] = dx;
                            pairs[length++] = dz;
                        }
                    }
                }
            }
            return Arrays.copyOf(pairs, length);
        }

        /** Ngủ có quyền trước đi lang thang và nhìn ngẫu nhiên. */
        @Override
        public int priority() { return 4; }

        /** Đường đi, tư thế và hướng đầu được giành từ lúc tìm được vị trí. */
        @Override
        public int controls() { return MOVE | LOOK; }

        /** Phân tán chi phí tìm chỗ ngủ của các cá thể cùng bầy. */
        @Override
        public float retrySeconds() { return 0.16f + ThreadLocalRandom.current().nextFloat() * 0.07f; }

        /** Cáo chỉ ngủ ban ngày khi không có nguy hiểm hoặc mục tiêu đang đối đầu. */
        private boolean safeToRest(World world) {
            return daylight(world) && seenPlayer == null && target() == null && !inWater()
                    && getHurtFlashTimer() <= 0f && getFireTimer() <= 0f;
        }

        /** Thử chỗ hiện tại trước, sau đó dùng thứ tự ô lân cận đã tính sẵn. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            if (!safeToRest(world)) {
                searchX = Integer.MIN_VALUE;
                return false;
            }
            int x = (int) Math.floor(x());
            int y = (int) Math.floor(y());
            int z = (int) Math.floor(z());
            if (shelteredBySnowOrSpruce(world, x, y(), z)) {
                restX = x();
                restY = y();
                restZ = z();
                return true;
            }
            if (searchX != x || searchY != y || searchZ != z || cursor >= OFFSETS.length) {
                searchX = x;
                searchY = y;
                searchZ = z;
                cursor = 0;
            }
            for (int i = 0; i < SEARCH_BUDGET && cursor < OFFSETS.length; i++) {
                int candidateX = searchX + OFFSETS[cursor++];
                int candidateZ = searchZ + OFFSETS[cursor++];
                if (!shelteredBySnowOrSpruce(world, candidateX, searchY, candidateZ)) {
                    continue;
                }
                float feet = navigator().findStandableY(world, candidateX, searchY, candidateZ);
                if (Float.isFinite(feet) && shelteredBySnowOrSpruce(world, candidateX, feet, candidateZ)) {
                    restX = candidateX + 0.5f;
                    restY = feet;
                    restZ = candidateZ + 0.5f;
                    return true;
                }
            }
            return false;
        }

        /** Dừng ngủ khi trời tối, có người gây sợ hoặc nơi trú không còn hợp lệ. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return safeToRest(world) && (sleeping
                    ? isOnGround() && shelteredBySnowOrSpruce(world, (int) Math.floor(x()), y(), (int) Math.floor(z()))
                    : ageSeconds() < arriveBefore);
        }

        /** Giới hạn thời gian thử tới vị trí có mái, để không chạy mãi vào một góc bít. */
        @Override
        public void start(World world, MobEntity mob) {
            arriveBefore = ageSeconds() + 5;
        }

        /** Di chuyển về nơi trú rồi dừng lại và cuộn tròn ngay khi đất đã đỡ chân. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            if (isOnGround() && shelteredBySnowOrSpruce(world, (int) Math.floor(x()), y(), (int) Math.floor(z()))) {
                stopMoving();
                setSleeping(true);
            } else {
                moveToward(world, restX, restY, restZ, 0.8f);
            }
        }

        /** Bỏ pose ngủ để trả quyền cho mục tiêu chạy trốn hoặc hoạt động ban đêm. */
        @Override
        public void stop(World world, MobEntity mob) {
            setSleeping(false);
            stopMoving();
        }
    }

    /** Pha tiến lại gần, lấy đà, nhảy và hồi lại sau khi trượt xuống tuyết mỏng. */
    private enum PouncePhase { APPROACH, CROUCH, AIRBORNE, FACEPLANT, DONE }

    /** Nhắm thỏ hoặc gà, thu người lấy đà và đánh khi vồ chạm con mồi trong lúc hạ xuống. */
    private final class FoxPounceGoal implements MobGoal {
        /** Vận tốc đứng, trọng lực và giới hạn quãng ngang của vòng cung ngắn. */
        private static final float LAUNCH_Y = 10.6f;
        private static final float GRAVITY = 26f;
        private static final float LAUNCH_RANGE_SQUARED = 5.6f * 5.6f;
        /** Giới hạn đuổi tới tầm bật khi con mồi ở sau vật cản. */
        private static final float APPROACH_SECONDS = 6f;
        private static final float CROUCH_SECONDS = 0.35f;
        private static final float FACEPLANT_SECONDS = 0.6f;
        private static final float RECOVERY_SECONDS = 1.35f;
        private MobEntity prey;
        private PouncePhase phase = PouncePhase.DONE;
        private double phaseUntil;
        private double nextPounce;
        private boolean struck;

        /** Săn mồi đứng sau hành vi trốn và tự vệ. */
        @Override
        public int priority() { return 3; }

        /** Tiến gần, ngắm và bật nhảy thuộc cùng một vòng vồ. */
        @Override
        public int controls() { return MOVE | LOOK | JUMP; }

        /** Giới hạn tần suất quét con mồi xung quanh. */
        @Override
        public float retrySeconds() { return 0.28f + ThreadLocalRandom.current().nextFloat() * 0.12f; }

        /** Chọn con mồi còn sống, gần nhất và nhìn thấy trong bán kính mười sáu block. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            if (ageSeconds() < nextPounce || daylight(world) || seenPlayer != null || target() != null || inWater()) {
                return false;
            }
            prey = null;
            float best = PREY_RANGE * PREY_RANGE;
            for (int i = 0, count = world.entityCount(); i < count; i++) {
                if (!(world.entityAt(i) instanceof MobEntity candidate) || !isPrey(candidate) || !candidate.isAlive()) {
                    continue;
                }
                float dx = candidate.x() - x();
                float dy = candidate.y() - y();
                float dz = candidate.z() - z();
                float distance = dx * dx + dy * dy + dz * dz;
                if (distance <= best && canSee(world, candidate.x(),
                        candidate.y() + candidate.getHeight() * 0.5f, candidate.z())) {
                    best = distance;
                    prey = candidate;
                }
            }
            return prey != null;
        }

        /** Nhận hai cấu hình thỏ và gà, không phụ thuộc lớp triển khai cụ thể của con mồi. */
        private boolean isPrey(MobEntity candidate) {
            MobType type = candidate.type();
            return type == MobType.RABBIT || type == MobType.SNOW_RABBIT || type == MobType.CHICKEN;
        }

        /** Giữ quyền cho đến khi đáp đất, ngay cả khi con mồi vừa chết giữa vòng cung. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            if (phase == PouncePhase.AIRBORNE) {
                return ageSeconds() < phaseUntil;
            }
            if (phase == PouncePhase.FACEPLANT) {
                return true;
            }
            if (phase == PouncePhase.DONE || prey == null || !prey.isAlive() || daylight(world) || inWater()) {
                return false;
            }
            if (phase == PouncePhase.APPROACH && ageSeconds() >= phaseUntil) {
                return false;
            }
            float dx = prey.x() - x();
            float dz = prey.z() - z();
            return dx * dx + dz * dz <= (PREY_RANGE + 2f) * (PREY_RANGE + 2f);
        }

        /** Bắt đầu bằng bước tiếp cận; có thể lấy đà ngay nếu đã đứng đủ gần. */
        @Override
        public void start(World world, MobEntity mob) {
            phase = PouncePhase.APPROACH;
            phaseUntil = ageSeconds() + APPROACH_SECONDS;
            struck = false;
        }

        /** Chuyển pha lấy đà, phóng, ghi đòn trúng và xử lý tuyết khi đáp. */
        @Override
        public void tick(World world, MobEntity mob, float dt) {
            switch (phase) {
                case APPROACH -> approach(world);
                case CROUCH -> aimAndLaunch(world);
                case AIRBORNE -> finishLeap(world);
                case FACEPLANT -> {
                    stopMoving();
                    if (ageSeconds() >= phaseUntil) {
                        phase = PouncePhase.DONE;
                    }
                }
                case DONE -> stopMoving();
            }
        }

        /** Tiến tới tầm bật; chỉ hạ thân lấy đà trên mặt đất có đường nhìn. */
        private void approach(World world) {
            float dx = prey.x() - x();
            float dz = prey.z() - z();
            if (isOnGround() && dx * dx + dz * dz <= LAUNCH_RANGE_SQUARED && Math.abs(prey.y() - y()) <= 1.6f
                    && canSee(world, prey.x(), prey.y() + prey.getHeight() * 0.5f, prey.z())) {
                phase = PouncePhase.CROUCH;
                phaseUntil = ageSeconds() + CROUCH_SECONDS;
                stopMoving();
                mobVisualState().setPose(MobVisualState.Pose.CROUCH, 0f);
            } else {
                moveToward(world, prey.x(), prey.y(), prey.z(), 0.85f);
            }
        }

        /** Hướng thân vào con mồi và tính vận tốc ngang để vòng cung gặp chỗ đứng dự kiến. */
        private void aimAndLaunch(World world) {
            stopMoving();
            lookAt(prey.x(), prey.y() + prey.getHeight() * 0.5f, prey.z());
            faceBody(prey.x(), prey.z());
            float remaining = (float) (phaseUntil - ageSeconds());
            if (remaining > 0f) {
                mobVisualState().setPose(MobVisualState.Pose.CROUCH, 1f - remaining / CROUCH_SECONDS);
                return;
            }
            float dx = prey.x() - x();
            float dz = prey.z() - z();
            float landing = navigator().findStandableY(world, (int) Math.floor(prey.x()),
                    (int) Math.floor(y()), (int) Math.floor(prey.z()));
            float discriminant = LAUNCH_Y * LAUNCH_Y - 2f * GRAVITY * (landing - y());
            if (!isOnGround() || !(discriminant > 0f) || dx * dx + dz * dz > LAUNCH_RANGE_SQUARED
                    || !canSee(world, prey.x(), prey.y() + prey.getHeight() * 0.5f, prey.z())) {
                phase = PouncePhase.DONE;
                return;
            }
            float flight = (LAUNCH_Y + (float) Math.sqrt(discriminant)) / GRAVITY;
            float horizontalTime = flight - 0.025f;
            queueLeap(dx / horizontalTime, LAUNCH_Y, dz / horizontalTime, flight);
            phase = PouncePhase.AIRBORNE;
            phaseUntil = ageSeconds() + flight + 0.65f;
            mobVisualState().setPose(MobVisualState.Pose.POUNCE, 1f);
        }

        /** Ghi sát thương một lần ở phần đi xuống và cho trượt vào tuyết khi cú vồ hụt. */
        private void finishLeap(World world) {
            boolean landed = isOnGround();
            if (!struck && prey != null && prey.isAlive() && velocity.y <= 0f) {
                float dx = prey.x() - x();
                float dz = prey.z() - z();
                float reach = getHalfWidth() + prey.getHalfWidth() + 0.18f;
                if (dx * dx + dz * dz <= reach * reach && y() <= prey.y() + prey.getHeight() + 0.2f
                        && y() + getHeight() >= prey.y()
                        && canSee(world, prey.x(), prey.y() + prey.getHeight() * 0.5f, prey.z())) {
                    struck = strikeMelee(world, prey, 2f, dx * 3f, dz * 3f);
                }
            }
            if (landed || inWater()) {
                int blockX = (int) Math.floor(x());
                int blockY = (int) Math.floor(y() + 0.08f);
                int blockZ = (int) Math.floor(z());
                if (landed && !struck && world.getBlock(blockX, blockY, blockZ) == Block.SNOW_LAYER.getId()
                        && world.getBlockState(blockX, blockY, blockZ) < 3) {
                    phase = PouncePhase.FACEPLANT;
                    phaseUntil = ageSeconds() + FACEPLANT_SECONDS;
                    mobVisualState().setPose(MobVisualState.Pose.FACEPLANT, 1f);
                } else {
                    phase = PouncePhase.DONE;
                }
            }
        }

        /** Hạ tư thế đặc biệt và chừa khoảng lấy lại thăng bằng trước lần vồ kế tiếp. */
        @Override
        public void stop(World world, MobEntity mob) {
            prey = null;
            phase = PouncePhase.DONE;
            nextPounce = ageSeconds() + RECOVERY_SECONDS;
            stopMoving();
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }
    }
}
