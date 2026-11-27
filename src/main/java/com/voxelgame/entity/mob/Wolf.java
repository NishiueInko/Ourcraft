package com.voxelgame.entity.mob;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.engine.AudioManager;
import com.voxelgame.engine.MobAppearance;
import com.voxelgame.engine.MobSoundEvent;
import com.voxelgame.engine.MobVisualState;
import com.voxelgame.engine.World;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.FoodRegistry;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.Item;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sói tuyết:
 * - Khi hoang dã ({@code !tamed}): săn mồi tự nhiên và gọi đàn trong 16 block khi bị người chơi tấn công.
 * - Khi đã thuần hóa ({@code tamed}) bằng {@link Item#BONE} (tỷ lệ 1/3): nâng máu tối đa lên 20 HP,
 *   lưu giữ lâu dài ({@code persistent}), cho phép ngồi/đứng, đi theo chủ (hoặc dịch chuyển nếu cách > 24 block),
 *   không bao giờ tấn công chủ và bảo vệ chủ qua {@link #onOwnerAttackOrHurt(LivingEntity)}.
 */
public final class Wolf extends MobEntity {
    /** Phạm vi gọi đàn và tìm mồi, dùng khoảng cách ba chiều. */
    private static final float PACK_RANGE_SQUARED = 16f * 16f;
    /** Cho sói ở rìa đàn đủ tầm đánh trả, giữ tầm săn cũ cho mồi tự nhiên. */
    private static final float PLAYER_PURSUIT_RANGE = 32f;
    private static final float PREY_PURSUIT_RANGE = 20f;
    /** Khoảng giận lấy ngẫu nhiên theo từng cá thể khi người chơi đánh trúng. */
    private static final int MIN_ANGER_SECONDS = 20;
    private static final int MAX_ANGER_SECONDS = 39;
    /** Thông số thuần hóa chuẩn Minecraft: xác suất 1/3 mỗi Bone, tối đa 20 HP và khoảng cách theo chủ. */
    private static final float TAME_CHANCE = 1f / 3f;
    private static final float TAMED_MAX_HEALTH = 20f;
    private static final float FOLLOW_START_DISTANCE_SQUARED = 10f * 10f;
    private static final float FOLLOW_STOP_DISTANCE_SQUARED = 3f * 3f;
    private static final float TELEPORT_DISTANCE_SQUARED = 24f * 24f;
    private static final float FOLLOW_SPEED_MULTIPLIER = 1.15f;

    /** Đệm vị trí dùng lại trong lượt ngắm, tìm mồi và dịch chuyển theo chủ (zero-GC). */
    private final Vector3 nearby = new Vector3();
    private float huntDelay;
    private float shakeRemaining;
    private boolean wasWet;
    /** Trạng thái thuần hóa, tư thế ngồi và tham chiếu chủ sở hữu. */
    private boolean tamed;
    private boolean sitting;
    private Player owner;

    /** Tạo sói tuyết trưởng thành, dùng 4 HP mỗi cú cắn. */
    public Wolf(float x, float y, float z) {
        super(MobType.WOLF, x, y, z, 0f, 0, false);
        setMoveSpeed(3f);
        MobGoals.addDefaults(goals(), type(), false);
        goals().add(new SitGoal());
        goals().add(new MobGoals.MeleeAttackGoal(4, 1.35f, 4f, 1.55f, 1f, PLAYER_PURSUIT_RANGE));
        goals().add(new FollowOwnerGoal());
    }

    /** Tìm mồi theo nhịp riêng khi hoang dã; đi theo, ngồi hoặc dịch chuyển theo chủ khi đã thuần hóa. */
    @Override
    protected void updateGoals(World world, float dt) {
        if (tamed) {
            // Bảo vệ tuyệt đối: không bao giờ giữ chủ hoặc đồng đội cùng chủ làm mục tiêu
            LivingEntity currentTarget = target();
            if (currentTarget == owner || (currentTarget instanceof Wolf otherWolf
                    && otherWolf.isTamed() && otherWolf.owner == owner)) {
                setTarget(null);
            }
            if (sitting) {
                setTarget(null);
                stopMoving();
            } else {
                LivingEntity prey = target();
                float range = prey instanceof Player ? PLAYER_PURSUIT_RANGE : PREY_PURSUIT_RANGE;
                if (prey != null && distanceSquared(prey) > range * range) {
                    setTarget(null);
                }
                if (owner != null && owner.isAlive() && distanceSquared(owner) > TELEPORT_DISTANCE_SQUARED) {
                    teleportNearOwner(world);
                }
            }
        } else {
            huntDelay -= dt;
            LivingEntity prey = target();
            float range = prey instanceof Player ? PLAYER_PURSUIT_RANGE : PREY_PURSUIT_RANGE;
            if (prey != null && distanceSquared(prey) > range * range) {
                setTarget(null);
            }
            if (target() == null && huntDelay <= 0f) {
                huntDelay = 0.5f;
                setTarget(findPrey(world));
            }
        }
        super.updateGoals(world, dt);

        if (inWater()) {
            wasWet = true;
            shakeRemaining = 0f;
        } else if (wasWet && onGround) {
            wasWet = false;
            shakeRemaining = 0.85f;
        }
        updateAppearance();
        Player player = world.getPlayer();
        if (tamed && sitting) {
            shakeRemaining = 0f;
            mobVisualState().setPose(MobVisualState.Pose.SIT, 1f);
        } else if (target() instanceof Player) {
            shakeRemaining = 0f;
            mobVisualState().setPose(MobVisualState.Pose.ANGRY, 1f);
        } else if (shakeRemaining > 0f) {
            stopMoving();
            shakeRemaining = Math.max(0f, shakeRemaining - dt);
            mobVisualState().setPose(MobVisualState.Pose.SHAKE, shakeRemaining / 0.85f);
        } else if (target() == null && player != null && player.isAlive() && distanceSquared(player) <= 25f) {
            lookAt(nearby.x, nearby.y, nearby.z);
            mobVisualState().setPose(MobVisualState.Pose.TILT_HEAD, 1f);
        } else {
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }
    }

    /**
     * Tương tác chuột phải từ {@link com.voxelgame.VoxelGameApp}:
     * ưu tiên đốt lửa bằng Flint and Steel từ lớp cha, sau đó chuyển sang thuần hóa/ngồi-đứng.
     */
    @Override
    public boolean onPlayerInteract(World world, Player player) {
        if (super.onPlayerInteract(world, player)) {
            return true;
        }
        if (!isAlive() || player == null || !player.isAlive()) {
            return false;
        }
        Hotbar hotbar = player.getHotbar();
        Item heldItem = (hotbar != null && hotbar.getSelectedCount() > 0)
                ? hotbar.getSelectedItem() : Item.NONE;
        boolean handled = interactWithPlayer(player, heldItem);
        if (handled && world != null && world.getAudio() != null) {
            world.getAudio().playEffect(AudioManager.Effect.CLICK, 0.35f);
        }
        return handled;
    }

    /**
     * Tương tác giữa người chơi và sói bằng vật phẩm đang cầm ({@link Item#BONE} để thuần hóa,
     * tay không hoặc vật phẩm thường để bật/tắt ngồi sau khi thuần hóa).
     *
     * @param player   người chơi tương tác
     * @param heldItem vật phẩm đang cầm (có thể là {@link Item#NONE} hoặc {@code null})
     * @return {@code true} nếu tương tác đã được xử lý
     */
    public boolean interactWithPlayer(Player player, Item heldItem) {
        return interactWithPlayer(player, heldItem, ThreadLocalRandom.current().nextFloat());
    }

    /**
     * Phiên bản xác định trước kết quả ngẫu nhiên trong {@code [0, 1)} phục vụ kiểm thử đơn vị.
     *
     * @param player      người chơi tương tác
     * @param heldItem    vật phẩm đang cầm
     * @param rollForTest giá trị tung xác suất thuần hóa (thành công khi {@code rollForTest < 1/3f})
     * @return {@code true} nếu tương tác đã được xử lý
     */
    public boolean interactWithPlayer(Player player, Item heldItem, float rollForTest) {
        if (!isAlive() || player == null || !player.isAlive()) {
            return false;
        }
        Hotbar hotbar = player.getHotbar();
        if (!tamed) {
            // Sói hoang đang tấn công chính người chơi này thì không cho phép thuần hóa
            if (heldItem != Item.BONE || target() == player) {
                return false;
            }
            if (hotbar != null && hotbar.getSelectedItem() == Item.BONE && hotbar.getSelectedCount() > 0) {
                hotbar.consumeSelectedItemStack();
            }
            if (rollForTest < TAME_CHANCE) {
                tamed = true;
                owner = player;
                sitting = false;
                setPersistent(true);
                setTarget(null);
                stopMoving();
                setMaxHealth(TAMED_MAX_HEALTH, true);
                updateAppearance();
                mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
            }
            return true;
        }

        // Sau khi thuần hóa, chỉ chủ sở hữu mới có quyền ra lệnh ngồi/đứng hoặc cho ăn hồi máu
        if (owner != null && player != owner) {
            return false;
        }
        if (heldItem == Item.FLINT_AND_STEEL) {
            return false;
        }
        if (getHealth() < getMaxHealth() && isWolfFood(heldItem)) {
            FoodRegistry.FoodSpec spec = FoodRegistry.of(heldItem);
            if (spec != null) {
                if (hotbar != null && hotbar.getSelectedItem() == heldItem && hotbar.getSelectedCount() > 0) {
                    hotbar.consumeSelectedItemStack();
                }
                heal(spec.nutrition());
                return true;
            }
        }
        sitting = !sitting;
        if (sitting) {
            setTarget(null);
            stopMoving();
            mobVisualState().setPose(MobVisualState.Pose.SIT, 1f);
        } else {
            mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
        }
        return true;
    }

    /**
     * Thông báo cho sói đã thuần hóa về kẻ tấn công chủ hoặc mục tiêu mà chủ vừa đánh.
     * Bỏ qua nếu sói chưa thuần hóa, đang ngồi, đã chết, hoặc mục tiêu chính là chủ/đồng đội cùng chủ.
     *
     * @param threat thực thể thù địch liên quan đến chủ sở hữu
     */
    public void onOwnerAttackOrHurt(LivingEntity threat) {
        if (!tamed || !isAlive() || sitting || threat == null || !threat.isAlive()
                || threat == this || threat == owner) {
            return;
        }
        if (threat instanceof Wolf otherWolf && otherWolf.isTamed() && otherWolf.owner == owner) {
            return;
        }
        enrage(threat, MAX_ANGER_SECONDS);
    }

    /** Trả {@code true} nếu sói đã được thuần hóa. */
    public boolean isTamed() {
        return tamed;
    }

    /** Trả {@code true} nếu sói đang ở tư thế ngồi chờ lệnh. */
    public boolean isSitting() {
        return sitting;
    }

    /** Trả người chơi sở hữu sói này, hoặc {@code null} nếu còn hoang dã. */
    public Player getOwner() {
        return owner;
    }

    /** Chọn cá thể sống gần nhất trong các loài sói tuyết săn. */
    private MobEntity findPrey(World world) {
        MobEntity nearest = null;
        float best = PACK_RANGE_SQUARED;
        for (int index = 0, count = world.entityCount(); index < count; index++) {
            if (!(world.entityAt(index) instanceof MobEntity mob) || !mob.isAlive()) {
                continue;
            }
            boolean prey = switch (mob.type()) {
                case RABBIT, SNOW_RABBIT, SNOW_FOX, SHEEP, SKELETON, STRAY -> true;
                default -> false;
            };
            if (prey) {
                float distance = distanceSquared(mob);
                if (distance <= best) {
                    nearest = mob;
                    best = distance;
                }
            }
        }
        return nearest;
    }

    /**
     * Xử lý khi bị đánh trúng:
     * - Sói thuần hóa không bao giờ đánh trả chủ hoặc đồng đội cùng chủ, và đứng dậy đánh trả kẻ khác.
     * - Sói hoang giữ nguyên phản ứng gọi cả đàn trong 16 block tấn công người chơi.
     */
    @Override
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        boolean applied = super.damage(world, amount, source, attacker, knockbackX, knockbackZ);
        if (!applied) {
            return false;
        }
        if (tamed) {
            if (attacker == owner || (attacker instanceof Wolf otherWolf
                    && otherWolf.isTamed() && otherWolf.owner == owner)) {
                if (target() == attacker) {
                    setTarget(null);
                }
                updateAppearance();
                return true;
            }
            if (isAlive() && attacker instanceof LivingEntity living && living.isAlive()) {
                if (sitting) {
                    sitting = false;
                    mobVisualState().setPose(MobVisualState.Pose.IDLE, 0f);
                }
                enrage(living, ThreadLocalRandom.current().nextInt(MIN_ANGER_SECONDS, MAX_ANGER_SECONDS + 1));
            }
            updateAppearance();
            return true;
        }
        if (world != null && attacker instanceof Player player && player.isAlive()) {
            if (isAlive()) {
                defendPack(world, player);
            }
            for (int index = 0, count = world.entityCount(); index < count; index++) {
                if (world.entityAt(index) instanceof Wolf wolf && wolf != this && !wolf.isTamed()
                        && wolf.isAlive() && distanceSquared(wolf) <= PACK_RANGE_SQUARED) {
                    wolf.defendPack(world, player);
                }
            }
        }
        return true;
    }

    /** Cập nhật đồng thời mục tiêu, texture và tiếng gầm khi cả đàn đáp trả. */
    private void defendPack(World world, Player player) {
        if (mobVisualState().appearance() != MobAppearance.WOLF_SNOWY_ANGRY) {
            playSound(world, MobSoundEvent.WOLF_GROWL);
        }
        enrage(player, ThreadLocalRandom.current().nextInt(MIN_ANGER_SECONDS, MAX_ANGER_SECONDS + 1));
        mobVisualState().setPose(MobVisualState.Pose.ANGRY, 1f);
        updateAppearance();
    }

    /** Chỉ dùng hình mắt đỏ trong lúc đang truy đuổi người chơi. */
    private void updateAppearance() {
        boolean angryAtPlayer = target() instanceof Player && (!tamed || target() != owner);
        mobVisualState().setAppearance(angryAtPlayer
                ? MobAppearance.WOLF_SNOWY_ANGRY : MobAppearance.WOLF_SNOWY);
    }

    /** Dịch chuyển sói đã thuần hóa tới một ô trống vững chắc gần chủ sở hữu khi bị bỏ lại > 24 block. */
    private void teleportNearOwner(World world) {
        if (world == null || owner == null || !owner.isAlive()) {
            return;
        }
        owner.copyPosition(nearby);
        int baseX = (int) Math.floor(nearby.x);
        int baseY = (int) Math.floor(nearby.y);
        int baseZ = (int) Math.floor(nearby.z);

        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    if (tryTeleportToColumn(world, baseX + dx, baseY, baseZ + dz)) {
                        return;
                    }
                }
            }
        }
        tryTeleportToColumn(world, baseX, baseY, baseZ);
    }

    /** Kiểm tra cột đích có mặt đứng khô ráo an toàn và đặt sói vào tâm ô nếu hợp lệ. */
    private boolean tryTeleportToColumn(World world, int tx, int baseY, int tz) {
        if (!world.isInsideWorld(tx, 0, tz)) {
            return false;
        }
        float feetY = navigator().findStandableY(world, tx, baseY, tz);
        if (!Float.isFinite(feetY)) {
            return false;
        }
        int belowY = (int) Math.floor(feetY - 0.05f);
        int footBlockY = (int) Math.floor(feetY + 0.1f);
        if (world.getBlock(tx, belowY, tz) == Block.WATER.getId()
                || world.getBlock(tx, footBlockY, tz) == Block.WATER.getId()) {
            return false;
        }
        setPosition(tx + 0.5f, feetY, tz + 0.5f);
        stopMotion();
        stopMoving();
        LivingEntity prey = target();
        if (prey != null && distanceSquared(prey) > PREY_PURSUIT_RANGE * PREY_PURSUIT_RANGE) {
            setTarget(null);
        }
        return true;
    }

    /** Các loại thịt giúp hồi máu cho sói đã thuần hóa khi bị thương. */
    private static boolean isWolfFood(Item item) {
        return item == Item.BEEF || item == Item.COOKED_BEEF
                || item == Item.PORKCHOP || item == Item.COOKED_PORKCHOP
                || item == Item.MUTTON || item == Item.COOKED_MUTTON
                || item == Item.CHICKEN || item == Item.COOKED_CHICKEN
                || item == Item.RABBIT || item == Item.COOKED_RABBIT
                || item == Item.ROTTEN_FLESH;
    }

    /** Chép tâm vào bộ đệm và trả bình phương khoảng cách. */
    private float distanceSquared(LivingEntity other) {
        return other.copyCenterPosition(nearby).dst2(x(), y() + height * 0.5f, z());
    }

    /** Goal ưu tiên cao giữ sói đứng yên tại chỗ khi đang ở trạng thái ngồi. */
    private final class SitGoal implements MobGoal {
        @Override
        public int priority() {
            return 1;
        }

        @Override
        public int controls() {
            return MOVE;
        }

        @Override
        public boolean canStart(World world, MobEntity mob) {
            return tamed && sitting;
        }

        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return tamed && sitting;
        }

        @Override
        public void start(World world, MobEntity mob) {
            mob.stopMoving();
        }

        @Override
        public void tick(World world, MobEntity mob, float dt) {
            mob.stopMoving();
        }

        @Override
        public void stop(World world, MobEntity mob) {
        }
    }

    /** Goal dẫn sói đã thuần hóa chạy theo chủ khi đứng cách chủ quá 10 block và không chiến đấu. */
    private final class FollowOwnerGoal implements MobGoal {
        private final Vector3 ownerCenter = new Vector3();

        @Override
        public int priority() {
            return 5;
        }

        @Override
        public int controls() {
            return MOVE | LOOK;
        }

        @Override
        public boolean canStart(World world, MobEntity mob) {
            return tamed && !sitting && owner != null && owner.isAlive()
                    && target() == null && distanceSquared(owner) > FOLLOW_START_DISTANCE_SQUARED;
        }

        @Override
        public boolean shouldContinue(World world, MobEntity mob) {
            return tamed && !sitting && owner != null && owner.isAlive()
                    && target() == null && distanceSquared(owner) > FOLLOW_STOP_DISTANCE_SQUARED;
        }

        @Override
        public void start(World world, MobEntity mob) {
        }

        @Override
        public void tick(World world, MobEntity mob, float dt) {
            if (owner == null || !owner.isAlive()) {
                return;
            }
            owner.copyCenterPosition(ownerCenter);
            float feetY = ownerCenter.y - owner.getHeight() * 0.5f;
            mob.lookAt(ownerCenter.x, feetY + owner.getHeight() * 0.8f, ownerCenter.z);
            mob.moveToward(world, ownerCenter.x, feetY, ownerCenter.z, FOLLOW_SPEED_MULTIPLIER);
        }

        @Override
        public void stop(World world, MobEntity mob) {
            mob.stopMoving();
        }
    }
}
