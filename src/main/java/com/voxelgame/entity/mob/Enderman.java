package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.Raycast;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Quái trung lập phản ứng với ngắm vào mắt, nước và đạn đạo. */
public final class Enderman extends MobEntity {
    /** Tầm ngắm mắt, kích thước vùng mắt và bán kính tối đa tìm điểm dịch chuyển. */
    private static final float SIGHT_RANGE = 64f;
    private static final float EYE_RADIUS = 0.28f;
    private static final int TELEPORT_RADIUS = 32;
    private static final int TELEPORT_ATTEMPTS = 24;
    private static final int NEAR_FALLBACK_RADIUS = 4;
    private static final int BLOCK_ATTEMPTS = 8;

    /** Bộ đệm tia mắt và trạng thái đích dịch chuyển được tái sử dụng. */
    private final Vector3 eye = new Vector3();
    private final Vector3 direction = new Vector3();
    private final Vector3 placement = new Vector3();
    private final Vector3 wetFallback = new Vector3();
    private final Raycast.HitResult obstruction = Raycast.HitResult.miss();
    private float gazeDelay;
    private float wetDelay;
    private float combatTeleportDelay = 2.8f;
    private float blockDelay = ThreadLocalRandom.current().nextFloat(6f, 14f);
    private Block heldBlock = Block.AIR;

    /** Dùng goal chung và đòn cận chiến 7 HP khi đang giận. */
    public Enderman(float x, float y, float z) {
        super(MobType.ENDERMAN, x, y, z, 0f, 0, false);
        setMoveSpeed(3f);
        MobGoals.addDefaults(goals(), type(), false);
        goals().add(new MobGoals.MeleeAttackGoal(4, 1f, 7f, 0.85f, 1f, SIGHT_RANGE));
    }

    /** Kiểm tra kích thích tại đầu lượt trước khi bộ goal chọn đường đi. */
    @Override
    protected void updateGoals(World world, float dt) {
        wetDelay -= dt;
        if (wetDelay <= 0f && (inWater() || world.isRainingAt(x(), y() + getHeight(), z()))) {
            wetDelay = 1f;
            damage(world, 1f, DamageSource.WATER, null, 0f, 0f);
            if (!isAlive()) {
                return;
            }
            teleport(world, NEAR_FALLBACK_RADIUS);
        }

        gazeDelay -= dt;
        Player player = world.getPlayer();
        if (target() == null && gazeDelay <= 0f) {
            gazeDelay = 0.10f;
            if (player != null && player.isAlive() && seesEyes(world, player)) {
                enrage(player, 25f);
                playSound(world, MobSoundEvent.ENDERMAN_STARE);
            }
        }

        LivingEntity victim = target();
        if (victim != null) {
            mobVisualState().setPose(MobVisualState.Pose.ANGRY, 1f);
            setMoveSpeed(4.1f);
            combatTeleportDelay -= dt;
            if (combatTeleportDelay <= 0f) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                combatTeleportDelay = random.nextFloat(2.5f, 4.5f);
                victim.copyPosition(placement);
                if (placement.dst2(x(), y(), z()) > 36f && random.nextInt(4) == 0) {
                    teleport(world, NEAR_FALLBACK_RADIUS);
                }
            }
        } else {
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
            setMoveSpeed(3f);
            blockDelay -= dt;
            if (blockDelay <= 0f) {
                blockDelay = ThreadLocalRandom.current().nextFloat(6f, 14f);
                moveNaturalBlock(world);
            }
        }
        super.updateGoals(world, dt);
    }

    /** So tia thật từ mắt người chơi với vùng mắt nhỏ, rồi thử vật cản voxel. */
    private boolean seesEyes(World world, Player player) {
        if (!player.copyAim(eye, direction)) {
            return false;
        }
        float dx = x() - eye.x;
        float dy = y() + getHeight() * 0.92f - eye.y;
        float dz = z() - eye.z;
        float distance = dx * dx + dy * dy + dz * dz;
        float along = direction.dot(dx, dy, dz);
        return distance <= SIGHT_RANGE * SIGHT_RANGE && along > 0f
                && distance - along * along <= EYE_RADIUS * EYE_RADIUS
                && !Raycast.castOccluder(world, eye, direction, along, obstruction).isHit();
    }

    /** Phát tiếng hét khi lần đầu chuyển từ trung lập sang giận vì bị tấn công. */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker, float knockbackX,
                          float knockbackZ) {
        boolean newAttacker = attacker instanceof LivingEntity && target() != attacker;
        boolean applied = super.damage(world, amount, source, attacker, knockbackX, knockbackZ);
        if (applied && isAlive() && newAttacker && target() == attacker) {
            playSound(world, MobSoundEvent.ENDERMAN_SCREAM);
        }
        return applied;
    }

    /** Tên không gây sát thương, kể cả khi địa hình quanh mob không có chỗ đáp hợp lệ. */
    @Override
    public boolean dodgeProjectile(World world, Entity owner) {
        teleport(world, TELEPORT_RADIUS);
        return true;
    }

    /** Ưu tiên vị trí khô có mái; khi cả vùng đang mưa vẫn chọn được nền chắc để thoát nước. */
    private boolean teleport(World world, int fallbackRadius) {
        int bx = MathUtils.floor(x());
        int bz = MathUtils.floor(z());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        wetFallback.y = Float.NaN;
        for (int i = 0; i < TELEPORT_ATTEMPTS; i++) {
            int tx = bx + random.nextInt(-TELEPORT_RADIUS, TELEPORT_RADIUS + 1);
            int tz = bz + random.nextInt(-TELEPORT_RADIUS, TELEPORT_RADIUS + 1);
            if (findLanding(world, tx, tz)) {
                finishTeleport(world, placement);
                return true;
            }
        }
        if (Float.isNaN(wetFallback.y) && findFallbackLanding(world, bx, bz, fallbackRadius)) {
            finishTeleport(world, placement);
            return true;
        }
        if (!Float.isNaN(wetFallback.y)) {
            finishTeleport(world, wetFallback);
            return true;
        }
        return false;
    }

    /** Duyệt các vành từ gần tới xa khi lần né tên cần tìm được cả điểm đáp lẻ. */
    private boolean findFallbackLanding(World world, int bx, int bz, int radius) {
        for (int ring = 1; ring <= radius; ring++) {
            for (int delta = -ring; delta <= ring; delta++) {
                if (findLanding(world, bx + delta, bz - ring) || findLanding(world, bx + delta, bz + ring)
                        || (Math.abs(delta) < ring && (findLanding(world, bx - ring, bz + delta)
                        || findLanding(world, bx + ring, bz + delta)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Tìm điểm đứng khô trong bán kính 3D và lưu một phương án dưới mưa để dự phòng. */
    private boolean findLanding(World world, int bx, int bz) {
        if (!world.isInsideWorld(bx, 0, bz)) {
            return false;
        }
        float tx = bx + 0.5f;
        float tz = bz + 0.5f;
        float dx = tx - x();
        float dz = tz - z();
        float horizontal = dx * dx + dz * dz;
        if (horizontal < 1f || horizontal > TELEPORT_RADIUS * TELEPORT_RADIUS) {
            return false;
        }
        float ty = navigator().findStandableY(world, bx, MathUtils.floor(y()), bz);
        if (!Float.isFinite(ty)) {
            int surface = MathUtils.floor(world.getPrecipitationSurface(bx, bz));
            ty = navigator().findStandableY(world, bx, surface, bz);
        }
        float dy = ty - y();
        if (!Float.isFinite(ty) || horizontal + dy * dy > TELEPORT_RADIUS * TELEPORT_RADIUS) {
            return false;
        }
        for (int by = MathUtils.floor(ty); by <= MathUtils.floor(ty + getHeight()); by++) {
            if (Block.fromId(world.getBlock(bx, by, bz)).isLiquid()) {
                return false;
            }
        }
        if (world.hasLivingOverlap(tx, ty, tz, getHalfWidth(), getHeight(), this)) {
            return false;
        }
        if (world.isRainingAt(tx, ty + getHeight(), tz)) {
            if (Float.isNaN(wetFallback.y)) {
                wetFallback.set(tx, ty, tz);
            }
            return false;
        }
        placement.set(tx, ty, tz);
        return true;
    }

    /** Đồng bộ vận tốc, quãng rơi và âm thanh sau khi chọn điểm đáp. */
    private void finishTeleport(World world, Vector3 landing) {
        setPosition(landing.x, landing.y, landing.z);
        velocity.setZero();
        stopMoving();
        playSound(world, MobSoundEvent.ENDERMAN_TELEPORT);
    }

    /** Thỉnh thoảng nhặt một khối tự nhiên và đặt lại tại ô trống có nền gần đó. */
    private void moveNaturalBlock(World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int bx = MathUtils.floor(x());
        int by = MathUtils.floor(y());
        int bz = MathUtils.floor(z());
        for (int i = 0; i < BLOCK_ATTEMPTS; i++) {
            int tx = bx + random.nextInt(-3, 4);
            int ty = by + random.nextInt(-1, 2);
            int tz = bz + random.nextInt(-3, 4);
            if (tx == bx && tz == bz || !world.isInsideWorld(tx, ty, tz)) {
                continue;
            }
            Block block = Block.fromId(world.getBlock(tx, ty, tz));
            if (heldBlock == Block.AIR) {
                if (canCarry(block)) {
                    heldBlock = block;
                    mobVisualState().setHeldBlock(heldBlock);
                    world.setBlock(tx, ty, tz, Block.AIR.getId());
                    return;
                }
            } else if (block == Block.AIR && world.isSolidBlock(tx, ty - 1, tz)
                    && !intersectsBlock(tx, ty, tz) && world.tryPlaceBlock(tx, ty, tz, heldBlock, world.getPlayer())) {
                heldBlock = Block.AIR;
                mobVisualState().setHeldBlock(null);
                return;
            }
        }
    }

    /** Lấy và xóa khối đang cầm để rơi ra ngoài thế giới khi Enderman bị tiêu diệt. */
    Block takeHeldBlockForLoot() {
        Block carried = heldBlock;
        heldBlock = Block.AIR;
        mobVisualState().setHeldBlock(null);
        return carried;
    }

    /** Chỉ cho phép nhặt các khối tự nhiên được quy định cho loài. */
    private static boolean canCarry(Block block) {
        return block == Block.DIRT || block == Block.GRASS || block == Block.SAND || block == Block.GRAVEL
                || block == Block.DANDELION || block == Block.POPPY || block == Block.CORNFLOWER
                || block == Block.OXEYE_DAISY;
    }
}
