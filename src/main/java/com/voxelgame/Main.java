package com.voxelgame;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/**
 * Điểm khởi chạy chính (Entry Point) của dự án Voxel Game 3D (Minecraft Edition).
 *
 * Lớp này chịu trách nhiệm duy nhất: cấu hình cửa sổ Desktop thông qua
 * {@link Lwjgl3ApplicationConfiguration} và khởi tạo {@link Lwjgl3Application}
 * để chạy ứng dụng libGDX với backend LWJGL 3.
 *
 * CẤU TRÚC KIẾN TRÚC SAU TÁI CẤU TRÚC:
 * - {@code com.voxelgame.Main}: Lớp khởi chạy gốc duy nhất (giữ nguyên cấu hình {@code mainClass} trong {@code build.gradle}).
 * - {@code com.voxelgame.VoxelGameApp}: Lớp ứng dụng chính kế thừa {@code ApplicationAdapter},
 *   quản lý toàn bộ vòng lặp Game Loop, xử lý Input, trạng thái Pause Menu.
 * - {@code com.voxelgame.entity}: Package đóng gói cây kế thừa Thực thể ({@code Entity}, {@code Player},
 *   {@code ItemEntity}, {@code Hotbar}, {@code Block} và 9 lớp Block con).
 * - {@code com.voxelgame.engine}: Package đóng gói lõi Engine Đồ họa, Thế giới, Vật lý, Giao diện
 *   và Âm thanh ({@code World}, {@code Chunk}, {@code Camera}, {@code Raycast}, {@code ShaderProgram},
 *   {@code Texture}, {@code UIRenderer}, {@code AudioManager}).
 */
public class Main {

    /**
     * Hàm main() - điểm khởi đầu duy nhất của toàn bộ ứng dụng.
     * Tạo cấu hình cửa sổ Desktop và khởi chạy vòng lặp libGDX.
     *
     * @param args tham số dòng lệnh (không sử dụng)
     */
    public static void main(String[] args) {
        // =====================================================================
        // Tạo cấu hình cửa sổ Desktop thông qua Lwjgl3ApplicationConfiguration
        // =====================================================================
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();

        // Tiêu đề cửa sổ game hiển thị trên thanh tiêu đề hệ điều hành
        config.setTitle("Voxel Game 3D - Minecraft OOP Edition (Java & libGDX LWJGL3)");

        // Kích thước mặc định 1280 x 720 pixel (tỷ lệ 16:9 chuẩn HD)
        config.setWindowedMode(1280, 720);

        // Bật V-Sync đồng bộ tốc độ khung hình với tần số quét màn hình (thường 60 Hz)
        // để đạt 60 FPS mượt mà, loại bỏ hiện tượng xé hình (screen tearing)
        config.useVsync(true);

        // Cấu hình bộ đệm khung hình nền (Back Buffer):
        // - RGBA 8-8-8-8 bit: Mỗi kênh màu Red/Green/Blue/Alpha dùng 8 bit (256 mức)
        // - Depth Buffer 24 bit: Đảm bảo kiểm tra chiều sâu 3D chính xác cho voxel
        // - Stencil Buffer 0 bit: Không cần sử dụng
        // - Mẫu khử răng cưa (MSAA) 0: Không dùng để tối ưu hiệu năng GPU
        config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 0);

        // Cho phép người dùng thay đổi kích thước cửa sổ bằng cách kéo viền
        config.setResizable(true);

        // =====================================================================
        // Khởi chạy ứng dụng libGDX với lớp VoxelGameApp (ApplicationAdapter)
        // Constructor của Lwjgl3Application sẽ:
        //   1. Tạo cửa sổ GLFW với cấu hình ở trên
        //   2. Tạo OpenGL Context 3.3 Core Profile (tương thích GLSL 330)
        //   3. Gọi VoxelGameApp.create() để khởi tạo tài nguyên
        //   4. Chạy vòng lặp Game Loop (gọi render() mỗi khung hình)
        //   5. Khi đóng cửa sổ, gọi VoxelGameApp.dispose() để giải phóng tài nguyên
        // =====================================================================
        new Lwjgl3Application(new VoxelGameApp(), config);
    }
}
