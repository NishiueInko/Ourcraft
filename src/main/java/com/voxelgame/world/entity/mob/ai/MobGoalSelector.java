package com.voxelgame.world.entity.mob.ai;

import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.level.World;

import java.util.ArrayList;
import java.util.Objects;

/** Điều phối mục tiêu theo ưu tiên và quyền điều khiển, dùng bộ nhớ đã có trong vòng cập nhật. */
public final class MobGoalSelector {
    /** Dãy sắp theo ưu tiên, dùng thứ tự đăng ký để chọn giữa các ứng viên đồng hạng. */
    private final ArrayList<Entry> entries = new ArrayList<>();

    /** Khóa đăng ký trong lúc các callback đang được gọi. */
    private boolean updating;

    /** Đăng ký một đối tượng mục tiêu cho cá thể hiện tại. */
    public void add(MobGoal goal) {
        Objects.requireNonNull(goal);
        if (updating) {
            throw new IllegalStateException("Không thể đăng ký mục tiêu ngay trong nhịp cập nhật");
        }
        int priority = goal.priority();
        int insertion = entries.size();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.goal == goal) {
                return;
            }
            if (priority < entry.priority && insertion == entries.size()) {
                insertion = i;
            }
        }
        entries.add(insertion, new Entry(goal, priority));
    }

    /** Giải phóng kênh hết hạn, chọn mục tiêu đủ điều kiện, rồi cập nhật các mục tiêu đã chọn. */
    public void tick(World world, MobEntity mob, float dt) {
        if (!(dt > 0f) || !Float.isFinite(dt)) {
            return;
        }
        if (updating) {
            throw new IllegalStateException("Không thể lồng các nhịp cập nhật mục tiêu");
        }
        updating = true;
        try {
            int count = entries.size();
            for (int i = 0; i < count; i++) {
                Entry entry = entries.get(i);
                entry.retryRemaining = Math.max(0f, entry.retryRemaining - dt);
                if (entry.running && !entry.goal.shouldContinue(world, mob)) {
                    stop(entry, world, mob);
                }
            }
            for (int i = 0; i < count; i++) {
                Entry candidate = entries.get(i);
                if (candidate.running || candidate.retryRemaining > 0f || isBlocked(candidate)) {
                    continue;
                }
                candidate.retryRemaining = candidate.goal.retrySeconds();
                if (candidate.goal.canStart(world, mob)) {
                    for (int j = i + 1; j < count; j++) {
                        Entry running = entries.get(j);
                        if (running.running && (running.controls & candidate.controls) != 0) {
                            stop(running, world, mob);
                        }
                    }
                    candidate.running = true;
                    candidate.goal.start(world, mob);
                }
            }
            for (int i = 0; i < count; i++) {
                Entry entry = entries.get(i);
                if (entry.running) {
                    entry.goal.tick(world, mob, dt);
                }
            }
        } finally {
            updating = false;
        }
    }

    /** Dừng mọi mục tiêu hoạt động, ví dụ khi mob nhận điều khiển trực tiếp từ người cưỡi. */
    public void stopAll(World world, MobEntity mob) {
        if (updating) {
            throw new IllegalStateException("Không thể dừng bộ chọn ngay trong nhịp cập nhật");
        }
        updating = true;
        try {
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (entry.running) {
                    stop(entry, world, mob);
                }
            }
        } finally {
            updating = false;
        }
    }

    /** Mục tiêu ưu tiên bằng hoặc cao hơn đã giữ kênh thì ứng viên chờ đến nhịp khác. */
    private boolean isBlocked(Entry candidate) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.priority > candidate.priority) {
                break;
            }
            if (entry.running && (entry.controls & candidate.controls) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Xóa trạng thái đang chạy trước khi gọi hành vi kết thúc. */
    private static void stop(Entry entry, World world, MobEntity mob) {
        entry.running = false;
        entry.goal.stop(world, mob);
    }

    /** Siêu dữ liệu cố định và bộ đếm tái thử của mỗi mục tiêu. */
    private static final class Entry {
        private final MobGoal goal;
        private final int priority;
        private final int controls;
        private float retryRemaining;
        private boolean running;

        /** Chụp ưu tiên và các kênh khi đăng ký, dùng nguyên trạng đến khi bỏ bộ chọn. */
        private Entry(MobGoal goal, int priority) {
            this.goal = goal;
            this.priority = priority;
            controls = goal.controls();
        }
    }
}
