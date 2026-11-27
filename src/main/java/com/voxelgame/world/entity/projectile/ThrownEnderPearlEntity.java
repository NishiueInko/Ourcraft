package com.voxelgame.world.entity.projectile;

import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.math.Vector3;

/**
 * Ngọc Ender (Ender Pearl) bay theo quỹ đạo parabol và dịch chuyển chủ sở hữu tới điểm chạm an toàn,
 * đồng thời đặt lại quãng rơi tích lũy và gây {@code 5.0f} sát thương rơi chuẩn Minecraft.
 */
public final class ThrownEnderPearlEntity extends ProjectileEntity {
    /** Gia tốc trọng trường dương (block/giây²) dùng chung với đạn ném bằng tay. */
    public static final float GRAVITY = 14f;
    /** Lượng sát thương dịch chuyển gây cho chủ sở hữu khi ngọc chạm đích. */
    public static final float TELEPORT_DAMAGE = 5.0f;
    /** Bán kính va chạm và thời gian bay tối đa của ngọc Ender. */
    private static final float RADIUS = 0.125f;
    private static final float LIFETIME = 10f;
    /** Các bước lùi dọc ngược hướng bay khi ngọc đâm vào mặt bên hoặc trần khối. */
    private static final float[] BACKOFF_DISTANCES = {0.35f, 0.6f, 0.9f};
    /** Độ lệch tìm ô trống 2 block cao quanh điểm va chạm làm phương án dự phòng. */
    private static final int[] SEARCH_XZ = {0, -1, 1};
    private static final int[] SEARCH_Y = {0, 1, -1, 2};
    /** Sai số nhỏ để tránh chạm biên AABB khi đứng sát mặt khối. */
    private static final float CLEARANCE_EPSILON = 0.001f;

    /** Bộ đệm tọa độ đích được cấp phát một lần trên mỗi viên ngọc (zero-GC). */
    private final Vector3 safeLanding = new Vector3();

    /**
     * Tạo ngọc Ender được ném từ chủ sở hữu trong thế giới hiện tại.
     *
     * @param world thế giới nơi ném đạn (tham số ngữ cảnh thống nhất giao diện Track A)
     * @param owner thực thể sống sẽ được dịch chuyển khi ngọc chạm đích
     * @param x     tọa độ xuất phát X
     * @param y     tọa độ xuất phát Y
     * @param z     tọa độ xuất phát Z
     * @param vx    vận tốc ban đầu theo trục X
     * @param vy    vận tốc ban đầu theo trục Y
     * @param vz    vận tốc ban đầu theo trục Z
     */
    public ThrownEnderPearlEntity(World world, LivingEntity owner, float x, float y, float z,
                                  float vx, float vy, float vz) {
        this((Entity) owner, x, y, z, vx, vy, vz);
    }

    /**
     * Tạo ngọc Ender với chữ ký đồng nhất cùng {@link ArrowEntity} và {@link ThrownPotionEntity}.
     *
     * @param owner thực thể ném đạn
     * @param x     tọa độ xuất phát X
     * @param y     tọa độ xuất phát Y
     * @param z     tọa độ xuất phát Z
     * @param vx    vận tốc ban đầu theo trục X
     * @param vy    vận tốc ban đầu theo trục Y
     * @param vz    vận tốc ban đầu theo trục Z
     */
    public ThrownEnderPearlEntity(Entity owner, float x, float y, float z, float vx, float vy, float vz) {
        super(owner, x, y, z, vx, vy, vz, RADIUS, GRAVITY, LIFETIME, MobAppearance.POTION);
    }

    /**
     * Dịch chuyển chủ sở hữu còn sống tới vị trí đứng an toàn gần điểm va chạm,
     * xóa quãng rơi ({@code fallDistance = 0}) và gây {@code 5.0f} sát thương.
     */
    @Override
    protected void onImpact(World world, LivingEntity target) {
        if (world == null || !(owner() instanceof LivingEntity livingOwner) || !livingOwner.isAlive()) {
            return;
        }
        // Tách khỏi thú cưỡi nếu chủ sở hữu đang cưỡi trước khi dịch chuyển tới điểm mới
        livingOwner.leaveMount();
        findSafeTeleportPosition(world, livingOwner, safeLanding);
        livingOwner.setPosition(safeLanding.x, safeLanding.y, safeLanding.z);
        livingOwner.stopMotion();
        livingOwner.damage(world, TELEPORT_DAMAGE, DamageSource.FALL, null, 0f, 0f);
    }

    /**
     * Tìm tọa độ đứng có đủ chiều cao cho thân và đầu thực thể quanh điểm va chạm,
     * ưu tiên vị trí chạm trực tiếp rồi lùi nhẹ ngược hướng bay nếu va vào mặt bên.
     */
    private void findSafeTeleportPosition(World world, LivingEntity owner, Vector3 out) {
        float halfWidth = owner.getHalfWidth();
        float height = owner.getHeight();
        float impactX = position.x;
        float impactY = position.y;
        float impactZ = position.z;

        // 1. Thử ngay tại điểm chạm (hợp lệ ngay khi ngọc rơi xuống mặt trên sàn)
        if (canStandAt(world, impactX, impactY, impactZ, halfWidth, height)) {
            out.set(impactX, impactY, impactZ);
            return;
        }

        // 2. Lùi nhẹ ngược theo hướng bay 3D và hướng bay ngang XZ nếu đâm vào mặt bên/trần
        float speed = velocity.len();
        if (speed > CLEARANCE_EPSILON) {
            float dirX = velocity.x / speed;
            float dirY = velocity.y / speed;
            float dirZ = velocity.z / speed;
            for (float backoff : BACKOFF_DISTANCES) {
                float cx = impactX - dirX * backoff;
                float cy = impactY - dirY * backoff;
                float cz = impactZ - dirZ * backoff;
                if (canStandAt(world, cx, cy, cz, halfWidth, height)) {
                    out.set(cx, cy, cz);
                    return;
                }
            }
        }

        float horizSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizSpeed > CLEARANCE_EPSILON) {
            float hDirX = velocity.x / horizSpeed;
            float hDirZ = velocity.z / horizSpeed;
            for (float backoff : BACKOFF_DISTANCES) {
                float cx = impactX - hDirX * backoff;
                float cz = impactZ - hDirZ * backoff;
                if (canStandAt(world, cx, impactY, cz, halfWidth, height)) {
                    out.set(cx, impactY, cz);
                    return;
                }
                float snappedY = (float) Math.floor(impactY);
                if (canStandAt(world, cx, snappedY, cz, halfWidth, height)) {
                    out.set(cx, snappedY, cz);
                    return;
                }
                if (canStandAt(world, cx, snappedY + 1.0f, cz, halfWidth, height)) {
                    out.set(cx, snappedY + 1.0f, cz);
                    return;
                }
            }
        }

        // 3. Quét các ô lân cận có khoảng trống 2 block cho chân và đầu
        int baseX = (int) Math.floor(impactX);
        int baseY = (int) Math.floor(impactY);
        int baseZ = (int) Math.floor(impactZ);
        for (int dy : SEARCH_Y) {
            for (int dx : SEARCH_XZ) {
                for (int dz : SEARCH_XZ) {
                    float cx = baseX + dx + 0.5f;
                    float cy = baseY + dy;
                    float cz = baseZ + dz + 0.5f;
                    if (canStandAt(world, cx, cy, cz, halfWidth, height)) {
                        out.set(cx, cy, cz);
                        return;
                    }
                }
            }
        }

        // 4. Phương án cuối nếu toàn bộ vùng xung quanh đều bị chặn kín
        out.set(impactX, impactY, impactZ);
    }

    /**
     * Kiểm tra hộp va chạm AABB của chủ sở hữu tại {@code (x, y, z)} có nằm trong thế giới
     * và hoàn toàn không giao cắt với bất kỳ khối rắn nào hay không.
     */
    private static boolean canStandAt(World world, float x, float y, float z, float halfWidth, float height) {
        if (!world.isInsideWorld((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) {
            return false;
        }
        float minX = x - halfWidth + CLEARANCE_EPSILON;
        float maxX = x + halfWidth - CLEARANCE_EPSILON;
        float minY = y + CLEARANCE_EPSILON;
        float maxY = y + height - CLEARANCE_EPSILON;
        float minZ = z - halfWidth + CLEARANCE_EPSILON;
        float maxZ = z + halfWidth - CLEARANCE_EPSILON;

        int bx0 = (int) Math.floor(minX);
        int bx1 = (int) Math.floor(maxX);
        int by0 = (int) Math.floor(minY);
        int by1 = (int) Math.floor(maxY);
        int bz0 = (int) Math.floor(minZ);
        int bz1 = (int) Math.floor(maxZ);

        for (int bx = bx0; bx <= bx1; bx++) {
            for (int by = by0; by <= by1; by++) {
                for (int bz = bz0; bz <= bz1; bz++) {
                    if (!world.isInsideWorld(bx, by, bz)) {
                        return false;
                    }
                    Block.Bounds bounds = world.getCollisionBounds(bx, by, bz);
                    if (bounds != null && bounds.intersects(bx, by, bz,
                            minX, minY, minZ, maxX, maxY, maxZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
