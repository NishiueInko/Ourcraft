package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.Player;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import java.lang.reflect.Proxy;

/** Chạy kiểm tra hồi quy của các hệ thống voxel mà không khởi tạo OpenGL. */
public final class VoxelRegressionChecks {
    /** Chạy toàn bộ nhóm kiểm tra và ném lỗi khi hành vi sai. */
    public static void main(String[] args) {
        checkBlockRegistry();
        checkBlockUpdates();
        checkFallingBlocks();
        checkRaycast();
        checkPlayerSurfaces();
        ChunkMeshRegressionChecks.run();
        TerrainGenerationChecks.run();
        EnvironmentChecks.run();
        System.out.println("Các kiểm tra hồi quy voxel đã qua.");
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
