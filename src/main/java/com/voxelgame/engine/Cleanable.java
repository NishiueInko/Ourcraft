package com.voxelgame.engine;

/**
 * Giao diện chuẩn hóa vòng đời giải phóng tài nguyên hệ thống (GPU VRAM, OpenGL VAO/VBO/Shader/Texture,
 * luồng âm thanh nền và bộ nhớ Off-heap).
 *
 * THỂ HIỆN TÍNH TRỪU TƯỢNG (ABSTRACTION) & ĐA HÌNH (POLYMORPHISM):
 * - Mọi hệ thống con nắm giữ tài nguyên cấp thấp (`AudioManager`, `ShaderProgram`, `Texture`,
 *   `UIRenderer`, `Chunk`, `World`) đều triển khai giao diện `Cleanable`.
 * - Khi đóng cửa sổ game, `Window` chỉ cần duyệt qua danh sách `List<Cleanable>` và gọi đa hình
 *   `resource.cleanup()` mà không cần phụ thuộc vào chi tiết cài đặt cụ thể của từng lớp.
 */
public interface Cleanable {

    /**
     * Giải phóng toàn bộ tài nguyên phần cứng hoặc luồng chạy ngầm do đối tượng nắm giữ.
     */
    void cleanup();
}
