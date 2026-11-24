package com.voxelgame.entity;

import com.voxelgame.engine.World;

/** Thực thể vật lý có máu, hiệu ứng trạng thái và vòng đời chiến đấu. */
public abstract class LivingEntity extends Entity {
    /** Thời gian bảo vệ sau sát thương và thời gian hiển thị phản hồi bị đánh. */
    private static final float HURT_SECONDS = 0.5f;
    /** Thời gian giữ lại thực thể chết cho hoạt ảnh. */
    private static final float DEATH_SECONDS = 1.0f;
    /** Chu kỳ mất một HP vì trúng độc hoặc đang cháy. */
    private static final float POISON_INTERVAL = 1.25f;
    private static final float FIRE_INTERVAL = 1.0f;
    /** Sai số nhỏ để nhịp tích lũy theo frame chạm đúng mốc. */
    private static final float TIMER_EPSILON = 0.000001f;
    /** Dùng chung mảng rỗng cho thực thể không có ghế. */
    private static final LivingEntity[] NO_PASSENGERS = new LivingEntity[0];

    protected final float maxHealth;
    protected final float armor;
    protected final boolean undead;
    private float health;
    private float invulnerabilityTimer;
    private float hurtFlashTimer;
    private float fireTimer;
    private float fireElapsed;
    private float poisonElapsed;
    private float fallStartY;
    private float fallDistance;
    private boolean dead;
    private float deathTimer;
    private final StatusEffectInstance[] effects = new StatusEffectInstance[StatusEffectType.COUNT];
    private LivingEntity mount;
    private final LivingEntity[] passengers;

    /** Khởi tạo thực thể không có ghế ngồi. */
    protected LivingEntity(float x, float y, float z, float halfWidth, float height,
                           float maxHealth, float armor, boolean undead) {
        this(x, y, z, halfWidth, height, maxHealth, armor, undead, 0);
    }

    /** Khởi tạo chỉ số và cấp phát trước số ghế cùng các khe hiệu ứng. */
    protected LivingEntity(float x, float y, float z, float halfWidth, float height,
                           float maxHealth, float armor, boolean undead, int seatCount) {
        super(x, y, z, halfWidth, height);
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.armor = armor;
        this.undead = undead;
        passengers = seatCount == 0 ? NO_PASSENGERS : new LivingEntity[seatCount];
        for (int i = 0; i < effects.length; i++) {
            effects[i] = new StatusEffectInstance();
        }
        resetFallTracking();
    }

    /** Cập nhật hồi chiêu, chuyển động và tác động môi trường theo thứ tự. */
    @Override
    public final void update(World world, float deltaTime) {
        if (world == null || !(deltaTime > 0f) || !Float.isFinite(deltaTime)) {
            return;
        }
        if (dead) {
            deathTimer = Math.max(0f, deathTimer - deltaTime);
            finishLivingUpdate(world);
            return;
        }

        invulnerabilityTimer = Math.max(0f, invulnerabilityTimer - deltaTime);
        hurtFlashTimer = Math.max(0f, hurtFlashTimer - deltaTime);
        boolean ignoreFallBeforeMove = ignoresFall();
        updateLiving(world, deltaTime);
        if (!dead) {
            if (ignoreFallBeforeMove || ignoresFall()) {
                resetFallTracking();
            } else {
                updateFall(world);
            }
        }
        if (!dead) {
            updateFire(world, deltaTime);
        }
        if (!dead) {
            updateEffects(world, deltaTime);
        }
        finishLivingUpdate(world);
    }

    /** Chạy điều khiển hoặc AI và cập nhật vật lý của thực thể còn sống. */
    protected abstract void updateLiving(World world, float deltaTime);

    /** Cho phép lớp con xử lý hồi sinh sau khi đã hoàn tất lượt vật lý. */
    protected void finishLivingUpdate(World world) {
    }

    /** Áp dụng giáp, miễn sát thương, phản hồi và đẩy lùi cho một đòn hợp lệ. */
    public boolean damage(World world, float amount, DamageSource source, Entity attacker,
                          float knockbackX, float knockbackZ) {
        if (dead || invulnerabilityTimer > TIMER_EPSILON || !(amount > 0f) || !Float.isFinite(amount)) {
            return false;
        }
        if (source.armorApplies() && armor > 0f) {
            float effectiveArmor = Math.min(20f, Math.max(armor * 0.2f, armor - amount * 0.5f));
            amount *= 1f - effectiveArmor / 25f;
        }
        health = Math.max(0f, health - amount);
        invulnerabilityTimer = HURT_SECONDS;
        hurtFlashTimer = HURT_SECONDS;
        applyKnockback(knockbackX, knockbackZ);
        if (health <= 0f) {
            dead = true;
            deathTimer = DEATH_SECONDS;
            leaveMount();
            for (int seat = 0; seat < passengers.length; seat++) {
                if (passengers[seat] != null) {
                    passengers[seat].leaveMount();
                }
            }
        }
        return true;
    }

    /** Thêm xung lực ngang, có thể ghi đè nếu điều khiển đặt lại vận tốc mỗi frame. */
    protected void applyKnockback(float x, float z) {
        velocity.x += x;
        velocity.z += z;
    }

    /** Phục hồi HP của thực thể còn sống, giới hạn theo máu tối đa. */
    public void heal(float amount) {
        if (!dead && amount > 0f && Float.isFinite(amount)) {
            health = Math.min(maxHealth, health + amount);
        }
    }

    /** Thay hiệu ứng hiện tại khi hiệu ứng mới mạnh hơn hoặc kéo dài hơn. */
    public boolean applyEffect(StatusEffectType type, int amplifier, float durationSeconds) {
        if (dead || amplifier < 0 || !(durationSeconds > 0f) || !Float.isFinite(durationSeconds)
                || isImmuneTo(type)) {
            return false;
        }
        StatusEffectInstance effect = effects[type.ordinal()];
        boolean active = effect.duration() > 0f;
        if (active && amplifier <= effect.amplifier() && durationSeconds <= effect.duration()) {
            return false;
        }
        effect.set(amplifier, durationSeconds);
        if (type == StatusEffectType.POISON && !active) {
            poisonElapsed = 0f;
        }
        return true;
    }

    /** Trả trạng thái còn hiệu lực ở thời điểm truy vấn. */
    public boolean hasEffect(StatusEffectType type) {
        return effects[type.ordinal()].duration() > 0f;
    }

    /** Trả thời lượng còn lại cho HUD và AI. */
    public float getEffectDuration(StatusEffectType type) {
        return effects[type.ordinal()].duration();
    }

    /** Thực thể undead miễn nhiễm độc. */
    public boolean isImmuneTo(StatusEffectType type) {
        return undead && type == StatusEffectType.POISON;
    }

    /** Tính hệ số vận tốc ngang, giới hạn ở 0 khi hiệu ứng quá mạnh. */
    public float getMovementSpeedMultiplier() {
        StatusEffectInstance slow = effects[StatusEffectType.SLOWNESS.ordinal()];
        return slow.duration() > 0f ? Math.max(0f, 1f - 0.15f * (slow.amplifier() + 1f)) : 1f;
    }

    /** Trừ 4 HP sát thương cận chiến mỗi cấp Weakness; đòn ở mức 0 không gây sát thương. */
    public float getMeleeDamage(float baseDamage) {
        StatusEffectInstance weakness = effects[StatusEffectType.WEAKNESS.ordinal()];
        float penalty = weakness.duration() > 0f ? 4f * (weakness.amplifier() + 1f) : 0f;
        return Math.max(0f, baseDamage - penalty);
    }

    /** Giảm thời lượng hiệu ứng và xử lý các nhịp độc không gây chết. */
    private void updateEffects(World world, float deltaTime) {
        for (int i = 0; i < effects.length; i++) {
            StatusEffectInstance effect = effects[i];
            if (effect.duration() <= 0f) {
                continue;
            }
            float activeTime = effect.advance(deltaTime);
            if (i == StatusEffectType.POISON.ordinal()) {
                poisonElapsed += activeTime;
                while (poisonElapsed + TIMER_EPSILON >= POISON_INTERVAL) {
                    poisonElapsed = Math.max(0f, poisonElapsed - POISON_INTERVAL);
                    damage(world, Math.min(1f, health - 1f), DamageSource.POTION_MAGIC, null, 0f, 0f);
                }
            } else if (i == StatusEffectType.HUNGER.ordinal()) {
                onHungerEffect(effect.amplifier(), activeTime);
            }
        }
    }

    /** Cho phép thực thể có thanh đói xử lý thời gian bị Hunger. */
    protected void onHungerEffect(int amplifier, float activeTime) {
    }

    /** Bắt đầu hoặc nối dài thời gian cháy mà giữ nhịp sát thương hiện tại. */
    public void ignite(float durationSeconds) {
        if (!dead && durationSeconds > fireTimer && Float.isFinite(durationSeconds)) {
            fireTimer = durationSeconds;
        }
    }

    /** Trả số giây cháy còn lại. */
    public float getFireTimer() {
        return fireTimer;
    }

    /** Dập lửa khi chạm nước hoặc mưa, nếu không thì xử lý 1 HP mỗi giây cháy. */
    private void updateFire(World world, float deltaTime) {
        if (fireTimer <= 0f) {
            return;
        }
        if (touchesLiquid(world, Block.WATER) || world.isRainingAt(position.x, position.y + height, position.z)) {
            extinguish();
            return;
        }
        float activeTime = Math.min(fireTimer, deltaTime);
        fireTimer -= activeTime;
        fireElapsed += activeTime;
        while (!dead && fireElapsed + TIMER_EPSILON >= FIRE_INTERVAL) {
            fireElapsed = Math.max(0f, fireElapsed - FIRE_INTERVAL);
            damage(world, 1f, DamageSource.FIRE, null, 0f, 0f);
        }
        if (fireTimer <= 0f) {
            extinguish();
        }
    }

    /** Xóa cả thời gian cháy lẫn phần lẻ của nhịp sát thương. */
    private void extinguish() {
        fireTimer = 0f;
        fireElapsed = 0f;
    }

    /** Thử các ô chất lỏng giao với hộp thân thể, loại biên chỉ vừa chạm mặt ô. */
    protected final boolean touchesLiquid(World world, Block liquid) {
        int minX = (int) Math.floor(position.x - halfWidth + CONTACT_EPSILON);
        int maxX = (int) Math.floor(position.x + halfWidth - CONTACT_EPSILON);
        int minY = (int) Math.floor(position.y + CONTACT_EPSILON);
        int maxY = (int) Math.floor(position.y + height - CONTACT_EPSILON);
        int minZ = (int) Math.floor(position.z - halfWidth + CONTACT_EPSILON);
        int maxZ = (int) Math.floor(position.z + halfWidth - CONTACT_EPSILON);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlock(x, y, z) == liquid.getId()) {
                        if (world.getBlock(x, y + 1, z) != Block.AIR.getId() || position.y < y + 0.88f) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Theo dõi đỉnh quỹ đạo và chỉ tính sát thương khi tiếp đất ngoài nước. */
    protected final void updateFall(World world) {
        fallStartY = Math.max(fallStartY, position.y);
        fallDistance = fallStartY - position.y;
        if (fallDistance > 0f && touchesLiquid(world, Block.WATER)) {
            resetFallTracking();
        } else if (onGround) {
            damage(world, fallDistance - 3f - getFallReduction(), DamageSource.FALL, null, 0f, 0f);
            resetFallTracking();
        }
    }

    /** Cho phép bỏ theo dõi rơi khi đang cưỡi hoặc có khả năng bay. */
    protected boolean ignoresFall() {
        return mount != null;
    }

    /** Trả số block an toàn cộng thêm cho thực thể có khả năng giảm sát thương rơi. */
    protected float getFallReduction() {
        return 0f;
    }

    /** Bắt đầu lại quãng rơi tại tọa độ chân hiện tại. */
    protected final void resetFallTracking() {
        fallStartY = position.y;
        fallDistance = 0f;
    }

    /** Đặt tọa độ từ bên ngoài mà không tính dịch chuyển như một cú rơi. */
    @Override
    public void setPosition(float x, float y, float z) {
        super.setPosition(x, y, z);
        resetFallTracking();
    }

    /** Đặt lại sinh tồn khi tái sinh. */
    protected final void restoreLife() {
        health = maxHealth;
        invulnerabilityTimer = 0f;
        hurtFlashTimer = 0f;
        dead = false;
        deathTimer = 0f;
        extinguish();
        poisonElapsed = 0f;
        for (int i = 0; i < effects.length; i++) {
            effects[i].set(0, 0f);
        }
        resetFallTracking();
    }

    /** Trả HP hiện tại. */
    public float getHealth() {
        return health;
    }

    /** Trả HP tối đa. */
    public float getMaxHealth() {
        return maxHealth;
    }

    /** Trả lượng giáp tự nhiên. */
    public float getArmor() {
        return armor;
    }

    /** Trả tộc undead để AI và hiệu ứng nhận diện. */
    public boolean isUndead() {
        return undead;
    }

    /** Thời gian nháy khi bị thương còn lại, tính bằng giây. */
    public float getHurtFlashTimer() {
        return hurtFlashTimer;
    }

    /** Thời gian còn lại cho hoạt ảnh chết. */
    public float getDeathTimer() {
        return deathTimer;
    }

    /** Xác định thực thể còn có thể hành động và được chọn làm mục tiêu. */
    public boolean isAlive() {
        return !dead;
    }

    /** Xác định khi nào World có thể bỏ thực thể khỏi danh sách. */
    public boolean isReadyToRemove() {
        return dead && deathTimer <= TIMER_EPSILON;
    }

    /** Trả thú đang cưỡi, nếu có. */
    public final LivingEntity getMount() {
        return mount;
    }

    /** Trả hành khách của ghế được chỉ định. */
    public final LivingEntity getPassenger(int seat) {
        return passengers[seat];
    }

    /** Trả số ghế vật lý của thực thể. */
    public final int getSeatCount() {
        return passengers.length;
    }

    /** Đặt một người cưỡi vào ghế trống và tránh tạo chu trình cưỡi. */
    public final boolean tryMount(LivingEntity vehicle, int seat) {
        if (vehicle == null || mount != null || dead || vehicle.dead
                || seat < 0 || seat >= vehicle.passengers.length || vehicle.passengers[seat] != null) {
            return false;
        }
        for (LivingEntity current = vehicle; current != null; current = current.mount) {
            if (current == this) {
                return false;
            }
        }
        mount = vehicle;
        vehicle.passengers[seat] = this;
        resetFallTracking();
        return true;
    }

    /** Gỡ liên kết ghế hai chiều và bắt đầu theo dõi rơi tại vị trí mới. */
    public final void leaveMount() {
        if (mount == null) {
            return;
        }
        for (int seat = 0; seat < mount.passengers.length; seat++) {
            if (mount.passengers[seat] == this) {
                mount.passengers[seat] = null;
                break;
            }
        }
        mount = null;
        resetFallTracking();
    }

    /** Cho phép lớp con tiêu thụ thao tác chuột phải trước khi đặt block. */
    public boolean onPlayerInteract(World world, Player player) {
        return false;
    }
}
