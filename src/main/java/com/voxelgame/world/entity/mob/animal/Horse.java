package com.voxelgame.world.entity.mob.animal;

import com.voxelgame.client.render.entity.MobAppearance;
import com.voxelgame.world.entity.mob.EquineMob;
import com.voxelgame.world.entity.mob.MobType;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Ngựa Plains có thể thuần hóa, với chỉ số và vân lông riêng của mỗi cá thể. */
public final class Horse extends EquineMob {
    /** Bảy sắc lông nền dùng khi chọn màu chung cho một đàn. */
    private static final MobAppearance[] COATS = {
            MobAppearance.HORSE_WHITE, MobAppearance.HORSE_CREAMY, MobAppearance.HORSE_CHESTNUT,
            MobAppearance.HORSE_BROWN, MobAppearance.HORSE_BLACK, MobAppearance.HORSE_GRAY,
            MobAppearance.HORSE_DARKBROWN
    };
    /** Mỗi cá thể chọn vân riêng, kể cả lựa chọn lông trơn. */
    private static final MobAppearance.HorseMarkings[] MARKINGS = MobAppearance.HorseMarkings.values();

    /** Tạo cá thể đơn với đầy đủ lựa chọn màu lông. */
    public Horse(float x, float y, float z) {
        this(x, y, z, randomCoat(ThreadLocalRandom.current()));
    }

    /** Tạo cá thể trưởng thành với sắc lông được chọn cho cả đàn. */
    public Horse(float x, float y, float z, MobAppearance coat) {
        this(x, y, z, coat, ThreadLocalRandom.current());
    }

    /** Lấy trung bình ba mẫu để đa số cá thể có tốc độ và sức nhảy gần trung tâm khoảng. */
    private Horse(float x, float y, float z, MobAppearance coat, RandomGenerator random) {
        super(MobType.HORSE, x, y, z, 4.8f + 9.7f * meanOfThree(random),
                1.25f + 4f * meanOfThree(random), randomHealth(random));
        mobVisualState().setAppearance(coat);
        mobVisualState().setHorseMarkings(MARKINGS[random.nextInt(MARKINGS.length)]);
    }

    /** Chọn một màu nền ngẫu nhiên để bộ sinh tái sử dụng trong cả đàn. */
    public static MobAppearance randomCoat(RandomGenerator random) {
        return COATS[random.nextInt(COATS.length)];
    }

    /** Lấy trung bình của ba số thực phân bố đều từ 0 đến gần 1. */
    private static float meanOfThree(RandomGenerator random) {
        return (random.nextFloat() + random.nextFloat() + random.nextFloat()) / 3f;
    }
}
