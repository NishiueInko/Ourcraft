package com.voxelgame;

import com.voxelgame.engine.Window;

/**
 * Điểm khởi chạy chính (Entry Point) của dự án Voxel Game 3D (Minecraft Edition)
 * sau khi tái cấu trúc hoàn chỉnh theo chuẩn 4 tính chất Lập trình Hướng đối tượng (OOP).
 *
 * CẤU TRÚC KIẾN TRÚC MỚI:
 * - `com.voxelgame.Main`: Lớp khởi chạy gốc duy nhất (giữ nguyên cấu hình `mainClass` trong `build.gradle`).
 * - `com.voxelgame.entity`: Package đóng gói cây kế thừa Thực thể (`Entity`, `Player`, `ItemEntity`, `Hotbar`,
 *   `Block` và 9 lớp Block con).
 * - `com.voxelgame.engine`: Package đóng gói lõi Engine Đồ họa, Thế giới, Vật lý, Giao diện và Âm thanh
 *   (`Cleanable`, `Window`, `World`, `Chunk`, `Camera`, `Raycast`, `ShaderProgram`, `Texture`, `UIRenderer`, `AudioManager`).
 */
public class Main {

    public static void main(String[] args) {
        Window window = new Window("Voxel Game 3D - Minecraft OOP Edition (Java, LWJGL 3 & OpenGL 3.3)", 1280, 720);
        window.run();
    }
}
