package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobGoal;
import com.voxelgame.entity.mob.MobGoalSelector;
import com.voxelgame.entity.mob.MobGoals;
import com.voxelgame.entity.mob.MobType;

/** Kiểm tra bộ chọn mục tiêu AI, thứ tự ưu tiên và vòng đời các kênh điều khiển. */
final class MobGoalChecks {
    /** Kiểm tra AI trên CPU bằng các mục tiêu có lịch sử gọi xác định. */
    static void run() {
        checkPreemption();
        checkEqualPriorityAndMultipleControls();
        checkRetryInterval();
        World world = World.empty();
        try {
            checkMeleeOcclusion(world);
            checkTemptFollow(world);
            checkNearestShelter(world);
        } finally {
            world.dispose();
        }
    }

    /** Ưu tiên cao dừng mục tiêu di chuyển cũ trước khi khởi động và không dừng kênh nhìn. */
    private static void checkPreemption() {
        MobGoalSelector selector = new MobGoalSelector();
        StringBuilder events = new StringBuilder();
        Probe walk = new Probe(4, MobGoal.MOVE, 'W', events);
        Probe escape = new Probe(1, MobGoal.MOVE, 'E', events);
        Probe look = new Probe(3, MobGoal.LOOK, 'L', events);
        selector.add(walk);
        selector.add(look);
        selector.add(escape);
        walk.ready = true;
        look.ready = true;
        selector.tick(null, null, 0.05f);
        require(events.toString().equals("+L+W.L.W"), "Hai kênh quan sát và đi phải chạy song song");

        events.setLength(0);
        escape.ready = true;
        selector.tick(null, null, 0.05f);
        require(events.toString().equals("-W+E.E.L"), "Mục tiêu cũ phải dừng trước khi mục tiêu mới khởi động");

        events.setLength(0);
        escape.ready = false;
        selector.tick(null, null, 0.05f);
        require(events.toString().equals("-E+W.L.W"), "Kênh vừa rảnh phải được dùng lại trong cùng nhịp");
        events.setLength(0);
        selector.stopAll(null, null);
        selector.stopAll(null, null);
        require(events.toString().equals("-L-W"), "Dừng bộ chọn phải đóng từng mục tiêu đang chạy đúng một lần");
    }

    /** Mục tiêu đồng hạng đang chạy được giữ và ứng viên dùng hai kênh phải có đủ cả hai. */
    private static void checkEqualPriorityAndMultipleControls() {
        MobGoalSelector selector = new MobGoalSelector();
        StringBuilder events = new StringBuilder();
        Probe first = new Probe(2, MobGoal.MOVE, 'A', events);
        Probe current = new Probe(2, MobGoal.MOVE, 'B', events);
        Probe combined = new Probe(3, MobGoal.MOVE | MobGoal.LOOK, 'C', events);
        Probe look = new Probe(4, MobGoal.LOOK, 'L', events);
        selector.add(first);
        selector.add(current);
        selector.add(combined);
        selector.add(look);
        current.ready = true;
        combined.ready = true;
        look.ready = true;
        selector.tick(null, null, 0.05f);
        require(events.toString().equals("+B+L.B.L"), "Ứng viên cần hai kênh không được chiếm một kênh lẻ");
        events.setLength(0);
        first.ready = true;
        selector.tick(null, null, 0.05f);
        require(events.toString().equals(".B.L"), "Ưu tiên ngang nhau phải giữ mục tiêu đang chạy");
        events.setLength(0);
        current.ready = false;
        selector.tick(null, null, 0.05f);
        require(events.toString().equals("-B+A.A.L"), "Khi mục tiêu đồng hạng kết thúc, kênh mở cho ứng viên chờ");
        events.setLength(0);
        first.ready = false;
        selector.tick(null, null, 0.05f);
        require(events.toString().equals("-A-L+C.C"), "Mục tiêu cần hai kênh được tiếp quản toàn bộ cùng lúc");
    }

    /** Truy vấn đắt tiền được giãn nhịp ngay cả khi mục tiêu chưa kích hoạt. */
    private static void checkRetryInterval() {
        MobGoalSelector selector = new MobGoalSelector();
        Probe search = new Probe(2, MobGoal.MOVE, 'S', new StringBuilder());
        search.retry = 0.25f;
        selector.add(search);
        selector.tick(null, null, 0.125f);
        selector.tick(null, null, 0.125f);
        require(search.startChecks == 1, "Không được thử lại trước thời hạn kiểm tra");
        selector.tick(null, null, 0.125f);
        require(search.startChecks == 2, "Hết khoảng nghỉ phải cho kiểm tra lại");
    }

    /** Cận chiến bám theo mục tiêu sau tường nhưng chỉ gây sát thương khi đường đánh thoáng. */
    private static void checkMeleeOcclusion(World world) {
        ProbeMob attacker = new ProbeMob(MobType.SPIDER, 50.5f, 20f, 50.5f);
        Player target = new Player(52.1f, 20f, 50.5f);
        MobGoals.MeleeAttackGoal attack = new MobGoals.MeleeAttackGoal(1, 1.2f, 3f, 0.9f, 1f, 10f);
        attacker.setTarget(target);
        require(attack.canStart(world, attacker), "Đích còn sống trong tầm phải khởi động cận chiến");
        world.setBlock(51, 20, 50, Block.STONE.getId());
        world.setBlock(51, 21, 50, Block.STONE.getId());
        attack.tick(world, attacker, 0.05f);
        require(target.getHealth() == 20f && attacker.destinationX > attacker.x(),
                "Tường chắn đòn đánh nhưng vẫn cho mục tiêu thử điều hướng");
        world.setBlock(51, 20, 50, Block.AIR.getId());
        world.setBlock(51, 21, 50, Block.AIR.getId());
        attack.tick(world, attacker, 0.05f);
        require(target.getHealth() == 17f, "Khi bỏ tường, đòn trong tầm phải trúng mục tiêu");
        target.update(world, 0.6f);
        attack.tick(world, attacker, 0.05f);
        require(target.getHealth() == 17f, "Goal phải áp hồi chiêu độc lập với miễn sát thương của mục tiêu");
    }

    /** Chỉ ô Hotbar đang chọn được dùng để dụ, kể cả khi có đúng vật phẩm ở ô khác. */
    private static void checkTemptFollow(World world) {
        Player player = new Player(76.5f, 20f, 70.5f);
        world.registerPlayer(player);
        ProbeMob rabbit = new ProbeMob(MobType.RABBIT, 70.5f, 20f, 70.5f);
        MobGoals.TemptFollowGoal follow = new MobGoals.TemptFollowGoal(1, Block.DANDELION, 9f, 0.8f);
        require(!follow.canStart(world, rabbit), "Ô Hotbar đang chọn không chứa vật phẩm dụ");
        int lureSlot = 5;
        require(player.getHotbar().addItem(Block.DANDELION, 1), "Cần nạp bồ công anh vào ô trống đầu tiên");
        require(!follow.canStart(world, rabbit), "Vật phẩm trong ô khác chưa thể dụ mob");
        player.getHotbar().selectSlot(lureSlot);
        require(follow.canStart(world, rabbit), "Thỏ phải nhận ra bồ công anh trên tay");
        follow.tick(world, rabbit, 0.05f);
        require(rabbit.destinationX == 76.5f, "Goal phải gửi điểm chân người chơi tới bộ điều hướng");
    }

    /** Chỗ trú gần hơn được chọn dù ô xa hơn cũng đủ khoảng trống và có mái. */
    private static void checkNearestShelter(World world) {
        for (int x = 99; x <= 106; x++) {
            world.setBlock(x, 19, 100, Block.STONE.getId());
        }
        world.setBlock(103, 23, 100, Block.OAK_LEAVES.getId());
        world.setBlock(105, 23, 100, Block.STONE.getId());
        ProbeMob mob = new ProbeMob(MobType.ZOMBIE, 100.5f, 20f, 100.5f);
        MobGoals.SunlightShelterGoal shelter = new MobGoals.SunlightShelterGoal(1);
        require(!shelter.canStart(world, mob), "Mob chưa cháy không cần chọn nơi trú");
        mob.ignite(2f);
        for (int i = 0; i < 8; i++) {
            if (shelter.canStart(world, mob)) {
                shelter.start(world, mob);
                shelter.tick(world, mob, 0.05f);
                break;
            }
        }
        require(mob.destinationX == 103.5f, "Mob phải chọn mái lá gần hơn");
        mob.setPosition(103.5f, 20f, 100.5f);
        require(!shelter.shouldContinue(world, mob), "Goal trú nắng phải nhả kênh ngay dưới mái");
    }

    /** Phát lỗi theo thông báo cụ thể của hồi quy. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Mục tiêu thử nghiệm ghi vòng đời mà không dùng vật lý, địa hình hay GPU. */
    private static final class Probe implements MobGoal {
        private final int priority;
        private final int controls;
        private final char label;
        private final StringBuilder events;
        private boolean ready;
        private float retry;
        private int startChecks;

        /** Gán ưu tiên, kênh và ký tự nhận diện vào bộ ghi chung. */
        private Probe(int priority, int controls, char label, StringBuilder events) {
            this.priority = priority;
            this.controls = controls;
            this.label = label;
            this.events = events;
        }

        /** Đọc giá trị ưu tiên đã cấu hình. */
        @Override
        public int priority() { return priority; }

        /** Đọc kênh điều khiển đã cấu hình. */
        @Override
        public int controls() { return controls; }

        /** Trả nhịp tìm thử của tình huống hiện tại. */
        @Override
        public float retrySeconds() { return retry; }

        /** Cho phép khởi động theo cờ của tình huống. */
        @Override
        public boolean canStart(World world, MobEntity mob) {
            startChecks++;
            return ready;
        }

        /** Dừng khi cờ điều khiển được tắt. */
        @Override
        public boolean shouldContinue(World world, MobEntity mob) { return ready; }

        /** Ghi thời điểm bắt đầu. */
        @Override
        public void start(World world, MobEntity mob) { events.append('+').append(label); }

        /** Ghi lượt hoạt động. */
        @Override
        public void tick(World world, MobEntity mob, float dt) { events.append('.').append(label); }

        /** Ghi thời điểm kết thúc. */
        @Override
        public void stop(World world, MobEntity mob) { events.append('-').append(label); }
    }

    /** Mob thử nghiệm ghi điểm đích trực tiếp để tách logic chọn khỏi bước quét vật lý. */
    private static final class ProbeMob extends MobEntity {
        private float destinationX = Float.NaN;

        /** Tạo cá thể với đúng hộp và chỉ số của loài đang kiểm tra. */
        private ProbeMob(MobType type, float x, float y, float z) {
            super(type, x, y, z, 0f, 0, false);
        }

        /** Giữ tọa độ của lệnh điều hướng gần nhất. */
        @Override
        public void moveToward(World world, float x, float y, float z, float speedMultiplier) {
            destinationX = x;
        }
    }
}
