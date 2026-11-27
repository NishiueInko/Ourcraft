package com.voxelgame.core;

import com.voxelgame.client.render.ShaderProgram;

/**
 * Giao diện hợp đồng chuẩn hóa khả năng kết xuất đồ họa 3D bằng ShaderProgram.
 * Cho phép các đối tượng trong thế giới được vẽ đa hình mà không phụ thuộc vào lớp cụ thể.
 */
public interface Renderable {

    /**
     * Kết xuất đối tượng lên màn hình bằng chương trình shader hiện hành.
     *
     * @param shader Chương trình GLSL đang được kích hoạt.
     */
    void render(ShaderProgram shader);
}
