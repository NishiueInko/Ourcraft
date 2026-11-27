package com.voxelgame.client.gui;

import com.badlogic.gdx.utils.Disposable;

/**
 * Lớp trừu tượng cơ sở cho mọi màn hình giao diện bật/tắt trên trò chơi (Screen),
 * thiết kế theo kiến trúc {@code net.minecraft.client.gui.screens.Screen} của Minecraft.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * - Tính Trừu tượng (Abstraction): định nghĩa vòng đời chuẩn ({@link #open()}, {@link #close()},
 *   {@link #pausesWorld()}, {@link #dispose()}) và các hook đầu vào chung cho mọi lớp màn hình con.
 * - Tính Đa hình (Polymorphism): các lớp con như {@link PauseScreen} (tạm dừng thế giới) và
 *   {@link InventoryScreen} (giữ thế giới chạy nền) triển khai hành vi đặc thù riêng.
 */
public abstract class Screen implements Disposable {

    /** Cờ trạng thái đánh dấu màn hình hiện có đang hiển thị hay không. */
    protected boolean open = false;

    /** Kiểm tra màn hình hiện có đang mở hay không. */
    public boolean isOpen() {
        return open;
    }

    /** Mở màn hình và kích hoạt hook {@link #onOpen()}. */
    public void open() {
        open = true;
        onOpen();
    }

    /** Đóng màn hình và kích hoạt hook {@link #onClose()}. */
    public void close() {
        open = false;
        onClose();
    }

    /** Đảo trạng thái mở/đóng hiện tại của màn hình. */
    public void toggle() {
        if (open) {
            close();
        } else {
            open();
        }
    }

    /** Hook mở rộng để lớp con cập nhật lại lưới hoặc trạng thái khi màn hình vừa mở. */
    protected void onOpen() {
    }

    /** Hook mở rộng để lớp con dọn trạng thái tạm khi màn hình vừa đóng. */
    protected void onClose() {
    }

    /**
     * Cho biết màn hình này có tạm dừng vòng cập nhật thế giới khi đang mở hay không.
     * Ví dụ: {@link PauseScreen} trả {@code true}, {@link InventoryScreen} trả {@code false}.
     */
    public abstract boolean pausesWorld();

    /** Hook xử lý phím nhấn trên màn hình (mặc định không tiêu thụ). */
    public boolean handleKeyDown(int keycode) {
        return false;
    }

    /** Hook xử lý nhấn chuột trên màn hình theo tọa độ pixel Top-Left (mặc định không tiêu thụ). */
    public boolean handleMouseClick(float mouseX, float mouseY, int screenW, int screenH,
                                    int button, boolean shiftDown) {
        return false;
    }

    /** Hook xử lý kéo chuột trên màn hình (mặc định không tiêu thụ). */
    public boolean handleMouseDrag(float mouseX, float mouseY, int screenW, int screenH) {
        return false;
    }

    /** Hook xử lý nhả chuột trên màn hình. */
    public void handleMouseRelease(float mouseX, float mouseY, int screenW, int screenH, int button) {
    }

    /** Giải phóng tài nguyên đồ họa gắn với màn hình (nếu có). */
    @Override
    public void dispose() {
        open = false;
    }
}
