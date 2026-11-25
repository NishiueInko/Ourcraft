package com.voxelgame.engine;

import com.badlogic.gdx.math.MathUtils;
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

    /** Dựng hộp có cùng kích thước hình học và ảnh trải Minecraft. */
    private static ModelPart skinBox(float x, float y, float z, float dx, float dy, float dz,
                                     int width, int height, int depth, int u, int v, ModelPart... children) {
        return skinBox(x, y, z, dx, dy, dz, width, height, depth, uv(u, v, width, height, depth), children);
    }

    /** Dựng hộp ở tỉ lệ của VoxelGame, lấy sáu mặt từ hình trải nguồn. */
    private static ModelPart skinBox(float x, float y, float z, float dx, float dy, float dz,
                                     int width, int height, int depth, ModelPart.CuboidUv texture,
                                     ModelPart... children) {
        return new ModelPart(x, y, z, dx, dy, dz, width, height, depth, 0f, texture, children);
    }

    /** Tạo hình trải thẳng cho đầu, chân và các bộ phận không nằm ngang. */
    private static ModelPart.CuboidUv uv(int u, int v, int width, int height, int depth) {
        return new ModelPart.CuboidUv(u, v, width, height, depth, ModelPart.UvOrientation.MINECRAFT);
    }

    /** Tạo bản đối xứng của hình trải dùng chung cho chi trái. */
    private static ModelPart.CuboidUv mirrored(int u, int v, int width, int height, int depth) {
        return new ModelPart.CuboidUv(u, v, width, height, depth, ModelPart.UvOrientation.MIRRORED);
    }

    /** Trải thân nằm ngang từ hộp nguồn có trục dài theo chiều đứng. */
    private static ModelPart.CuboidUv horizontal(int u, int v, int width, int height, int depth) {
        return new ModelPart.CuboidUv(u, v, width, height, depth, ModelPart.UvOrientation.HORIZONTAL);
    }

    /** Dựng vỏ lông, áo hoặc mắt, phồng nhẹ để tránh trùng mặt với lớp nền. */
    private static ModelPart shell(float dx, float dy, float dz, int width, int height, int depth,
                                   ModelPart.CuboidUv texture, float inflate, ModelPart.Layer layer) {
        return new ModelPart(0f, 0f, 0f, dx, dy, dz, width, height, depth, inflate, texture).setLayer(layer);
    }

    /** Giữ UV Ourcraft cho các phần trang bị cầm tay nằm trên một ảnh riêng. */
    private static ModelPart equipment(float x, float y, float z, float dx, float dy, float dz,
                                       int width, int height, int depth, int u, int v, ModelPart... children) {
        return box(x, y, z, dx, dy, dz, width, height, depth, u, v, children).setLayer(ModelPart.Layer.EQUIPMENT);
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

    /** Dựng tay chân, đầu và các lớp áo theo bố cục của từng nhóm hình người. */
    private static MobModel humanoid(HumanoidKind kind) {
        boolean bones = kind == HumanoidKind.SKELETON || kind == HumanoidKind.PARCHED || kind == HumanoidKind.STRAY;
        boolean villager = kind == HumanoidKind.VILLAGER || kind == HumanoidKind.WITCH;
        int limbSize = bones ? 2 : 4;
        int torsoDepth = villager ? 6 : 4;
        ModelPart weapon = switch (kind) {
            case SKELETON, STRAY, PARCHED -> bow();
            case HUSK -> spear();
            case WITCH -> equipment(0, -9, -1, -1.5f, -3, -1.5f, 3, 4, 3, 96, 40,
                    equipment(0, 1, 0, -0.5f, 0, -0.5f, 1, 2, 1, 110, 40));
            default -> null;
        };
        ModelPart head = humanoidHead(kind);
        ModelPart leftArm = humanoidLimb(kind, true, 5, 17, limbSize, weapon);
        ModelPart rightArm = humanoidLimb(kind, true, -5, 17, limbSize, null);
        ModelPart leftLeg = humanoidLimb(kind, false, 2, 10, limbSize, null);
        ModelPart rightLeg = humanoidLimb(kind, false, -2, 10, limbSize, null);
        ModelPart.CuboidUv bodyUv = uv(16, villager ? 20 : 16, 8, 12, torsoDepth);
        ModelPart coat = switch (kind) {
            case STRAY -> shell(-4, 9, -2, 8, 10, 4, bodyUv, 0.45f, ModelPart.Layer.OVERLAY);
            case PARCHED -> shell(-4, 9, -2, 8, 10, 4, uv(16, 48, 8, 12, 4), 0.05f, ModelPart.Layer.BASE);
            case WITCH -> shell(-4, 3, -3, 8, 16, 6, uv(0, 38, 8, 20, 6), 0.12f, ModelPart.Layer.BASE);
            default -> null;
        };
        ModelPart[] parts = coat == null
                ? new ModelPart[]{head, leftArm, rightArm, leftLeg, rightLeg}
                : new ModelPart[]{head, leftArm, rightArm, leftLeg, rightLeg, coat};
        ModelPart body = skinBox(0, 0, 0, -4, 9, -torsoDepth * 0.5f, 8, 10, torsoDepth, bodyUv, parts);
        return new HumanoidModel(body, head, leftArm, rightArm, leftLeg, rightLeg, weapon, kind);
    }

    /** Gắn mũ, mũi hoặc lớp trùm đầu khớp với phần trên của đúng ảnh. */
    private static ModelPart humanoidHead(HumanoidKind kind) {
        ModelPart.CuboidUv headUv = uv(0, 0, 8, 8, 8);
        return switch (kind) {
            case WITCH -> skinBox(0, 19, 0, -4, 0, -4, 8, 10, 8, 0, 0,
                    skinBox(0, 1, -4, -1, -2, -2, 2, 4, 2, 24, 0,
                            skinBox(0.5f, -1, -1.8f, -0.5f, 0, -0.5f, 1, 1, 1, 0, 0)),
                    skinBox(0, 9, 0, -5, 0, -5, 10, 2, 10, 0, 64,
                            skinBox(0.25f, 2, 0, -3.5f, 0, -3.5f, 7, 4, 7, 0, 76,
                                    skinBox(1, 4, 0.25f, -2, 0, -2, 4, 4, 4, 0, 87,
                                            skinBox(1.5f, 3, 1, -0.5f, 0, -0.5f, 1, 2, 1, 0, 95)))));
            case VILLAGER -> skinBox(0, 19, 0, -4, 0, -4, 8, 10, 8, 0, 0,
                    skinBox(0, 3, -4, -1, -2, -2, 2, 4, 2, 24, 0));
            case STRAY -> skinBox(0, 19, 0, -4, 0, -4, 8, 8, 8, headUv,
                    shell(-4, 0, -4, 8, 8, 8, headUv, 0.3f, ModelPart.Layer.OVERLAY));
            case PARCHED -> skinBox(0, 19, 0, -4, 0, -4, 8, 8, 8, headUv,
                    shell(-4, 0, -4, 8, 8, 8, uv(0, 32, 8, 8, 8), 0.2f, ModelPart.Layer.BASE));
            default -> skinBox(0, 19, 0, -4, 0, -4, 8, 8, 8, headUv);
        };
    }

    /** Tạo chi và gắn lớp vải theo đúng chi để chúng cùng chuyển động. */
    private static ModelPart humanoidLimb(HumanoidKind kind, boolean arm, int x, int y,
                                         int size, ModelPart weapon) {
        boolean left = x > 0;
        boolean villager = kind == HumanoidKind.VILLAGER || kind == HumanoidKind.WITCH;
        int u = arm ? (villager ? 44 : 40) : 0;
        int v = villager ? 22 : 16;
        boolean witchArm = kind == HumanoidKind.WITCH && arm;
        float minY = witchArm ? -6f : arm ? -9f : -10f;
        int sourceHeight = witchArm ? 8 : 12;
        int height = witchArm ? 7 : 10;
        float side = -size * 0.5f;
        ModelPart.CuboidUv texture = left ? mirrored(u, v, size, sourceHeight, size)
                : uv(u, v, size, sourceHeight, size);
        ModelPart clothes = null;
        if (kind == HumanoidKind.STRAY) {
            ModelPart.CuboidUv clothUv = left ? mirrored(u, v, 4, 12, 4) : uv(u, v, 4, 12, 4);
            clothes = shell(-1.5f, minY, -1.5f, 3, 10, 3, clothUv, 0.2f, ModelPart.Layer.OVERLAY);
        } else if (kind == HumanoidKind.PARCHED) {
            if (arm && left) {
                texture = uv(56, 16, 2, 12, 2);
            }
            ModelPart.CuboidUv clothUv = arm
                    ? uv(left ? 40 : 42, left ? 48 : 33, 3, 12, 3)
                    : uv(left ? 4 : 0, 49, 3, 12, 3);
            clothes = shell(-1.5f, minY, -1.5f, 3, 10, 3, clothUv, 0f, ModelPart.Layer.BASE);
        }
        if (witchArm) {
            // Vùng tay áo kết thúc ở V=34. Dùng đoạn ngang của cặp tay gập cho phần cổ tay.
            clothes = skinBox(0, 0, 0, -2, -9, -2, 4, 3, 4, uv(40, 38, 8, 4, 4));
        }
        ModelPart[] attachments;
        if (weapon != null && clothes != null) {
            attachments = new ModelPart[]{weapon, clothes};
        } else if (weapon != null || clothes != null) {
            attachments = new ModelPart[]{weapon != null ? weapon : clothes};
        } else {
            attachments = new ModelPart[0];
        }
        return skinBox(x, y, 0, side, minY, side, size, height, size, texture, attachments);
    }

    /** Tạo cung ba đoạn và dây cung từ hình ảnh trang bị gốc. */
    private static ModelPart bow() {
        return equipment(0, -8, -1, -1, -4, -1, 2, 8, 2, 0, 44,
                equipment(0, 4, 0, -1, 0, 0, 2, 3, 2, 8, 44),
                equipment(0, -4, 0, -1, -3, 0, 2, 3, 2, 8, 44),
                equipment(0, 0, 2, -0.5f, -6, 0, 1, 12, 1, 16, 44));
    }

    /** Tạo thân cán gỗ, đầu giáo kim loại và cổ nối theo khớp tay. */
    private static ModelPart spear() {
        return equipment(0, -8, 0, -0.5f, -12, 0, 1, 18, 1, 22, 40,
                equipment(0, -12, 0, -1.5f, -4, -0.5f, 3, 4, 2, 28, 40),
                equipment(0, -16, 0, -0.5f, -2, 0, 1, 2, 1, 38, 40));
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
                float draw = state.poseAmount();
                leftArm.setRotation(92 + headPitch, headYaw - 12, -7);
                rightArm.setRotation(77 + headPitch, headYaw + 19 + 9 * draw, 11 + 7 * draw);
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
            } else if (pose == MobVisualState.Pose.DRINK && kind == HumanoidKind.WITCH) {
                float lift = Math.min(1f, state.poseAmount() * 4);
                leftArm.setRotation(46 + 69 * lift, -12 + 40 * lift, -18 + 30 * lift);
                rightArm.setRotation(45 - breath, 10, 21);
                weapon.setRotation(-100 * lift, 0, 8 * lift);
            } else if (kind == HumanoidKind.WITCH) {
                leftArm.setRotation(46 + breath, -12, -18);
                rightArm.setRotation(45 - breath, 10, 21);
            } else if (weapon != null) {
                weapon.setRotation(-22, 0, 0);
            }
        }
    }

    /** Dựng Creeper với đầu, thân đứng và bốn chân dùng chung một hình trải. */
    private static MobModel creeper() {
        ModelPart head = skinBox(0, 14, -1, -4.5f, 0, -3.5f, 9, 7, 7, uv(0, 0, 8, 8, 8));
        ModelPart[] legs = animalLegs(3.8f, 5, -3, 3, 3, 4, uv(0, 16, 4, 6, 4));
        ModelPart body = skinBox(0, 0, 0, -3.5f, 4, -2.5f, 7, 11, 5, uv(16, 16, 8, 12, 4),
                head, legs[0], legs[1], legs[2], legs[3]);
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

    /** Dựng hai đốt mỗi chân từ hình trải chân nhện và đặt lớp phát sáng trùng đầu. */
    private static MobModel spider() {
        ModelPart.CuboidUv headUv = uv(32, 4, 8, 8, 8);
        ModelPart head = skinBox(0, 6, -6, -4, -2, -5, 8, 6, 8, headUv,
                shell(-4, -2, -5, 8, 6, 8, headUv, 0.045f, ModelPart.Layer.EYES));
        ModelPart abdomen = skinBox(0, 4, 3, -6.5f, -1, -1, 13, 7, 13, uv(0, 12, 10, 8, 12));
        ModelPart.CuboidUv legUv = uv(18, 0, 16, 2, 2);
        ModelPart[] hips = new ModelPart[8];
        ModelPart[] knees = new ModelPart[8];
        for (int i = 0; i < hips.length; i++) {
            int sign = i < 4 ? 1 : -1;
            float thighX = sign > 0 ? 0 : -7;
            float shinX = sign > 0 ? 0 : -8;
            knees[i] = skinBox(sign * 6, 0, 0, shinX, -1, -1, 8, 2, 2, legUv);
            hips[i] = skinBox(sign * 4, 5, (i % 4 - 1.5f) * 2.3f,
                    thighX, -1, -1, 7, 2, 2, legUv, knees[i]);
        }
        ModelPart thorax = skinBox(0, 0, 0, -4.5f, 3, -4, 9, 7, 8, uv(0, 0, 6, 6, 6),
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

    /** Dựng Enderman với đầu rỗng ở khe miệng và lớp mắt phát sáng trùng hình trải đầu. */
    private static MobModel enderman() {
        ModelPart.CuboidUv headUv = uv(0, 0, 8, 8, 8);
        // Hàm dùng vùng tối đục ở nóc đầu để khe miệng có nền ngay cả khi hàm hạ xuống.
        ModelPart jaw = skinBox(0, 2, 2, -3.5f, -1, -5.5f, 7, 2, 6, uv(8, 0, 4, 3, 3));
        ModelPart head = skinBox(0, 33, -1, -4, 2, -4, 8, 8, 8, headUv, jaw,
                shell(-4, 2, -4, 8, 8, 8, uv(0, 16, 8, 8, 8), -0.5f, ModelPart.Layer.BASE),
                shell(-4, 2, -4, 8, 8, 8, headUv, 0.04f, ModelPart.Layer.EYES));
        ModelPart.CuboidUv rightLimb = uv(56, 0, 2, 30, 2);
        ModelPart.CuboidUv leftLimb = mirrored(56, 0, 2, 30, 2);
        ModelPart leftArm = skinBox(5, 33, 0, -1, -23, -1, 2, 24, 2, leftLimb);
        ModelPart rightArm = skinBox(-5, 33, 0, -1, -23, -1, 2, 24, 2, rightLimb);
        ModelPart leftLeg = skinBox(2, 22, 0, -1, -22, -1, 2, 22, 2, leftLimb);
        ModelPart rightLeg = skinBox(-2, 22, 0, -1, -22, -1, 2, 22, 2, rightLimb);
        ModelPart body = skinBox(0, 0, 0, -4, 21, -2, 8, 14, 4, uv(32, 16, 8, 12, 4),
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

    /** Dựng bốn chân dùng chung hình trải, với cặp trước đứng đầu mảng. */
    private static ModelPart[] animalLegs(float halfWidth, int height, float frontZ, float backZ,
                                          int width, int depth, ModelPart.CuboidUv texture) {
        return new ModelPart[]{
                leg(halfWidth, height, frontZ, width, depth, texture),
                leg(-halfWidth, height, frontZ, width, depth, texture),
                leg(halfWidth, height, backZ, width, depth, texture),
                leg(-halfWidth, height, backZ, width, depth, texture)
        };
    }

    /** Dựng chân xoay từ hông, hạ tới mặt đất trong tư thế đứng. */
    private static ModelPart leg(float x, int height, float z, int width, int depth,
                                 ModelPart.CuboidUv texture, ModelPart... children) {
        return skinBox(x, height, z, -width * 0.5f, -height, -depth * 0.5f,
                width, height, depth, texture, children);
    }

    /** Đánh hai cặp chân chéo luân phiên để giữ dáng đi ổn định. */
    private static void walk(ModelPart[] legs, float angle) {
        legs[0].setRotation(angle, 0, 0);
        legs[1].setRotation(-angle, 0, 0);
        legs[2].setRotation(-angle, 0, 0);
        legs[3].setRotation(angle, 0, 0);
    }

    /** Gắn đầu, chân, đuôi và các vỏ trang trí vào khớp thân. */
    private static ModelPart animalBody(int width, int height, int depth, float bottom, ModelPart.CuboidUv texture,
                                         ModelPart head, ModelPart[] legs, ModelPart tail, ModelPart... accents) {
        int tailCount = tail == null ? 0 : 1;
        ModelPart[] children = new ModelPart[5 + tailCount + accents.length];
        children[0] = head;
        System.arraycopy(legs, 0, children, 1, legs.length);
        if (tail != null) {
            children[5] = tail;
        }
        System.arraycopy(accents, 0, children, 5 + tailCount, accents.length);
        return skinBox(0, 0, 0, -width * 0.5f, bottom, -depth * 0.5f, width, height, depth, texture, children);
    }

    /** Dựng bò ôn đới với vùng ảnh riêng cho mõm, sừng và bầu vú. */
    private static MobModel cow() {
        ModelPart head = skinBox(0, 13, -7, -4, -3, -5, 8, 8, 6, 0, 0,
                skinBox(0, 0, -5, -3, -3, -1, 6, 3, 1, 1, 33),
                skinBox(4, 4, -3, 0, 0, 0, 1, 3, 1, 22, 0),
                skinBox(-4, 4, -3, -1, 0, 0, 1, 3, 1, 22, 0));
        ModelPart[] legs = animalLegs(3.4f, 8, -5.5f, 5.5f, 3, 3, uv(0, 16, 4, 12, 4));
        ModelPart udder = skinBox(0, 0, 0, -2, 6, 2, 4, 1, 5, horizontal(52, 0, 4, 6, 1));
        ModelPart body = animalBody(10, 8, 17, 7, horizontal(18, 4, 12, 18, 10), head, legs, null, udder);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.GRAZER);
    }

    /** Dựng lớp len quanh thân, trán và phần trên của chân từ ảnh wool. */
    private static MobModel sheep() {
        ModelPart.Layer overlay = ModelPart.Layer.OVERLAY;
        ModelPart head = skinBox(0, 12, -6, -3, -3, -5, 6, 6, 7, uv(0, 0, 6, 6, 8),
                shell(-3, -3, -3, 6, 6, 5, uv(0, 0, 6, 6, 6), 0.6f, overlay));
        ModelPart[] legs = new ModelPart[4];
        for (int i = 0; i < legs.length; i++) {
            float x = i % 2 == 0 ? 3.2f : -3.2f;
            float z = i < 2 ? -4.5f : 4.5f;
            legs[i] = leg(x, 7, z, 3, 3, uv(0, 16, 4, 12, 4),
                    shell(-1.5f, -3.5f, -1.5f, 3, 4, 3, uv(0, 16, 4, 6, 4), 0.45f, overlay));
        }
        ModelPart.CuboidUv bodyUv = horizontal(28, 8, 8, 16, 6);
        ModelPart wool = shell(-4.5f, 6, -7, 9, 7, 14, bodyUv, 1f, overlay);
        ModelPart body = animalBody(9, 7, 14, 6, bodyUv, head, legs, null, wool);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.GRAZER);
    }

    /** Dựng lợn ôn đới với mõm riêng nằm trước mặt. */
    private static MobModel pig() {
        ModelPart head = skinBox(0, 10, -5, -3.5f, -3, -5, 7, 7, 7, uv(0, 0, 8, 8, 8),
                skinBox(0, 0, -5, -2, -3, -1, 4, 3, 1, 16, 16));
        ModelPart[] legs = animalLegs(3.3f, 6, -4.5f, 4.5f, 3, 3, uv(0, 16, 4, 6, 4));
        ModelPart body = animalBody(9, 7, 15, 5, horizontal(28, 8, 10, 16, 8), head, legs, null);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.GRAZER);
    }

    /** Dựng dê với mõm dài, sừng, râu phẳng và hình trải riêng cho từng chân. */
    private static MobModel goat() {
        ModelPart head = skinBox(0, 13, -6, -2.5f, -3, -6, 5, 7, 7, uv(34, 46, 5, 7, 10),
                skinBox(-1.9f, 3, -2, -1, 0, -1, 2, 7, 2, 12, 55),
                skinBox(1.9f, 3, -2, -1, 0, -1, 2, 7, 2, 12, 55),
                skinBox(-3.5f, 1, -2, -1.5f, 0, 0, 3, 2, 1, 2, 61),
                skinBox(3.5f, 1, -2, -1.5f, 0, 0, 3, 2, 1, mirrored(2, 61, 3, 2, 1)),
                skinBox(0, -2, -3, 0, -4, -3, 0, 4, 4, uv(23, 52, 0, 7, 5)));
        ModelPart[] legs = {
                leg(3, 8, -4.5f, 3, 3, uv(49, 2, 3, 10, 3)),
                leg(-3, 8, -4.5f, 3, 3, uv(35, 2, 3, 10, 3)),
                leg(3, 8, 4.5f, 3, 3, uv(36, 29, 3, 6, 3)),
                leg(-3, 8, 4.5f, 3, 3, uv(49, 29, 3, 6, 3))
        };
        ModelPart shoulder = skinBox(0, 0, 0, -4.5f, 7, -6, 9, 9, 8, uv(0, 28, 11, 14, 11));
        ModelPart body = animalBody(8, 8, 14, 7, uv(1, 1, 9, 11, 16), head, legs, null, shoulder);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.GOAT);
    }

    /** Dựng gấu với vai cao, chân trước và sau dùng hai vùng ảnh riêng. */
    private static MobModel polarBear() {
        ModelPart head = skinBox(0, 15, -8, -3.5f, -3, -4, 7, 7, 7, 0, 0,
                skinBox(0, 0, -4, -2.5f, -3, -3, 5, 3, 3, 0, 44),
                skinBox(3.2f, 3, 0, -1, 0, -0.5f, 2, 2, 1, 26, 0),
                skinBox(-3.2f, 3, 0, -1, 0, -0.5f, 2, 2, 1, mirrored(26, 0, 2, 2, 1)));
        ModelPart[] legs = {
                leg(4.2f, 9, -7, 4, 5, uv(50, 22, 4, 10, 8)),
                leg(-4.2f, 9, -7, 4, 5, uv(50, 22, 4, 10, 8)),
                leg(4.2f, 9, BEAR_HIND_HIP_DEPTH, 4, 4, uv(50, 40, 4, 10, 6)),
                leg(-4.2f, 9, BEAR_HIND_HIP_DEPTH, 4, 4, uv(50, 40, 4, 10, 6))
        };
        ModelPart shoulder = skinBox(0, 0, -5, -5.5f, 8, -5, 11, 11, 10, horizontal(39, 0, 12, 12, 10));
        ModelPart body = animalBody(12, 9, 14, 8, horizontal(0, 19, 14, 14, 11), head, legs, null, shoulder);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.BEAR);
    }

    /** Dựng thỏ với tai và bàn chân trái, phải dùng các vùng ảnh độc lập. */
    private static MobModel rabbit() {
        ModelPart head = skinBox(0, 7, -3, -2.5f, -1, -3, 5, 4, 5, 32, 0,
                skinBox(1.5f, 3, 0, -1, 0, -0.5f, 2, 6, 1, 58, 0),
                skinBox(-1.5f, 3, 0, -1, 0, -0.5f, 2, 6, 1, 52, 0),
                skinBox(0, 0, -3, -0.5f, 0, -0.5f, 1, 1, 1, 32, 9));
        ModelPart[] legs = {
                leg(2.2f, 4, -2.7f, 2, 2, uv(8, 15, 2, 7, 2)),
                leg(-2.2f, 4, -2.7f, 2, 2, uv(0, 15, 2, 7, 2)),
                leg(2.6f, 4, 2.6f, 2, 5, uv(30, 15, 2, 4, 5),
                        skinBox(0, -4, 0, -1, 0, -4, 2, 1, 6, uv(26, 24, 2, 1, 7))),
                leg(-2.6f, 4, 2.6f, 2, 5, uv(16, 15, 2, 4, 5),
                        skinBox(0, -4, 0, -1, 0, -4, 2, 1, 6, uv(8, 24, 2, 1, 7)))
        };
        ModelPart tail = skinBox(0, 6.5f, 5, -1.5f, -2, 0, 3, 3, 2, 52, 6);
        ModelPart body = animalBody(6, 5, 10, 3, uv(0, 0, 6, 5, 10), head, legs, tail);
        return new QuadrupedModel(body, head, tail, legs, AnimalKind.RABBIT);
    }

    /** Dựng sói tuyết với vùng riêng ở mõm, ngực và đuôi. */
    private static MobModel wolf() {
        ModelPart head = skinBox(0, 12, -5.8f, -3, -3, -3, 6, 6, 4, 0, 0,
                skinBox(0, -3, -2.5f, -1.5f, 0, -3.5f, 3, 3, 4, 0, 10),
                skinBox(2, 3, 0, -1, 0, -0.5f, 2, 2, 1, 16, 14),
                skinBox(-2, 3, 0, -1, 0, -0.5f, 2, 2, 1, 16, 14));
        ModelPart[] legs = animalLegs(2.7f, 7, -4.8f, 4.8f, 2, 2, uv(0, 18, 2, 8, 2));
        ModelPart tail = skinBox(0, 11.5f, 6.5f, -1, -2, 0, 2, 2, 8, horizontal(9, 18, 2, 8, 2));
        ModelPart chest = skinBox(0, 0, 0, -4, 6, -7, 8, 7, 5, horizontal(21, 0, 8, 6, 7));
        ModelPart body = animalBody(6, 6, 10, 6, horizontal(18, 14, 6, 9, 6), head, legs, tail, chest);
        return new QuadrupedModel(body, head, tail, legs, AnimalKind.WOLF);
    }

    /** Dựng cáo tuyết dùng chung UV cho ảnh thức và ảnh ngủ. */
    private static MobModel fox() {
        ModelPart head = skinBox(0, 7.5f, -4.5f, -4, -2.5f, -3.5f, 8, 6, 6, 1, 5,
                skinBox(0, -2.5f, -3.5f, -2, 0, -3, 4, 2, 3, 6, 18),
                skinBox(3, 3, 0, -1, 0, -0.5f, 2, 2, 1, 15, 1),
                skinBox(-3, 3, 0, -1, 0, -0.5f, 2, 2, 1, 8, 1));
        ModelPart.CuboidUv left = uv(13, 24, 2, 6, 2);
        ModelPart.CuboidUv right = uv(4, 24, 2, 6, 2);
        ModelPart[] legs = {
                leg(2.5f, 4, -4, 2, 2, left), leg(-2.5f, 4, -4, 2, 2, right),
                leg(2.5f, 4, 4, 2, 2, left), leg(-2.5f, 4, 4, 2, 2, right)
        };
        ModelPart tail = skinBox(0, 6.5f, 5.5f, -2, -2, 0, 4, 5, 10, horizontal(30, 0, 4, 9, 5));
        ModelPart body = animalBody(6, 6, 12, 3, horizontal(24, 15, 6, 11, 6), head, legs, tail);
        return new QuadrupedModel(body, head, tail, legs, AnimalKind.FOX);
    }

    /** Dựng ngựa và lừa chung hình trải, dùng vùng tai dài của riêng lừa. */
    private static MobModel equine(boolean donkey) {
        ModelPart.CuboidUv earUv = donkey ? uv(0, 12, 2, 7, 1) : uv(19, 16, 2, 3, 1);
        ModelPart upperHead = equineBox(donkey, 0, 8, -2, -3, -2, -4, 6, 6, 7, uv(0, 13, 6, 5, 7),
                equineBox(donkey, 0, 0, -4, -2, -2, -5, 4, 5, 5, uv(0, 25, 4, 5, 5)),
                skinBox(2, 4, 1, -1, 0, 0, 2, donkey ? 7 : 3, 1, earUv),
                skinBox(-2, 4, 1, -1, 0, 0, 2, donkey ? 7 : 3, 1, earUv));
        ModelPart[] neckParts = donkey
                ? new ModelPart[]{upperHead}
                : new ModelPart[]{upperHead,
                        skinBox(0, 0, 0, -1, 0, 1, 2, 13, 2, uv(56, 36, 2, 16, 2))};
        ModelPart neck = equineBox(donkey, 0, 16, -7, -2, -2, -3, 4, 11, 7,
                uv(0, 35, 4, 12, 7), neckParts);
        int height = donkey ? 12 : 13;
        ModelPart[] legs = new ModelPart[4];
        for (int i = 0; i < legs.length; i++) {
            boolean left = i % 2 == 0;
            float z = i < 2 ? -6 : EQUINE_HIND_HIP_DEPTH;
            ModelPart.CuboidUv foot = left ? mirrored(48, 21, 4, 11, 4) : uv(48, 21, 4, 11, 4);
            legs[i] = equineBox(donkey, left ? 3.8f : -3.8f, height, z, -1.5f, -height, -2,
                    3, height, 4, foot);
        }
        ModelPart tail = skinBox(0, 18, 8.5f, -1.5f, -10, 0, 3, 10, 4, uv(42, 36, 3, 14, 4));
        ModelPart.CuboidUv bodyUv = uv(0, 32, 10, 10, 22);
        ModelPart body = donkey
                ? animalBody(9, 9, 17, 11, bodyUv, neck, legs, tail)
                : animalBody(10, 10, 19, 12, bodyUv, neck, legs, tail,
                        shell(-5, 12, -9.5f, 10, 10, 19, bodyUv, 0.09f, ModelPart.Layer.OVERLAY));
        return new QuadrupedModel(body, neck, tail, legs, AnimalKind.EQUINE);
    }

    /** Thêm lớp đốm ngựa lên đúng hình học và UV của từng bộ phận. */
    private static ModelPart equineBox(boolean donkey, float x, float y, float z, float dx, float dy, float dz,
                                       int width, int height, int depth, ModelPart.CuboidUv texture,
                                       ModelPart... children) {
        if (donkey) {
            return skinBox(x, y, z, dx, dy, dz, width, height, depth, texture, children);
        }
        ModelPart[] layers = new ModelPart[children.length + 1];
        System.arraycopy(children, 0, layers, 0, children.length);
        layers[children.length] = shell(dx, dy, dz, width, height, depth, texture, 0.08f, ModelPart.Layer.OVERLAY);
        return skinBox(x, y, z, dx, dy, dz, width, height, depth, texture, layers);
    }

    /** Dựng lạc đà thường và xác sống theo cùng bố cục thân, bướu và bốn chân. */
    private static MobModel camel() {
        ModelPart head = skinBox(0, 4, -6, -3.5f, 0, -3.5f, 7, 13, 7, uv(21, 0, 7, 14, 7),
                skinBox(0, 8, -3.5f, -2.5f, 0, -6, 5, 5, 6, 50, 0),
                skinBox(3, 11, 2, -0.5f, 0, -1, 3, 1, 2, 45, 0),
                skinBox(-3, 11, 2, -2.5f, 0, -1, 3, 1, 2, 67, 0));
        ModelPart neck = skinBox(0, 20, -6, -3.5f, -2, -9, 7, 7, 16, uv(60, 24, 7, 8, 19), head);
        ModelPart hump = skinBox(0, 26, -1, -4.5f, 0, -5.5f, 9, 5, 11, 74, 0);
        ModelPart[] legs = {
                leg(3.8f, 18, -6.5f, 4, 4, uv(0, 0, 5, 21, 5)),
                leg(-3.8f, 18, -6.5f, 4, 4, uv(0, 26, 5, 21, 5)),
                leg(3.8f, 18, 6.5f, 4, 4, uv(58, 16, 5, 21, 5)),
                leg(-3.8f, 18, 6.5f, 4, 4, uv(94, 16, 5, 21, 5))
        };
        ModelPart tail = skinBox(0, 22, 9, -1.5f, -9, 0, 3, 9, 0, uv(122, 0, 3, 14, 0));
        ModelPart body = animalBody(10, 9, 18, 17, uv(0, 25, 15, 12, 27), neck, legs, tail, hump);
        return new QuadrupedModel(body, neck, tail, legs, AnimalKind.CAMEL);
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

        /** Khối ngậm theo khớp đầu khi cáo nhìn hoặc cuộn mình ngủ. */
        @Override
        ModelPart heldBlockPart() {
            return kind == AnimalKind.FOX ? head : null;
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
                    } else if (kind == AnimalKind.GOAT) {
                        if (entity.isOnGround()) {
                            body.setTranslation(0, -1.8f * amount, 0);
                            head.setRotation(-14f * amount, headYaw, 0);
                            foldLegs(24f * amount, -32f * amount);
                        } else {
                            body.setRotation(12f * amount, 0, 0);
                            foldLegs(43f * amount, -38f * amount);
                        }
                    }
                }
                case TILT_HEAD -> {
                    if (kind == AnimalKind.WOLF) {
                        head.setRotation(headPitch, headYaw, 32 * amount);
                    }
                }
                case SHAKE -> {
                    if (kind == AnimalKind.WOLF) {
                        float roll = MathUtils.sin(ageInTicks * 1.9f) * 18f * amount;
                        body.setRotation(0, 0, roll);
                        head.setRotation(headPitch, headYaw, -roll * 1.4f);
                        tail.setRotation(8, 0, roll);
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
                case CROUCH -> {
                    if (kind == AnimalKind.FOX) {
                        body.setTranslation(0, -2 * amount, 0);
                        foldLegs(42 * amount, -48 * amount);
                        tail.setRotation(11 * amount, MathUtils.sin(ageInTicks * 0.8f) * 24 * amount, 0);
                    }
                }
                case FACEPLANT -> {
                    if (kind == AnimalKind.FOX) {
                        body.setRotation(-35 * amount, 0, 0);
                        body.setTranslation(0, -2 * amount, 0);
                        head.setRotation(-52 * amount, 0, 0);
                        foldLegs(78 * amount, -24 * amount);
                        tail.setRotation(-60 * amount, 0, 0);
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

    /** Dựng gà ôn đới với mỏ, yếm và hai cánh độc lập. */
    private static MobModel chicken() {
        ModelPart head = skinBox(0, 10, -3, -2, -1, -2.5f, 4, 6, 3, 0, 0,
                skinBox(0, 0, -2.5f, -2, 1, -2, 4, 2, 2, 14, 0),
                skinBox(0, 0, -2.5f, -1, -1, -1, 2, 2, 2, 14, 4));
        ModelPart rightWing = skinBox(-3, 8, 0, -1, -3, -3, 1, 4, 6, 24, 13);
        ModelPart leftWing = skinBox(3, 8, 0, 0, -3, -3, 1, 4, 6, 24, 13);
        ModelPart rightLeg = skinBox(-1.7f, 5, 0, -1.5f, -5, -2, 3, 5, 3, 26, 0);
        ModelPart leftLeg = skinBox(1.7f, 5, 0, -1.5f, -5, -2, 3, 5, 3, 26, 0);
        ModelPart body = skinBox(0, 0, 0, -3, 5, -4, 6, 6, 8, horizontal(0, 9, 6, 8, 6),
                head, rightWing, leftWing, rightLeg, leftLeg);
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
