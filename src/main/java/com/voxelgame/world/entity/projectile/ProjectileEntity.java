package com.voxelgame.world.entity.projectile;

import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.client.render.entity.MobVisual;
import com.voxelgame.client.render.entity.MobVisualState;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Quản lý quỹ đạo có trọng lực, quét va chạm và kết xuất dùng chung của đạn. */
public abstract class ProjectileEntity extends Entity implements MobVisual {
    /** Chia biến thiên quỹ đạo thành đoạn tối đa một tick mô phỏng 20 Hz. */
    private static final float MAX_SWEEP_SECONDS = 0.05f;
    /** Giữ góc ngang cũ khi đạn bay gần như thẳng đứng. */
    private static final float FACING_EPSILON = 0.0000001f;

    /** Chủ sở hữu và cấu hình vật lý cố định của phát bắn. */
    private final Entity owner;
    private final float gravity;
    private float lifeRemaining;
    private boolean finished;

    /** Tái sử dụng đoạn dịch chuyển và bộ đọc tâm mục tiêu. */
    private final Vector3 segment = new Vector3();
    private final Vector3 targetCenter = new Vector3();
    private final MobVisualState visual;

    /** Tạo đạn tại tâm phát, với vận tốc theo block/giây và thời gian sống tính theo giây. */
    protected ProjectileEntity(Entity owner, float x, float y, float z, float vx, float vy, float vz,
                               float radius, float gravity, float lifetime, MobAppearance appearance) {
        super(x, y, z, radius, 0f);
        if (!Float.isFinite(vx) || !Float.isFinite(vy) || !Float.isFinite(vz)) {
            throw new IllegalArgumentException("Vận tốc đạn phải hữu hạn");
        }
        this.owner = owner;
        this.gravity = gravity;
        lifeRemaining = lifetime;
        velocity.set(vx, vy, vz);
        visual = new MobVisualState(appearance);
        updateFacing();
    }

    /** Tích phân parabol từng đoạn ngắn, kết thúc khi chạm, hết hạn hoặc ra ngoài thế giới. */
    @Override
    public final void update(World world, float dt) {
        if (finished || world == null || !(dt > 0f) || !Float.isFinite(dt)) {
            return;
        }
        float remaining = Math.min(dt, lifeRemaining);
        while (remaining > 0f && !finished) {
            if (!isInWorld(world)) {
                finished = true;
                return;
            }
            float step = Math.min(remaining, MAX_SWEEP_SECONDS);
            segment.set(velocity.x * step, (velocity.y - 0.5f * gravity * step) * step, velocity.z * step);
            sweep(world);
            velocity.y -= gravity * step;
            remaining -= step;
        }
        lifeRemaining -= Math.min(dt, lifeRemaining);
        finished |= lifeRemaining <= 0f || !isInWorld(world);
        if (!finished) {
            updateFacing();
        }
    }

    /** Chặn tọa độ không hữu hạn hoặc tâm đạn đã rời lưới thế giới. */
    private boolean isInWorld(World world) {
        return Float.isFinite(position.x) && Float.isFinite(position.y) && Float.isFinite(position.z)
                && world.isInsideWorld(floorCell(position.x), floorCell(position.y), floorCell(position.z));
    }

    /** Chọn giao điểm sớm nhất trong đoạn, ưu tiên khối nếu khoảng cách bằng nhau. */
    private void sweep(World world) {
        float lengthSquared = segment.len2();
        if (!(lengthSquared > 0f) || !Float.isFinite(lengthSquared)) {
            finished = !Float.isFinite(lengthSquared);
            return;
        }
        float closest = blockEntry(world);
        LivingEntity directHit = null;
        for (int i = 0; i < world.entityCount(); i++) {
            Entity entity = world.entityAt(i);
            if (entity instanceof LivingEntity living && canAffect(living)) {
                float entry = intersect(living, Math.min(1f, closest));
                if (entry < closest) {
                    closest = entry;
                    directHit = living;
                }
            }
        }
        if (Float.isFinite(closest)) {
            position.mulAdd(segment, closest);
            finished = true;
            onImpact(world, directHit);
        } else {
            position.add(segment);
        }
    }

    /** Duyệt ô dọc đường tâm và dừng tại đoạn đầu tiên có va chạm với hộp đạn. */
    private float blockEntry(World world) {
        int x = floorCell(position.x);
        int y = floorCell(position.y);
        int z = floorCell(position.z);
        int sx = segment.x >= 0f ? 1 : -1;
        int sy = segment.y >= 0f ? 1 : -1;
        int sz = segment.z >= 0f ? 1 : -1;
        float nextX = boundaryTime(position.x, x, sx, segment.x);
        float nextY = boundaryTime(position.y, y, sy, segment.y);
        float nextZ = boundaryTime(position.z, z, sz, segment.z);
        float strideX = Math.abs(1f / segment.x);
        float strideY = Math.abs(1f / segment.y);
        float strideZ = Math.abs(1f / segment.z);

        float from = 0f;
        while (world.isInsideWorld(x, y, z)) {
            float to = Math.min(1f, Math.min(nextX, Math.min(nextY, nextZ)));
            float hit = blockEntryInInterval(world, from, to);
            if (Float.isFinite(hit) || to >= 1f) {
                return hit;
            }
            from = to;
            if (nextX == to) {
                x += sx;
                nextX += strideX;
            }
            if (nextY == to) {
                y += sy;
                nextY += strideY;
            }
            if (nextZ == to) {
                z += sz;
                nextZ += strideZ;
            }
        }
        return Float.POSITIVE_INFINITY;
    }

    /** Chỉ kiểm tra những ô mà hộp đạn có thể chạm trong khoảng đang duyệt. */
    private float blockEntryInInterval(World world, float from, float to) {
        float startX = position.x + segment.x * from;
        float startY = position.y + segment.y * from;
        float startZ = position.z + segment.z * from;
        float endX = position.x + segment.x * to;
        float endY = position.y + segment.y * to;
        float endZ = position.z + segment.z * to;
        // Lấy cả ô ở phía dưới nếu mặt đáy hộp vừa trùng với biên nguyên.
        int minX = floorCell(Math.nextDown(Math.min(startX, endX) - halfWidth));
        int minY = floorCell(Math.nextDown(Math.min(startY, endY) - halfWidth));
        int minZ = floorCell(Math.nextDown(Math.min(startZ, endZ) - halfWidth));
        int maxX = floorCell(Math.nextUp(Math.max(startX, endX) + halfWidth));
        int maxY = floorCell(Math.nextUp(Math.max(startY, endY) + halfWidth));
        int maxZ = floorCell(Math.nextUp(Math.max(startZ, endZ) + halfWidth));
        float closest = Float.POSITIVE_INFINITY;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block.Bounds bounds = world.getCollisionBounds(x, y, z);
                    if (bounds == null) {
                        continue;
                    }
                    float entry = intersect(x + bounds.minX() - halfWidth, y + bounds.minY() - halfWidth,
                            z + bounds.minZ() - halfWidth, x + bounds.maxX() + halfWidth,
                            y + bounds.maxY() + halfWidth, z + bounds.maxZ() + halfWidth,
                            from, Math.min(to, closest));
                    closest = Math.min(closest, entry);
                }
            }
        }
        return closest;
    }

    /** Làm tròn xuống chính xác cho cả tọa độ sát mặt nguyên của voxel. */
    private static int floorCell(float coordinate) {
        return (int) Math.floor(coordinate);
    }

    /** Tính tỷ lệ tới biên ô, hoặc vô cực khi đoạn bay song song với biên. */
    private static float boundaryTime(float origin, int cell, int direction, float travel) {
        return travel == 0f ? Float.POSITIVE_INFINITY
                : (cell + (direction > 0 ? 1f : 0f) - origin) / travel;
    }

    /** Cắt tâm đạn chuyển động với AABB mục tiêu nới theo bán kính đạn. */
    private float intersect(LivingEntity living, float limit) {
        living.copyCenterPosition(targetCenter);
        float horizontal = living.getHalfWidth() + halfWidth;
        float vertical = living.getHeight() * 0.5f + halfWidth;
        return intersect(targetCenter.x - horizontal, targetCenter.y - vertical, targetCenter.z - horizontal,
                targetCenter.x + horizontal, targetCenter.y + vertical, targetCenter.z + horizontal, 0f, limit);
    }

    /** Giao ba khoảng theo trục của đoạn bay với một hộp đã nới theo bán kính. */
    private float intersect(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                            float near, float far) {
        for (int axis = 0; axis < 3; axis++) {
            float origin = axis == 0 ? position.x : axis == 1 ? position.y : position.z;
            float travel = axis == 0 ? segment.x : axis == 1 ? segment.y : segment.z;
            float low = axis == 0 ? minX : axis == 1 ? minY : minZ;
            float high = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
            if (travel == 0f) {
                if (origin < low || origin > high) {
                    return Float.POSITIVE_INFINITY;
                }
                continue;
            }
            float first = (low - origin) / travel;
            float last = (high - origin) / travel;
            near = Math.max(near, Math.min(first, last));
            far = Math.min(far, Math.max(first, last));
            if (far < near) {
                return Float.POSITIVE_INFINITY;
            }
        }
        return near;
    }

    /** Tránh chủ, các cá thể cùng chuỗi ghế và đồng phe trước khi nhận va chạm hoặc hiệu ứng. */
    protected final boolean canAffect(LivingEntity candidate) {
        if (!candidate.isAlive() || candidate == owner) {
            return false;
        }
        if (owner instanceof LivingEntity shooter && (shooter.getMount() != null || candidate.getMount() != null)
                && mountRoot(shooter) == mountRoot(candidate)) {
            return false;
        }
        return !(owner instanceof MobEntity source && candidate instanceof MobEntity mob && source.isAlliedTo(mob));
    }

    /** Lần theo chuỗi ghế đã được LivingEntity đảm bảo không có chu trình. */
    private static LivingEntity mountRoot(LivingEntity entity) {
        LivingEntity mount;
        while ((mount = entity.getMount()) != null) {
            entity = mount;
        }
        return entity;
    }

    /** Tính khoảng cách từ điểm trúng đến tâm AABB mà không tạo vector mới. */
    protected final float squaredDistanceTo(LivingEntity living) {
        return living.copyCenterPosition(targetCenter).dst2(position);
    }

    /** Phản ứng khi trúng mục tiêu, hoặc khi trúng địa hình nếu target null. */
    protected abstract void onImpact(World world, LivingEntity target);

    /** Hướng mẫu về phía vận tốc tức thời, dùng trục -Z của mẫu làm hướng trước. */
    private void updateFacing() {
        float horizontal = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = horizontal <= FACING_EPSILON ? visual.bodyYaw()
                : MathUtils.atan2(-velocity.x, -velocity.z) * MathUtils.radiansToDegrees;
        float pitch = MathUtils.atan2(velocity.y, horizontal) * MathUtils.radiansToDegrees;
        visual.setAnimation(yaw, 0f, 0f, 0f, 0f, 0f);
        visual.setBodyPitch(pitch);
    }

    /** Cho phép World thu hồi một đạn đã kết thúc. */
    public final boolean isFinished() { return finished; }

    /** Trả thực thể chịu trách nhiệm đối với tác động của đạn. */
    public final Entity owner() { return owner; }

    /** Dùng lại ảnh, hình học và trạng thái hướng qua MobRenderer. */
    @Override
    public final MobVisualState mobVisualState() { return visual; }

    /** Phần hình của đạn nằm trong lượt MobRenderer. */
    @Override
    public final void render(ShaderProgram shader) {
    }
}
