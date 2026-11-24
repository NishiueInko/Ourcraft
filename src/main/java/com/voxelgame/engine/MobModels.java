package com.voxelgame.engine;

import com.voxelgame.entity.LivingEntity;

/**
 * Khung khớp theo loài. Tọa độ đo bằng pixel, với chân trên Y=0
 * và hướng nhìn trung tính dọc -Z.
 */
public final class MobModels {
    /** Hai vị trí hông sau cần được bù khi thân dựng lên trên hai chân. */
    private static final float BEAR_HIND_HIP_DEPTH = 7;
    private static final float EQUINE_HIND_HIP_DEPTH = 6;

    /** Các bộ xương dùng chung, chỉ tạo hình học mô tả trước khi đến lượt vẽ. */
    static final MobModel ZOMBIE = humanoid(HumanoidKind.ZOMBIE);
    static final MobModel ZOMBIE_VILLAGER = humanoid(HumanoidKind.VILLAGER);
    static final MobModel SKELETON = humanoid(HumanoidKind.SKELETON);
    static final MobModel HUSK = humanoid(HumanoidKind.HUSK);
    static final MobModel PARCHED = humanoid(HumanoidKind.PARCHED);
    static final MobModel STRAY = humanoid(HumanoidKind.STRAY);
    static final MobModel WITCH = humanoid(HumanoidKind.WITCH);
    static final MobModel CREEPER = creeper();
    static final MobModel SPIDER = spider();
    static final MobModel ENDERMAN = enderman();
    static final MobModel COW = cow();
    static final MobModel SHEEP = sheep();
    static final MobModel PIG = pig();
    static final MobModel POLAR_BEAR = polarBear();
    static final MobModel GOAT = goat();
    static final MobModel CHICKEN = chicken();
    static final MobModel RABBIT = rabbit();
    static final MobModel WOLF = wolf();
    static final MobModel FOX = fox();
    static final MobModel HORSE = equine(false);
    static final MobModel DONKEY = equine(true);
    static final MobModel CAMEL = camel();
    static final MobModel ARROW = arrow();
    static final MobModel POTION = potion();

    /** Tập khung chỉ được khởi tạo một lần để renderer sở hữu vòng đời mesh dùng chung. */
    private MobModels() {
    }

    /** Lấy mô hình tương ứng với một diện mạo đã đăng ký. */
    public static MobModel model(MobAppearance appearance) {
        return appearance.model();
    }

    /** Dựng mô tả một hộp từ kích thước thực và góc trái của hình trải UV. */
    private static ModelPart box(float x, float y, float z, float dx, float dy, float dz,
                                 int width, int height, int depth, int u, int v, ModelPart... children) {
        return new ModelPart(x, y, z, dx, dy, dz, width, height, depth, u, v, 0f, children);
    }

    /** Dựng một vỏ riêng để vẽ lông, hoa văn, áo khoác hoặc mắt trên lớp nền. */
    private static ModelPart shell(float dx, float dy, float dz, int width, int height, int depth,
                                   int u, int v, float inflate, ModelPart.Layer layer) {
        return new ModelPart(0f, 0f, 0f, dx, dy, dz, width, height, depth, u, v, inflate).setLayer(layer);
    }

    /** Tách dữ liệu hoạt ảnh khỏi lớp gameplay, kể cả khi chạy bản xem trước với thực thể thường. */
    private static MobVisualState visualState(LivingEntity entity) {
        return entity instanceof MobVisual visual ? visual.mobVisualState() : null;
    }

    /** Đọc tư thế cơ sở, dùng IDLE khi thực thể xem trước chưa khai báo tư thế. */
    private static MobVisualState.Pose pose(MobVisualState state) {
        return state == null ? MobVisualState.Pose.IDLE : state.pose();
    }

    /** Tính chu kỳ bước bằng radian và trả góc khớp theo độ. */
    private static float gait(float phase, float amount, float amplitude) {
        return (float) Math.cos(phase) * amount * amplitude;
    }

    /** Các kiểu hình người có tỷ lệ và phụ kiện riêng. */
    private enum HumanoidKind {
        ZOMBIE, VILLAGER, HUSK, SKELETON, PARCHED, STRAY, WITCH
    }

    /** Dựng thân, các tay mang trang bị và đầu có phụ kiện theo kiểu hình người. */
    private static MobModel humanoid(HumanoidKind kind) {
        boolean bones = kind == HumanoidKind.SKELETON || kind == HumanoidKind.PARCHED || kind == HumanoidKind.STRAY;
        boolean coat = kind == HumanoidKind.STRAY;
        int headHeight = kind == HumanoidKind.VILLAGER ? 11 : 8;
        int armWidth = bones ? 2 : 3;
        int armDepth = bones ? 3 : 4;
        int legWidth = bones ? 2 : 3;
        int legDepth = bones ? 3 : 4;
        ModelPart weapon = switch (kind) {
            case SKELETON, STRAY, PARCHED -> bow();
            case HUSK -> spear();
            case WITCH -> box(0, -9, -1, -1.5f, -3, -1.5f, 3, 4, 3, 96, 40,
                    box(0, 1, 0, -0.5f, 0, -0.5f, 1, 2, 1, 110, 40));
            default -> null;
        };
        ModelPart head = switch (kind) {
            case WITCH -> box(0, 19, 0, -4, 0, -3.5f, 8, headHeight, 7, 0, 0,
                    box(0, 2, -3, -1.5f, -1, -3, 3, 3, 3, 34, 0),
                    box(0, 7, 0, -6, 0, -5.5f, 12, 2, 11, 43, 0,
                            box(1, 2, 0, -4, 0, -4, 8, 5, 8, 67, 20,
                                    box(2, 4, 1, -2, 0, -2, 4, 4, 4, 96, 0))));
            case VILLAGER -> box(0, 19, 0, -4, 0, -3.5f, 8, headHeight, 7, 0, 0,
                    box(0, 3, -3, -1.5f, -2, -3, 3, 4, 3, 34, 0));
            case STRAY -> box(0, 19, 0, -4, 0, -3.5f, 8, headHeight, 7, 0, 0,
                    shell(-4, 0, -3.5f, 8, headHeight, 7, 0, 0, 0.3f, ModelPart.Layer.OVERLAY));
            default -> box(0, 19, 0, -4, 0, -3.5f, 8, headHeight, 7, 0, 0);
        };

        ModelPart leftArm = humanoidArm(5, armWidth, armDepth, coat, weapon);
        ModelPart rightArm = humanoidArm(-5, armWidth, armDepth, coat, null);
        ModelPart leftLeg = humanoidLeg(2, legWidth, legDepth, coat);
        ModelPart rightLeg = humanoidLeg(-2, legWidth, legDepth, coat);
        ModelPart[] limbs = {head, leftArm, rightArm, leftLeg, rightLeg};
        ModelPart body = coat
                ? box(0, 0, 0, -4, 9, -2.5f, 8, 10, 5, 0, 22, head, leftArm, rightArm, leftLeg, rightLeg,
                        shell(-4, 9, -2.5f, 8, 10, 5, 0, 22, 0.45f, ModelPart.Layer.OVERLAY))
                : box(0, 0, 0, -4, 9, -2.5f, 8, 10, 5, 0, 22, limbs);
        return new HumanoidModel(body, head, leftArm, rightArm, leftLeg, rightLeg, weapon, kind);
    }

    /** Tạo tay xoay ở vai và gắn trang bị vào lòng bàn tay khi có. */
    private static ModelPart humanoidArm(float x, int width, int depth, boolean coat, ModelPart weapon) {
        float dx = -width * 0.5f;
        float dz = -depth * 0.5f;
        ModelPart sleeve = coat
                ? shell(dx, -9, dz, width, 10, depth, 48, 22, 0.3f, ModelPart.Layer.OVERLAY)
                : null;
        if (weapon != null && sleeve != null) {
            return box(x, 17, 0, dx, -9, dz, width, 10, depth, 48, 22, weapon, sleeve);
        }
        if (weapon != null) {
            return box(x, 17, 0, dx, -9, dz, width, 10, depth, 48, 22, weapon);
        }
        if (sleeve != null) {
            return box(x, 17, 0, dx, -9, dz, width, 10, depth, 48, 22, sleeve);
        }
        return box(x, 17, 0, dx, -9, dz, width, 10, depth, 48, 22);
    }

    /** Tạo chân xoay tại hông, với gấu áo khoác đồng bộ khi cần. */
    private static ModelPart humanoidLeg(float x, int width, int depth, boolean coat) {
        float dx = -width * 0.5f;
        float dz = -depth * 0.5f;
        if (coat) {
            return box(x, 10, 0, dx, -10, dz, width, 10, depth, 32, 22,
                    shell(dx, -10, dz, width, 10, depth, 32, 22, 0.28f, ModelPart.Layer.OVERLAY));
        }
        return box(x, 10, 0, dx, -10, dz, width, 10, depth, 32, 22);
    }

    /** Tạo cung ba đoạn và dây cung, dùng vùng ảnh của trang bị trên tấm ảnh cùng loài. */
    private static ModelPart bow() {
        return box(0, -8, -1, -1, -4, -1, 2, 8, 2, 0, 44,
                box(0, 4, 0, -1, 0, 0, 2, 3, 2, 8, 44),
                box(0, -4, 0, -1, -3, 0, 2, 3, 2, 8, 44),
                box(0, 0, 2, -0.5f, -6, 0, 1, 12, 1, 16, 44));
    }

    /** Tạo thân cán gỗ, đầu giáo kim loại và cổ nối theo khớp tay. */
    private static ModelPart spear() {
        return box(0, -8, 0, -0.5f, -12, 0, 1, 18, 1, 22, 40,
                box(0, -12, 0, -1.5f, -4, -0.5f, 3, 4, 2, 28, 40),
                box(0, -16, 0, -0.5f, -2, 0, 1, 2, 1, 38, 40));
    }

    /** Hoạt ảnh hình người có vòng bước và tư thế chiến đấu theo nhóm. */
    private static final class HumanoidModel extends MobModel {
        /** Các khớp cần thay đổi mỗi khung và phụ kiện có góc riêng. */
        private final ModelPart head;
        private final ModelPart leftArm;
        private final ModelPart rightArm;
        private final ModelPart leftLeg;
        private final ModelPart rightLeg;
        private final ModelPart weapon;
        private final HumanoidKind kind;

        /** Giữ các khớp đã dựng để cập nhật hoạt ảnh mà không cấp phát thêm đối tượng. */
        private HumanoidModel(ModelPart body, ModelPart head, ModelPart leftArm, ModelPart rightArm,
                              ModelPart leftLeg, ModelPart rightLeg, ModelPart weapon, HumanoidKind kind) {
            super(body);
            this.head = head;
            this.leftArm = leftArm;
            this.rightArm = rightArm;
            this.leftLeg = leftLeg;
            this.rightLeg = rightLeg;
            this.weapon = weapon;
            this.kind = kind;
        }

        /** Thay vòng vung tay bằng động tác giơ tay, bắn cung hoặc đâm giáo khi cần. */
        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            MobVisualState state = visualState(entity);
            MobVisualState.Pose pose = pose(state);
            float step = gait(limbSwing, limbSwingAmount, 44);
            float breath = (float) Math.sin(ageInTicks * 0.13f) * 3;
            head.setRotation(headPitch, headYaw, 0);
            leftLeg.setRotation(step, 0, 0);
            rightLeg.setRotation(-step, 0, 0);
            leftArm.setRotation(-step, 0, 3);
            rightArm.setRotation(step, 0, -3);
            if (kind == HumanoidKind.HUSK) {
                weapon.setVisible(pose == MobVisualState.Pose.THRUST);
            }
            if (pose == MobVisualState.Pose.AIM_BOW) {
                leftArm.setRotation(92 + headPitch, headYaw - 12, -7);
                rightArm.setRotation(77 + headPitch, headYaw + 28, 18);
                if (weapon != null && kind != HumanoidKind.HUSK) {
                    weapon.setRotation(-88, 0, 0);
                }
            } else if (pose == MobVisualState.Pose.THRUST) {
                float extension = state.poseAmount();
                leftArm.setRotation(65 + extension * 32 + headPitch, headYaw, -8);
                rightArm.setRotation(25 - extension * 34, 10, 0);
            } else if (pose == MobVisualState.Pose.UNDEAD || kind == HumanoidKind.ZOMBIE
                    || kind == HumanoidKind.VILLAGER || kind == HumanoidKind.HUSK) {
                leftArm.setRotation(83 + breath, -8, 0);
                rightArm.setRotation(83 - breath, 8, 0);
            } else if (kind == HumanoidKind.WITCH) {
                leftArm.setRotation(46 + breath, -12, -18);
                rightArm.setRotation(45 - breath, 10, 21);
            } else if (weapon != null) {
                weapon.setRotation(-22, 0, 0);
            }
        }
    }

    /** Dựng Creeper dạng hạt khoáng, với tấm giáp ngực và bốn chân móc ngắn. */
    private static MobModel creeper() {
        ModelPart head = box(0, 14, -1, -4.5f, 0, -3.5f, 9, 7, 7, 0, 0);
        ModelPart[] legs = animalLegs(3.8f, 5, -3, 3, 3, 4, false, false);
        ModelPart breastplate = box(0, 8, -3, -3, -2, -1, 6, 7, 1, 44, 0);
        ModelPart body = box(0, 0, 0, -3.5f, 4, -3, 7, 11, 6, 0, 24,
                head, breastplate, legs[0], legs[1], legs[2], legs[3]);
        return new CreeperModel(body, head, legs);
    }

    /** Lắc bốn chân Creeper đối pha trong khi đầu vẫn quan sát mục tiêu. */
    private static final class CreeperModel extends MobModel {
        /** Đầu và mảng bốn chân theo thứ tự cặp trước rồi cặp sau. */
        private final ModelPart head;
        private final ModelPart[] legs;

        /** Ghi nhận các khớp Creeper được thao tác trong vòng cập nhật. */
        private CreeperModel(ModelPart body, ModelPart head, ModelPart[] legs) {
            super(body);
            this.head = head;
            this.legs = legs;
        }

        /** Áp động tác bước cho hai cặp chéo trước khi renderer áp độ phồng lên toàn thân. */
        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            head.setRotation(headPitch, headYaw, 0);
            walk(legs, gait(limbSwing, limbSwingAmount, 42));
        }
    }

    /** Dựng nhện chân tám nhánh, mỗi chân có khớp gối riêng và mắt nằm trên lớp phát sáng. */
    private static MobModel spider() {
        ModelPart head = box(0, 6, -7, -4, -2, -3, 8, 6, 6, 40, 0,
                shell(-4, -2, -3, 8, 6, 6, 40, 0, 0.045f, ModelPart.Layer.EYES),
                box(-2, 0, -2, -1, -3, -2, 2, 3, 2, 76, 0),
                box(2, 0, -2, -1, -3, -2, 2, 3, 2, 76, 0));
        ModelPart abdomen = box(0, 4, 3, -6.5f, -1, -1, 13, 7, 13, 0, 24);
        ModelPart[] hips = new ModelPart[8];
        ModelPart[] knees = new ModelPart[8];
        for (int i = 0; i < hips.length; i++) {
            int sign = i < 4 ? 1 : -1;
            float thighX = sign > 0 ? 0 : -7;
            float shinX = sign > 0 ? 0 : -8;
            knees[i] = box(sign * 6, 0, 0, shinX, -1, -1, 8, 2, 2, 84, 26);
            hips[i] = box(sign * 4, 5, (i % 4 - 1.5f) * 2.3f,
                    thighX, -1, -1, 7, 2, 2, 64, 26, knees[i]);
        }
        ModelPart thorax = box(0, 0, 0, -4.5f, 3, -4, 9, 7, 8, 0, 0,
                head, abdomen, hips[0], hips[1], hips[2], hips[3], hips[4], hips[5], hips[6], hips[7]);
        return new SpiderModel(thorax, head, abdomen, hips, knees);
    }

    /** Xếp bốn chân mỗi bên thành hình quạt, với góc riêng cho đùi và cẳng. */
    private static final class SpiderModel extends MobModel {
        /** Các phần chuyển động trong bước đi đặc trưng của nhện. */
        private final ModelPart head;
        private final ModelPart abdomen;
        private final ModelPart[] hips;
        private final ModelPart[] knees;

        /** Dùng lại dãy khớp đã dựng cho mọi cá thể nhện. */
        private SpiderModel(ModelPart thorax, ModelPart head, ModelPart abdomen,
                            ModelPart[] hips, ModelPart[] knees) {
            super(thorax);
            this.head = head;
            this.abdomen = abdomen;
            this.hips = hips;
            this.knees = knees;
        }

        /** Đổi pha giữa các chân kề nhau, giữ cẳng chân nghiêng về nền. */
        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            head.setRotation(headPitch * 0.65f, headYaw * 0.7f, 0);
            abdomen.setRotation(gait(limbSwing * 2, limbSwingAmount, 4), 0, 0);
            for (int i = 0; i < hips.length; i++) {
                int side = i < 4 ? 1 : -1;
                int row = i % 4;
                float phase = limbSwing + row * (float) Math.PI + (side < 0 ? (float) Math.PI : 0);
                float lift = Math.max(0, gait(phase, limbSwingAmount, 19));
                hips[i].setRotation(0, side * (36 - row * 24 + gait(phase, limbSwingAmount, 17)),
                        side * (16 + lift));
                knees[i].setRotation(0, 0, side * (-43 - lift * 0.6f));
            }
        }
    }

    /** Dựng Enderman dáng cao, với hàm có bản lề và mắt phát sáng độc lập. */
    private static MobModel enderman() {
        ModelPart jaw = box(0, 2, 2, -3.5f, -2, -6, 7, 2, 6, 34, 0);
        ModelPart head = box(0, 35, -1, -4, 2, -3.5f, 8, 7, 7, 0, 0, jaw,
                shell(-4, 2, -3.5f, 8, 7, 7, 0, 0, 0.04f, ModelPart.Layer.EYES),
                box(0, 8, 1, -2, 0, -1.5f, 4, 3, 3, 65, 0));
        ModelPart leftArm = box(5, 33, 0, -1.5f, -23, -1.5f, 3, 24, 3, 50, 24);
        ModelPart rightArm = box(-5, 33, 0, -1.5f, -23, -1.5f, 3, 24, 3, 50, 24);
        ModelPart leftLeg = box(2, 22, 0, -1.5f, -22, -1.5f, 3, 22, 3, 34, 24);
        ModelPart rightLeg = box(-2, 22, 0, -1.5f, -22, -1.5f, 3, 22, 3, 34, 24);
        ModelPart body = box(0, 0, 0, -4, 21, -2, 8, 14, 4, 0, 24,
                head, leftArm, rightArm, leftLeg, rightLeg);
        return new EndermanModel(body, head, jaw, leftArm, rightArm, leftLeg, rightLeg);
    }

    /** Dùng nhịp bước dài, mở hàm và giật nhẹ đầu khi giận. */
    private static final class EndermanModel extends MobModel {
        /** Mỗi khớp chính có góc độc lập, kể cả hàm. */
        private final ModelPart head;
        private final ModelPart jaw;
        private final ModelPart leftArm;
        private final ModelPart rightArm;
        private final ModelPart leftLeg;
        private final ModelPart rightLeg;

        /** Giữ tham chiếu đến các khớp dựng sẵn. */
        private EndermanModel(ModelPart body, ModelPart head, ModelPart jaw, ModelPart leftArm,
                              ModelPart rightArm, ModelPart leftLeg, ModelPart rightLeg) {
            super(body);
            this.head = head;
            this.jaw = jaw;
            this.leftArm = leftArm;
            this.rightArm = rightArm;
            this.leftLeg = leftLeg;
            this.rightLeg = rightLeg;
        }

        /** Giới hạn sải chân và mở hàm lên đến 34 độ trong tư thế giận. */
        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            boolean angry = pose(visualState(entity)) == MobVisualState.Pose.ANGRY;
            float step = gait(limbSwing, limbSwingAmount, 23);
            float twitch = angry ? (float) Math.sin(ageInTicks * 2.2f) * 2 : 0;
            head.setRotation(headPitch, headYaw + twitch, twitch);
            jaw.setRotation(-34 * (angry ? 1 : 0), 0, 0);
            leftLeg.setRotation(step, 0, 0);
            rightLeg.setRotation(-step, 0, 0);
            leftArm.setRotation(-step, 0, angry ? 11 : 4);
            rightArm.setRotation(step, 0, angry ? -11 : -4);
        }
    }

    /** Các biến thể bốn chân có nhịp và tập tư thế tương ứng. */
    private enum AnimalKind {
        GRAZER, GOAT, BEAR, RABBIT, WOLF, FOX, EQUINE, CAMEL
    }

    /** Dựng chân trên hai đường hông với bộ UV dùng chung giữa bên trái và phải. */
    private static ModelPart[] animalLegs(float halfWidth, int length, float frontZ, float backZ,
                                          int width, int depth, boolean wool, boolean markings) {
        int height = length;
        ModelPart.Layer layer = ModelPart.Layer.OVERLAY;
        float dx = -width * 0.5f;
        float dz = -depth * 0.5f;
        ModelPart[] legs = new ModelPart[4];
        for (int i = 0; i < legs.length; i++) {
            float x = (i % 2 == 0 ? 1 : -1) * halfWidth;
            float z = i < 2 ? frontZ : backZ;
            if (wool || markings) {
                legs[i] = box(x, length, z, dx, -height, dz, width, height, depth, 76, 0,
                        shell(dx, -height, dz, width, height, depth, 76, 0, wool ? 0.32f : 0.08f, layer));
            } else {
                legs[i] = box(x, length, z, dx, -height, dz, width, height, depth, 76, 0);
            }
        }
        return legs;
    }

    /** Đánh hai cặp chân chéo luân phiên để giữ dáng đi ổn định. */
    private static void walk(ModelPart[] legs, float angle) {
        legs[0].setRotation(angle, 0, 0);
        legs[1].setRotation(-angle, 0, 0);
        legs[2].setRotation(-angle, 0, 0);
        legs[3].setRotation(angle, 0, 0);
    }

    /** Dựng mũi nhô ở mặt trước, nối với khớp đầu hoặc cổ. */
    private static ModelPart muzzle(float y, float z, int width, int height, int depth) {
        return box(0, y, z, -width * 0.5f, 0, -depth, width, height, depth, 40, 0);
    }

    /** Dựng tai từ điểm đính trên đầu lên trên. */
    private static ModelPart ear(float x, float y, float z, int width, int height, int depth) {
        return box(x, y, z, -width * 0.5f, 0, -depth * 0.5f, width, height, depth, 96, 0);
    }

    /** Dựng đuôi thuôn theo đáy thân với điểm xoay dọc sống lưng. */
    private static ModelPart tail(float y, float z, int width, int height, int depth) {
        return box(0, y, z, -width * 0.5f, -height, 0, width, height, depth, 96, 24);
    }

    /** Dựng thân cùng bốn chân, đầu và các phụ kiện còn lại cùng biến đổi theo thân. */
    private static ModelPart animalBody(int width, int height, int depth, float bottom, ModelPart head,
                                         ModelPart[] legs, ModelPart tail, ModelPart... accents) {
        int tailCount = tail == null ? 0 : 1;
        ModelPart[] children = new ModelPart[5 + tailCount + accents.length];
        children[0] = head;
        System.arraycopy(legs, 0, children, 1, legs.length);
        if (tail != null) {
            children[5] = tail;
        }
        System.arraycopy(accents, 0, children, 5 + tailCount, accents.length);
        return box(0, 0, 0, -width * 0.5f, bottom, -depth * 0.5f, width, height, depth, 0, 24, children);
    }

    /** Dựng bò thân bè, sừng ngắn, mõm rộng và đuôi dài. */
    private static MobModel cow() {
        ModelPart head = box(0, 13, -7, -4, -3, -5, 8, 7, 6, 0, 0,
                muzzle(-3, -4, 7, 3, 3),
                box(3, 3, -1, 0, 0, -1, 2, 3, 2, 112, 0),
                box(-3, 3, -1, -2, 0, -1, 2, 3, 2, 112, 0));
        ModelPart[] legs = animalLegs(3.4f, 8, -5.5f, 5.5f, 3, 3, false, false);
        ModelPart tail = tail(14, 8, 2, 7, 3);
        return new QuadrupedModel(animalBody(10, 8, 17, 7, head, legs, tail), head, tail, legs, AnimalKind.GRAZER);
    }

    /** Dựng cừu với lớp lông phồng quanh thân, trán và phần trên của chân. */
    private static MobModel sheep() {
        ModelPart head = box(0, 12, -6, -3.5f, -3, -5, 7, 7, 6, 0, 0,
                ear(3.8f, 1, -1, 2, 3, 2), ear(-3.8f, 1, -1, 2, 3, 2),
                shell(-3.5f, -3, -5, 7, 7, 6, 0, 0, 0.65f, ModelPart.Layer.OVERLAY));
        ModelPart[] legs = animalLegs(3.2f, 7, -4.5f, 4.5f, 3, 3, true, false);
        ModelPart wool = shell(-4.5f, 6, -7, 9, 7, 14, 0, 24, 1f, ModelPart.Layer.OVERLAY);
        return new QuadrupedModel(animalBody(9, 7, 14, 6, head, legs, null, wool),
                head, null, legs, AnimalKind.GRAZER);
    }

    /** Dựng lợn bụng thấp, tai ngắn, mõm nhô và đuôi gấp. */
    private static MobModel pig() {
        ModelPart head = box(0, 10, -6, -3.5f, -3, -4, 7, 6, 6, 0, 0,
                muzzle(-2, -3, 5, 3, 3), ear(2.5f, 2, -2, 2, 3, 2), ear(-2.5f, 2, -2, 2, 3, 2));
        ModelPart[] legs = animalLegs(3.3f, 6, -4.5f, 4.5f, 3, 3, false, false);
        ModelPart tail = tail(10, 7, 2, 2, 3);
        return new QuadrupedModel(animalBody(9, 7, 15, 5, head, legs, tail), head, tail, legs, AnimalKind.GRAZER);
    }

    /** Dựng dê núi có sừng hai đốt và râu tách khớp đầu. */
    private static MobModel goat() {
        ModelPart rightHorn = box(-2.8f, 3, 0, -1, 0, -1, 2, 5, 2, 112, 0,
                box(0, 4, 0, -1, 0, -1, 2, 3, 2, 112, 10));
        ModelPart leftHorn = box(2.8f, 3, 0, -1, 0, -1, 2, 5, 2, 112, 0,
                box(0, 4, 0, -1, 0, -1, 2, 3, 2, 112, 10));
        ModelPart head = box(0, 13, -6, -3.5f, -3, -4, 7, 7, 6, 0, 0,
                rightHorn, leftHorn, muzzle(-3, -3, 5, 3, 3),
                box(0, -2, -4, -1.5f, -4, -1, 3, 4, 2, 58, 0));
        ModelPart[] legs = animalLegs(3.0f, 8, -4.5f, 4.5f, 3, 3, false, false);
        ModelPart tail = tail(13, 6, 3, 2, 3);
        return new QuadrupedModel(animalBody(8, 8, 14, 7, head, legs, tail), head, tail, legs, AnimalKind.GOAT);
    }

    /** Dựng gấu với vai cao, bàn chân rộng và đầu nhỏ hơn thân. */
    private static MobModel polarBear() {
        ModelPart head = box(0, 15, -8, -4, -3, -5, 8, 7, 7, 0, 0,
                muzzle(-3, -4, 6, 4, 4), ear(3, 3, 0, 2, 2, 2), ear(-3, 3, 0, 2, 2, 2));
        ModelPart[] legs = animalLegs(4.2f, 9, -7, BEAR_HIND_HIP_DEPTH, 4, 4, false, false);
        ModelPart tail = tail(14, 10, 3, 3, 2);
        return new QuadrupedModel(animalBody(12, 10, 20, 8, head, legs, tail), head, tail, legs, AnimalKind.BEAR);
    }

    /** Dựng thỏ với tai dài, chân trước nhỏ và bắp đùi sau lớn. */
    private static MobModel rabbit() {
        ModelPart head = box(0, 7, -3, -2.5f, -1, -3, 5, 5, 5, 0, 0,
                ear(1.5f, 4, -1, 2, 8, 2), ear(-1.5f, 4, -1, 2, 8, 2),
                muzzle(-1, -2, 3, 2, 2));
        ModelPart[] legs = animalLegs(2.2f, 4, -2.7f, 3, 2, 2, false, false);
        for (int i = 2; i < legs.length; i++) {
            float x = i == 2 ? 2.6f : -2.6f;
            legs[i] = box(x, 4, 2.6f, -1.5f, -4, -2, 3, 5, 4, 72, 38);
        }
        ModelPart tail = tail(6.5f, 5, 3, 3, 2);
        return new QuadrupedModel(animalBody(6, 5, 10, 3, head, legs, tail), head, tail, legs, AnimalKind.RABBIT);
    }

    /** Dựng sói tuyết với ngực to hơn hông, mõm thon và đuôi dài. */
    private static MobModel wolf() {
        ModelPart head = box(0, 12, -5.8f, -3.5f, -3, -4, 7, 6, 6, 0, 0,
                muzzle(-2, -3, 5, 3, 4), ear(2.4f, 2.5f, 0, 2, 4, 2), ear(-2.4f, 2.5f, 0, 2, 4, 2));
        ModelPart[] legs = animalLegs(2.7f, 7, -4.8f, 4.8f, 2, 3, false, false);
        ModelPart tail = tail(11.5f, 6.5f, 3, 3, 8);
        ModelPart chest = box(0, 0, 0, -3.8f, 6, -7, 8, 7, 5, 60, 24);
        return new QuadrupedModel(animalBody(6, 6, 14, 6, head, legs, tail, chest), head, tail, legs, AnimalKind.WOLF);
    }

    /** Dựng cáo chân thấp với đuôi dày nối liền thân khi cuộn ngủ. */
    private static MobModel fox() {
        ModelPart head = box(0, 7.5f, -4.5f, -3.5f, -2.5f, -4, 7, 6, 6, 0, 0,
                muzzle(-2.5f, -3.5f, 4, 3, 4),
                ear(2.4f, 3, -0.5f, 2, 4, 2), ear(-2.4f, 3, -0.5f, 2, 4, 2));
        ModelPart[] legs = animalLegs(2.5f, 4, -4, 4, 2, 2, false, false);
        ModelPart tail = tail(6.5f, 5.5f, 4, 4, 11);
        return new QuadrupedModel(animalBody(6, 5, 12, 3, head, legs, tail), head, tail, legs, AnimalKind.FOX);
    }

    /** Dựng ngựa và lừa với cổ gập theo đầu, bờm riêng và tai dài hơn ở lừa. */
    private static MobModel equine(boolean donkey) {
        int earHeight = donkey ? 7 : 4;
        ModelPart mane = box(0, 2, 1, -1.5f, 0, 0, 3, 9, 3, 72, 24);
        ModelPart upperHead = donkey
                ? box(0, 8, -2, -3.5f, -2, -6, 7, 7, 8, 0, 0, muzzle(-2, -5, 6, 4, 4),
                        ear(2, 4, 0, 2, earHeight, 2), ear(-2, 4, 0, 2, earHeight, 2))
                : box(0, 8, -2, -3.5f, -2, -6, 7, 7, 8, 0, 0, muzzle(-2, -5, 6, 4, 4),
                        ear(2, 4, 0, 2, earHeight, 2), ear(-2, 4, 0, 2, earHeight, 2),
                        shell(-3.5f, -2, -6, 7, 7, 8, 0, 0, 0.09f, ModelPart.Layer.OVERLAY));
        ModelPart neck = box(0, 16, -7, -3, -2, -3, 6, 11, 6, 98, 42, upperHead, mane);
        ModelPart[] legs = animalLegs(3.8f, donkey ? 12 : 13, -6, EQUINE_HIND_HIP_DEPTH, 3, 4, false, !donkey);
        ModelPart tail = tail(18, 8.5f, donkey ? 2 : 3, donkey ? 8 : 10, 3);
        ModelPart body;
        if (donkey) {
            body = animalBody(9, 9, 17, 11, neck, legs, tail);
        } else {
            ModelPart markings = shell(-5, 12, -9.5f, 10, 10, 19, 0, 24, 0.09f, ModelPart.Layer.OVERLAY);
            body = animalBody(10, 10, 19, 12, neck, legs, tail, markings);
        }
        return new QuadrupedModel(body, neck, tail, legs, AnimalKind.EQUINE);
    }

    /** Dựng lạc đà hai bướu với chân dài và cổ gập đến tư thế ngồi. */
    private static MobModel camel() {
        ModelPart head = box(0, 11, -2, -3.5f, -2, -5, 7, 7, 7, 0, 0,
                muzzle(-2, -4, 6, 4, 4), ear(2.2f, 4, 0, 2, 3, 2), ear(-2.2f, 4, 0, 2, 3, 2));
        ModelPart neck = box(0, 21, -6, -3, -2, -3.5f, 6, 14, 7, 97, 42, head);
        ModelPart humpFront = box(0, 25, -4.3f, -3.5f, 0, -3, 7, 6, 6, 64, 42);
        ModelPart humpBack = box(0, 26, 3.6f, -3.5f, 0, -3, 7, 6, 6, 64, 42);
        ModelPart[] legs = animalLegs(3.8f, 18, -6.5f, 6.5f, 3, 4, false, false);
        ModelPart tail = tail(22, 9, 2, 7, 3);
        return new QuadrupedModel(animalBody(10, 9, 18, 17, neck, legs, tail, humpFront, humpBack),
                neck, tail, legs, AnimalKind.CAMEL);
    }

    /** Chia sẻ nhịp bốn chân và chỉ điều chỉnh khớp theo những tư thế từng loài hỗ trợ. */
    private static final class QuadrupedModel extends MobModel {
        /** Các gốc biến đổi của thân và các chi. */
        private final ModelPart body;
        private final ModelPart head;
        private final ModelPart tail;
        private final ModelPart[] legs;
        private final AnimalKind kind;

        /** Lưu trật tự khớp để không tạo mảng lúc vẽ. */
        private QuadrupedModel(ModelPart body, ModelPart head, ModelPart tail, ModelPart[] legs, AnimalKind kind) {
            super(body);
            this.body = body;
            this.head = head;
            this.tail = tail;
            this.legs = legs;
            this.kind = kind;
        }

        /** Hòa chuyển động nhìn, bước, đuôi và tư thế riêng của các loài bốn chân. */
        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            MobVisualState state = visualState(entity);
            MobVisualState.Pose pose = pose(state);
            float amount = state == null ? 0 : state.poseAmount();
            if (state != null && kind == AnimalKind.FOX && state.appearance() == MobAppearance.SNOW_FOX_SLEEP) {
                pose = MobVisualState.Pose.SLEEP;
                amount = 1;
            }
            if (state != null && kind == AnimalKind.WOLF && state.appearance() == MobAppearance.WOLF_SNOWY_ANGRY
                    && pose == MobVisualState.Pose.IDLE) {
                pose = MobVisualState.Pose.ANGRY;
                amount = 1;
            }
            walk(legs, gait(limbSwing, limbSwingAmount, 39));
            head.setRotation(headPitch, headYaw, 0);
            if (tail != null) {
                tail.setRotation(kind == AnimalKind.FOX || kind == AnimalKind.WOLF ? 8 : 0,
                        gait(limbSwing + ageInTicks * 0.06f, limbSwingAmount, 19), 0);
            }
            switch (pose) {
                case GRAZE -> head.setRotation(-55 * amount, headYaw * 0.2f, 0);
                case RAM -> {
                    if (kind == AnimalKind.GOAT) {
                        head.setRotation(-72 * amount, 0, 0);
                        body.setRotation(-5 * amount, 0, 0);
                    }
                }
                case REAR -> {
                    if (kind == AnimalKind.BEAR || kind == AnimalKind.EQUINE) {
                        rear(amount, headYaw, headPitch);
                    }
                }
                case HOP -> {
                    if (kind == AnimalKind.RABBIT) {
                        body.setTranslation(0, amount * 2.5f, 0);
                        body.setRotation(12 * amount, 0, 0);
                        walk(legs, 0);
                        legs[0].setRotation(52 * amount, 0, 0);
                        legs[1].setRotation(52 * amount, 0, 0);
                        legs[2].setRotation(-36 * amount, 0, 0);
                        legs[3].setRotation(-36 * amount, 0, 0);
                    }
                }
                case TILT_HEAD -> {
                    if (kind == AnimalKind.WOLF) {
                        head.setRotation(headPitch, headYaw, 32 * amount);
                    }
                }
                case ANGRY -> {
                    if (kind == AnimalKind.WOLF) {
                        head.setRotation(headPitch - 12 * amount, headYaw, 0);
                        tail.setRotation(-34 * amount, 0, 0);
                    }
                }
                case POUNCE -> {
                    if (kind == AnimalKind.FOX) {
                        body.setRotation(27 * amount, 0, 0);
                        body.setTranslation(0, 2 * amount, 0);
                        foldLegs(59 * amount, -63 * amount);
                        tail.setRotation(-34 * amount, 0, 0);
                    }
                }
                case SLEEP -> {
                    if (kind == AnimalKind.FOX) {
                        sleep(amount);
                    }
                }
                case SIT -> {
                    if (kind == AnimalKind.CAMEL) {
                        sit(amount);
                    }
                }
                default -> {
                    // Tư thế thông thường giữ các giá trị bước và nhìn đã tính phía trên.
                }
            }
        }

        /** Nhấc thân lên chân sau và giữ điểm đặt hai chân sau tại chỗ. */
        private void rear(float amount, float headYaw, float headPitch) {
            float angle = 62 * amount;
            double radians = Math.toRadians(angle);
            float sine = (float) Math.sin(radians);
            float cosine = (float) Math.cos(radians);
            float hipHeight = legs[2].geometry().height();
            float hipDepth = kind == AnimalKind.BEAR ? BEAR_HIND_HIP_DEPTH : EQUINE_HIND_HIP_DEPTH;
            body.setRotation(angle, 0, 0);
            // Bù phần hông di chuyển do thân quay để chân sau vẫn chạm mặt đất.
            body.setTranslation(0, hipHeight * (1 - cosine) + hipDepth * sine,
                    hipDepth * (1 - cosine) - hipHeight * sine);
            head.setRotation(headPitch - 44 * amount, headYaw * 0.6f, 0);
            foldLegs(22 * amount, -angle);
        }

        /** Thu cả bốn chân, xoay đầu về sườn và quấn đuôi về trước quanh thân cáo. */
        private void sleep(float amount) {
            body.setRotation(0, 0, -13 * amount);
            body.setTranslation(0, -1.8f * amount, 0);
            head.setRotation(-17 * amount, 72 * amount, 20 * amount);
            tail.setRotation(-6 * amount, 143 * amount, 0);
            foldLegs(69 * amount, -73 * amount);
        }

        /** Hạ thân và thu chân theo hai hướng để bướu giữ thăng bằng khi lạc đà ngồi. */
        private void sit(float amount) {
            body.setTranslation(0, -12 * amount, 0);
            body.setRotation(-4 * amount, 0, 0);
            head.setRotation(-22 * amount, 0, 0);
            tail.setRotation(-23 * amount, 0, 0);
            foldLegs(78 * amount, -65 * amount);
        }

        /** Dùng cùng một góc cho hai chân trước và một góc cho hai chân sau. */
        private void foldLegs(float forward, float back) {
            legs[0].setRotation(forward, 0, 0);
            legs[1].setRotation(forward, 0, 0);
            legs[2].setRotation(back, 0, 0);
            legs[3].setRotation(back, 0, 0);
        }
    }

    /** Dựng gà với mỏ, mào, hai cánh, đuôi và hai chân riêng biệt. */
    private static MobModel chicken() {
        ModelPart head = box(0, 10, -3, -2.5f, -1, -3, 5, 5, 5, 0, 0,
                muzzle(0, -2.5f, 4, 2, 3),
                box(0, 4, -1, -1, 0, -1, 2, 3, 3, 112, 0));
        ModelPart rightWing = box(-3, 8, 0, -1, -3, -2, 2, 4, 6, 64, 24);
        ModelPart leftWing = box(3, 8, 0, -1, -3, -2, 2, 4, 6, 64, 24);
        ModelPart rightLeg = box(-1.7f, 5, 0, -0.5f, -5, -2, 1, 6, 3, 76, 0);
        ModelPart leftLeg = box(1.7f, 5, 0, -0.5f, -5, -2, 1, 6, 3, 76, 0);
        ModelPart body = box(0, 0, 0, -3.5f, 5, -4, 7, 6, 9, 0, 24,
                head, rightWing, leftWing, rightLeg, leftLeg, tail(9, 4, 4, 2, 4));
        return new ChickenModel(body, head, rightWing, leftWing, rightLeg, leftLeg);
    }

    /** Hoạt ảnh gà vỗ cánh đối xứng khi nhảy hoặc rơi. */
    private static final class ChickenModel extends MobModel {
        /** Các khớp điều khiển vòng bước chân và nhịp cánh. */
        private final ModelPart head;
        private final ModelPart rightWing;
        private final ModelPart leftWing;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;

        /** Nhận các khớp của gà đã được lắp ráp. */
        private ChickenModel(ModelPart body, ModelPart head, ModelPart rightWing, ModelPart leftWing,
                             ModelPart rightLeg, ModelPart leftLeg) {
            super(body);
            this.head = head;
            this.rightWing = rightWing;
            this.leftWing = leftWing;
            this.rightLeg = rightLeg;
            this.leftLeg = leftLeg;
        }

        /** Tách nhịp cánh theo tuổi khỏi nhịp bước theo quãng đường. */
        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            MobVisualState state = visualState(entity);
            float flap = 5;
            if (pose(state) == MobVisualState.Pose.FLAP) {
                flap += (53 + 29 * (float) Math.sin(ageInTicks * 1.35f)) * state.poseAmount();
            }
            float step = gait(limbSwing, limbSwingAmount, 46);
            head.setRotation(headPitch, headYaw, 0);
            rightWing.setRotation(0, 0, -flap);
            leftWing.setRotation(0, 0, flap);
            rightLeg.setRotation(step, 0, 0);
            leftLeg.setRotation(-step, 0, 0);
        }
    }

    /** Dựng mũi tên có đầu kim loại và hai cánh đuôi giao nhau, hướng về -Z. */
    private static MobModel arrow() {
        return fixed(box(0, 1, 0, -0.5f, -0.5f, -5, 1, 1, 10, 0, 24,
                box(0, 0, -4.8f, -1.5f, -1.5f, -2, 3, 3, 2, 40, 0),
                box(0, 0, 2.6f, -1.5f, -0.5f, 0, 3, 1, 3, 76, 0),
                box(0, 0, 2.6f, -0.5f, -1.5f, 0, 1, 3, 3, 90, 0)));
    }

    /** Dựng lọ thuốc có nút bần và cổ chai tách màu. */
    private static MobModel potion() {
        return fixed(box(0, 0, 0, -2.5f, 0, -2, 5, 5, 4, 0, 24,
                box(0, 5, 0, -1.5f, 0, -1.5f, 3, 2, 3, 40, 0),
                box(0, 7, 0, -1, 0, -1, 2, 1, 2, 76, 0)));
    }

    /** Giữ tư thế cục bộ của đạn; hướng bay do biến đổi gốc quyết định. */
    private static MobModel fixed(ModelPart root) {
        return new MobModel(root) {
            /** Đạn giữ các khớp cố định ở mọi khung hình. */
            @Override
            public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                       float ageInTicks, float headYaw, float headPitch) {
                // Định hướng chuyển động thuộc thực thể đạn, không thuộc khung khớp.
            }
        };
    }
}
