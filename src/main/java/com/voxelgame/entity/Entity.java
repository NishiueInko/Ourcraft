package com.voxelgame.entity;

import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.World;
import org.joml.Vector3f;

/**
 * Lớp cha trừu tượng gốc (Abstract Base Class) đại diện cho mọi thực thể tồn tại trong không gian 3D.
 *
 * THỂ HIỆN 4 TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Khai báo khái niệm tổng quát về một thực thể trong thế giới Voxel với 2 hành vi trừu tượng
 *      bắt buộc các lớp con định nghĩa: `update(World, float)` và `render(ShaderProgram)`.
 * 2. Tính Kế thừa (Inheritance):
 *    - Cung cấp các thuộc tính vật lý cốt lõi (`position`, `velocity`, `halfWidth`, `height`, `onGround`)
 *      và thuật toán kiểm tra giao cắt hộp va chạm AABB (`intersectsBlock`) cho mọi lớp con
 *      (`Player`, `ItemEntity`, `Block` và 9 lớp Block cụ thể).
 * 3. Tính Đa hình (Polymorphism):
 *    - Đa hình lúc biên dịch (Compile-time Polymorphism / Method Overloading): Nạp chồng 2 constructor
 *      cho thực thể động có tọa độ khởi tạo và thực thể tĩnh mẫu (Flyweight Block).
 *    - Đa hình lúc chạy (Runtime Polymorphism / Method Overriding): Cho phép `World` quản lý chung
 *      danh sách `List<Entity>` và gọi `entity.update(...)`, `entity.render(...)` đồng nhất.
 * 4. Tính Đóng gói (Encapsulation):
 *    - Bảo vệ trạng thái tọa độ nội bộ bằng kỹ thuật sao chép phòng thủ (Defensive Copying) trong
 *      `getPosition()`, `getCenterPosition()` và `getVelocity()`, ngăn bên ngoài sửa trực tiếp vector gốc.
 */
public abstract class Entity {

    // Tọa độ điểm chính giữa dưới đáy (Bottom-Center) của hộp va chạm AABB
    protected final Vector3f position;
    // Vector vận tốc chuyển động theo 3 trục (X, Y, Z)
    protected final Vector3f velocity;

    // Một nửa chiều rộng (bán kính ngang) và toàn bộ chiều cao của hộp va chạm AABB
    protected final float halfWidth;
    protected final float height;

    // Cờ trạng thái báo hiệu thực thể đang đứng chạm mặt đất rắn
    protected boolean onGround = false;

    /**
     * Constructor đầy đủ dành cho các thực thể động xuất hiện tại tọa độ cụ thể trong thế giới
     * (ví dụ: Player hoặc ItemEntity văng ra khi đập vỡ Block).
     */
    protected Entity(float x, float y, float z, float halfWidth, float height) {
        this.position = new Vector3f(x, y, z);
        this.velocity = new Vector3f(0.0f, 0.0f, 0.0f);
        this.halfWidth = halfWidth;
        this.height = height;
    }

    /**
     * Constructor nạp chồng (Overloaded Constructor) dành cho các thực thể mẫu không gắn cố định
     * với một vị trí duy nhất (ví dụ: các thực thể Flyweight của lớp Block).
     */
    protected Entity(float halfWidth, float height) {
        this(0.0f, 0.0f, 0.0f, halfWidth, height);
    }

    /**
     * Kiểm tra xem hộp va chạm AABB của thực thể hiện tại có giao cắt với ô lưới Block
     * tại tọa độ nguyên (blockX, blockY, blockZ) hay không.
     * Dùng để ngăn người chơi đặt Block mới đè vào chính cơ thể mình.
     */
    public boolean intersectsBlock(int blockX, int blockY, int blockZ) {
        float entityMinX = position.x - halfWidth;
        float entityMaxX = position.x + halfWidth;
        float entityMinY = position.y;
        float entityMaxY = position.y + height;
        float entityMinZ = position.z - halfWidth;
        float entityMaxZ = position.z + halfWidth;

        float bMinX = blockX;
        float bMaxX = blockX + 1.0f;
        float bMinY = blockY;
        float bMaxY = blockY + 1.0f;
        float bMinZ = blockZ;
        float bMaxZ = blockZ + 1.0f;

        return (entityMinX < bMaxX && entityMaxX > bMinX) &&
               (entityMinY < bMaxY && entityMaxY > bMinY) &&
               (entityMinZ < bMaxZ && entityMaxZ > bMinZ);
    }

    /**
     * Trả về bản sao (Defensive Copy) tọa độ chân của thực thể để bảo vệ dữ liệu nội bộ.
     */
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    /**
     * Cập nhật tọa độ của thực thể một cách an toàn.
     */
    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    /**
     * Trả về bản sao (Defensive Copy) tọa độ tâm hình học của thực thể.
     */
    public Vector3f getCenterPosition() {
        return new Vector3f(position.x, position.y + height * 0.5f, position.z);
    }

    /**
     * Trả về bản sao (Defensive Copy) vector vận tốc hiện tại của thực thể.
     */
    public Vector3f getVelocity() {
        return new Vector3f(velocity);
    }

    public float getHalfWidth() {
        return halfWidth;
    }

    public float getHeight() {
        return height;
    }

    public boolean isOnGround() {
        return onGround;
    }

    /**
     * Phương thức trừu tượng cập nhật trạng thái logic và vật lý của thực thể ở mỗi khung hình.
     */
    public abstract void update(World world, float deltaTime);

    /**
     * Phương thức trừu tượng kết xuất hình ảnh 3D của thực thể lên màn hình.
     */
    public abstract void render(ShaderProgram shader);
}
