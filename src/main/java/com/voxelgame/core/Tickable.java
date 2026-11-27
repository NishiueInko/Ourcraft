package com.voxelgame.core;

import com.voxelgame.world.level.World;

/**
 * Giao diện hợp đồng chuẩn hóa vòng cập nhật logic theo thời gian thực (deltaTime).
 * Mọi thực thể và hệ thống mô phỏng thế giới đều có thể triển khai giao diện này
 * để được điều phối đa hình trong Game Loop.
 */
public interface Tickable {

    /**
     * Cập nhật trạng thái logic sau một khoảng thời gian khung hình.
     *
     * @param world     Thế giới hiện tại chứa dữ liệu voxel và thực thể.
     * @param deltaTime Thời gian trôi qua giữa hai khung hình (tính bằng giây).
     */
    void tick(World world, float deltaTime);
}
