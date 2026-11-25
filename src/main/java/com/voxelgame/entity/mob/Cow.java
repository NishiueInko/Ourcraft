package com.voxelgame.entity.mob;

import com.voxelgame.engine.World;
import com.voxelgame.entity.Entity;

/** Bò đồng bằng tự đi lang thang và quay lại gần đàn nếu bị tách nhóm. */
public final class Cow extends MobEntity {
    /** Lấy chỉ số và nhóm âm thanh của bò, cùng các goal hiền lành dùng chung. */
    public Cow(float x, float y, float z) {
        super(MobType.COW, x, y, z);
        setMoveSpeed(2f);
        goals().add(new HerdGoal());
    }

    /** Dẫn bò về phía đồng loại trong tầm gần, nhường khi hoảng hoặc khi đã nhập đàn. */
    private static final class HerdGoal implements MobGoal {
        /** Khoảng bắt đầu, khoảng dừng và tầm tìm đồng loại, theo bình phương block. */
        private static final float SEPARATED_SQUARED = 4.5f * 4.5f;
        private static final float REJOINED_SQUARED = 2.5f * 2.5f;
        private static final float SEARCH_SQUARED = 12f * 12f;
        private MobEntity companion;

        /** Giữ kênh di chuyển trước Wander và sau Panic. */
        @Override public int priority() { return 5; }
        /** Để goal khác tiếp tục điều khiển hướng nhìn. */
        @Override public int controls() { return MOVE; }
        /** Chỉ quét đàn theo nhịp nửa giây khi chưa theo cá thể nào. */
        @Override public float retrySeconds() { return 0.5f; }

        /** Chọn đồng loại gần nhất, chỉ theo nếu khoảng trống giữa hai con đủ lớn. */
        @Override
        public boolean canStart(World world, MobEntity self) {
            if (!self.isOnGround() || self.panicTime() > 0f) {
                return false;
            }
            float closest = SEARCH_SQUARED;
            MobEntity nearest = null;
            for (int i = 0, count = world.entityCount(); i < count; i++) {
                Entity entity = world.entityAt(i);
                if (!(entity instanceof MobEntity cow) || cow == self || cow.type() != MobType.COW
                        || !cow.isAlive() || !cow.isOnGround()) {
                    continue;
                }
                float distance = distanceSquared(self, cow);
                if (distance < closest) {
                    closest = distance;
                    nearest = cow;
                }
            }
            companion = closest > SEPARATED_SQUARED ? nearest : null;
            return companion != null;
        }

        /** Tránh huých đồng loại khi đã nhập đàn, cũng bỏ đích đã chết hoặc đi quá xa. */
        @Override
        public boolean shouldContinue(World world, MobEntity self) {
            if (companion == null || !companion.isAlive() || self.panicTime() > 0f) {
                return false;
            }
            float distance = distanceSquared(self, companion);
            return distance > REJOINED_SQUARED && distance < SEARCH_SQUARED;
        }

        /** Đồng loại đã được chọn ngay trong lúc kiểm tra khởi động. */
        @Override public void start(World world, MobEntity self) { }

        /** Gửi đích mới nhất qua điều hướng tránh vực, gai và dung nham dùng chung. */
        @Override
        public void tick(World world, MobEntity self, float dt) {
            self.moveToward(world, companion.x(), companion.y(), companion.z(), 0.85f);
        }

        /** Bỏ tham chiếu tới đồng loại và trả kênh di chuyển cho goal kế tiếp. */
        @Override
        public void stop(World world, MobEntity self) {
            self.stopMoving();
            companion = null;
        }

        /** Khoảng cách chân ba chiều, không tạo bản sao tọa độ. */
        private static float distanceSquared(MobEntity a, MobEntity b) {
            float dx = a.x() - b.x();
            float dy = a.y() - b.y();
            float dz = a.z() - b.z();
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
