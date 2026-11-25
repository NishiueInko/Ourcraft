package com.voxelgame.engine;

import com.voxelgame.entity.Block;
import com.voxelgame.entity.DamageSource;
import com.voxelgame.entity.Player;
import com.voxelgame.entity.StatusEffectType;
import com.voxelgame.entity.mob.CamelHusk;
import com.voxelgame.entity.mob.Husk;
import com.voxelgame.entity.mob.MobAttitude;
import com.voxelgame.entity.mob.MobEntity;
import com.voxelgame.entity.mob.MobType;
import com.voxelgame.entity.mob.Parched;
import com.voxelgame.entity.projectile.ProjectileEntity;

/** Kiểm tra bộ kỵ sĩ sa mạc, tấn công bằng giáo và trao lại quyền điều khiển. */
final class CamelHuskBehaviorChecks {
    /** Nhịp CPU cố định theo tick gameplay. */
    private static final float TICK = 0.05f;

    /** Chạy các tình huống chiến đấu và tiếp quản thú cưỡi. */
    static void run() {
        checkMountedCombat();
        checkDriverHandoverAndCapture();
    }

    /** Giáo dùng tốc độ thật của thú cưỡi để gây thêm sát thương, và cung thủ bắn từ ghế sau. */
    private static void checkMountedCombat() {
        World world = desertArena();
        try {
            Player player = new Player(19.5f, 1f, 12.5f);
            world.registerPlayer(player);
            CamelHusk camel = createCamel();
            Husk front = new Husk(12.5f, 3.38f, 12.5f, true);
            Parched back = new Parched(12.5f, 3.38f, 12.5f);
            require(front.hasIronSpear() && front.tryMount(camel, 0) && back.tryMount(camel, 1)
                            && camel.activeDriver() == front,
                    "Bộ kỵ sĩ cần hai ghế độc lập và Husk cầm giáo trên ghế lái");
            require(!camel.burnsInSunlight() && !front.burnsInSunlight() && !back.burnsInSunlight(),
                    "Cả ba thành viên chịu được nắng");
            for (int tick = 0; tick < 120 && !player.hasEffect(StatusEffectType.HUNGER); tick++) {
                camel.update(world, TICK);
                front.update(world, TICK);
            }
            require(player.hasEffect(StatusEffectType.HUNGER) && player.getHealth() <= 14f,
                    "Husk trên ghế trước phải lao tới đâm giáo, gây ít nhất 6 HP và Hunger");

            // Cố định thú sau khi đổi người lái để kiểm tra riêng quỹ đạo của cung thủ.
            front.discard();
            camel.setPosition(12.5f, 1f, 12.5f);
            for (int tick = 0; tick < 100 && !player.hasEffect(StatusEffectType.WEAKNESS); tick++) {
                player.update(world, TICK);
                back.update(world, TICK);
                for (int index = 0, count = world.entityCount(); index < count; index++) {
                    if (world.entityAt(index) instanceof ProjectileEntity projectile) {
                        projectile.update(world, TICK);
                    }
                }
            }
            require(player.getEffectDuration(StatusEffectType.WEAKNESS) > 29f,
                    "Parched trên ghế sau phải bắn trúng tên Weakness I 30 giây");
        } finally {
            world.dispose();
        }
    }

    /** Giữ ghế sau và lùi tầm cung khi mất ghế lái, rồi cho phép thuần hóa khi hết tay lái địch. */
    private static void checkDriverHandoverAndCapture() {
        World world = desertArena();
        try {
            Player player = new Player(16.5f, 1f, 12.5f);
            world.registerPlayer(player);
            CamelHusk camel = createCamel();
            Husk front = new Husk(12.5f, 3.38f, 12.5f, true);
            Parched back = new Parched(12.5f, 3.38f, 12.5f);
            require(front.tryMount(camel, 0) && back.tryMount(camel, 1), "Không gắn được bộ kỵ sĩ");
            require(camel.attitude() == MobAttitude.HOSTILE && !camel.onPlayerInteract(world, player),
                    "Người chơi không được cưỡi khi có kỵ sĩ địch");
            require(front.damage(world, 100f, DamageSource.MELEE, player, 0f, 0f), "Husk phải bị hạ");
            require(camel.attitude() == MobAttitude.HOSTILE && camel.getPassenger(0) == null
                            && camel.getPassenger(1) == back && camel.activeDriver() == back
                            && !camel.isTamed() && !camel.onPlayerInteract(world, player),
                    "Parched còn sống phải giữ ghế sau và quyền điều khiển");
            for (int tick = 0; tick < 30; tick++) {
                camel.update(world, TICK);
            }
            require(camel.x() < 10.5f, "Lạc đà do Parched lái phải lùi ra khỏi đối thủ đứng gần");
            back.leaveMount();
            require(camel.attitude() == MobAttitude.PASSIVE, "Kỵ sĩ cuối tự rời ghế cũng phải giải phóng thú cưỡi");
            require(back.tryMount(camel, 1) && back.damage(world, 100f, DamageSource.MELEE, player, 0f, 0f),
                    "Parched phải rời ghế ngay khi bị hạ");
            require(camel.attitude() == MobAttitude.PASSIVE && camel.onPlayerInteract(world, player)
                            && player.getMount() == camel && camel.isTamed() && camel.isPersistent(),
                    "Hết kỵ sĩ địch phải lập tức cho người chơi thuần hóa và cưỡi");
            camel.update(world, TICK);
            require(camel.target() == null, "Thú cưỡi đã thuần hóa phải bỏ mục tiêu truy đuổi trước đó");
            for (int tick = 0; tick < 20; tick++) {
                camel.updateRiderInput(player, 1f, 0f, false, true, TICK);
                camel.update(world, TICK);
            }
            camel.updateRiderInput(player, 1f, 0f, false, false, TICK);
            camel.update(world, TICK);
            require(camel.isDashMount() && Math.abs(camel.dashCooldownRemaining() - 2.75f) < 0.001f,
                    "Camel Husk đã thuần hóa phải bắt đầu Dash với 2,75 giây hồi chiêu");
        } finally {
            world.dispose();
        }
    }

    /** Dựng Camel Husk thông qua danh mục để xác minh factory. */
    private static CamelHusk createCamel() {
        MobEntity camel = MobType.CAMEL_HUSK.create(12.5f, 1f, 12.5f);
        require(camel instanceof CamelHusk && camel.getSeatCount() == 2 && camel.isUndead(),
                "Camel Husk phải có factory, tộc xác sống và hai chỗ ngồi");
        return (CamelHusk) camel;
    }

    /** Lót mặt cát để mô phỏng đi, lùi và gây đòn ở ngoài trời. */
    private static World desertArena() {
        World world = World.empty();
        for (int x = 3; x <= 32; x++) {
            for (int z = 3; z <= 28; z++) {
                world.setBlockInternal(x, 0, z, Block.SAND.getId(), 0);
            }
        }
        return world;
    }

    /** Nêu đúng yêu cầu bị vi phạm nếu kiểm tra không đạt. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
