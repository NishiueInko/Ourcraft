package com.voxelgame.engine;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.Block;

/**
 * Quản lý góc nhìn thứ nhất (First-Person Camera) trong không gian 3 chiều và phép chiếu 2D cho giao diện.
 *
 * THỂ HIỆN TÍNH ĐÓNG GÓI (ENCAPSULATION - DEFENSIVE COPYING):
 * - Toàn bộ các vector cơ sở nội bộ (`position`, `front`, `up`, `right`, `worldUp`) được khóa `private final`.
 * - Các phương thức truy vấn `getPosition()`, `getFront()`, `getRight()`, `getUp()` luôn trả về một bản sao
 *   độc lập (`new Vector3(...)`), ngăn chặn tuyệt đối việc các lớp bên ngoài vô tình gọi sửa trực tiếp
 *   làm biến dạng hệ trục tọa độ của Camera.
 */
public class Camera {

    /** Màu, khoảng sương và độ đậm lớp phủ dùng chung cho mọi lượt vẽ khi mắt chìm. */
    public enum LiquidView {
        AIR(0f, 0f, 0f, 0f, 0f, 0f),
        WATER(0.035f, 0.18f, 0.27f, 1f, 22f, 0.23f),
        LAVA(0.64f, 0.12f, 0.015f, 0.25f, 1.6f, 0.68f);

        public final float red;
        public final float green;
        public final float blue;
        public final float fogStart;
        public final float fogEnd;
        public final float tintAlpha;

        /** Lưu bộ thông số cố định để dùng lại mỗi khung hình. */
        LiquidView(float red, float green, float blue, float fogStart, float fogEnd, float tintAlpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.fogStart = fogStart;
            this.fogEnd = fogEnd;
            this.tintAlpha = tintAlpha;
        }
    }

    /** Chọn môi trường từ mắt camera, kể cả lúc người chơi đang ngồi trên thú. */
    public LiquidView liquidView(World world) {
        Block liquid = world.liquidAt(position.x, position.y, position.z);
        if (liquid == Block.WATER) {
            return LiquidView.WATER;
        }
        return liquid == Block.LAVA ? LiquidView.LAVA : LiquidView.AIR;
    }

    // Tọa độ vị trí mắt của Camera trong không gian thế giới (World Space)
    private final Vector3 position;

    // 3 vector cơ sở vuông góc từng đôi một xác định hệ trục tọa độ gắn liền với mắt người chơi
    private final Vector3 front;   // Hướng đang nhìn thẳng tới trước
    private final Vector3 up;      // Hướng đỉnh đầu của Camera
    private final Vector3 right;   // Hướng bên tay phải của Camera
    private final Vector3 worldUp; // Hướng thẳng đứng cố định của thế giới (0, 1, 0)

    // Vector đích nhìn tạm thời được tái sử dụng để tránh cấp phát rác GC trong Game Loop
    private final Vector3 lookTarget = new Vector3();

    // Góc quay Euler (đơn vị: độ - Degrees)
    private float yaw = -90.0f;
    private float pitch = 0.0f;

    // Các thông số ống kính 3D (Frustum)
    private static final float BASE_FOV_DEG = 70.0f;
    private float currentFovDeg = BASE_FOV_DEG;
    /** Độ thu hẹp góc nhìn (độ âm, tối đa -8 độ) khi kéo dây cung và giá trị mục tiêu để nội suy mượt. */
    private float bowZoomOffsetDeg = 0.0f;
    private float targetBowZoomOffsetDeg = 0.0f;
    private static final float BOW_ZOOM_LERP_SPEED = 16.0f;
    private static final float NEAR_PLANE = 0.08f;
    // Giới hạn chiều sâu kết xuất để giữ ngân sách vẽ khi thế giới mở rộng.
    private static final float FAR_PLANE = 1024.0f;

    // Bộ đệm ma trận được tái sử dụng mỗi khung hình để tránh tạo rác bộ nhớ (Garbage Collection)
    private final Matrix4 viewMatrix = new Matrix4();
    private final Matrix4 projectionMatrix = new Matrix4();
    private final Matrix4 orthoMatrix = new Matrix4();

    public Camera() {
        this.position = new Vector3(0.0f, 0.0f, 0.0f);
        this.front = new Vector3(0.0f, 0.0f, -1.0f);
        this.up = new Vector3(0.0f, 1.0f, 0.0f);
        this.right = new Vector3(1.0f, 0.0f, 0.0f);
        this.worldUp = new Vector3(0.0f, 1.0f, 0.0f);

        updateCameraVectors();
    }

    /**
     * Xoay góc nhìn Camera dựa trên độ dịch chuyển của con trỏ chuột.
     *
     * @param yawOffset độ dịch chuyển góc nhìn ngang
     * @param pitchOffset độ dịch chuyển góc nhìn dọc
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
        front.nor();

        right.set(front).crs(worldUp).nor();
        up.set(right).crs(front).nor();
    }

    /**
     * Cập nhật hiệu ứng mở rộng nhẹ góc nhìn (Dynamic FOV) khi người chơi Chạy nhanh (Sprint) hoặc Bay (Fly).
     *
     * @param isSprinting cờ người chơi đang chạy nhanh
     * @param isFlying cờ người chơi đang bay
     * @param deltaTime thời gian giữa hai khung hình
     */
    public void updateDynamicFov(boolean isSprinting, boolean isFlying, float deltaTime) {
        float targetFovDeg = BASE_FOV_DEG;
        if (isSprinting) {
            targetFovDeg += 8.0f;
        }
        if (isFlying) {
            targetFovDeg += 5.0f;
        }
        float lerpSpeed = Math.min(1.0f, deltaTime * 10.0f);
        currentFovDeg += (targetFovDeg - currentFovDeg) * lerpSpeed;
    }

    /**
     * Cập nhật mượt độ thu hẹp FOV khi kéo cung mà không làm thay đổi Dynamic FOV của Sprint/Fly.
     *
     * @param targetOffsetDeg độ lệch FOV mục tiêu (ví dụ {@code -8.0f * pullProgress})
     * @param deltaTime       thời gian giữa hai khung hình
     */
    public void updateBowZoom(float targetOffsetDeg, float deltaTime) {
        targetBowZoomOffsetDeg = Float.isFinite(targetOffsetDeg) ? targetOffsetDeg : 0.0f;
        if (deltaTime > 0.0f && Float.isFinite(deltaTime)) {
            float lerpSpeed = Math.min(1.0f, deltaTime * BOW_ZOOM_LERP_SPEED);
            bowZoomOffsetDeg += (targetBowZoomOffsetDeg - bowZoomOffsetDeg) * lerpSpeed;
        }
    }

    /**
     * Đặt trực tiếp độ lệch FOV kéo cung (dùng khi hủy trạng thái kéo cung do đổi ô Hotbar).
     *
     * @param offsetDeg độ lệch FOV cần áp dụng ngay lập tức
     */
    public void setBowZoomOffset(float offsetDeg) {
        float safe = Float.isFinite(offsetDeg) ? offsetDeg : 0.0f;
        targetBowZoomOffsetDeg = safe;
        bowZoomOffsetDeg = safe;
    }

    /** Trả về độ lệch FOV kéo cung hiện tại (0.0f khi không kéo cung). */
    public float getBowZoomOffset() {
        return bowZoomOffsetDeg;
    }

    /** Trả về góc FOV hiệu dụng (đã cộng cả Dynamic FOV lẫn độ lệch kéo cung). */
    public float getCurrentFovDeg() {
        return Math.max(30.0f, Math.min(110.0f, currentFovDeg + bowZoomOffsetDeg));
    }

    /**
     * Trả về Ma trận Nhìn (View Matrix) dùng phép biến đổi `lookAt(eye, center, up)`.
     * Tái sử dụng đối tượng viewMatrix có sẵn, không cấp phát thêm bộ nhớ.
     *
     * @return ma trận nhìn Matrix4
     */
    public Matrix4 getViewMatrix() {
        lookTarget.set(position).add(front);
        return viewMatrix.setToLookAt(position, lookTarget, up);
    }

    /**
     * Trả về Ma trận Chiếu phối cảnh 3D (Perspective Projection Matrix).
     *
     * @param windowWidth chiều rộng màn hình
     * @param windowHeight chiều cao màn hình
     * @return ma trận chiếu phối cảnh Matrix4
     */
    public Matrix4 getProjectionMatrix(int windowWidth, int windowHeight) {
        float aspectRatio = (float) Math.max(1, windowWidth) / Math.max(1, windowHeight);
        return projectionMatrix.setToProjection(NEAR_PLANE, FAR_PLANE, getCurrentFovDeg(), aspectRatio);
    }

    /**
     * Trả về Ma trận Chiếu trực giao 2D (Orthographic Projection Matrix) phục vụ vẽ giao diện (UI).
     * Trục Y đi từ 0 (đỉnh màn hình) xuống windowHeight (đáy màn hình), khớp 100% với hệ tọa độ Top-Left.
     *
     * @param windowWidth chiều rộng màn hình
     * @param windowHeight chiều cao màn hình
     * @return ma trận trực giao Matrix4
     */
    public Matrix4 getOrthoMatrix(int windowWidth, int windowHeight) {
        return orthoMatrix.setToOrtho(0.0f, windowWidth, windowHeight, 0.0f, -1.0f, 1.0f);
    }

    /**
     * Trả về bản sao (Defensive Copy) vị trí mắt Camera.
     *
     * @return đối tượng Vector3 mới chứa vị trí
     */
    public Vector3 getPosition() {
        return new Vector3(position);
    }

    /** Chép vị trí mắt vào bộ đệm của vòng lặp kết xuất. */
    public Vector3 copyPosition(Vector3 target) {
        return target.set(position);
    }

    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    /**
     * Trả về bản sao (Defensive Copy) vector hướng nhìn phía trước.
     *
     * @return đối tượng Vector3 mới chứa hướng nhìn
     */
    public Vector3 getFront() {
        return new Vector3(front);
    }

    /** Chép hướng nhìn vào bộ đệm dùng lại cho raycast. */
    public Vector3 copyFront(Vector3 target) {
        return target.set(front);
    }

    /**
     * Trả về bản sao (Defensive Copy) vector hướng bên phải.
     *
     * @return đối tượng Vector3 mới chứa hướng phải
     */
    public Vector3 getRight() {
        return new Vector3(right);
    }

    /**
     * Trả về bản sao (Defensive Copy) vector hướng đỉnh đầu Camera.
     *
     * @return đối tượng Vector3 mới chứa hướng đỉnh đầu
     */
    public Vector3 getUp() {
        return new Vector3(up);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
