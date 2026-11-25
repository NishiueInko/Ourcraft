package com.voxelgame.engine;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.Camel;

/** Kiểm tra lạc đà với địa hình và giao diện tương tác cũng dùng trong trò chơi. */
final class CamelMountChecks {
    /** Chạy các ca hình thể, tương tác, điều khiển và nổi dưới nước. */
    static void run() {
        checkDimensionsAndFeeding();
        checkRestAndClearance();
        checkStepAndDash();
        checkMountedWadingAndDismount();
    }

    /** Đo hộp thấp khi nghỉ và kiểm tra mái thấp ngăn lạc đà đứng dậy. */
    private static void checkRestAndClearance() {
        World world = arena(Block.SAND);
        try {
            Camel camel = new Camel(100.5f, 20f, 100.5f);
            for (int i = 0; i < 720 && camel.getHeight() > 1f; i++) {
                camel.update(world, 0.05f);
            }
            require(Math.abs(camel.getHeight() - 0.945f) < 0.0001f
                    && camel.mobVisualState().pose() == MobVisualState.Pose.SIT,
                    "Ngồi nghỉ trên cát phải hạ cả hộp va chạm lẫn tư thế");
            int roofX = (int) Math.floor(camel.x());
            int roofZ = (int) Math.floor(camel.z());
            world.setBlock(roofX, 21, roofZ, Block.STONE.getId());
            Player player = new Player(camel.x() + 2f, 20f, camel.z());
            camel.onPlayerInteract(world, player);
            require(camel.getHeight() < 1f && player.getMount() == null,
                    "Không được đứng vào khối rắn hay nhận hành khách khi đang kẹt dưới mái");
            world.setBlock(roofX, 21, roofZ, Block.AIR.getId());
            require(camel.onPlayerInteract(world, player) && player.getMount() == camel && camel.getHeight() > 2.37f,
                    "Lạc đà phải đứng và nhận người cưỡi ngay khi đã thông chỗ");
        } finally {
            world.dispose();
        }
    }

    /** Điều khiển qua API nạp thật, kiểm tra bậc 1,5 block, vòng cung và đúng thời gian hồi. */
    private static void checkStepAndDash() {
        World world = arena(Block.STONE);
        try {
            for (int x = 104; x <= 129; x++) {
                for (int z = 97; z <= 103; z++) {
                    world.setBlock(x, 20, z, Block.STONE.getId());
                    world.setBlock(x, 21, z, Block.SNOW_LAYER.getId(), 4);
                }
            }
            Camel camel = new Camel(100.5f, 20f, 100.5f);
            Player player = new Player(102.5f, 20f, 100.5f);
            require(camel.onPlayerInteract(world, player) && player.getMount() == camel,
                    "Lạc đà hiền cho phép cưỡi ngay");
            Player passenger = new Player(99.5f, 20f, 101.5f);
            require(camel.onPlayerInteract(world, passenger) && passenger.getMountedSeat() == 1
                    && player.getMountedSeat() == 0 && camel.copySeatPosition(0, new Vector3())
                    .dst2(camel.copySeatPosition(1, new Vector3())) > 0.9f,
                    "Hai hành khách phải ở hai điểm ghế riêng và ghế trước được giữ quyền lái");
            steer(world, camel, player, 1, false, false, 100);
            require(camel.x() > 105f && Math.abs(camel.y() - 21.5f) < 0.02f,
                    "Lạc đà phải bước trực tiếp lên bậc một block rưỡi");
            steer(world, camel, player, 0, false, true, 60);
            require(camel.chargeProgress() >= 0.99f, "Phải nạp đầy thanh khi giữ Space");
            float startX = camel.x();
            float startY = camel.y();
            steer(world, camel, player, 0, false, false, 1);
            require(Math.abs(camel.dashCooldownRemaining() - 2.75f) < 0.05f,
                    "Nhả Space phải khởi động hồi chiêu 2,75 giây");
            float highest = camel.y();
            for (int i = 0; i < 40; i++) {
                steer(world, camel, player, 0, false, false, 1);
                highest = Math.max(highest, camel.y());
            }
            require(camel.x() - startX > 10f && camel.x() - startX < 12.8f
                    && highest - startY > 0.8f && highest - startY < 1.3f,
                    "Lướt đầy nạp phải đi gần mười hai block và nâng chân gần một block");
            steer(world, camel, player, 0, false, true, 45);
            require(camel.chargeProgress() == 0f, "Không được nạp lại trong hồi chiêu");
            steer(world, camel, player, 0, false, false, 90);
            require(camel.dashCooldownRemaining() == 0f, "Hồi chiêu phải tự hết sau đúng thời gian");
        } finally {
            world.dispose();
        }
    }

    /** Lạc đà lội hồ hai lớp, hất người khi ngập sâu rồi mới nổi. */
    private static void checkMountedWadingAndDismount() {
        World world = arena(Block.STONE);
        try {
            for (int x = 98; x <= 128; x++) {
                for (int z = 98; z <= 102; z++) {
                    for (int y = 20; y <= (x < 112 ? 21 : 22); y++) {
                        world.setBlock(x, y, z, Block.WATER.getId());
                    }
                }
            }
            Camel camel = new Camel(100.5f, 22f, 100.5f);
            Player player = new Player(97f, 22f, 100.5f);
            Camera camera = new Camera();
            player.attachControls(camera);
            require(camel.onPlayerInteract(world, player), "Cần ghế lái để kiểm tra lội nước");
            steer(world, camel, player, 1, false, false, 100);
            player.syncMountedPosition();
            require(player.getMount() == camel && camel.x() > 102f && camel.x() < 109f
                            && Math.abs(camel.y() - 20f) < 0.01f
                            && Math.abs(camera.copyPosition(new Vector3()).y - player.getPosition().y - 1.62f) < 0.01f,
                    "Lạc đà phải chạm đáy, đi chậm và giữ đồng bộ ghế với mắt");
            for (int tick = 0; tick < 320 && player.getMount() != null; tick++) {
                steer(world, camel, player, 1, false, false, 1);
            }
            require(player.getMount() == null && camel.getPassenger(0) == null,
                    "Nước sâu phải gỡ liên kết ở cả hai phía tại X=" + camel.x());
            float dismountY = camel.y();
            for (int tick = 0; tick < 12; tick++) {
                camel.update(world, 0.025f);
            }
            require(camel.y() > dismountY + 0.5f, "Lạc đà phải bắt đầu nổi khi trống ghế");
        } finally {
            world.dispose();
        }
    }

    /** Dựng mặt phẳng đủ rộng để kiểm tra cát, bước bậc và không gian lướt. */
    private static World arena(Block surface) {
        World world = World.empty();
        for (int x = 75; x <= 132; x++) {
            for (int z = 75; z <= 128; z++) {
                world.setBlock(x, 18, z, Block.STONE.getId());
                world.setBlock(x, 19, z, surface.getId());
            }
        }
        return world;
    }

    /** Cập nhật lệnh ghế trước và vật lý theo bước cố định 20 vị trí mỗi nửa giây. */
    private static void steer(World world, Camel camel, Player player, float x, boolean sprint, boolean space, int frames) {
        for (int i = 0; i < frames; i++) {
            camel.updateRiderInput(player, x, 0f, sprint, space, 0.025f);
            camel.update(world, 0.025f);
        }
    }

    /** Xương rồng hồi máu cho lạc đà bị thương và tiêu thụ đúng một vật phẩm. */
    private static void checkDimensionsAndFeeding() {
        World world = arena(Block.STONE);
        try {
            Camel camel = new Camel(100.5f, 20f, 100.5f);
            Player player = new Player(106.5f, 20f, 100.5f);
            world.registerPlayer(player);
            require(camel.getHealth() == 32f && camel.stepHeight() == 1.5f
                    && camel.maxSafeFall() == 7f && camel.getSeatCount() == 2
                    && Math.abs(camel.getHeight() - 2.38f) < 0.0001f
                    && Math.abs(camel.getHalfWidth() - 0.85f) < 0.0001f,
                    "Lạc đà đứng phải có đúng kích thước, tầm bước bậc và máu");
            camel.damage(world, 8f, DamageSource.MELEE, null, 0f, 0f);
            require(player.getHotbar().addItem(Block.CACTUS, 2), "Cần xương rồng trong Hotbar");
            player.getHotbar().selectSlot(5);
            require(camel.onPlayerInteract(world, player) && camel.getHealth() > 24f
                    && player.getHotbar().getSelectedCount() == 1 && player.getMount() == null,
                    "Tương tác khi cầm xương rồng phải cho ăn và hồi máu trước khi cưỡi");
            for (int i = 0; i < 40; i++) {
                camel.update(world, 0.025f);
            }
            require(camel.x() > 101.5f, "Lạc đà phải tiến về người đang cầm xương rồng");
        } finally {
            world.dispose();
        }
    }

    /** Báo mô tả cụ thể khi hành vi khác với mong đợi. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
