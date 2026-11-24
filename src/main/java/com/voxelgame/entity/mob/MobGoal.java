package com.voxelgame.entity.mob;

import com.voxelgame.engine.World;

/** Hành vi AI được cấp phát riêng cho một mob và tái sử dụng qua nhiều nhịp cập nhật. */
public interface MobGoal {
    /** Kênh điều hướng ngang, điều khiển điểm đích hoặc dừng lại. */
    int MOVE = 1;
    /** Kênh điều khiển hướng nhìn của đầu. */
    int LOOK = 1 << 1;
    /** Kênh điều khiển nhảy chủ động và bơi nổi. */
    int JUMP = 1 << 2;

    /** Số nhỏ hơn được xét trước và có quyền thay mục tiêu có số lớn hơn. */
    int priority();

    /** Trả tổ hợp kênh cần giữ trong toàn bộ thời gian mục tiêu hoạt động. */
    int controls();

    /** Khoảng nghỉ trước khi thử lại một mục tiêu dùng truy vấn tương đối tốn chi phí. */
    default float retrySeconds() {
        return 0f;
    }

    /** Kiểm tra điều kiện và chuẩn bị dữ liệu khi tất cả kênh có thể tiếp quản. */
    boolean canStart(World world, MobEntity mob);

    /** Kiểm tra mục tiêu đang chạy còn phải tiếp tục ở nhịp hiện tại hay không. */
    boolean shouldContinue(World world, MobEntity mob);

    /** Nhận quyền điều khiển sau khi mọi mục tiêu bị thay đã dừng. */
    void start(World world, MobEntity mob);

    /** Áp dụng hành vi cho nhịp đã được cấp quyền điều khiển. */
    void tick(World world, MobEntity mob, float dt);

    /** Dừng hành vi và bỏ yêu cầu điều khiển để mục tiêu mới tiếp quản. */
    void stop(World world, MobEntity mob);
}
