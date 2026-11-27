package com.voxelgame.world.item;

import com.voxelgame.client.audio.AudioManager;
import com.voxelgame.client.render.Camera;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.Entity;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.player.Hotbar;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.level.World;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.math.Vector3;

/**
 * Quản lý trạng thái phiên sử dụng vật phẩm chủ động của người chơi (State) và ủy quyền đa hình
 * sang hệ phân cấp {@link Item}:
 * - {@link BowItem}: kéo dây cung, giảm tốc {@code 0.4x} và thu hẹp FOV tối đa {@code -8.0f}.
 * - {@link SpearItem}: đòn đâm Jab ({@code 4.2f}) và tụ lực lao tới Charge.
 * - {@link ConsumableItem} ({@link FoodItem} & {@link PotionItem}): giữ chuột phải {@code 1.6s} để ăn/uống.
 * - {@link ThrowableItem} & {@link GoatHornItem}: sử dụng tức thời qua {@link Item#onInstantUse}.
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
    static final float DEFAULT_MELEE_REACH = 3.0f;
    static final float DEFAULT_MELEE_DAMAGE = 1.0f;
    static final float DEFAULT_MELEE_COOLDOWN = 0.6f;

    /** Thông số Cung (Bow) theo chuẩn Minecraft 1.21.11. */
    public static final float BOW_FULL_DRAW_SECONDS = 1.0f;
    public static final float BOW_MIN_DRAW_SECONDS = 0.15f;
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

    /** Thời gian hoàn thành ăn/uống (1.6 giây), nhịp âm thanh và độ hạ điểm xuất phát đạn đạo. */
    public static final float CONSUME_DURATION_SECONDS = 1.6f;
    private static final float CONSUME_SOUND_INTERVAL = 0.25f;
    static final float EYE_SPAWN_DROP = 0.1f;

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
     *   <li>Ủy quyền đa hình sang {@link Item#getUseAction()} hoặc {@link Item#onInstantUse}.</li>
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

        // Ưu tiên 1: Tương tác thực thể trước mặt (Flint & Steel Lượt 1 -> Interactable Lượt 2)
        copyAimRay(player, camera, tmpEye, tmpDir);
        Raycast.castOccluder(world, tmpEye, tmpDir, INTERACT_REACH, occluderHit);
        Raycast.castEntity(world, tmpEye, tmpDir, INTERACT_REACH, occluderHit.getDistance(), player, entityHit);
        if (entityHit.isHit() && entityHit.getEntity().onPlayerInteract(world, player)) {
            return true;
        }

        // Ưu tiên 2: Ủy quyền đa hình cho vật phẩm đang cầm trên Hotbar
        Hotbar hotbar = player.getHotbar();
        if (hotbar == null || hotbar.getSelectedCount() <= 0) {
            return false;
        }
        Item selected = hotbar.getSelectedItem();
        if (selected == null || selected.isNone()) {
            return false;
        }

        Item.UseAction action = selected.getUseAction();
        if (action != Item.UseAction.NONE) {
            if (!selected.canStartContinuousUse(world, player)) {
                return false;
            }
            UseState nextState = toUseState(action);
            beginContinuousUse(nextState, selected, player, selected.slowsPlayerWhileUsing());
            if (nextState == UseState.CHARGE_SPEAR) {
                playEffect(world, AudioManager.Effect.SPEAR_CHARGE, 0.55f);
            }
            return true;
        }

        // Ưu tiên 3: Hành vi sử dụng tức thời (ném đạn, thổi tù và) hoặc trả false để đặt Block
        return selected.onInstantUse(world, player, camera, this);
    }

    /**
     * Xử lý sự kiện nhả Chuột Phải: ủy quyền sang {@link Item#onReleaseUse} của vật phẩm đang giữ
     * rồi dọn sạch trạng thái phiên sử dụng.
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

        activeItem.onReleaseUse(world, player, camera, useElapsed, this);
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
                player.setItemUseSlowdown(activeItem.slowsPlayerWhileUsing());
                if (camera != null) {
                    camera.updateBowZoom(getBowZoomOffset(), dt);
                }
            }
            case EAT_FOOD, DRINK_POTION -> {
                if (!activeItem.canStartContinuousUse(world, player)) {
                    resetUseState(player, camera);
                    return;
                }
                player.setItemUseSlowdown(activeItem.slowsPlayerWhileUsing());
                consumeSoundTimer += dt;
                if (consumeSoundTimer >= CONSUME_SOUND_INTERVAL) {
                    consumeSoundTimer -= CONSUME_SOUND_INTERVAL;
                    if (activeItem instanceof ConsumableItem consumable) {
                        playEffect(world, consumable.getConsumeTickSound(), 0.45f);
                    }
                }
                if (useElapsed >= activeItem.getUseDurationSeconds()) {
                    activeItem.onConsumeComplete(world, player);
                    resetUseState(player, camera);
                }
            }
            case CHARGE_SPEAR -> {
                player.setItemUseSlowdown(activeItem.slowsPlayerWhileUsing());
                trySpearChargeHit(world, player, camera);
            }
            case NONE -> {
            }
        }
    }

    /**
     * Xử lý đòn cận chiến Chuột Trái bằng cách truy vấn đa hình tầm với, sát thương và hồi chiêu
     * từ {@link Item} đang cầm trên tay.
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
        Item selected = (hotbar != null && hotbar.getSelectedCount() > 0 && hotbar.getSelectedItem() != null)
                ? hotbar.getSelectedItem() : Item.NONE;
        float reach = selected.getMeleeReach();
        float baseDamage = selected.getMeleeDamage();
        float cooldown = selected.getMeleeCooldown();

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

    /** Trả về độ lệch FOV thu hẹp khi kéo cung theo công thức {@code -8.0f * pullProgress}. */
    public float getBowZoomOffset() {
        float pull = getBowPullProgress();
        return pull > 0.0f ? BOW_MAX_ZOOM_OFFSET * pull : 0.0f;
    }

    /** Trả về thời gian hồi chiêu còn lại (giây) của sừng dê {@link Item#GOAT_HORN}. */
    public float getGoatHornCooldown() {
        return goatHornCooldown;
    }

    /** Kích hoạt thổi sừng dê nếu đã hết hồi chiêu 7,0 giây (gọi từ {@link GoatHornItem}). */
    void triggerGoatHorn(World world) {
        if (goatHornCooldown <= 0.0f) {
            goatHornCooldown = Player.GOAT_HORN_COOLDOWN;
            playEffect(world, AudioManager.Effect.GOAT_HORN, 1.0f);
        }
    }

    /** Cập nhật bộ đệm tia ngắm (zero-GC) và trả về tọa độ mắt dùng cho {@link BowItem}/{@link ThrowableItem}. */
    Vector3 aimEye(Player player, Camera camera) {
        copyAimRay(player, camera, tmpEye, tmpDir);
        return tmpEye;
    }

    /** Trả về vector hướng ngắm đã được cập nhật bởi {@link #aimEye(Player, Camera)}. */
    Vector3 aimDirection() {
        return tmpDir;
    }

    /** Ánh xạ {@link Item.UseAction} sang {@link UseState} tương ứng. */
    private static UseState toUseState(Item.UseAction action) {
        return switch (action) {
            case BOW -> UseState.DRAW_BOW;
            case SPEAR -> UseState.CHARGE_SPEAR;
            case EAT -> UseState.EAT_FOOD;
            case DRINK -> UseState.DRINK_POTION;
            case NONE -> UseState.NONE;
        };
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

    /** Phát hiệu ứng âm thanh an toàn ngay cả khi đang chạy trong {@code World.empty()} không có OpenAL. */
    void playEffect(World world, AudioManager.Effect effect, float gain) {
        AudioManager audio = world != null ? world.getAudio() : null;
        if (audio != null) {
            audio.playEffect(effect, gain);
        }
    }
}
