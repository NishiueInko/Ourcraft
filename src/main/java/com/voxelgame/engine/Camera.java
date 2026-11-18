package com.voxelgame.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Quản lý góc nhìn thứ nhất (First-Person Camera) trong không gian 3 chiều và phép chiếu 2D cho giao diện.
 *
 * THỂ HIỆN TÍNH ĐÓNG GÓI (ENCAPSULATION - DEFENSIVE COPYING):
 * - Toàn bộ các vector cơ sở nội bộ (`position`, `front`, `up`, `right`, `worldUp`) được khóa `private final`.
 * - Các phương thức truy vấn `getPosition()`, `getFront()`, `getRight()`, `getUp()` luôn trả về một bản sao
 *   độc lập (`new Vector3f(...)`), ngăn chặn tuyệt đối việc các lớp bên ngoài vô tình gọi `.add()` hoặc `.mul()`
 *   làm biến dạng hệ trục tọa độ của Camera.
 */
public class Camera {

    // Tọa độ vị trí mắt của Camera trong không gian thế giới (World Space)
    private final Vector3f position;

    // 3 vector cơ sở vuông góc từng đôi một xác định hệ trục tọa độ gắn liền với mắt người chơi
    private final Vector3f front;   // Hướng đang nhìn thẳng tới trước
    private final Vector3f up;      // Hướng đỉnh đầu của Camera
    private final Vector3f right;   // Hướng bên tay phải của Camera
    private final Vector3f worldUp; // Hướng thẳng đứng cố định của thế giới (0, 1, 0)

    // Góc quay Euler (đơn vị: độ - Degrees)
    private float yaw = -90.0f;
    private float pitch = 0.0f;

    // Các thông số ống kính 3D (Frustum)
    private static final float BASE_FOV_DEG = 70.0f;
    private float currentFovRad = (float) Math.toRadians(BASE_FOV_DEG);
    private static final float NEAR_PLANE = 0.08f;
    private static final float FAR_PLANE = 160.0f;

    // Bộ đệm ma trận được tái sử dụng mỗi khung hình để tránh tạo rác bộ nhớ (Garbage Collection)
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f orthoMatrix = new Matrix4f();

    public Camera() {
        this.position = new Vector3f(0.0f, 0.0f, 0.0f);
        this.front = new Vector3f(0.0f, 0.0f, -1.0f);
        this.up = new Vector3f(0.0f, 1.0f, 0.0f);
        this.right = new Vector3f(1.0f, 0.0f, 0.0f);
        this.worldUp = new Vector3f(0.0f, 1.0f, 0.0f);

        updateCameraVectors();
    }

    /**
     * Xoay góc nhìn Camera dựa trên độ dịch chuyển của con trỏ chuột.
     */
    public void rotate(float yawOffset, float pitchOffset) {
        yaw += yawOffset;
        pitch += pitchOffset;

        // Giới hạn góc nhìn dọc trong khoảng [-89.0, 89.0] độ để chống hiện tượng Gimbal Lock
        if (pitch > 89.0f) {
            pitch = 89.0f;
        }
        if (pitch < -89.0f) {
            pitch = -89.0f;
        }

        updateCameraVectors();
    }

    private void updateCameraVectors() {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        front.x = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
        front.y = (float) Math.sin(pitchRad);
        front.z = (float) (Math.sin(yawRad) * Math.cos(pitchRad));
        front.normalize();

        front.cross(worldUp, right).normalize();
        right.cross(front, up).normalize();
    }

    /**
     * Cập nhật hiệu ứng mở rộng nhẹ góc nhìn (Dynamic FOV) khi người chơi Chạy nhanh (Sprint) hoặc Bay (Fly).
     */
    public void updateDynamicFov(boolean isSprinting, boolean isFlying, float deltaTime) {
        float targetFovDeg = BASE_FOV_DEG;
        if (isSprinting) {
            targetFovDeg += 8.0f;
        }
        if (isFlying) {
            targetFovDeg += 5.0f;
        }
        float targetFovRad = (float) Math.toRadians(targetFovDeg);
        float lerpSpeed = Math.min(1.0f, deltaTime * 10.0f);
        currentFovRad += (targetFovRad - currentFovRad) * lerpSpeed;
    }

    /**
     * Trả về Ma trận Nhìn (View Matrix) dùng hàm `lookAt(eye, center, up)`.
     */
    public Matrix4f getViewMatrix() {
        Vector3f target = new Vector3f(position).add(front);
        return viewMatrix.identity().lookAt(position, target, up);
    }

    /**
     * Trả về Ma trận Chiếu phối cảnh 3D (Perspective Projection Matrix).
     */
    public Matrix4f getProjectionMatrix(int windowWidth, int windowHeight) {
        float aspectRatio = (float) windowWidth / (float) Math.max(1, windowHeight);
        return projectionMatrix.identity().perspective(currentFovRad, aspectRatio, NEAR_PLANE, FAR_PLANE);
    }

    /**
     * Trả về Ma trận Chiếu trực giao 2D (Orthographic Projection Matrix) phục vụ vẽ giao diện (UI).
     */
    public Matrix4f getOrthoMatrix(int windowWidth, int windowHeight) {
        return orthoMatrix.identity().ortho(0.0f, windowWidth, windowHeight, 0.0f, -1.0f, 1.0f);
    }

    /**
     * Trả về bản sao (Defensive Copy) vị trí mắt Camera.
     */
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    /**
     * Trả về bản sao (Defensive Copy) vector hướng nhìn phía trước.
     */
    public Vector3f getFront() {
        return new Vector3f(front);
    }

    /**
     * Trả về bản sao (Defensive Copy) vector hướng bên phải.
     */
    public Vector3f getRight() {
        return new Vector3f(right);
    }

    /**
     * Trả về bản sao (Defensive Copy) vector hướng đỉnh đầu Camera.
     */
    public Vector3f getUp() {
        return new Vector3f(up);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
