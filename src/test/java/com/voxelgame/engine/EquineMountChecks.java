package com.voxelgame.engine;

import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.Block;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.mob.Donkey;
import com.voxelgame.entity.mob.Horse;
import com.voxelgame.entity.mob.MountableMob;

import java.util.Random;

/** Xác minh cơ chế cưỡi ngựa và lừa bằng vòng cập nhật vật lý thật. */
final class EquineMountChecks {
    /** Kiểm tra chỉ số, ghế, thuần hóa, bậc, nhảy và vị trí xuống thú. */
    static void run() {
        checkTraits();
        World world = World.empty();
        try {
            checkRiding(world);
            checkHeadroom(world);
        } finally {
            world.dispose();
        }
    }

    /** Mái lọt thân thú nhưng chặn kỵ sĩ phải ngăn lên yên, đi và bật nhảy. */
    private static void checkHeadroom(World world) {
        for (int x = 45; x <= 64; x++) {
            for (int z = 17; z <= 21; z++) {
                world.setBlockInternal(x, 15, z, Block.STONE.getId(), 0);
                if (x >= 54) {
                    world.setBlockInternal(x, 18, z, Block.STONE.getId(), 0);
                }
            }
        }
        TestMount mount = new TestMount(54.5f, 16f, 19.5f);
        Player rider = new Player(53.5f, 16f, 19.5f);
        require(!mount.onPlayerInteract(world, rider), "Mái quá thấp cho người chơi lên yên");
        mount.setPosition(52f, 16f, 19.5f);
        require(mount.onPlayerInteract(world, rider), "Ghế ngoài mái dùng được");
        for (int i = 0; i < 32; i++) {
            mount.updateRiderInput(rider, 1f, 0f, false, false, 0.05f);
            mount.update(world, 0.05f);
        }
        require(mount.x() > 53f && mount.x() + rider.getHalfWidth() <= 54.001f,
                "Quét ngang dừng ghế trước mái ngay cả khi thú còn lọt bên dưới");
        world.setBlockInternal(53, 20, 19, Block.STONE.getId(), 0);
        for (int tick = 0; tick < 20; tick++) {
            mount.updateRiderInput(rider, 0f, 0f, false, true, 0.05f);
            mount.update(world, 0.05f);
        }
        mount.updateRiderInput(rider, 0f, 0f, false, false, 0.05f);
        for (int tick = 0; tick < 20; tick++) {
            mount.update(world, 0.05f);
            require(mount.copySeatPosition(0, new Vector3()).y + rider.getHeight() <= 20.001f,
                    "Quét dọc dừng đầu người chơi tại trần khi nhảy");
        }
    }

    /** Thú đã thuần cố định chỉ số để kiểm tra riêng va chạm của ghế. */
    private static final class TestMount extends MountableMob {
        private TestMount(float x, float y, float z) {
            super(com.voxelgame.entity.mob.MobType.DONKEY, x, y, z, 1, 1f, 7.5f, 2.1f, true);
        }
    }

    /** Chọn một màu lông cho bầy và kiểm tra giới hạn chỉ số của cả hai loài. */
    private static void checkTraits() {
        MobAppearance coat = Horse.randomCoat(new Random(42));
        for (int i = 0; i < 128; i++) {
            Horse horse = new Horse(0f, 12f, 0f, coat);
            require(horse.mobVisualState().appearance() == coat && horse.mobVisualState().horseMarkings() != null,
                    "Ngựa trong đàn giữ màu lông chung và có kiểu đốm riêng");
            require(horse.getMaxHealth() >= 15f && horse.getMaxHealth() <= 30f
                            && horse.getHealth() == horse.getMaxHealth()
                            && horse.moveSpeed() >= 4.8f && horse.moveSpeed() <= 14.5f
                            && horse.jumpHeight() >= 1.25f && horse.jumpHeight() <= 5.25f,
                    "Chỉ số ngựa phải nằm trong khoảng của loài");
            Donkey donkey = new Donkey(0f, 12f, 0f);
            require(donkey.getMaxHealth() >= 15f && donkey.getMaxHealth() <= 30f
                            && donkey.moveSpeed() == 7.5f && donkey.jumpHeight() == 2.1f,
                    "Lừa có máu biến thiên và tốc độ, sức nhảy cố định");
        }
    }

    /** Thử cưỡi lừa hoang, kiểm tra hất, bước bậc và nhảy bằng thao tác Space. */
    private static void checkRiding(World world) {
        for (int x = 15; x <= 39; x++) {
            for (int z = 15; z <= 29; z++) {
                world.setBlockInternal(x, 15, z, Block.STONE.getId(), 0);
                if (x >= 26) {
                    world.setBlockInternal(x, 16, z, Block.STONE.getId(), 0);
                }
            }
        }
        MountableMob donkey = new Donkey(23.5f, 16f, 23.5f);
        Player rider = new Player(22.5f, 16f, 23.5f);
        donkey.setBaby(true);
        require(!donkey.onPlayerInteract(world, rider), "Không thể cưỡi lừa con");
        donkey.setBaby(false);
        require(!donkey.isTamed() && donkey.getSeatCount() == 1, "Lừa hoang bắt đầu với một ghế");
        int previousTemper = 0;
        for (int attempt = 0; attempt < 4 && !donkey.isTamed(); attempt++) {
            require(donkey.onPlayerInteract(world, rider) && rider.getMount() == donkey,
                    "Chuột phải phải đặt người chơi vào ghế lừa");
            Vector3 seat = donkey.copySeatPosition(0, new Vector3());
            require(seat.y > donkey.y() + 0.9f, "Ghế nằm trên lưng lừa");
            for (int tick = 0; tick < 110 && rider.getMount() == donkey && !donkey.isTamed(); tick++) {
                donkey.updateRiderInput(rider, 1f, 0f, false, true, 0.05f);
                donkey.update(world, 0.05f);
            }
            require(donkey.temper() > previousTemper && donkey.chargeProgress() == 0f,
                    "Cưỡi hoang tăng thiện cảm nhưng không nạp nhảy");
            previousTemper = donkey.temper();
        }
        require(donkey.isTamed() && donkey.isPersistent() && rider.getMount() == donkey,
                "Lừa đã quen giữ người cưỡi và được lưu giữ");
        for (int tick = 0; tick < 26; tick++) {
            donkey.updateRiderInput(rider, 1f, 0f, false, false, 0.05f);
            donkey.update(world, 0.05f);
        }
        require(donkey.x() > 27f && donkey.y() >= 16.99f, "Lừa phải bước thẳng lên nền cao một block");
        for (int tick = 0; tick < 20; tick++) {
            donkey.updateRiderInput(rider, 0f, 0f, false, true, 0.05f);
            donkey.update(world, 0.05f);
        }
        require(donkey.chargeProgress() > 0.95f && donkey.isOnGround(), "Giữ Space chỉ nạp lực trên mặt đất");
        float ground = donkey.y();
        donkey.updateRiderInput(rider, 0f, 0f, false, false, 0.05f);
        donkey.update(world, 0.05f);
        float peak = donkey.y();
        for (int tick = 0; tick < 22; tick++) {
            donkey.update(world, 0.05f);
            peak = Math.max(peak, donkey.y());
        }
        require(peak > ground + 1.7f && peak < ground + 2.4f, "Thả Space phải phóng lừa lên khoảng 2,1 block");
        require(donkey.tryDismount(world, rider) && rider.getMount() == null && rider.getPosition().y >= 15.99f,
                "Xuống thú chọn nền an toàn cạnh lừa");
        donkey.setPosition(50.5f, 16f, 50.5f);
        require(donkey.onPlayerInteract(world, rider) && !donkey.tryDismount(world, rider)
                        && rider.getMount() == donkey,
                "Gặp vực sâu quanh thú phải giữ người chơi trên ghế");
    }

    /** Ném lỗi có mô tả nếu trạng thái thực khác hành vi mong đợi. */
    private static void require(boolean passes, String message) {
        if (!passes) {
            throw new AssertionError(message);
        }
    }
}
