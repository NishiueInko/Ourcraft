package com.voxelgame.world.entity.projectile;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.mob.MobCategory;
import com.voxelgame.world.entity.mob.MobEntity;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.Objects;

/**
 * Mũi tên gây sát thương vật lý, có thể mang hiệu ứng, tự cắm cố định vào đất (cho phép người chơi nhặt lại
 * và rơi tiếp khi khối đỡ bị đào mất) hoặc cắm theo thân mob khi bắn trúng.
 */
public final class ArrowEntity extends ProjectileEntity {
    /** Gia tốc rơi dương để bộ bắn tính độ cao ngắm. */
    public static final float GRAVITY = 20f;
    /** Kích thước, thời gian bay tối đa và độ đẩy ngang của tên. */
    private static final float RADIUS = 0.045f;
    private static final float LIFETIME = 8f;
    private static final float KNOCKBACK_SPEED = 1.4f;
    /** Thời gian mũi tên cắm trên đất hoặc trên mob trước khi tự biến mất (giây). */
    private static final float GROUND_DESPAWN_SECONDS = 60f;
    private static final float MOB_STICK_DESPAWN_SECONDS = 30f;
    /** Trễ ngắn trước khi cho phép nhặt lại mũi tên cắm trên đất và bán kính nhặt. */
    private static final float PICKUP_DELAY_SECONDS = 0.2f;
    private static final float PICKUP_RADIUS = 1.15f;
    private static final float PICKUP_RADIUS_SQUARED = PICKUP_RADIUS * PICKUP_RADIUS;

    private final ArrowType type;
    private final float damage;
    /** Chỉ mũi tên do người chơi bắn mới cho phép nhặt lại từ mặt đất. */
    private final boolean pickupable;

    /** Trạng thái mũi tên đang cắm vào một khối rắn trên địa hình. */
    private boolean inGround;
    private int stuckBlockX;
    private int stuckBlockY;
    private int stuckBlockZ;
    private float stuckTimeRemaining;
    private float pickupDelay;

    /** Trạng thái mũi tên đang cắm trên thân một mob sống (zero-GC với vector đệm dùng lại). */
    private MobEntity stuckMob;
    private float stuckLocalX;
    private float stuckLocalY;
    private float stuckLocalZ;
    private float stuckRelativeYaw;
    private float stuckPitch;
    private final Vector3 tmpAnchor = new Vector3();

    /** Tạo tên tại điểm bắn và nhận lượng sát thương HP từ cấu hình cung thủ. */
    public ArrowEntity(Entity owner, float x, float y, float z, float vx, float vy, float vz,
                       ArrowType type, float damage) {
        super(owner, x, y, z, vx, vy, vz, RADIUS, GRAVITY, LIFETIME, MobAppearance.ARROW);
        this.type = Objects.requireNonNull(type);
        if (!(damage > 0f) || !Float.isFinite(damage)) {
            throw new IllegalArgumentException("Sát thương tên phải dương và hữu hạn");
        }
        this.damage = damage;
        this.pickupable = owner instanceof Player;
    }

    /** Trả về {@code true} khi mũi tên đang cắm trên đất hoặc bám trên thân mob. */
    @Override
    protected boolean isAnchored() {
        return inGround || stuckMob != null;
    }

    /** Cập nhật mũi tên đang cắm trên khối (kiểm tra đào khối, nhặt lại, hết hạn) hoặc bám theo mob. */
    @Override
    protected boolean updateAnchored(World world, float dt) {
        if (inGround) {
            stuckTimeRemaining -= dt;
            if (stuckTimeRemaining <= 0f) {
                inGround = false;
                markFinished();
                return true;
            }
            // Nếu khối mà mũi tên đang cắm vào bị phá vỡ, mũi tên rơi xuống trở lại theo trọng lực
            if (world.getCollisionBounds(stuckBlockX, stuckBlockY, stuckBlockZ) == null) {
                inGround = false;
                velocity.set(0f, -0.5f, 0f);
                resumeFlight(LIFETIME);
                return false;
            }
            if (pickupDelay > 0f) {
                pickupDelay = Math.max(0f, pickupDelay - dt);
            } else if (pickupable) {
                tryPickup(world);
            }
            return true;
        }

        if (stuckMob != null) {
            stuckTimeRemaining -= dt;
            if (stuckTimeRemaining <= 0f || !stuckMob.isAlive() || stuckMob.isReadyToRemove()) {
                stuckMob = null;
                markFinished();
                return true;
            }
            syncStuckMobTransform();
            return true;
        }

        return false;
    }

    /** Cho phép né trước khi ghi sát thương; cắm vào khối khi hụt mục tiêu hoặc cắm vào mob khi trúng. */
    @Override
    protected void onImpact(World world, LivingEntity target) {
        if (target == null) {
            inGround = true;
            stuckMob = null;
            stuckBlockX = hitBlockX();
            stuckBlockY = hitBlockY();
            stuckBlockZ = hitBlockZ();
            stuckTimeRemaining = GROUND_DESPAWN_SECONDS;
            pickupDelay = PICKUP_DELAY_SECONDS;
            velocity.setZero();
            keepAliveAfterImpact();
            return;
        }
        if (target.dodgeProjectile(world, owner())) {
            return;
        }
        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float scale = horizontalSpeed > 0f ? KNOCKBACK_SPEED / horizontalSpeed : 0f;
        if (!target.damage(world, damage, DamageSource.ARROW, owner(), velocity.x * scale, velocity.z * scale)) {
            return;
        }
        type.apply(target);
        if (target.isAlive() && owner() instanceof MobEntity shooter && shooter.isAlive()
                && target instanceof MobEntity victim && shooter.type().category() == MobCategory.MONSTER
                && victim.type().category() == MobCategory.MONSTER) {
            shooter.retaliateAgainst(victim);
            victim.retaliateAgainst(shooter);
        }
        if (target.isAlive() && target instanceof MobEntity mob) {
            attachToMob(mob);
            velocity.setZero();
            keepAliveAfterImpact();
        }
    }

    /** Lưu tọa độ và góc tương đối của mũi tên trong hệ quy chiếu cục bộ của mob vừa bị bắn trúng. */
    private void attachToMob(MobEntity mob) {
        mob.copyPosition(tmpAnchor);
        float dx = position.x - tmpAnchor.x;
        float dy = MathUtils.clamp(position.y - tmpAnchor.y, 0.1f, Math.max(0.2f, mob.getHeight() - 0.1f));
        float dz = position.z - tmpAnchor.z;
        float mobYaw = mob.mobVisualState().bodyYaw();
        float cos = MathUtils.cosDeg(mobYaw);
        float sin = MathUtils.sinDeg(mobYaw);
        stuckLocalX = dx * cos - dz * sin;
        stuckLocalY = dy;
        stuckLocalZ = dx * sin + dz * cos;
        stuckRelativeYaw = mobVisualState().bodyYaw() - mobYaw;
        stuckPitch = mobVisualState().bodyPitch();
        stuckMob = mob;
        inGround = false;
        stuckTimeRemaining = MOB_STICK_DESPAWN_SECONDS;
    }

    /** Đồng bộ vị trí và góc xoay của mũi tên theo vị trí và hướng thân mới nhất của mob. */
    private void syncStuckMobTransform() {
        stuckMob.copyPosition(tmpAnchor);
        float mobYaw = stuckMob.mobVisualState().bodyYaw();
        float cos = MathUtils.cosDeg(mobYaw);
        float sin = MathUtils.sinDeg(mobYaw);
        float worldDx = stuckLocalX * cos + stuckLocalZ * sin;
        float worldDz = -stuckLocalX * sin + stuckLocalZ * cos;
        position.set(tmpAnchor.x + worldDx, tmpAnchor.y + stuckLocalY, tmpAnchor.z + worldDz);
        mobVisualState().setAnimation(mobYaw + stuckRelativeYaw, 0f, 0f, 0f, 0f, 0f);
        mobVisualState().setBodyPitch(stuckPitch);
    }

    /** Cho phép người chơi đi lại gần nhặt lại mũi tên cắm trên đất vào túi đồ 36 ô. */
    private void tryPickup(World world) {
        Player player = world.getPlayer();
        if (player == null || !player.isAlive()) {
            return;
        }
        player.copyCenterPosition(tmpAnchor);
        if (tmpAnchor.dst2(position) > PICKUP_RADIUS_SQUARED) {
            return;
        }
        if (player.getInventory().addItem(type.toItem(), 1)) {
            if (world.getAudio() != null) {
                world.getAudio().playEffect(AudioManager.Effect.PICKUP, 0.25f);
            }
            inGround = false;
            markFinished();
        }
    }

    /** Cho biết mũi tên hiện có đang cắm cố định vào địa hình hay không. */
    public boolean isInGround() {
        return inGround;
    }

    /** Cho biết mũi tên hiện có đang cắm trên thân một mob hay không. */
    public boolean isStuckInMob() {
        return stuckMob != null;
    }

    /** Trả về mob đang bị mũi tên cắm vào, hoặc {@code null} nếu không cắm trên mob. */
    public MobEntity getStuckMob() {
        return stuckMob;
    }

    /** Cho biết mũi tên này có thể được người chơi nhặt lại khi cắm trên đất hay không. */
    public boolean isPickupable() {
        return pickupable;
    }

    /** Trả về loại đầu mũi tên (thường, Slowness hoặc Weakness). */
    public ArrowType getType() {
        return type;
    }
}
