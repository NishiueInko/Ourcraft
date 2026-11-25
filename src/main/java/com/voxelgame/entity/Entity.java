package com.voxelgame.entity;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.World;

/**
 * Lớp cha trừu tượng gốc (Abstract Base Class) đại diện cho mọi thực thể tồn
 * tại trong không gian 3D.
 *
 * THỂ HIỆN 4 TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 * - Khai báo khái niệm tổng quát về một thực thể trong thế giới Voxel với 2
 * hành vi trừu tượng
 * bắt buộc các lớp con định nghĩa: `update(World, float)` và
 * `render(ShaderProgram)`.
 * 2. Tính Kế thừa (Inheritance):
 * - Cung cấp các thuộc tính vật lý cốt lõi (`position`, `velocity`,
 * `halfWidth`, `height`, `onGround`)
 * và thuật toán kiểm tra giao cắt hộp va chạm AABB (`intersectsBlock`) cho mọi
 * lớp con
 * (`Player`, `ItemEntity`, `Block` và 9 lớp Block cụ thể).
 * 3. Tính Đa hình (Polymorphism):
 * - Đa hình lúc biên dịch (Compile-time Polymorphism / Method Overloading): Nạp
 * chồng 2 constructor
 * cho thực thể động có tọa độ khởi tạo và thực thể tĩnh mẫu (Flyweight Block).
 * - Đa hình lúc chạy (Runtime Polymorphism / Method Overriding): Cho phép
 * `World` quản lý chung
 * danh sách `List<Entity>` và gọi `entity.update(...)`, `entity.render(...)`
 * đồng nhất.
 * 4. Tính Đóng gói (Encapsulation):
 * - Bảo vệ trạng thái tọa độ nội bộ bằng kỹ thuật sao chép phòng thủ (Defensive
 * Copying) trong
 * `getPosition()`, `getCenterPosition()` và `getVelocity()`, ngăn bên ngoài sửa
 * trực tiếp vector gốc.
 */
public abstract class Entity {

    /** Khe nhỏ để thực thể không dính vào bề mặt va chạm. */
    protected static final float CONTACT_EPSILON = 0.0001f;
    /** Đỉnh vật cản của lần quét gần nhất, dùng khi thử bước lên tuyết. */
    protected float obstacleTop;
    /** Kết quả gần nhất dùng chung giữa hộp thân và các hộp gắn kèm trong một lần quét. */
    private float sweepLimit;
    private Block sweepHit;

    // Tọa độ điểm chính giữa dưới đáy (Bottom-Center) của hộp va chạm AABB (sử dụng
    // Vector3 của libGDX)
    protected final Vector3 position;
    // Vector vận tốc chuyển động theo 3 trục (X, Y, Z)
    protected final Vector3 velocity;

    // Một nửa chiều rộng (bán kính ngang) và toàn bộ chiều cao của hộp va chạm AABB
    protected final float halfWidth;
    /** Chiều cao thực của hộp, có thể đổi khi loài có tư thế ngồi. */
    protected float height;

    // Cờ trạng thái báo hiệu thực thể đang đứng chạm mặt đất rắn
    protected boolean onGround = false;

    /**
     * Constructor đầy đủ dành cho các thực thể động xuất hiện tại tọa độ cụ thể
     * trong thế giới
     * (ví dụ: Player hoặc ItemEntity văng ra khi đập vỡ Block).
     *
     * @param x         tọa độ khởi tạo trục X
     * @param y         tọa độ khởi tạo trục Y (đáy thực thể)
     * @param z         tọa độ khởi tạo trục Z
     * @param halfWidth một nửa chiều rộng (bán kính theo trục ngang)
     * @param height    tổng chiều cao thực thể theo trục đứng
     */
    protected Entity(float x, float y, float z, float halfWidth, float height) {
        this.position = new Vector3(x, y, z);
        this.velocity = new Vector3(0.0f, 0.0f, 0.0f);
        this.halfWidth = halfWidth;
        this.height = height;
    }

    /**
     * Constructor nạp chồng (Overloaded Constructor) dành cho các thực thể mẫu
     * không gắn cố định
     * với một vị trí duy nhất (ví dụ: các thực thể Flyweight của lớp Block).
     *
     * @param halfWidth một nửa chiều rộng
     * @param height    chiều cao
     */
    protected Entity(float halfWidth, float height) {
        this(0.0f, 0.0f, 0.0f, halfWidth, height);
    }

    /**
     * Kiểm tra xem hộp va chạm AABB của thực thể hiện tại có giao cắt với ô lưới
     * Block
     * tại tọa độ nguyên (blockX, blockY, blockZ) hay không.
     * Dùng để ngăn người chơi đặt Block mới đè vào chính cơ thể mình.
     *
     * @param blockX tọa độ X nguyên của block cần kiểm tra
     * @param blockY tọa độ Y nguyên của block cần kiểm tra
     * @param blockZ tọa độ Z nguyên của block cần kiểm tra
     * @return true nếu hộp va chạm của thực thể giao cắt với khối lập phương của
     *         block
     */
    public boolean intersectsBlock(int blockX, int blockY, int blockZ) {
        return intersectsBlock(blockX, blockY, blockZ, Block.FULL_BOUNDS);
    }

    /** Kiểm tra hộp tùy chỉnh của block tại tọa độ trong thế giới. */
    public boolean intersectsBlock(int blockX, int blockY, int blockZ, Block.Bounds bounds) {
        return bounds != null && bounds.intersects(blockX, blockY, blockZ,
                position.x - halfWidth, position.y, position.z - halfWidth,
                position.x + halfWidth, position.y + height, position.z + halfWidth);
    }

    /** Quét một trục của hộp thực thể qua địa hình và dừng tại mặt gần nhất. */
    protected Block moveAxis(World world, int axis, float distance) {
        if (distance == 0) {
            return null;
        }
        sweepLimit = distance;
        sweepHit = null;
        sweepTerrainBox(world, axis, distance, position.x, position.y, position.z, halfWidth, height);
        sweepAttachedTerrainBoxes(world, axis, distance);
        float limit = sweepLimit;
        if (sweepHit != null) {
            limit = distance > 0f ? Math.max(0, limit - CONTACT_EPSILON)
                    : Math.min(0, limit + (axis == 1 ? 0 : CONTACT_EPSILON));
        }
        if (axis == 0) {
            position.x += limit;
        } else if (axis == 1) {
            position.y += limit;
        } else {
            position.z += limit;
        }
        return sweepHit;
    }

    /** Cho thực thể mang hành khách bổ sung hộp của người ngồi vào cùng lượt quét. */
    protected void sweepAttachedTerrainBoxes(World world, int axis, float distance) {
    }

    /** Quét một hộp qua các hình va chạm chính xác và cập nhật mặt cản gần nhất. */
    protected final void sweepTerrainBox(World world, int axis, float distance,
                                         float centerX, float base, float centerZ, float radius, float boxHeight) {
        float left = centerX - radius;
        float right = centerX + radius;
        float top = base + boxHeight;
        float back = centerZ - radius;
        float front = centerZ + radius;
        float low = Math.min(0, distance);
        float high = Math.max(0, distance);
        int minX = (int) Math.floor(left + (axis == 0 ? low : 0));
        int maxX = (int) Math.floor(right + (axis == 0 ? high : 0));
        int minY = (int) Math.floor(base + (axis == 1 ? low : 0));
        int maxY = (int) Math.floor(top + (axis == 1 ? high : 0));
        int minZ = (int) Math.floor(back + (axis == 2 ? low : 0));
        int maxZ = (int) Math.floor(front + (axis == 2 ? high : 0));
        boolean positive = distance > 0;
        float entityEdge = axis == 0 ? (positive ? right : left)
                : axis == 1 ? (positive ? top : base) : (positive ? front : back);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block.Bounds b = world.getCollisionBounds(x, y, z);
                    if (b == null || (axis != 0 && (right <= x + b.minX() || left >= x + b.maxX()))
                            || (axis != 1 && (top <= y + b.minY() || base >= y + b.maxY()))
                            || (axis != 2 && (front <= z + b.minZ() || back >= z + b.maxZ()))) {
                        continue;
                    }
                    float edge = axis == 0 ? x + (positive ? b.minX() : b.maxX())
                            : axis == 1 ? y + (positive ? b.minY() : b.maxY())
                            : z + (positive ? b.minZ() : b.maxZ());
                    float gap = edge - entityEdge;
                    if (positive ? gap >= -CONTACT_EPSILON && gap <= sweepLimit
                            : gap <= CONTACT_EPSILON && gap >= sweepLimit) {
                        sweepLimit = gap;
                        sweepHit = Block.fromId(world.getBlock(x, y, z));
                        obstacleTop = y + b.maxY();
                    }
                }
            }
        }
    }

    /** Kiểm tra hộp thực thể tại vị trí hiện tại trước khi cam kết một bước. */
    protected boolean overlapsTerrain(World world) {
        for (int x = (int) Math.floor(position.x - halfWidth); x <= (int) Math.floor(position.x + halfWidth); x++) {
            for (int y = (int) Math.floor(position.y); y <= (int) Math.floor(position.y + height); y++) {
                for (int z = (int) Math.floor(position.z - halfWidth); z <= (int) Math.floor(position.z + halfWidth); z++) {
                    if (intersectsBlock(x, y, z, world.getCollisionBounds(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Trả về bản sao (Defensive Copy) tọa độ chân của thực thể để bảo vệ dữ liệu
     * nội bộ.
     *
     * @return đối tượng Vector3 mới mang giá trị vị trí hiện tại
     */
    public Vector3 getPosition() {
        return new Vector3(position);
    }

    /** Chép tọa độ chân vào bộ đệm có sẵn để duyệt thực thể mà không cấp phát. */
    public Vector3 copyPosition(Vector3 target) {
        return target.set(position);
    }

    /** Chép tâm thực thể vào bộ đệm có sẵn để cập nhật vật lý mỗi khung hình. */
    public Vector3 copyCenterPosition(Vector3 target) {
        return target.set(position.x, position.y + height * 0.5f, position.z);
    }

    /**
     * Cập nhật tọa độ của thực thể một cách an toàn.
     *
     * @param x tọa độ mới trục X
     * @param y tọa độ mới trục Y
     * @param z tọa độ mới trục Z
     */
    public void setPosition(float x, float y, float z) {
        this.position.set(x, y, z);
    }

    /**
     * Trả về bản sao (Defensive Copy) tọa độ tâm hình học của thực thể.
     *
     * @return đối tượng Vector3 mới mang tọa độ tâm cơ thể
     */
    public Vector3 getCenterPosition() {
        return new Vector3(position.x, position.y + height * 0.5f, position.z);
    }

    /**
     * Trả về bản sao (Defensive Copy) vector vận tốc hiện tại của thực thể.
     *
     * @return đối tượng Vector3 mới mang giá trị vận tốc hiện tại
     */
    public Vector3 getVelocity() {
        return new Vector3(velocity);
    }

    /** Chép vận tốc vào bộ đệm của AI mà không tạo vector mỗi lượt cập nhật. */
    public Vector3 copyVelocity(Vector3 target) {
        return target.set(velocity);
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
     * Phương thức trừu tượng cập nhật trạng thái logic và vật lý của thực thể ở mỗi
     * khung hình.
     *
     * @param world     thế giới voxel chứa thực thể
     * @param deltaTime khoảng thời gian trôi qua giữa hai khung hình (giây)
     */
    public abstract void update(World world, float deltaTime);

    /**
     * Phương thức trừu tượng kết xuất hình ảnh 3D của thực thể lên màn hình.
     *
     * @param shader chương trình Shader đang hoạt động
     */
    public abstract void render(ShaderProgram shader);
}
