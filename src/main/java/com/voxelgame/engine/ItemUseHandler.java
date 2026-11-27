package com.voxelgame.engine;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Entity;
import com.voxelgame.entity.FoodRegistry;
import com.voxelgame.entity.Hotbar;
import com.voxelgame.entity.Item;
import com.voxelgame.entity.ItemEntity;
import com.voxelgame.entity.LivingEntity;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.projectile.ArrowEntity;
import com.voxelgame.entity.projectile.ArrowType;
import com.voxelgame.entity.projectile.ThrownEggEntity;
import com.voxelgame.entity.projectile.ThrownEnderPearlEntity;

/**
 * Quản lý toàn bộ vòng đời sử dụng vật phẩm chủ động của người chơi (Track A):
 * - Kéo và bắn {@link Item#BOW} với 3 loại mũi tên ({@code ARROW}, {@code TIPPED_ARROW_SLOWNESS},
 *   {@code TIPPED_ARROW_WEAKNESS}), kèm hiệu ứng giảm tốc {@code 0.4x} và thu hẹp FOV tối đa {@code -8.0f}.
 * - Đòn đâm tầm xa (Jab {@code 4.2f}) và tụ lực lao tới (Charge) của {@link Item#IRON_SPEAR}.
 * - Giữ chuột phải {@code 1.6s} để ăn 19 thức ăn ({@link FoodRegistry}), uống 4 lọ thuốc Witch hoặc {@link Item#MILK_BUCKET}.
 * - Ném {@link Item#ENDER_PEARL}, {@link Item#EGG} và thổi {@link Item#GOAT_HORN} với hồi chiêu {@code 7.0s}.
 *
 * Tuân thủ zero-GC trong Game Loop bằng cách tái sử dụng toàn bộ vector và mảng cố định.
 */
public final class ItemUseHandler {

    /** Các trạng thái giữ chuột phải liên tục đang hoạt động. */
    public enum UseState {
        /** Không sử dụng vật phẩm nào. */
        NONE,
        /** Đang kéo dây cung. */
        DRAW_BOW,
        /** Đang hạ giáo sắt để lao tới (Charge). */
        CHARGE_SPEAR,
        /** Đang ăn một trong 19 thức ăn của {@link FoodRegistry}. */
        EAT_FOOD,
        /** Đang uống một trong 4 lọ thuốc Witch hoặc {@link Item#MILK_BUCKET}. */
        DRINK_POTION
    }

    /** Tầm với tương tác mob mặc định (khớp RAYCAST_REACH của VoxelGameApp). */
    private static final float INTERACT_REACH = 5.0f;
    /** Tầm đánh cận chiến tay không mặc định và hồi chiêu cơ bản. */
    private static final float DEFAULT_MELEE_REACH = 3.0f;
    private static final float DEFAULT_MELEE_DAMAGE = 1.0f;
    private static final float DEFAULT_MELEE_COOLDOWN = 0.6f;

    /** Thông số Cung (Bow) theo chuẩn Minecraft 1.21.11. */
    public static final float BOW_FULL_DRAW_SECONDS = 1.0f;
    public static final float BOW_MIN_DRAW_SECONDS = 0.15f;
    private static final float BOW_MIN_SPEED = 12.0f;
    private static final float BOW_MAX_SPEED = 42.0f;
    private static final float BOW_MIN_DAMAGE = 2.0f;
    private static final float BOW_MAX_DAMAGE = 9.0f;
    private static final float BOW_MAX_ZOOM_OFFSET = -8.0f;

    /** Thông số Giáo sắt (Iron Spear): Jab (chuột trái) và Charge (giữ chuột phải). */
    public static final float SPEAR_JAB_REACH = 4.2f;
    public static final float SPEAR_JAB_DAMAGE = 5.0f;
    public static final float SPEAR_JAB_COOLDOWN = 0.65f;
    public static final float SPEAR_CHARGE_REACH = 3.5f;
    public static final float SPEAR_CHARGE_MIN_SPEED = 4.0f;
    public static final float SPEAR_CHARGE_BASE_DAMAGE = 5.0f;
    public static final float SPEAR_CHARGE_SPEED_SCALE = 0.8f;
    public static final float SPEAR_CHARGE_MAX_BONUS = 8.0f;
    public static final float SPEAR_CHARGE_TARGET_COOLDOWN = 0.4f;
    private static final float SPEAR_CHARGE_KNOCKBACK = 3.2f;
    private static final float SPEAR_RAISE_SECONDS = 0.25f;

    /** Thời gian hoàn thành ăn/uống (1.6 giây), nhịp âm thanh và vận tốc ném đạn. */
    public static final float CONSUME_DURATION_SECONDS = 1.6f;
    private static final float CONSUME_SOUND_INTERVAL = 0.25f;
    private static final float THROW_SPEED = 22.0f;
    private static final float EYE_SPAWN_DROP = 0.1f;

    /** Số mục tiêu tối đa theo dõi hồi chiêu Spear Charge cùng lúc (mảng tĩnh zero-GC). */
    private static final int MAX_SPEAR_HIT_TRACKED = 8;

    /** Bộ đệm tia ngắm, vận tốc và kết quả raycast tái sử dụng qua mọi khung hình. */
    private final Vector3 tmpEye = new Vector3();
    private final Vector3 tmpDir = new Vector3();
    private final Vector3 tmpVel = new Vector3();
    private final Vector3 tmpCenter = new Vector3();
    private final Raycast.HitResult occluderHit = Raycast.HitResult.miss();
    private final Raycast.EntityHitResult entityHit = new Raycast.EntityHitResult();

    /** Bảng tĩnh theo dõi thời gian miễn nhiễm 0.4s cho từng mục tiêu vừa trúng Charge giáo sắt. */
    private final LivingEntity[] spearHitEntities = new LivingEntity[MAX_SPEAR_HIT_TRACKED];
    private final float[] spearHitCooldowns = new float[MAX_SPEAR_HIT_TRACKED];

    /** Trạng thái sử dụng vật phẩm hiện tại. */
    private UseState state = UseState.NONE;
    private Item activeItem = Item.NONE;
    private float useElapsed = 0.0f;
    private float consumeSoundTimer = 0.0f;
    private float goatHornCooldown = 0.0f;

    /** Tham chiếu gần nhất để dọn dẹp FOV và tốc độ người chơi khi đổi ô Hotbar. */
    private Player lastPlayer;
    private Camera lastCamera;

    /**
     * Xử lý sự kiện nhấn Chuột Phải theo đúng thứ tự ưu tiên:
     * <ol>
     *   <li>Tương tác mob trước mặt ({@link Entity} / {@link LivingEntity#onPlayerInteract(World, Player)}).</li>
     *   <li>Bắt đầu kéo {@link Item#BOW}, hạ {@link Item#IRON_SPEAR}, giữ ăn/uống, ném đạn hoặc thổi {@link Item#GOAT_HORN}.</li>
     *   <li>Trả về {@code false} nếu không thuộc nhóm vật phẩm sử dụng để {@code VoxelGameApp} đặt Block như cũ.</li>
     * </ol>
     *
     * @param world  thế giới hiện tại
     * @param player người chơi đang thao tác
     * @param camera camera hiện tại của người chơi
     * @return {@code true} nếu thao tác đã được xử lý, {@code false} để tiếp tục đặt Block
     */
    public boolean onSecondaryPress(World world, Player player, Camera camera) {
        this.lastPlayer = player;
        this.lastCamera = camera;
        if (world == null || player == null || !player.isAlive()) {
            return false;
        }
        if (state != UseState.NONE) {
            return true;
        }

        // Ưu tiên 1: Tương tác thực thể trước mặt (Wolf, Sheep, Cow, Goat, thú cưỡi, Flint & Steel...)
        copyAimRay(player, camera, tmpEye, tmpDir);
        Raycast.castOccluder(world, tmpEye, tmpDir, INTERACT_REACH, occluderHit);
        Raycast.castEntity(world, tmpEye, tmpDir, INTERACT_REACH, occluderHit.getDistance(), player, entityHit);
        if (entityHit.isHit() && entityHit.getEntity().onPlayerInteract(world, player)) {
            return true;
        }

        // Ưu tiên 2: Vật phẩm sử dụng ở ô đang chọn trên Hotbar
        Hotbar hotbar = player.getHotbar();
        if (hotbar == null || hotbar.getSelectedCount() <= 0) {
            return false;
        }
        Item selected = hotbar.getSelectedItem();

        if (selected == Item.BOW) {
            if (findFirstArrowSlot(hotbar) < 0) {
                return false;
            }
            beginContinuousUse(UseState.DRAW_BOW, selected, player, true);
            return true;
        }

        if (selected == Item.IRON_SPEAR) {
            beginContinuousUse(UseState.CHARGE_SPEAR, selected, player, false);
            playEffect(world, AudioManager.Effect.SPEAR_CHARGE, 0.55f);
            return true;
        }

        if (FoodRegistry.isFood(selected)) {
            if (player.getHunger() >= player.getMaxHunger()) {
                return false;
            }
            beginContinuousUse(UseState.EAT_FOOD, selected, player, true);
            return true;
        }

        if (isDrinkable(selected)) {
            beginContinuousUse(UseState.DRINK_POTION, selected, player, true);
            return true;
        }

        if (selected == Item.ENDER_PEARL) {
            hotbar.consumeSelectedItemStack();
            ThrownEnderPearlEntity pearl = new ThrownEnderPearlEntity(
                    world,
                    player,
                    tmpEye.x,
                    tmpEye.y - EYE_SPAWN_DROP,
                    tmpEye.z,
                    tmpDir.x * THROW_SPEED,
                    tmpDir.y * THROW_SPEED,
                    tmpDir.z * THROW_SPEED
            );
            world.addEntity(pearl);
            playEffect(world, AudioManager.Effect.THROW, 0.50f);
            return true;
        }

        if (selected == Item.EGG) {
            hotbar.consumeSelectedItemStack();
            ThrownEggEntity egg = new ThrownEggEntity(
                    world,
                    player,
                    tmpEye.x,
                    tmpEye.y - EYE_SPAWN_DROP,
                    tmpEye.z,
                    tmpDir.x * THROW_SPEED,
                    tmpDir.y * THROW_SPEED,
                    tmpDir.z * THROW_SPEED
            );
            world.addEntity(egg);
            playEffect(world, AudioManager.Effect.THROW, 0.50f);
            return true;
        }

        if (selected == Item.GOAT_HORN) {
            if (goatHornCooldown <= 0.0f) {
                goatHornCooldown = Player.GOAT_HORN_COOLDOWN;
                playEffect(world, AudioManager.Effect.GOAT_HORN, 1.0f);
            }
            return true;
        }

        // Ưu tiên 3: Trả false để VoxelGameApp đặt Block hoặc thực thi nhánh mặc định
        return false;
    }

    /**
     * Xử lý sự kiện nhả Chuột Phải:
     * - Với {@link UseState#DRAW_BOW}: bắn {@link ArrowEntity} nếu kéo {@code >= 0.15s} và còn mũi tên.
     * - Với các trạng thái giữ khác: hủy sạch sẽ trạng thái đang thực hiện.
     *
     * @param world  thế giới hiện tại
     * @param player người chơi đang thao tác
     * @param camera camera hiện tại của người chơi
     * @return {@code true} nếu đang trong một trạng thái sử dụng vật phẩm và đã xử lý nhả chuột
     */
    public boolean onSecondaryRelease(World world, Player player, Camera camera) {
        this.lastPlayer = player;
        this.lastCamera = camera;
        if (state == UseState.NONE) {
            return false;
        }

        if (state == UseState.DRAW_BOW && world != null && player != null && player.isAlive()) {
            Hotbar hotbar = player.getHotbar();
            if (hotbar != null && hotbar.getSelectedItem() == Item.BOW
                    && hotbar.getSelectedCount() > 0 && useElapsed >= BOW_MIN_DRAW_SECONDS) {
                int arrowSlot = findFirstArrowSlot(hotbar);
                if (arrowSlot >= 0) {
                    Item arrowItem = hotbar.getSlotItem(arrowSlot);
                    ArrowType arrowType = toArrowType(arrowItem);
                    if (arrowType != null) {
                        float pull = getBowPullProgress();
                        float speed = BOW_MIN_SPEED + (BOW_MAX_SPEED - BOW_MIN_SPEED) * pull;
                        float damage = BOW_MIN_DAMAGE + (BOW_MAX_DAMAGE - BOW_MIN_DAMAGE) * pull;
                        hotbar.setSlot(arrowSlot, arrowItem, hotbar.getSlotCount(arrowSlot) - 1);

                        copyAimRay(player, camera, tmpEye, tmpDir);
                        ArrowEntity arrow = new ArrowEntity(
                                player,
                                tmpEye.x,
                                tmpEye.y - EYE_SPAWN_DROP,
                                tmpEye.z,
                                tmpDir.x * speed,
                                tmpDir.y * speed,
                                tmpDir.z * speed,
                                arrowType,
                                damage
                        );
                        world.addEntity(arrow);
                        playEffect(world, AudioManager.Effect.BOW_SHOOT, 0.60f);
                    }
                }
            }
        }

        resetUseState(player, camera);
        return true;
    }

    /**
     * Cập nhật tiến trình sử dụng vật phẩm liên tục mỗi khung hình, hồi chiêu sừng dê,
     * thu hẹp FOV kéo cung và kiểm tra va chạm Charge của giáo sắt.
     *
     * @param dt     khoảng thời gian trôi qua giữa hai khung hình (giây)
     * @param world  thế giới hiện tại
     * @param player người chơi hiện tại
     * @param camera camera hiện tại
     */
    public void update(float dt, World world, Player player, Camera camera) {
        this.lastPlayer = player;
        this.lastCamera = camera;
        if (!(dt > 0.0f) || !Float.isFinite(dt)) {
            return;
        }

        goatHornCooldown = Math.max(0.0f, goatHornCooldown - dt);
        advanceSpearHitCooldowns(dt);

        if (state == UseState.NONE) {
            if (player != null) {
                player.setItemUseSlowdown(false);
            }
            if (camera != null) {
                camera.updateBowZoom(0.0f, dt);
            }
            return;
        }

        Hotbar hotbar = player != null ? player.getHotbar() : null;
        if (world == null || player == null || !player.isAlive()
                || hotbar == null || hotbar.getSelectedCount() <= 0
                || hotbar.getSelectedItem() != activeItem) {
            resetUseState(player, camera);
            return;
        }

        useElapsed += dt;

        switch (state) {
            case DRAW_BOW -> {
                player.setItemUseSlowdown(true);
                if (camera != null) {
                    camera.updateBowZoom(getBowZoomOffset(), dt);
                }
            }
            case EAT_FOOD -> {
                if (player.getHunger() >= player.getMaxHunger()) {
                    resetUseState(player, camera);
                    return;
                }
                player.setItemUseSlowdown(true);
                consumeSoundTimer += dt;
                if (consumeSoundTimer >= CONSUME_SOUND_INTERVAL) {
                    consumeSoundTimer -= CONSUME_SOUND_INTERVAL;
                    playEffect(world, AudioManager.Effect.EAT, 0.45f);
                }
                if (useElapsed >= CONSUME_DURATION_SECONDS) {
                    if (player.consumeFood(activeItem)) {
                        hotbar.consumeSelectedItemStack();
                    }
                    playEffect(world, AudioManager.Effect.BURP, 0.45f);
                    resetUseState(player, camera);
                }
            }
            case DRINK_POTION -> {
                player.setItemUseSlowdown(true);
                consumeSoundTimer += dt;
                if (consumeSoundTimer >= CONSUME_SOUND_INTERVAL) {
                    consumeSoundTimer -= CONSUME_SOUND_INTERVAL;
                    playEffect(world, AudioManager.Effect.DRINK, 0.45f);
                }
                if (useElapsed >= CONSUME_DURATION_SECONDS) {
                    completeDrink(world, player, hotbar, activeItem);
                    playEffect(world, AudioManager.Effect.DRINK, 0.50f);
                    resetUseState(player, camera);
                }
            }
            case CHARGE_SPEAR -> {
                player.setItemUseSlowdown(false);
                trySpearChargeHit(world, player, camera);
            }
            case NONE -> {
            }
        }
    }

    /**
     * Xử lý đòn cận chiến Chuột Trái:
     * - Khi đang cầm {@link Item#IRON_SPEAR}: Jab tầm {@code 4.2f}, sát thương cơ sở {@code 5.0f} ({@code 7.5f} crit), hồi chiêu {@code 0.65s}.
     * - Với các vật phẩm khác hoặc tay không: giữ nguyên tầm {@code 3.0f}, sát thương {@code 1.0f}, hồi chiêu {@code 0.6s}.
     *
     * @param world  thế giới hiện tại
     * @param player người chơi tấn công
     * @param camera camera dùng xác định tia ngắm
     * @return {@code true} nếu tia cận chiến chạm một {@link LivingEntity} (không bắt đầu đào block)
     */
    public boolean onPrimaryMelee(World world, Player player, Camera camera) {
        this.lastPlayer = player;
        this.lastCamera = camera;
        if (world == null || player == null || !player.isAlive()) {
            return false;
        }

        Hotbar hotbar = player.getHotbar();
        boolean holdingSpear = hotbar != null
                && hotbar.getSelectedItem() == Item.IRON_SPEAR
                && hotbar.getSelectedCount() > 0;
        float reach = holdingSpear ? SPEAR_JAB_REACH : DEFAULT_MELEE_REACH;
        float baseDamage = holdingSpear ? SPEAR_JAB_DAMAGE : DEFAULT_MELEE_DAMAGE;
        float cooldown = holdingSpear ? SPEAR_JAB_COOLDOWN : DEFAULT_MELEE_COOLDOWN;

        copyAimRay(player, camera, tmpEye, tmpDir);
        Raycast.castOccluder(world, tmpEye, tmpDir, reach, occluderHit);
        Raycast.castEntity(world, tmpEye, tmpDir, reach, occluderHit.getDistance(), player, entityHit);
        if (!entityHit.isHit()) {
            return false;
        }

        player.attack(world, entityHit.getEntity(), tmpDir, baseDamage, cooldown);
        return true;
    }

    /**
     * Hủy sạch sẽ mọi trạng thái sử dụng vật phẩm khi người chơi đổi ô Hotbar giữa chừng,
     * đưa {@code bowZoomOffset} và tốc độ di chuyển về giá trị bình thường.
     */
    public void onHotbarSlotChanged() {
        resetUseState(lastPlayer, lastCamera);
    }

    /** Trả về tiến trình kéo dây cung trong đoạn {@code [0.0f, 1.0f]}. */
    public float getBowPullProgress() {
        if (state != UseState.DRAW_BOW) {
            return 0.0f;
        }
        return Math.min(1.0f, useElapsed / BOW_FULL_DRAW_SECONDS);
    }

    /** Trả về {@code true} nếu người chơi đang giữ chuột phải hạ giáo sắt (Charge). */
    public boolean isChargingSpear() {
        return state == UseState.CHARGE_SPEAR;
    }

    /** Trả về tiến trình sử dụng vật phẩm hiện tại trong đoạn {@code [0.0f, 1.0f]}. */
    public float getUseProgress() {
        return switch (state) {
            case NONE -> 0.0f;
            case DRAW_BOW -> getBowPullProgress();
            case EAT_FOOD, DRINK_POTION -> Math.min(1.0f, useElapsed / CONSUME_DURATION_SECONDS);
            case CHARGE_SPEAR -> Math.min(1.0f, useElapsed / SPEAR_RAISE_SECONDS);
        };
    }

    /** Trả về trạng thái giữ chuột phải hiện tại. */
    public UseState getUseState() {
        return state;
    }

    /**
     * Trả về độ lệch FOV thu hẹp khi kéo cung theo công thức {@code -8.0f * pullProgress}.
     */
    public float getBowZoomOffset() {
        float pull = getBowPullProgress();
        return pull > 0.0f ? BOW_MAX_ZOOM_OFFSET * pull : 0.0f;
    }

    /** Trả về thời gian hồi chiêu còn lại (giây) của sừng dê {@link Item#GOAT_HORN}. */
    public float getGoatHornCooldown() {
        return goatHornCooldown;
    }

    /** Khởi tạo trạng thái giữ chuột phải liên tục. */
    private void beginContinuousUse(UseState newState, Item item, Player player, boolean slowdown) {
        this.state = newState;
        this.activeItem = item;
        this.useElapsed = 0.0f;
        this.consumeSoundTimer = 0.0f;
        player.setItemUseSlowdown(slowdown);
    }

    /** Đưa mọi trạng thái sử dụng vật phẩm và góc thu hẹp FOV về mặc định. */
    private void resetUseState(Player player, Camera camera) {
        state = UseState.NONE;
        activeItem = Item.NONE;
        useElapsed = 0.0f;
        consumeSoundTimer = 0.0f;
        if (player != null) {
            player.setItemUseSlowdown(false);
        }
        if (camera != null) {
            camera.setBowZoomOffset(0.0f);
        }
    }

    /** Hoàn tất uống một trong 4 lọ thuốc Witch hoặc {@link Item#MILK_BUCKET}. */
    private void completeDrink(World world, Player player, Hotbar hotbar, Item item) {
        if (item == Item.MILK_BUCKET) {
            player.clearStatusEffects();
            int selectedSlot = hotbar.getSelectedSlot();
            hotbar.consumeSelectedItemStack();
            if (hotbar.getSlotCount(selectedSlot) == 0) {
                hotbar.setSlot(selectedSlot, Item.BUCKET, 1);
            } else if (!hotbar.addItem(Item.BUCKET, 1)) {
                player.copyPosition(tmpVel);
                world.spawnDroppedItem(new ItemEntity(tmpVel.x, tmpVel.y + 1.0f, tmpVel.z, Item.BUCKET));
            }
            return;
        }
        player.consumePotion(item);
    }

    /** Kiểm tra va chạm Charge của {@link Item#IRON_SPEAR} khi tốc độ ngang đạt {@code >= 4.0f}. */
    private void trySpearChargeHit(World world, Player player, Camera camera) {
        float speed = getHorizontalSpeed(player);
        if (speed < SPEAR_CHARGE_MIN_SPEED) {
            return;
        }

        copyAimRay(player, camera, tmpEye, tmpDir);
        float horizLen = (float) Math.sqrt(tmpDir.x * tmpDir.x + tmpDir.z * tmpDir.z);
        float fwdX = horizLen > 0.0001f ? tmpDir.x / horizLen : 1.0f;
        float fwdZ = horizLen > 0.0001f ? tmpDir.z / horizLen : 0.0f;

        LivingEntity target = findChargeTarget(world, player, fwdX, fwdZ);
        if (target == null || isOnSpearChargeCooldown(target)) {
            return;
        }

        float damage = SPEAR_CHARGE_BASE_DAMAGE + Math.min(SPEAR_CHARGE_MAX_BONUS, speed * SPEAR_CHARGE_SPEED_SCALE);
        float finalDamage = player.getMeleeDamage(damage);
        float kbX = fwdX * SPEAR_CHARGE_KNOCKBACK;
        float kbZ = fwdZ * SPEAR_CHARGE_KNOCKBACK;

        if (target.damage(world, finalDamage, DamageSource.MELEE, player, kbX, kbZ)) {
            recordSpearChargeHit(target);
            player.notifyTamedWolves(world, target);
        }
    }

    /** Tìm thực thể sống hợp lệ phía trước trong tầm {@code 3.5f} khi đang lao với giáo sắt. */
    private LivingEntity findChargeTarget(World world, Player player, float fwdX, float fwdZ) {
        LivingEntity mount = player.getMount();
        Raycast.castOccluder(world, tmpEye, tmpDir, SPEAR_CHARGE_REACH, occluderHit);
        float blockLimit = occluderHit.getDistance();
        Raycast.castEntity(world, tmpEye, tmpDir, SPEAR_CHARGE_REACH, blockLimit, player, entityHit);
        if (entityHit.isHit() && entityHit.getEntity() != mount) {
            return entityHit.getEntity();
        }

        // Dự phòng cho mob thấp hơn tầm mắt nhưng nằm ngay phía trước hướng lao của người chơi
        LivingEntity best = null;
        float bestDistSq = SPEAR_CHARGE_REACH * SPEAR_CHARGE_REACH;
        for (int i = 0, count = world.entityCount(); i < count; i++) {
            if (!(world.entityAt(i) instanceof LivingEntity living)
                    || !living.isAlive() || living == player || living == mount) {
                continue;
            }
            living.copyCenterPosition(tmpCenter);
            float dx = tmpCenter.x - tmpEye.x;
            float dy = tmpCenter.y - tmpEye.y;
            float dz = tmpCenter.z - tmpEye.z;
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > bestDistSq || distSq < 0.0001f) {
                continue;
            }
            float horizDist = (float) Math.sqrt(dx * dx + dz * dz);
            if (horizDist > 0.0001f && (dx * fwdX + dz * fwdZ) / horizDist < 0.5f) {
                continue;
            }
            float dist = (float) Math.sqrt(distSq);
            if (dist > blockLimit) {
                continue;
            }
            best = living;
            bestDistSq = distSq;
        }
        return best;
    }

    /** Lấy tốc độ di chuyển ngang (block/giây) của người chơi hoặc thú cưỡi hiện tại. */
    private float getHorizontalSpeed(Player player) {
        LivingEntity mover = player.getMount() != null ? player.getMount() : player;
        mover.copyVelocity(tmpVel);
        return (float) Math.sqrt(tmpVel.x * tmpVel.x + tmpVel.z * tmpVel.z);
    }

    /** Giảm bộ đếm miễn nhiễm 0.4s và dọn các ô đã hết hạn hoặc mục tiêu đã chết. */
    private void advanceSpearHitCooldowns(float dt) {
        for (int i = 0; i < MAX_SPEAR_HIT_TRACKED; i++) {
            if (spearHitEntities[i] != null) {
                spearHitCooldowns[i] -= dt;
                if (spearHitCooldowns[i] <= 0.0f || !spearHitEntities[i].isAlive()) {
                    spearHitEntities[i] = null;
                    spearHitCooldowns[i] = 0.0f;
                }
            }
        }
    }

    /** Kiểm tra mục tiêu đang nằm trong cửa sổ hồi chiêu 0.4s của Spear Charge hay không. */
    private boolean isOnSpearChargeCooldown(LivingEntity target) {
        for (int i = 0; i < MAX_SPEAR_HIT_TRACKED; i++) {
            if (spearHitEntities[i] == target && spearHitCooldowns[i] > 0.0f) {
                return true;
            }
        }
        return false;
    }

    /** Ghi nhận mục tiêu vừa trúng Spear Charge với thời gian miễn nhiễm lặp 0.4s. */
    private void recordSpearChargeHit(LivingEntity target) {
        int replaceIndex = 0;
        float lowestRemaining = Float.POSITIVE_INFINITY;
        for (int i = 0; i < MAX_SPEAR_HIT_TRACKED; i++) {
            if (spearHitEntities[i] == target || spearHitEntities[i] == null) {
                spearHitEntities[i] = target;
                spearHitCooldowns[i] = SPEAR_CHARGE_TARGET_COOLDOWN;
                return;
            }
            if (spearHitCooldowns[i] < lowestRemaining) {
                lowestRemaining = spearHitCooldowns[i];
                replaceIndex = i;
            }
        }
        spearHitEntities[replaceIndex] = target;
        spearHitCooldowns[replaceIndex] = SPEAR_CHARGE_TARGET_COOLDOWN;
    }

    /** Chép tia ngắm từ Camera hoặc từ góc ngắm gắn trên Player vào bộ đệm dùng lại. */
    private static void copyAimRay(Player player, Camera camera, Vector3 outEye, Vector3 outDir) {
        if (camera != null) {
            camera.copyPosition(outEye);
            camera.copyFront(outDir);
            return;
        }
        if (player.copyAim(outEye, outDir)) {
            return;
        }
        player.copyCenterPosition(outEye);
        outDir.set(1.0f, 0.0f, 0.0f);
    }

    /** Tìm ô Hotbar đầu tiên từ trái sang phải có chứa mũi tên hợp lệ. */
    private static int findFirstArrowSlot(Hotbar hotbar) {
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            if (hotbar.getSlotCount(i) > 0 && toArrowType(hotbar.getSlotItem(i)) != null) {
                return i;
            }
        }
        return -1;
    }

    /** Ánh xạ vật phẩm mũi tên sang {@link ArrowType} tương ứng, hoặc {@code null} nếu không phải mũi tên. */
    private static ArrowType toArrowType(Item item) {
        if (item == Item.ARROW) {
            return ArrowType.NORMAL;
        }
        if (item == Item.TIPPED_ARROW_SLOWNESS) {
            return ArrowType.SLOWNESS;
        }
        if (item == Item.TIPPED_ARROW_WEAKNESS) {
            return ArrowType.WEAKNESS;
        }
        return null;
    }

    /** Kiểm tra vật phẩm thuộc nhóm 4 lọ thuốc Witch hoặc xô sữa. */
    private static boolean isDrinkable(Item item) {
        return item == Item.POTION_HEALING
                || item == Item.POTION_FIRE_RESISTANCE
                || item == Item.POTION_WATER_BREATHING
                || item == Item.POTION_SWIFTNESS
                || item == Item.MILK_BUCKET;
    }

    /** Phát hiệu ứng âm thanh an toàn ngay cả khi đang chạy trong {@code World.empty()} không có OpenAL. */
    private static void playEffect(World world, AudioManager.Effect effect, float gain) {
        AudioManager audio = world != null ? world.getAudio() : null;
        if (audio != null) {
            audio.playEffect(effect, gain);
        }
    }
}
