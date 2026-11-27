package com.voxelgame.engine;

import com.voxelgame.client.render.Camera;
import com.voxelgame.client.render.ShaderProgram;
import com.voxelgame.world.effect.StatusEffectType;
import com.voxelgame.world.entity.DamageSource;
import com.voxelgame.world.entity.LivingEntity;
import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.gamemode.CreativeGameMode;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.weather.WeatherManager;
import com.voxelgame.world.level.World;
import com.voxelgame.world.physics.Raycast;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import java.lang.reflect.Proxy;

/** Kiểm tra nền tảng sinh tồn và chiến đấu trên CPU. */
final class CombatAndLivingChecks {
    /** Chạy các phép kiểm tra của mốc chiến đấu. */
    static void run() {
        World world = World.empty();
        try {
            checkDamageAndDeath(world);
            checkStatusEffects(world);
            checkEntityRay(world);
            checkFireAndRain(world);
            checkPlayerSurvival(world);
            checkVoidRespawnControls(world);
            checkAttack(world);
        } finally {
            world.dispose();
        }
    }

    /** Nhấn đúp Space qua một lần rơi khỏi bản đồ vẫn phải bật bay như trước. */
    private static void checkVoidRespawnControls(World world) {
        Input previous = Gdx.input;
        boolean[] space = {true};
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> method.getName().equals("isKeyPressed")
                        && space[0] && (int) args[0] == Input.Keys.SPACE);
        try {
            Player player = new Player(64.5f, -26f, 60.5f);
            // Chế độ bay bằng Space x2 thuộc CreativeGameMode
            player.setGameMode(CreativeGameMode.INSTANCE);
            player.attachControls(new Camera());
            player.update(world, 0.05f);
            near(player.getPosition().y, 20f, "Rơi dưới Y = -25 phải về mặt đỡ an toàn");
            near(player.getHealth(), 20f, "Dịch chuyển khỏi đáy bản đồ không tính sát thương rơi");
            space[0] = false;
            player.update(world, 0.05f);
            space[0] = true;
            player.update(world, 0.05f);
            require(player.isFlying(), "Hồi sinh từ đáy bản đồ phải giữ chuỗi nhấn đúp Space");
        } finally {
            Gdx.input = previous;
        }
    }

    /** Kiểm tra giáp, miễn sát thương, đẩy lùi, ghế và dọn thực thể sau hoạt ảnh. */
    private static void checkDamageAndDeath(World world) {
        Target target = new Target(30.5f, 15f, 30.5f, 10f, false, 1);
        Target rider = new Target(30.5f, 16f, 30.5f, 0f, false, 1);
        world.addLivingEntity(target);
        world.addLivingEntity(target);
        require(world.entityCount() == 1, "Đăng ký lại không được tạo bản sao");
        require(rider.tryMount(target, 0) && target.getPassenger(0) == rider
                && !target.tryMount(rider, 0), "Liên kết ghế phải hai chiều và không tạo chu trình");
        require(target.damage(world, 5f, DamageSource.MELEE, null, 2f, -1f), "Đòn đầu phải trúng");
        near(target.getHealth(), 16.5f, "Giáp phải giảm sát thương đòn vật lý");
        near(target.getHurtFlashTimer(), 0.5f, "Nháy bị thương phải dài nửa giây");
        require(target.getVelocity().epsilonEquals(2f, 0f, -1f, 0.0001f), "Đòn trúng phải đẩy lùi");
        world.updateEntities(0.49f);
        require(!target.damage(world, 30f, DamageSource.FALL, null, 0f, 0f),
                "Đòn trong cửa sổ bảo vệ phải bị chặn");
        world.updateEntities(0.01f);
        require(target.damage(world, 30f, DamageSource.FALL, null, 0f, 0f) && !target.isAlive(),
                "Sát thương rơi sau cửa sổ phải hạ mục tiêu và bỏ qua giáp");
        require(rider.getMount() == null && target.getPassenger(0) == null, "Thú chết phải giải phóng ghế");
        world.updateEntities(0.99f);
        require(!target.isReadyToRemove() && world.entityCount() == 1, "World phải giữ thực thể đủ thời gian chết");
        world.updateEntities(0.01f);
        require(target.isReadyToRemove() && world.entityCount() == 0, "World phải dọn thực thể đã chết");
    }

    /** Kiểm tra hệ số hiệu ứng, thay mới, thời gian hiệu lực và độc không gây chết. */
    private static void checkStatusEffects(World world) {
        Target target = new Target(32.5f, 15f, 30.5f, 0f, false, 0);
        target.applyEffect(StatusEffectType.SLOWNESS, 0, 2f);
        near(target.getMovementSpeedMultiplier(), 0.85f, "Slowness I giảm 15% tốc độ ngang");
        require(!target.applyEffect(StatusEffectType.SLOWNESS, 0, 1f), "Hiệu ứng ngắn hơn không ghi đè cùng cấp");
        require(target.applyEffect(StatusEffectType.SLOWNESS, 1, 1f), "Cấp mạnh hơn phải ghi đè");
        near(target.getMovementSpeedMultiplier(), 0.7f, "Slowness II giảm 30% tốc độ ngang");
        target.applyEffect(StatusEffectType.WEAKNESS, 0, 2f);
        near(target.getMeleeDamage(1f), 0f, "Tay không với Weakness I không gây sát thương");
        near(target.getMeleeDamage(6f), 2f, "Weakness I trừ 4 HP khỏi đòn");
        target.damage(world, 18.5f, DamageSource.MELEE, null, 0f, 0f);
        target.applyEffect(StatusEffectType.POISON, 0, 4f);
        target.update(world, 1f);
        require(!target.hasEffect(StatusEffectType.SLOWNESS), "Slowness hết hạn phải ngừng tác động");
        near(target.getHealth(), 1.5f, "Độc chưa được gây sát thương trước 1,25 giây");
        target.update(world, 0.25f);
        near(target.getHealth(), 1f, "Độc phải dừng ở đúng 1 HP");
        target.update(world, 2.75f);
        require(target.isAlive() && !target.hasEffect(StatusEffectType.POISON), "Độc hết hạn và không làm chết");
        Target undead = new Target(33.5f, 15f, 30.5f, 0f, true, 0);
        require(undead.isImmuneTo(StatusEffectType.POISON)
                && !undead.applyEffect(StatusEffectType.POISON, 1, 4f), "Undead phải miễn nhiễm độc");
    }

    /** Kiểm tra mục tiêu gần nhất, bỏ qua người bắn và chặn tia bằng hộp địa hình. */
    private static void checkEntityRay(World world) {
        Vector3 origin = new Vector3(50.5f, 15.5f, 50.5f);
        Vector3 direction = new Vector3(3f, 0f, 0f);
        Target shooter = new Target(50.5f, 15f, 50.5f, 0f, false, 0);
        Target far = new Target(53.5f, 15f, 50.5f, 0f, false, 0);
        Target near = new Target(52.5f, 15f, 50.5f, 0f, false, 0);
        world.addLivingEntity(shooter);
        world.addLivingEntity(far);
        world.addLivingEntity(near);
        Raycast.EntityHitResult result = new Raycast.EntityHitResult();
        Raycast.HitResult wall = Raycast.HitResult.miss();
        Raycast.castEntity(world, origin, direction, 3f, Float.POSITIVE_INFINITY, shooter, result);
        require(result.getEntity() == near, "Tia phải chọn thực thể gần nhất và bỏ qua chính người bắn");
        near(result.getDistance(), 1.7f, "Khoảng cách đo tới mặt AABB theo block");
        world.setBlockInternal(51, 15, 50, Block.STONE.getId(), 0);
        Raycast.castOccluder(world, origin, direction, 3f, wall);
        near(wall.getDistance(), 0.5f, "Vật cản phải trả khoảng cách đến mặt gần");
        Raycast.castEntity(world, origin, direction, 3f, wall.getDistance(), shooter, result);
        require(!result.isHit() && Float.isInfinite(result.getDistance()), "Không được đánh xuyên tường");
        world.setBlockInternal(51, 15, 50, Block.SHORT_GRASS.getId(), 0);
        Raycast.castOccluder(world, origin, direction, 3f, wall);
        require(!wall.isHit(), "Cỏ chữ X không được chặn đòn đánh");
        near.damage(world, 20f, DamageSource.MELEE, shooter, 0f, 0f);
        Raycast.castEntity(world, origin, direction, 3f, wall.getDistance(), shooter, result);
        require(result.getEntity() == far, "Tia phải bỏ qua thực thể chết");
    }

    /** Lửa mất một HP mỗi giây, tắt trong nước và chỉ tắt dưới mưa khi lộ thiên. */
    private static void checkFireAndRain(World world) {
        Target target = new Target(1500.5f, 12f, 60.5f, 8f, false, 0);
        target.ignite(4f);
        target.update(world, 1f);
        near(target.getHealth(), 19f, "Sát thương lửa không giảm bởi giáp");
        world.setBlockInternal(1500, 12, 60, Block.WATER.getId(), 0);
        target.update(world, 1f);
        near(target.getFireTimer(), 0f, "Nước phải dập lửa");
        near(target.getHealth(), 19f, "Dập lửa trước khi gây thêm sát thương");
        world.setBlockInternal(1500, 12, 60, Block.AIR.getId(), 0);

        WeatherManager weather = new WeatherManager(1L);
        world.setWeather(weather);
        weather.toggle();
        weather.update(1f);
        world.setBlockInternal(1500, 15, 60, Block.STONE.getId(), 0);
        target.ignite(4f);
        target.update(world, 1f);
        near(target.getHealth(), 18f, "Mái che phải ngăn mưa dập lửa");
        world.setBlockInternal(1500, 15, 60, Block.AIR.getId(), 0);
        target.update(world, 1f);
        near(target.getFireTimer(), 0f, "Mưa ngoài trời phải dập lửa");
        world.setWeather(null);
    }

    /** Kiểm tra vật lý thật của Player, hồi HP, giảm đói và hồi sinh an toàn. */
    private static void checkPlayerSurvival(World world) {
        world.setBlockInternal(60, 10, 60, Block.STONE.getId(), 0);
        world.setBlockInternal(61, 10, 60, Block.STONE.getId(), 0);
        world.setBlockInternal(World.RESPAWN_X, 19, World.RESPAWN_Z, Block.STONE.getId(), 0);
        Player player = new Player(60.5f, 19f, 60.5f);
        player.attachControls(new Camera());
        tick(player, world, 60);
        near(player.getHealth(), 15f, "Rơi 8 block phải mất 5 HP khi đáp đất");

        world.setBlockInternal(61, 11, 60, Block.WATER.getId(), 0);
        world.setBlockInternal(61, 12, 60, Block.WATER.getId(), 0);
        Player swimmer = new Player(61.5f, 19f, 60.5f);
        swimmer.attachControls(new Camera());
        tick(swimmer, world, 120);
        near(swimmer.getHealth(), 20f, "Đáp trong nước không mất HP do rơi");

        world.setBlockInternal(60, 10, 60, Block.SAND.getId(), 0);
        world.setBlockInternal(60, 11, 60, Block.CACTUS.getId(), 0);
        Player cactus = new Player(60.5f, 13f, 60.5f);
        cactus.attachControls(new Camera());
        tick(cactus, world, 20);
        near(cactus.getHealth(), 19f, "Chạm gai mất đúng 1 HP trong cửa sổ 0,5 giây");
        require(cactus.getCactusFlash() > 0f, "Chạm gai vẫn phải hiện viền đỏ");
        Player cactusLanding = new Player(60.5f, 20f, 60.5f);
        cactusLanding.attachControls(new Camera());
        tick(cactusLanding, world, 50);
        near(cactusLanding.getHealth(), 15f, "Rơi xa xuống gai phải tính sát thương rơi trước khi bật nảy");

        world.setBlockInternal(62, 12, 60, Block.LAVA.getId(), 0);
        Player lava = new Player(62.5f, 12f, 60.5f);
        lava.update(world, 0.05f);
        near(lava.getHealth(), 16f, "Người chơi chạm dung nham mất 4 HP");
        require(lava.getFireTimer() > 0f, "Dung nham phải làm cháy");
        lava.update(world, 0.45f);
        near(lava.getHealth(), 16f, "Lava vẫn tuân thủ miễn sát thương");
        lava.update(world, 0.05f);
        near(lava.getHealth(), 12f, "Lava tiếp tục gây sát thương sau nửa giây");

        Player hungry = new Player(63.5f, 13f, 60.5f);
        hungry.damage(world, 3f, DamageSource.MELEE, null, 0f, 0f);
        hungry.update(world, 4f);
        near(hungry.getHealth(), 18f, "Đủ độ đói phải hồi một HP sau bốn giây");
        near(hungry.getHunger(), 19f, "Hồi máu phải tiêu hao một điểm đói");
        hungry.applyEffect(StatusEffectType.HUNGER, 1, 2f);
        hungry.update(world, 2f);
        near(hungry.getHunger(), 17f, "Hunger II phải tiêu hao một điểm đói mỗi giây");
        hungry.ignite(3f);
        hungry.applyEffect(StatusEffectType.SLOWNESS, 0, 2f);
        hungry.damage(world, 20f, DamageSource.FALL, null, 2f, 0f);
        hungry.update(world, 0.05f);
        require(hungry.getPosition().epsilonEquals(World.RESPAWN_X, 20f, World.RESPAWN_Z, 0.001f),
                "Hết HP phải hồi sinh tại mặt đỡ an toàn");
        require(hungry.isAlive() && !hungry.hasEffect(StatusEffectType.SLOWNESS)
                && hungry.getFireTimer() == 0f && !hungry.isFlying() && hungry.getVelocity().isZero(),
                "Hồi sinh phải dọn trạng thái chiến đấu và chuyển động");
        near(hungry.getHealth(), 20f, "Hồi sinh đầy HP");
        near(hungry.getHunger(), 20f, "Hồi sinh đầy độ đói");
    }

    /** Kiểm tra cú đánh thường, hồi chiêu, đòn giữa lúc rơi và tốc độ Slowness thực tế. */
    private static void checkAttack(World world) {
        Player attacker = new Player(70.5f, 15f, 70.5f);
        Target first = new Target(72.5f, 15f, 70.5f, 0f, false, 0);
        Target second = new Target(73.5f, 15f, 70.5f, 0f, false, 0);
        Vector3 direction = new Vector3(2f, 1f, 0f);
        attacker.attack(world, first, direction);
        attacker.attack(world, second, direction);
        near(first.getHealth(), 19f, "Đòn tay không gây 1 HP");
        near(second.getHealth(), 20f, "Hồi chiêu theo người đánh, kể cả khi đổi mục tiêu");
        attacker.attachControls(new Camera());
        attacker.update(world, 0.6f);
        attacker.attack(world, second, direction);
        near(second.getHealth(), 18.5f, "Đòn trong lúc rơi gây Critical Hit 1,5 HP");
        near(second.getVelocity().x, 2f, "Đòn đánh đẩy theo hướng nhìn chiếu ngang");

        Input previous = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> method.getName().equals("isKeyPressed") && (int) args[0] == Input.Keys.W);
        try {
            Camera camera = new Camera();
            camera.rotate(90f, 0f);
            Player moving = new Player(75.5f, 15f, 70.5f);
            moving.attachControls(camera);
            moving.applyEffect(StatusEffectType.SLOWNESS, 0, 2f);
            moving.update(world, 0.05f);
            near(moving.getVelocity().x, 5.5f * 0.85f, "Slowness phải tác động lên vận tốc điều khiển");
        } finally {
            Gdx.input = previous;
        }
    }

    /** Tiến vật lý theo bước thời gian của vòng lặp thật. */
    private static void tick(Player player, World world, int steps) {
        for (int i = 0; i < steps; i++) {
            player.update(world, 1f / 60f);
        }
    }

    /** So sánh điểm sinh tồn với sai số dấu phẩy động nhỏ. */
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.001f, message + " (" + actual + " thay vì " + expected + ")");
    }

    /** Báo lỗi kèm hành vi chưa đáp ứng. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Mục tiêu đứng yên cho kiểm tra quy tắc chiến đấu ngoài OpenGL. */
    private static final class Target extends LivingEntity {
        /** Đặt mục tiêu với AABB cố định và các tùy chọn giáp, tộc và ghế. */
        private Target(float x, float y, float z, float armor, boolean undead, int seats) {
            super(x, y, z, 0.3f, 1.8f, 20f, armor, undead, seats);
        }

        /** Giữ nguyên vị trí để cô lập luật chiến đấu khỏi AI. */
        @Override
        protected void updateLiving(World world, float deltaTime) {
        }

        /** Mục tiêu kiểm thử chạy trên CPU. */
        @Override
        public void render(ShaderProgram shader) {
        }
    }
}
