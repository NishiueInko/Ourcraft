package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputEventQueue;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.VoxelGameApp;
import java.lang.reflect.Proxy;

/** Chạy kiểm tra hồi quy của các hệ thống voxel mà không khởi tạo OpenGL. */
public final class VoxelRegressionChecks {
    /** Chạy toàn bộ nhóm kiểm tra và ném lỗi khi hành vi sai. */
    public static void main(String[] args) {
        CombatAndLivingChecks.run();
        MobAssetAndModelChecks.run();
        MobSpawnerChecks.run();
        HostileMobChecks.run();
        PassiveAndNeutralMobChecks.run();
        MountAndCamelHuskChecks.run();
        MobLootChecks.run();
        checkBlockRegistry();
        checkBlockUpdates();
        checkGrassRegrowth();
        checkFallingBlocks();
        checkRaycast();
        checkPlayerSurfaces();
        checkTerrainSpeedShortcut();
        ChunkMeshRegressionChecks.run();
        TerrainGenerationChecks.run();
        EnvironmentChecks.run();
        System.out.println("Các kiểm tra hồi quy voxel đã qua.");
    }

    /** Kiểm tra cỏ lan qua biên chunk và tàn dưới nước qua vòng cập nhật thật. */
    private static void checkGrassRegrowth() {
        World world = World.empty();
        try {
            world.registerPlayer(new Player(15.5f, 11f, 8.5f));
            for (int x = 8; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    world.setBlockInternal(x, 10, z, Block.GRASS.getId(), 0);
                }
            }
            world.setBlockInternal(16, 10, 8, Block.DIRT.getId(), 0);
            world.setBlockInternal(15, 11, 8, Block.WATER.getId(), 0);
            for (int i = 0; i < 12000; i++) {
                world.updateEntities(0.05f);
            }
            require(world.getBlock(16, 10, 8) == Block.GRASS.getId(), "Cỏ phải lan qua biên chunk");
            require(world.getBlock(15, 10, 8) == Block.DIRT.getId(), "Cỏ dưới nước phải tàn");
        } finally {
            world.dispose();
        }
    }

    /** Kiểm tra phản hồi và chuyển động của Player qua điểm vào cập nhật thật. */
    private static void checkPlayerSurfaces() {
        Input previous = Gdx.input;
        boolean[] forward = {false};
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed")) {
                        return forward[0] && (int) args[0] == Input.Keys.W;
                    }
                    throw new AssertionError("Cuộc gọi input ngoài dự kiến: " + method.getName());
                });
        World world = World.empty();
        try {
            for (int x = 4; x <= 25; x++) {
                Block floor = x < 16 ? Block.ICE : Block.STONE;
                world.setBlockInternal(x, 10, 8, floor.getId(), 0);
            }
            Camera camera = new Camera();
            camera.rotate(90, 0);
            Player player = new Player(5.5f, 11f, 8.5f);
            player.attachControls(camera);
            tickPlayer(world, player, 2);
            forward[0] = true;
            tickPlayer(world, player, 22);
            forward[0] = false;
            float before = player.getPosition().x;
            tickPlayer(world, player, 10);
            require(player.getPosition().x > before + 0.2f, "Nhả phím trên băng phải còn trượt");

            player.setPosition(18.5f, 11f, 8.5f);
            tickPlayer(world, player, 2);
            forward[0] = true;
            world.setBlock(19, 11, 8, Block.SNOW_LAYER.getId(), 3);
            tickPlayer(world, player, 14);
            require(player.getPosition().x > 19.1f && player.getPosition().y >= 11.375f,
                    "Người chơi phải bước lên tuyết thấp");
            forward[0] = false;
            before = player.getPosition().x;
            tickPlayer(world, player, 2);
            require(player.getPosition().x == before, "Rời băng phải trở lại điều khiển thường");

            world.setBlockInternal(21, 10, 8, Block.SAND.getId(), 0);
            world.setBlockInternal(21, 11, 8, Block.CACTUS.getId(), 0);
            player.setPosition(20.4f, 11f, 8.5f);
            forward[0] = true;
            tickPlayer(world, player, 7);
            require(player.getCactusFlash() > 0 && player.getPosition().x < 20.77f,
                    "Va cạnh cactus phải chặn và làm nháy viền đỏ");
            forward[0] = false;
            before = player.getPosition().x;
            tickPlayer(world, player, 5);
            require(player.getPosition().x < before, "Chạm cạnh cactus phải tạo phản lực");
        } finally {
            Gdx.input = previous;
            world.dispose();
        }
    }

    /** Kiểm tra mỗi lần bấm F1 đổi đúng một mức tốc độ cho di chuyển ngang và dọc khi bay. */
    private static void checkTerrainSpeedShortcut() {
        Input previous = Gdx.input;
        boolean[] held = new boolean[256];
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> held[(int) args[0]]);
        World world = World.empty();
        try {
            Player player = new Player(100.5f, 30f, 100.5f);
            player.attachControls(new Camera());
            VoxelGameApp app = new VoxelGameApp();
            // Gắn người chơi vào bộ xử lý thật mà không mở cửa sổ hay nạp tài nguyên đồ họa.
            var playerField = VoxelGameApp.class.getDeclaredField("player");
            playerField.setAccessible(true);
            playerField.set(app, player);
            var pausedField = VoxelGameApp.class.getDeclaredField("isPaused");
            pausedField.setAccessible(true);

            InputEventQueue queue = new InputEventQueue();
            int[] multipliers = {3, 5, 10, 25, 50, 1};
            int[][] directions = {{Input.Keys.W}, {Input.Keys.A}, {Input.Keys.S}, {Input.Keys.D},
                    {Input.Keys.W, Input.Keys.D}};
            for (int index = 0; index < multipliers.length; index++) {
                // Lần bấm đầu diễn ra khi Pause. Các lần lẻ giữ F1 qua nhiều lần cập nhật.
                pausedField.setBoolean(app, index == 0);
                held[Input.Keys.F1] = index % 2 != 0;
                queue.keyDown(Input.Keys.F1, 1);
                if (!held[Input.Keys.F1]) {
                    queue.keyUp(Input.Keys.F1, 2);
                }
                queue.drain(app);
                pausedField.setBoolean(app, false);

                for (int[] direction : directions) {
                    for (int key : direction) {
                        held[key] = true;
                    }
                    tickPlayer(world, player, 2);
                    Vector3 velocity = player.getVelocity();
                    require(Math.abs(Math.hypot(velocity.x, velocity.z) - 5.5f * multipliers[index]) < 0.1f,
                            "F1 phải đặt tốc độ " + multipliers[index] + "x cho hướng " + Input.Keys.toString(direction[0]));
                    for (int key : direction) {
                        held[key] = false;
                    }
                }
                if (held[Input.Keys.F1]) {
                    held[Input.Keys.F1] = false;
                    queue.keyUp(Input.Keys.F1, 3);
                    queue.drain(app);
                }
            }

            // Ctrl+S chỉ dùng điều khiển chạy lùi thông thường.
            queue.keyDown(Input.Keys.CONTROL_LEFT, 4);
            queue.keyDown(Input.Keys.S, 5);
            held[Input.Keys.S] = held[Input.Keys.CONTROL_LEFT] = true;
            queue.drain(app);
            tickPlayer(world, player, 1);
            Vector3 velocity = player.getVelocity();
            require(player.isSprinting() && Math.abs(Math.hypot(velocity.x, velocity.z) - 8.25f) < 0.1f,
                    "Ctrl+S phải chạy lùi ở tốc độ Sprint của mức 1x");

            // Bật chế độ bay bằng hai lần nhấn Space, ở xa mặt đất để kiểm tra vận tốc.
            held[Input.Keys.S] = held[Input.Keys.CONTROL_LEFT] = false;
            queue.keyUp(Input.Keys.S, 6);
            queue.keyUp(Input.Keys.CONTROL_LEFT, 7);
            queue.drain(app);
            player.setPosition(100.5f, 100f, 100.5f);
            held[Input.Keys.SPACE] = true;
            tickPlayer(world, player, 1);
            held[Input.Keys.SPACE] = false;
            tickPlayer(world, player, 1);
            held[Input.Keys.SPACE] = true;
            tickPlayer(world, player, 1);
            require(player.isFlying(), "Nhấn đúp Space phải bật chế độ bay");

            for (int multiplier : multipliers) {
                queue.keyDown(Input.Keys.F1, 8);
                queue.keyUp(Input.Keys.F1, 9);
                queue.drain(app);
                held[Input.Keys.SPACE] = true;
                tickPlayer(world, player, 1);
                require(player.getVelocity().y == 7f * multiplier, "Bay lên phải áp dụng mức " + multiplier + "x");

                held[Input.Keys.SPACE] = false;
                held[Input.Keys.SHIFT_LEFT] = true;
                tickPlayer(world, player, 1);
                require(player.getVelocity().y == -4f * multiplier, "Hạ độ cao phải áp dụng mức " + multiplier + "x");

                // Cách lần thử sau hơn 0,30 giây để Space hoặc Shift không bị tính là nhấn đúp.
                held[Input.Keys.SHIFT_LEFT] = false;
                tickPlayer(world, player, 19);
                require(player.isFlying() && player.getVelocity().y == 0f, "Thả phím phải giữ độ cao");
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Không gắn được người chơi để kiểm tra phím tắt", e);
        } finally {
            Gdx.input = previous;
            world.dispose();
        }
    }

    /** Chạy người chơi ở bước tham chiếu 60 Hz để kiểm tra vật lý. */
    private static void tickPlayer(World world, Player player, int count) {
        for (int frame = 0; frame < count; frame++) {
            player.update(world, 1f / 60f);
        }
    }

    /** Kiểm tra các cập nhật xuyên biên chunk và trạng thái của ô tuyết. */
    private static void checkBlockUpdates() {
        World world = World.empty();
        try {
            world.setBlock(15, 14, 15, Block.STONE.getId());
            world.setBlock(15, 15, 15, Block.SAND.getId());
            require(world.tryPlaceBlock(15, 16, 15, Block.CACTUS, null), "Không đặt được cactus trên cát");
            require(world.tryPlaceBlock(15, 17, 15, Block.CACTUS, null), "Không đặt được cactus trên cactus");
            world.setBlock(16, 16, 15, Block.STONE.getId());
            require(world.getBlock(15, 16, 15) == 0 && world.getBlock(15, 17, 15) == 0,
                    "Cột cactus phải tự gãy qua biên chunk");

            world.setBlock(15, 14, 15, Block.DIRT.getId());
            world.setBlock(15, 15, 15, Block.AIR.getId());
            require(world.tryPlaceBlock(15, 15, 15, Block.TALL_GRASS, null), "Không đặt được cỏ cao");
            require(world.getBlockState(15, 16, 15) == 1, "Nửa trên cỏ cao thiếu metadata");
            world.breakBlockAndSpawnDrop(15, 16, 15);
            require(world.getBlock(15, 15, 15) == 0, "Phá nửa trên phải dọn nửa dưới");

            world.setBlock(16, 14, 15, Block.WATER.getId());
            world.setBlock(15, 15, 15, Block.SUGAR_CANE.getId());
            world.setBlock(15, 16, 15, Block.SUGAR_CANE.getId());
            world.setBlock(16, 14, 15, Block.AIR.getId());
            require(world.getBlock(15, 15, 15) == 0 && world.getBlock(15, 16, 15) == 0,
                    "Mía phải gãy khi mất nước cạnh ô nền");

            world.setBlock(15, 14, 15, Block.SPRUCE_LEAVES.getId());
            require(world.tryPlaceBlock(15, 15, 15, Block.SNOW_LAYER, null), "Lá vân sam phải đỡ tuyết");
            for (int state = 1; state <= 7; state++) {
                require(world.tryStackSnow(15, 15, 15, null), "Không xếp được lớp tuyết " + (state + 1));
                require(world.getBlockState(15, 15, 15) == state, "Sai trạng thái tuyết");
            }
            require(!world.tryStackSnow(15, 15, 15, null), "Tuyết phải dừng ở tám lớp");
            world.setBlock(17, 15, 15, Block.LAVA.getId());
            require(world.getBlock(15, 15, 15) == 0, "Tuyết cách dung nham hai ô phải tan");

            // Chunk cuối phải nhận cập nhật tới mép bản đồ và bỏ qua các cột dư.
            int edgeX = World.WORLD_SIZE_X - 1;
            int edgeZ = World.WORLD_SIZE_Z - 1;
            world.setBlock(edgeX, 14, edgeZ, Block.SPRUCE_LEAVES.getId());
            require(world.tryPlaceBlock(edgeX, 15, edgeZ, Block.SNOW_LAYER, null), "Không đặt được tuyết ở mép bản đồ");
            world.setBlock(edgeX - 2, 15, edgeZ, Block.LAVA.getId());
            require(world.getBlock(edgeX, 15, edgeZ) == 0, "Cập nhật block sai tọa độ ở mép bản đồ");
            require(!world.setBlockInternal(edgeX + 1, 14, edgeZ, Block.STONE.getId(), 0),
                    "Cột dư trong chunk cuối phải nằm ngoài bản đồ");

            world.setBlock(10, 10, 10, Block.STONE.getId());
            world.setBlock(10, 11, 10, Block.ICE.getId());
            world.breakBlockAndSpawnDrop(10, 11, 10);
            require(world.getBlock(10, 11, 10) == Block.WATER.getId(), "Băng có nền phải để lại nước");
            world.setBlock(10, 13, 10, Block.ICE.getId());
            world.breakBlockAndSpawnDrop(10, 13, 10);
            require(world.getBlock(10, 13, 10) == 0, "Băng lơ lửng phải biến mất");
        } finally {
            world.dispose();
        }
    }

    /** Cát và sỏi lần lượt thành thực thể rồi đáp lại đúng ô trên nền. */
    private static void checkFallingBlocks() {
        World world = World.empty();
        try {
            world.setBlock(5, 10, 5, Block.STONE.getId());
            world.setBlock(5, 14, 5, Block.STONE.getId());
            world.setBlock(5, 15, 5, Block.SAND.getId());
            world.setBlock(5, 16, 5, Block.GRAVEL.getId());
            world.setBlock(5, 14, 5, Block.AIR.getId());
            require(world.getBlock(5, 15, 5) == 0 && world.getBlock(5, 16, 5) == 0,
                    "Cát và sỏi phải rời ô nguồn");
            for (int frame = 0; frame < 150; frame++) {
                world.updateEntities(1.0f / 60.0f);
            }
            require(world.getBlock(5, 11, 5) == Block.SAND.getId(), "Cát phải đáp trên nền");
            require(world.getBlock(5, 12, 5) == Block.GRAVEL.getId(), "Sỏi phải đáp trên cát");
        } finally {
            world.dispose();
        }
    }

    /** Đảm bảo tia chọn xuyên qua phần rỗng và có thể trúng cây không rắn. */
    private static void checkRaycast() {
        World world = World.empty();
        try {
            world.setBlockInternal(3, 3, 3, Block.CACTUS.getId(), 0);
            world.setBlockInternal(4, 3, 3, Block.STONE.getId(), 0);
            Raycast.HitResult skip = Raycast.cast(world, new Vector3(2, 3.4f, 3.02f), Vector3.X, 5);
            require(skip.getBlock() == Block.STONE, "Tia phải xuyên khoảng trống cạnh cactus");
            world.setBlockInternal(3, 3, 3, Block.SHORT_GRASS.getId(), 0);
            Raycast.HitResult plant = Raycast.cast(world, new Vector3(2, 3.4f, 3.5f), Vector3.X, 5);
            require(plant.getBlock() == Block.SHORT_GRASS && plant.getPrevBlockX() == 2,
                    "Tia phải chọn được cỏ và trả đúng mặt trúng");
        } finally {
            world.dispose();
        }
    }

    /** Xác nhận các ID cũ và các ID dành cho block mới đều tra cứu được. */
    private static void checkBlockRegistry() {
        Block[] original = {Block.AIR, Block.DIRT, Block.GRASS, Block.STONE,
                Block.BEDROCK, Block.OAK_LOG, Block.OAK_LEAVES, Block.WATER, Block.LAVA};
        for (int id = 0; id < original.length; id++) {
            require(Block.fromId((byte) id) == original[id], "ID cũ bị thay đổi: " + id);
        }
        for (int id = 9; id <= 25; id++) {
            require(Block.fromId((byte) id) != Block.AIR, "Thiếu block mới ở ID " + id);
        }
        require(Block.fromId((byte) 255) == Block.AIR, "ID chưa đăng ký phải trả về Air");
    }

    /** Báo điều kiện hồi quy cụ thể để Gradle kết thúc với lỗi. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
