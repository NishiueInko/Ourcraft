package com.voxelgame.engine;

import com.badlogic.gdx.math.MathUtils;
import com.voxelgame.entity.LivingEntity;

/**
 * Khung khớp chuẩn Minecraft theo từng loài (tỉ lệ texel 1:1).
 * Tọa độ đo bằng pixel (16 pixel = 1 block), với chân trên Y=0 và hướng nhìn trung tính dọc -Z.
 */
public final class MobModels {
    /** Hai vị trí hông sau cần được bù khi thân dựng lên trên hai chân. */
    private static final float BEAR_HIND_HIP_DEPTH = 7f;
    private static final float EQUINE_HIND_HIP_DEPTH = 9f;

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

    /** Dựng hộp khớp đúng 1:1 kích thước hình học và vùng trải UV Minecraft. */
    private static ModelPart skinBox(float x, float y, float z, float dx, float dy, float dz,
                                     int width, int height, int depth, int u, int v, ModelPart... children) {
        return skinBox(x, y, z, dx, dy, dz, width, height, depth, uv(u, v, width, height, depth), children);
    }

    /** Dựng hộp có chiều trải UV chỉ định (chuẩn, nằm ngang hoặc đối xứng gương). */
    private static ModelPart skinBox(float x, float y, float z, float dx, float dy, float dz,
                                     int width, int height, int depth, ModelPart.CuboidUv texture,
                                     ModelPart... children) {
        return new ModelPart(x, y, z, dx, dy, dz, width, height, depth, 0f, texture, children);
    }

    /** Dựng mặt phẳng sprite 2D vẽ cả hai mặt cho cung, giáo và lọ thuốc cầm tay. */
    private static ModelPart flatSprite(float x, float y, float z, float dx, float dy, float dz,
                                        int width, int height, int u, int v, int texW, int texH,
                                        ModelPart... children) {
        ModelPart.CuboidUv spriteUv = new ModelPart.CuboidUv(u, v, texW, texH, 0, ModelPart.UvOrientation.FLAT_SPRITE);
        return new ModelPart(x, y, z, dx, dy, dz, width, height, 0, 0f, spriteUv,
                ModelPart.Shape.FLAT_SPRITE, children).setDoubleSided(true);
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

    /** Dựng tay chân, đầu và các lớp áo theo bố cục chuẩn Minecraft của từng nhóm hình người. */
    private static MobModel humanoid(HumanoidKind kind) {
        boolean bones = kind == HumanoidKind.SKELETON || kind == HumanoidKind.PARCHED || kind == HumanoidKind.STRAY;
        boolean villager = kind == HumanoidKind.VILLAGER || kind == HumanoidKind.WITCH;
        int limbSize = bones ? 2 : 4;
        int torsoDepth = villager ? 6 : 4;
        ModelPart weapon = switch (kind) {
            case SKELETON, STRAY, PARCHED -> bow();
            case HUSK -> spear();
            case WITCH -> witchPotion();
            default -> null;
        };
        ModelPart head = humanoidHead(kind);
        if (kind == HumanoidKind.WITCH) {
            // Witch dùng cặp tay khoanh liền khối kiểu Villager, nâng lên mũi khi uống thuốc.
            ModelPart crossedArms = witchCrossedArms(weapon);
            ModelPart rightLeg = humanoidLimb(kind, false, 2, 12, limbSize, null);
            ModelPart leftLeg = humanoidLimb(kind, false, -2, 12, limbSize, null);
            ModelPart.CuboidUv bodyUv = uv(16, 20, 8, 12, 6);
            ModelPart coat = shell(-4, 4, -3, 8, 20, 6, uv(0, 38, 8, 20, 6), 0.5f, ModelPart.Layer.BASE);
            ModelPart body = skinBox(0, 0, 0, -4, 12, -3, 8, 12, 6, bodyUv,
                    head, crossedArms, leftLeg, rightLeg, coat);
            return new HumanoidModel(body, head, crossedArms, null, rightLeg, leftLeg, weapon, kind);
        }

        // Trong hệ +Y lên / -Z trước: +X là bên phải của mob (trái màn hình), -X là bên trái của mob.
        ModelPart rightArm = humanoidLimb(kind, true, 5, 22, limbSize, weapon);
        ModelPart leftArm = humanoidLimb(kind, true, -5, 22, limbSize, null);
        ModelPart rightLeg = humanoidLimb(kind, false, 2, 12, limbSize, null);
        ModelPart leftLeg = humanoidLimb(kind, false, -2, 12, limbSize, null);
        ModelPart.CuboidUv bodyUv = uv(16, villager ? 20 : 16, 8, 12, torsoDepth);
        ModelPart coat = switch (kind) {
            case STRAY -> shell(-4, 12, -2, 8, 12, 4, bodyUv, 0.25f, ModelPart.Layer.OVERLAY);
            case PARCHED -> shell(-4, 12, -2, 8, 12, 4, uv(16, 48, 8, 12, 4), 0.25f, ModelPart.Layer.BASE);
            default -> null;
        };
        ModelPart[] parts = coat == null
                ? new ModelPart[]{head, rightArm, leftArm, rightLeg, leftLeg}
                : new ModelPart[]{head, rightArm, leftArm, rightLeg, leftLeg, coat};
        ModelPart body = skinBox(0, 0, 0, -4, 12, -torsoDepth * 0.5f, 8, 12, torsoDepth, bodyUv, parts);
        if (bones) {
            // Lồng ngực xương có lỗ hở alpha ở khe sườn nên vẽ cả hai mặt.
            body.setDoubleSided(true);
        }
        return new HumanoidModel(body, head, rightArm, leftArm, rightLeg, leftLeg, weapon, kind);
    }

    /** Gắn mũ nghiêng 4 tầng, mũi có nốt ruồi hoặc lớp trùm đầu khớp với đúng vùng ảnh nguồn. */
    private static ModelPart humanoidHead(HumanoidKind kind) {
        ModelPart.CuboidUv headUv = uv(0, 0, 8, 8, 8);
        return switch (kind) {
            case WITCH -> {
                ModelPart tip = skinBox(0.75f, 3.5f, 0.75f, -0.5f, 0, -0.5f, 1, 2, 1, 0, 95)
                        .setBaseRotation(-12f, 0f, 6f);
                ModelPart tier2 = skinBox(0.75f, 4, 0.25f, -2, 0, -2, 4, 4, 4, 0, 87, tip)
                        .setBaseRotation(-6f, 0f, 3f);
                ModelPart tier1 = skinBox(0.25f, 2, 0, -3.5f, 0, -3.5f, 7, 4, 7, 0, 76, tier2)
                        .setBaseRotation(-3f, 0f, 1.5f);
                ModelPart brim = skinBox(0, 9, 0, -5, 0, -5, 10, 2, 10, 0, 64, tier1);
                ModelPart mole = skinBox(0.25f, -1.5f, -1.8f, -0.5f, 0, -0.5f, 1, 1, 1, 0, 0);
                ModelPart nose = skinBox(0, 1, -4, -1, -2, -2, 2, 4, 2, 24, 0, mole);
                yield skinBox(0, 24, 0, -4, 0, -4, 8, 10, 8, 0, 0, nose, brim);
            }
            case VILLAGER -> skinBox(0, 24, 0, -4, 0, -4, 8, 10, 8, 0, 0,
                    skinBox(0, 3, -4, -1, -2, -2, 2, 4, 2, 24, 0));
            case STRAY -> skinBox(0, 24, 0, -4, 0, -4, 8, 8, 8, headUv,
                    shell(-4, 0, -4, 8, 8, 8, headUv, 0.25f, ModelPart.Layer.OVERLAY));
            case PARCHED -> skinBox(0, 24, 0, -4, 0, -4, 8, 8, 8, headUv,
                    shell(-4, 0, -4, 8, 8, 8, uv(0, 32, 8, 8, 8), 0.25f, ModelPart.Layer.BASE),
                    shell(-4, 0, -4, 8, 8, 8, uv(32, 0, 8, 8, 8), 0.5f, ModelPart.Layer.BASE));
            default -> skinBox(0, 24, 0, -4, 0, -4, 8, 8, 8, headUv);
        };
    }

    /** Dựng cặp tay khoanh trước ngực chuẩn Villager/Witch cùng vị trí cầm lọ thuốc. */
    private static ModelPart witchCrossedArms(ModelPart potion) {
        ModelPart rightUpper = skinBox(0, 0, 0, 4, -8, -2, 4, 8, 4, uv(44, 22, 4, 8, 4));
        ModelPart leftUpper = skinBox(0, 0, 0, -8, -8, -2, 4, 8, 4, mirrored(44, 22, 4, 8, 4));
        ModelPart forearms = skinBox(0, 0, 0, -4, -8, -2, 8, 4, 4, uv(40, 38, 8, 4, 4));
        return new ModelPart(0, 21, -1, 0, 0, 0, 0, 0, 0, 0f, uv(40, 38, 0, 0, 0),
                rightUpper, leftUpper, forearms, potion).setBaseRotation(43f, 0f, 0f);
    }

    /** Tạo chi chuẩn Minecraft và gắn lớp vải ngoài theo đúng chi (+X là chi phải, -X là chi trái). */
    private static ModelPart humanoidLimb(HumanoidKind kind, boolean arm, int x, int y,
                                          int size, ModelPart weapon) {
        boolean left = x < 0;
        boolean villager = kind == HumanoidKind.VILLAGER || kind == HumanoidKind.WITCH;
        int u = arm ? (villager ? 44 : 40) : 0;
        int v = villager ? 22 : 16;
        float minY = arm ? -10f : -12f;
        float side = -size * 0.5f;
        ModelPart.CuboidUv texture = left ? mirrored(u, v, size, 12, size) : uv(u, v, size, 12, size);
        ModelPart clothes = null;
        if (kind == HumanoidKind.STRAY) {
            ModelPart.CuboidUv clothUv = left ? mirrored(u, v, 4, 12, 4) : uv(u, v, 4, 12, 4);
            clothes = shell(-2f, minY, -2f, 4, 12, 4, clothUv, 0.25f, ModelPart.Layer.OVERLAY);
        } else if (kind == HumanoidKind.PARCHED) {
            if (arm && left) {
                texture = uv(56, 16, 2, 12, 2);
            }
            ModelPart.CuboidUv clothUv;
            if (arm) {
                clothUv = left ? uv(40, 48, 4, 12, 4) : uv(40, 32, 4, 12, 4);
            } else {
                clothUv = left ? mirrored(0, 48, 4, 12, 4) : uv(0, 48, 4, 12, 4);
            }
            clothes = shell(-2f, minY, -2f, 4, 12, 4, clothUv, 0.25f, ModelPart.Layer.BASE);
        }
        ModelPart[] attachments;
        if (weapon != null && clothes != null) {
            attachments = new ModelPart[]{weapon, clothes};
        } else if (weapon != null || clothes != null) {
            attachments = new ModelPart[]{weapon != null ? weapon : clothes};
        } else {
            attachments = new ModelPart[0];
        }
        float minX = arm && size == 4 ? side - Math.signum(x) : side;
        return skinBox(x, y, 0, minX, minY, side, size, 12, size, texture, attachments);
    }

    /** Bốn hình cung 16×16 chuẩn Minecraft dùng cùng điểm cầm ở tay phải. */
    private static ModelPart bow() {
        ModelPart[] frames = new ModelPart[4];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = flatSprite(0, 0, 0, -7, -7, 0, 14, 14, i * 32, 64, 16, 16)
                    .setLayer(ModelPart.Layer.EQUIPMENT);
        }
        return new ModelPart(0, -9, -1, 0, 0, 0, 0, 0, 0, 0f, uv(0, 64, 0, 0, 0), frames)
                .setLayer(ModelPart.Layer.EQUIPMENT);
    }

    /** Giáo sắt chuẩn Minecraft lấy từ sprite 16×16 trên tấm trang bị held.png. */
    private static ModelPart spear() {
        return flatSprite(0, -9, -1, -6, -6, 0, 12, 12, 0, 0, 16, 16)
                .setBaseRotation(45f, 90f, 0f)
                .setLayer(ModelPart.Layer.EQUIPMENT);
    }

    /** Lọ thuốc cầm tay của Witch lấy từ sprite 16×16 trên tấm trang bị held.png. */
    private static ModelPart witchPotion() {
        return flatSprite(0, -7, -3, -3.5f, -3.5f, 0, 7, 7, 32, 0, 16, 16)
                .setLayer(ModelPart.Layer.EQUIPMENT);
    }

    /** Hoạt ảnh hình người có vòng bước, bắn cung, đâm giáo và tư thế uống thuốc Witch. */
    private static final class HumanoidModel extends MobModel {
        /** Các khớp cần thay đổi mỗi khung và phụ kiện có góc riêng. */
        private final ModelPart head;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;
        private final ModelPart weapon;
        private final HumanoidKind kind;

        /** Giữ các khớp đã dựng để cập nhật hoạt ảnh mà không cấp phát thêm đối tượng. */
        private HumanoidModel(ModelPart body, ModelPart head, ModelPart rightArm, ModelPart leftArm,
                              ModelPart rightLeg, ModelPart leftLeg, ModelPart weapon, HumanoidKind kind) {
            super(body);
            this.head = head;
            this.rightArm = rightArm;
            this.leftArm = leftArm;
            this.rightLeg = rightLeg;
            this.leftLeg = leftLeg;
            this.weapon = weapon;
            this.kind = kind;
        }

        /** Thay vòng vung tay bằng động tác giơ tay, bắn cung, đâm giáo hoặc uống thuốc Witch. */
        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            MobVisualState state = visualState(entity);
            MobVisualState.Pose pose = pose(state);
            float armSwing = gait(limbSwing, limbSwingAmount, MathUtils.radiansToDegrees);
            float step = armSwing * 1.4f;
            float breath = (float) Math.sin(ageInTicks * 0.13f) * 3;
            head.setRotation(headPitch, headYaw, 0);
            rightLeg.setRotation(-step, 0, 0);
            leftLeg.setRotation(step, 0, 0);

            if (kind == HumanoidKind.WITCH) {
                boolean drinking = pose == MobVisualState.Pose.DRINK;
                float lift = drinking ? Math.min(1f, state.poseAmount() * 4f) : 0f;
                rightArm.setRotation(43f + 50f * lift, 0f, 0f);
                weapon.setVisible(drinking);
                if (drinking) {
                    weapon.setTranslation(0f, -1f * lift, -1f * lift);
                }
                return;
            }

            rightArm.setRotation(armSwing, 0, -3);
            leftArm.setRotation(-armSwing, 0, 3);
            if (kind == HumanoidKind.SKELETON || kind == HumanoidKind.STRAY || kind == HumanoidKind.PARCHED) {
                float draw = state == null ? 0f : state.poseAmount();
                int frame = pose != MobVisualState.Pose.AIM_BOW || draw <= 0f ? 0
                        : draw < 0.65f ? 1 : draw < 0.9f ? 2 : 3;
                ModelPart[] frames = weapon.children();
                for (int i = 0; i < frames.length; i++) {
                    frames[i].setVisible(i == frame);
                    frames[i].setRotation(0, 0, -40);
                }
            }
            if (kind == HumanoidKind.HUSK) {
                weapon.setVisible(pose == MobVisualState.Pose.THRUST);
            }
            if (state != null && state.bowAim() > 0f && (kind == HumanoidKind.SKELETON
                    || kind == HumanoidKind.STRAY || kind == HumanoidKind.PARCHED)) {
                float aim = state.bowAim();
                float bob = MathUtils.sin(ageInTicks * 0.067f) * 2.86f;
                rightArm.setRotation(MathUtils.lerp(armSwing, 90 + headPitch + bob, aim),
                        (headYaw - 5.73f) * aim, -3f * (1f - aim));
                leftArm.setRotation(MathUtils.lerp(-armSwing, 90 + headPitch - bob, aim),
                        (headYaw + 28.65f) * aim, 3f * (1f - aim));
                weapon.setRotation(-22f * (1f - aim), 90f * aim, 0);
            } else if (pose == MobVisualState.Pose.THRUST) {
                float extension = state.poseAmount();
                rightArm.setRotation(65 + extension * 32 + headPitch, headYaw, 8);
                leftArm.setRotation(25 - extension * 34, -10, 0);
            } else if (pose == MobVisualState.Pose.UNDEAD || kind == HumanoidKind.ZOMBIE
                    || kind == HumanoidKind.VILLAGER || kind == HumanoidKind.HUSK) {
                float lift = entity instanceof com.voxelgame.entity.mob.MobEntity mob && mob.target() != null
                        ? 90f : 45f;
                rightArm.setRotation(lift + breath, -5.73f, 0);
                leftArm.setRotation(lift - breath, 5.73f, 0);
            } else if (weapon != null) {
                weapon.setRotation(-22, 0, 0);
            }
        }
    }

    /** Dựng Creeper chuẩn Minecraft cao 26 pixel với đầu, thân và bốn chân 4×6×4. */
    private static MobModel creeper() {
        ModelPart head = skinBox(0, 18, 0, -4, 0, -4, 8, 8, 8, uv(0, 0, 8, 8, 8));
        ModelPart[] legs = animalLegs(2, 6, -4, 4, 4, 4, uv(0, 16, 4, 6, 4));
        ModelPart body = skinBox(0, 0, 0, -4, 6, -2, 8, 12, 4, uv(16, 16, 8, 12, 4),
                head, legs[0], legs[1], legs[2], legs[3]);
        return new CreeperModel(body, head, legs);
    }

    /** Lắc bốn chân Creeper đối pha trong khi đầu vẫn quan sát mục tiêu. */
    private static final class CreeperModel extends MobModel {
        private final ModelPart head;
        private final ModelPart[] legs;

        private CreeperModel(ModelPart body, ModelPart head, ModelPart[] legs) {
            super(body);
            this.head = head;
            this.legs = legs;
        }

        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            head.setRotation(headPitch, headYaw, 0);
            walk(legs, gait(limbSwing, limbSwingAmount, 1.4f * MathUtils.radiansToDegrees));
        }
    }

    /** Dựng Nhện chuẩn Minecraft với đầu 8×8×8, ngực 6×6×6, bụng 10×8×12 và 8 chân 1 đốt 16×2×2. */
    private static MobModel spider() {
        ModelPart.CuboidUv headUv = uv(32, 4, 8, 8, 8);
        ModelPart head = skinBox(0, 9, -3, -4, -4, -8, 8, 8, 8, headUv,
                shell(-4, -4, -8, 8, 8, 8, headUv, 0.05f, ModelPart.Layer.EYES));
        ModelPart abdomen = skinBox(0, 9, 3, -5, -4, 0, 10, 8, 12, uv(0, 12, 10, 8, 12));
        ModelPart.CuboidUv rightLegUv = uv(18, 0, 16, 2, 2);
        ModelPart.CuboidUv leftLegUv = mirrored(18, 0, 16, 2, 2);
        ModelPart[] legs = new ModelPart[8];
        for (int i = 0; i < 4; i++) {
            float z = (i - 1.5f) * 1.5f;
            legs[i] = skinBox(3, 8, z, 0, -1, -1, 16, 2, 2, rightLegUv);
            legs[4 + i] = skinBox(-3, 8, z, -16, -1, -1, 16, 2, 2, leftLegUv);
        }
        ModelPart thorax = skinBox(0, 0, 0, -3, 6, -3, 6, 6, 6, uv(0, 0, 6, 6, 6),
                head, abdomen, legs[0], legs[1], legs[2], legs[3], legs[4], legs[5], legs[6], legs[7]);
        return new SpiderModel(thorax, head, abdomen, legs);
    }

    /** Xếp tám chân nhện hình quạt chuẩn Minecraft, dập nhẹ theo bước chạy. */
    private static final class SpiderModel extends MobModel {
        private final ModelPart head;
        private final ModelPart abdomen;
        private final ModelPart[] legs;

        private SpiderModel(ModelPart thorax, ModelPart head, ModelPart abdomen, ModelPart[] legs) {
            super(thorax);
            this.head = head;
            this.abdomen = abdomen;
            this.legs = legs;
        }

        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            head.setRotation(headPitch * 0.65f, headYaw * 0.7f, 0);
            abdomen.setRotation(gait(limbSwing * 2f, limbSwingAmount, 4f), 0, 0);
            for (int i = 0; i < legs.length; i++) {
                int side = i < 4 ? 1 : -1;
                int row = i % 4;
                float baseYaw = switch (row) {
                    case 0 -> 45f;
                    case 1 -> 22.5f;
                    case 2 -> -22.5f;
                    default -> -45f;
                };
                float phase = limbSwing * 0.6662f + row * (float) Math.PI * 0.5f + (side < 0 ? (float) Math.PI : 0f);
                float yawWave = (float) Math.cos(phase) * limbSwingAmount * 14f;
                float liftWave = Math.max(0f, (float) Math.sin(phase) * limbSwingAmount * 18f);
                legs[i].setRotation(0f, side * baseYaw + yawWave, side * (-36f + liftWave));
            }
        }
    }

    /** Dựng Enderman chuẩn Minecraft với đầu ngoài trượt lên để lộ hộp hàm trong (0,16) khi giận. */
    private static MobModel enderman() {
        ModelPart.CuboidUv headUv = uv(0, 0, 8, 8, 8);
        ModelPart outerHead = skinBox(0, 0, 0, -4, 0, -4, 8, 8, 8, headUv,
                shell(-4, 0, -4, 8, 8, 8, headUv, 0.04f, ModelPart.Layer.EYES));
        ModelPart jaw = shell(-4, 0, -4, 8, 8, 8, uv(0, 16, 8, 8, 8), -0.5f, ModelPart.Layer.BASE);
        ModelPart headGroup = new ModelPart(0, 40, 0, 0, 0, 0, 0, 0, 0, 0f, headUv, jaw, outerHead);

        ModelPart.CuboidUv rightLimb = uv(56, 0, 2, 30, 2);
        ModelPart.CuboidUv leftLimb = mirrored(56, 0, 2, 30, 2);
        ModelPart rightArm = skinBox(5, 38, 0, -1, -28, -1, 2, 30, 2, rightLimb);
        ModelPart leftArm = skinBox(-5, 38, 0, -1, -28, -1, 2, 30, 2, leftLimb);
        ModelPart rightLeg = skinBox(2, 30, 0, -1, -30, -1, 2, 30, 2, rightLimb);
        ModelPart leftLeg = skinBox(-2, 30, 0, -1, -30, -1, 2, 30, 2, leftLimb);
        ModelPart body = skinBox(0, 0, 0, -4, 28, -2, 8, 12, 4, uv(32, 16, 8, 12, 4),
                headGroup, rightArm, leftArm, rightLeg, leftLeg);
        return new EndermanModel(body, headGroup, outerHead, rightArm, leftArm, rightLeg, leftLeg);
    }

    /** Trượt đầu Enderman lên 5 px khi giận và đưa hai tay ra trước khi cầm khối. */
    private static final class EndermanModel extends MobModel {
        private final ModelPart body;
        private final ModelPart headGroup;
        private final ModelPart outerHead;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;

        private EndermanModel(ModelPart body, ModelPart headGroup, ModelPart outerHead,
                              ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
            super(body);
            this.body = body;
            this.headGroup = headGroup;
            this.outerHead = outerHead;
            this.rightArm = rightArm;
            this.leftArm = leftArm;
            this.rightLeg = rightLeg;
            this.leftLeg = leftLeg;
        }

        /** Gắn khối đang cầm theo khớp thân để luôn nằm giữa hai bàn tay Enderman. */
        @Override
        ModelPart heldBlockPart() {
            return body;
        }

        @Override
        public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                   float ageInTicks, float headYaw, float headPitch) {
            MobVisualState state = visualState(entity);
            boolean angry = pose(state) == MobVisualState.Pose.ANGRY;
            boolean carrying = state != null && state.heldBlock() != null;
            float step = gait(limbSwing, limbSwingAmount, 23);
            float twitch = angry ? (float) Math.sin(ageInTicks * 2.2f) * 2f : 0f;
            headGroup.setRotation(headPitch, headYaw + twitch, twitch);
            outerHead.setTranslation(0f, angry ? 5f : 0f, 0f);
            rightLeg.setRotation(-step, 0, 0);
            leftLeg.setRotation(step, 0, 0);
            if (carrying) {
                rightArm.setRotation(30f, 0, -3f);
                leftArm.setRotation(30f, 0, 3f);
            } else {
                rightArm.setRotation(step, 0, angry ? -11f : -4f);
                leftArm.setRotation(-step, 0, angry ? 11f : 4f);
            }
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

    /** Dựng bò ôn đới chuẩn Minecraft cao 25 pixel với mõm, sừng, 4 chân 4×12×4 và bầu vú. */
    private static MobModel cow() {
        ModelPart head = skinBox(0, 20, -8, -4, -4, -6, 8, 8, 6, 0, 0,
                skinBox(0, 0, -6, -3, -4, -1, 6, 3, 1, 1, 33),
                skinBox(4, 2, -4, 0, 0, 0, 1, 3, 1, 22, 0),
                skinBox(-4, 2, -4, -1, 0, 0, 1, 3, 1, 22, 0));
        ModelPart[] legs = animalLegs(4f, 12, -5f, 7f, 4, 4, uv(0, 16, 4, 12, 4));
        ModelPart udder = skinBox(0, 0, 0, -2, 11, 2, 4, 1, 6, horizontal(52, 0, 4, 6, 1));
        ModelPart body = animalBody(12, 10, 18, 12, horizontal(18, 4, 12, 18, 10), head, legs, null, udder);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.GRAZER);
    }

    /** Dựng cừu chuẩn Minecraft với lớp len trên đầu, thân và 4 chân 4×12×4. */
    private static MobModel sheep() {
        ModelPart.Layer overlay = ModelPart.Layer.OVERLAY;
        ModelPart head = skinBox(0, 18, -8, -3, -2, -6, 6, 6, 8, uv(0, 0, 6, 6, 8),
                shell(-3, -2, -4, 6, 6, 6, uv(0, 0, 6, 6, 6), 0.6f, overlay));
        ModelPart[] legs = new ModelPart[4];
        for (int i = 0; i < legs.length; i++) {
            float x = i % 2 == 0 ? 3f : -3f;
            float z = i < 2 ? -5f : 7f;
            legs[i] = leg(x, 12, z, 4, 4, uv(0, 16, 4, 12, 4),
                    shell(-2f, -6f, -2f, 4, 6, 4, uv(0, 16, 4, 6, 4), 0.5f, overlay));
        }
        ModelPart.CuboidUv bodyUv = horizontal(28, 8, 8, 16, 6);
        ModelPart wool = shell(-4f, 12, -8, 8, 6, 16, bodyUv, 1.75f, overlay);
        ModelPart body = animalBody(8, 6, 16, 12, bodyUv, head, legs, null, wool);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.GRAZER);
    }

    /** Dựng lợn ôn đới chuẩn Minecraft với chân 4×6×4, thân 10×16×8 và mõm 4×3×1. */
    private static MobModel pig() {
        ModelPart head = skinBox(0, 12, -6, -4, -4, -8, 8, 8, 8, uv(0, 0, 8, 8, 8),
                skinBox(0, 0, -8, -2, -3, -1, 4, 3, 1, 16, 16));
        ModelPart[] legs = animalLegs(3f, 6, -5f, 7f, 4, 4, uv(0, 16, 4, 6, 4));
        ModelPart body = animalBody(10, 8, 16, 6, horizontal(28, 8, 10, 16, 8), head, legs, null);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.GRAZER);
    }

    /** Dựng dê chuẩn Minecraft với đầu nghiêng -55°, sừng, tai, râu 2 mặt và 4 chân riêng. */
    private static MobModel goat() {
        ModelPart head = skinBox(0, 14, -7, -2.5f, -3, -8, 5, 7, 10, uv(34, 46, 5, 7, 10),
                skinBox(-1.9f, 4, -2, -1, 0, -1, 2, 7, 2, 12, 55),
                skinBox(1.9f, 4, -2, -1, 0, -1, 2, 7, 2, 12, 55),
                skinBox(-3.5f, 2, -2, -1.5f, 0, 0, 3, 2, 1, mirrored(2, 61, 3, 2, 1)),
                skinBox(3.5f, 2, -2, -1.5f, 0, 0, 3, 2, 1, uv(2, 61, 3, 2, 1)),
                skinBox(0, -3, -5, 0, -4, -3, 0, 7, 5, uv(23, 52, 0, 7, 5)).setDoubleSided(true))
                .setBaseRotation(-55f, 0f, 0f);
        ModelPart[] legs = {
                leg(2.8f, 10, -5f, 3, 3, uv(49, 2, 3, 10, 3)),
                leg(-2.8f, 10, -5f, 3, 3, uv(35, 2, 3, 10, 3)),
                leg(2.8f, 6, 6f, 3, 3, uv(36, 29, 3, 6, 3)),
                leg(-2.8f, 6, 6f, 3, 3, uv(49, 29, 3, 6, 3))
        };
        ModelPart frontWool = skinBox(0, 0, 0, -5.5f, 6, -7, 11, 14, 11, uv(0, 28, 11, 14, 11));
        ModelPart body = animalBody(9, 11, 16, 9, uv(1, 1, 9, 11, 16), head, legs, null, frontWool);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.GOAT);
    }

    /** Dựng gấu bắc cực chuẩn Minecraft với thân 14×14×11, vai 12×12×10 và chân 4×10. */
    private static MobModel polarBear() {
        ModelPart head = skinBox(0, 14, -9, -3.5f, -3, -5, 7, 7, 7, 0, 0,
                skinBox(0, 0, -5, -2.5f, -3, -3, 5, 3, 3, 0, 44),
                skinBox(3.5f, 3, -1, -1, 0, -0.5f, 2, 2, 1, 26, 0),
                skinBox(-3.5f, 3, -1, -1, 0, -0.5f, 2, 2, 1, mirrored(26, 0, 2, 2, 1)));
        ModelPart[] legs = {
                leg(4.5f, 10, -7f, 4, 8, uv(50, 22, 4, 10, 8)),
                leg(-4.5f, 10, -7f, 4, 8, uv(50, 22, 4, 10, 8)),
                leg(4.5f, 10, BEAR_HIND_HIP_DEPTH, 4, 6, uv(50, 40, 4, 10, 6)),
                leg(-4.5f, 10, BEAR_HIND_HIP_DEPTH, 4, 6, uv(50, 40, 4, 10, 6))
        };
        ModelPart shoulder = skinBox(0, 0, -6, -6f, 9, -6, 12, 10, 12, horizontal(39, 0, 12, 12, 10));
        ModelPart body = animalBody(14, 11, 14, 9, horizontal(0, 19, 14, 14, 11), head, legs, null, shoulder);
        return new QuadrupedModel(body, head, null, legs, AnimalKind.BEAR);
    }

    /** Dựng thỏ chuẩn Minecraft với tai cao 5 pixel không ăn lẹm vào đuôi và chân sau 2×1×7. */
    private static MobModel rabbit() {
        ModelPart head = skinBox(0, 8, -4.5f, -2.5f, -1, -4, 5, 4, 5, 32, 0,
                skinBox(1.5f, 3, -0.5f, -1, 0, -0.5f, 2, 5, 1, 58, 0).setBaseRotation(0f, -15f, 0f),
                skinBox(-1.5f, 3, -0.5f, -1, 0, -0.5f, 2, 5, 1, 52, 0).setBaseRotation(0f, 15f, 0f),
                skinBox(0, 0, -4, -0.5f, 0, -0.5f, 1, 1, 1, 32, 9));
        ModelPart[] legs = {
                leg(2.2f, 7, -3.5f, 2, 2, uv(8, 15, 2, 7, 2)),
                leg(-2.2f, 7, -3.5f, 2, 2, uv(0, 15, 2, 7, 2)),
                leg(2.6f, 4, 2.5f, 2, 5, uv(30, 15, 2, 4, 5),
                        skinBox(0, -4, 0, -1, 0, -5.5f, 2, 1, 7, uv(26, 24, 2, 1, 7))),
                leg(-2.6f, 4, 2.5f, 2, 5, uv(16, 15, 2, 4, 5),
                        skinBox(0, -4, 0, -1, 0, -5.5f, 2, 1, 7, uv(8, 24, 2, 1, 7)))
        };
        ModelPart tail = skinBox(0, 6.5f, 5, -1.5f, -1.5f, 0, 3, 3, 2, 52, 6);
        ModelPart body = animalBody(6, 5, 10, 3, uv(0, 0, 6, 5, 10), head, legs, tail);
        return new QuadrupedModel(body, head, tail, legs, AnimalKind.RABBIT);
    }

    /** Dựng sói tuyết chuẩn Minecraft với đầu 6×6×4, ngực 8×6×7 và thân 6×9×6. */
    private static MobModel wolf() {
        ModelPart head = skinBox(0, 10.5f, -7, -3, -3, -2, 6, 6, 4, 0, 0,
                skinBox(0, -3, -2, -1.5f, 0, -4, 3, 3, 4, 0, 10),
                skinBox(2, 3, 0, -1, 0, -0.5f, 2, 2, 1, 16, 14),
                skinBox(-2, 3, 0, -1, 0, -0.5f, 2, 2, 1, 16, 14));
        ModelPart[] legs = animalLegs(2.5f, 8, -4f, 5f, 2, 2, uv(0, 18, 2, 8, 2));
        ModelPart tail = skinBox(0, 12, 6.5f, -1, -1, 0, 2, 2, 8, horizontal(9, 18, 2, 8, 2))
                .setBaseRotation(-45f, 0f, 0f);
        ModelPart chest = skinBox(0, 0, 0, -4, 7, -6.5f, 8, 7, 6, horizontal(21, 0, 8, 6, 7));
        ModelPart body = animalBody(6, 6, 9, 8, horizontal(18, 14, 6, 9, 6), head, legs, tail, chest);
        return new QuadrupedModel(body, head, tail, legs, AnimalKind.WOLF);
    }

    /** Dựng cáo tuyết chuẩn Minecraft dùng chung UV cho ảnh thức và ảnh ngủ. */
    private static MobModel fox() {
        ModelPart head = skinBox(0, 7.5f, -5.5f, -4, -2.5f, -3, 8, 6, 6, 1, 5,
                skinBox(0, -2.5f, -3, -2, 0, -3, 4, 2, 3, 6, 18),
                skinBox(3, 3.5f, -0.5f, -1, 0, -0.5f, 2, 2, 1, 15, 1),
                skinBox(-3, 3.5f, -0.5f, -1, 0, -0.5f, 2, 2, 1, 8, 1));
        ModelPart.CuboidUv left = uv(13, 24, 2, 6, 2);
        ModelPart.CuboidUv right = uv(4, 24, 2, 6, 2);
        ModelPart[] legs = {
                leg(2.3f, 6, -4, 2, 2, right), leg(-2.3f, 6, -4, 2, 2, left),
                leg(2.3f, 6, 4, 2, 2, right), leg(-2.3f, 6, 4, 2, 2, left)
        };
        ModelPart tail = skinBox(0, 7, 5.5f, -2, -2, 0, 4, 5, 9, horizontal(30, 0, 4, 9, 5));
        ModelPart body = animalBody(6, 6, 11, 6, horizontal(24, 15, 6, 11, 6), head, legs, tail);
        return new QuadrupedModel(body, head, tail, legs, AnimalKind.FOX);
    }

    /** Dựng ngựa và lừa chuẩn Minecraft với chân 4×11×4, thân 10×10×22 và cổ nghiêng -30°. */
    private static MobModel equine(boolean donkey) {
        ModelPart.CuboidUv earUv = donkey ? uv(0, 12, 2, 7, 1) : uv(19, 16, 2, 3, 1);
        ModelPart rightEar = skinBox(2, 4, 1, -1, 0, 0, 2, donkey ? 7 : 3, 1, earUv);
        ModelPart leftEar = skinBox(-2, 4, 1, -1, 0, 0, 2, donkey ? 7 : 3, 1, earUv);
        if (donkey) {
            rightEar.setBaseRotation(0f, 0f, -15f);
            leftEar.setBaseRotation(0f, 0f, 15f);
        }
        ModelPart upperHead = equineBox(donkey, 0, 10, -1, -3, -1, -4, 6, 5, 7, uv(0, 13, 6, 5, 7),
                equineBox(donkey, 0, 0, -4, -2, -1, -5, 4, 5, 5, uv(0, 25, 4, 5, 5)),
                rightEar, leftEar);
        ModelPart[] neckParts = donkey
                ? new ModelPart[]{upperHead}
                : new ModelPart[]{upperHead,
                        skinBox(0, 0, 0, -1, -2, 2, 2, 16, 2, uv(56, 36, 2, 16, 2))};
        ModelPart neck = equineBox(donkey, 0, 18, -9, -2, -1, -3, 4, 12, 7,
                uv(0, 35, 4, 12, 7), neckParts).setBaseRotation(-30f, 0f, 0f);
        int height = 11;
        ModelPart[] legs = new ModelPart[4];
        for (int i = 0; i < legs.length; i++) {
            boolean right = i % 2 == 0;
            float z = i < 2 ? -9f : EQUINE_HIND_HIP_DEPTH;
            ModelPart.CuboidUv foot = right ? uv(48, 21, 4, 11, 4) : mirrored(48, 21, 4, 11, 4);
            legs[i] = equineBox(donkey, right ? 3.5f : -3.5f, height, z, -2f, -height, -2f,
                    4, height, 4, foot);
        }
        ModelPart tail = skinBox(0, 20, 11f, -1.5f, -14, 0, 3, 14, 4, uv(42, 36, 3, 14, 4))
                .setBaseRotation(30f, 0f, 0f);
        ModelPart.CuboidUv bodyUv = uv(0, 32, 10, 10, 22);
        ModelPart body = donkey
                ? animalBody(10, 10, 22, 11, bodyUv, neck, legs, tail)
                : animalBody(10, 10, 22, 11, bodyUv, neck, legs, tail,
                        shell(-5, 11, -11, 10, 10, 22, bodyUv, 0.08f, ModelPart.Layer.OVERLAY));
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

    /** Dựng lạc đà thường và xác sống chuẩn Minecraft cao 44 pixel với chân 5×21×5 và đuôi 2 mặt. */
    private static MobModel camel() {
        ModelPart head = skinBox(0, 6, -12, -3.5f, 0, -3.5f, 7, 14, 7, uv(21, 0, 7, 14, 7),
                skinBox(0, 8, -3.5f, -2.5f, 0, -6, 5, 5, 6, 50, 0),
                skinBox(3, 11, 2, 0f, 0, -1, 3, 1, 2, 45, 0),
                skinBox(-3, 11, 2, -3f, 0, -1, 3, 1, 2, 67, 0));
        ModelPart neck = skinBox(0, 24, -10, -3.5f, -2, -13, 7, 8, 19, uv(60, 24, 7, 8, 19), head);
        ModelPart hump = skinBox(0, 32, -1, -4.5f, 0, -5.5f, 9, 5, 11, 74, 0);
        ModelPart[] legs = {
                leg(4.9f, 21, -10f, 5, 5, uv(0, 0, 5, 21, 5)),
                leg(-4.9f, 21, -10f, 5, 5, uv(0, 26, 5, 21, 5)),
                leg(4.9f, 21, 10f, 5, 5, uv(58, 16, 5, 21, 5)),
                leg(-4.9f, 21, 10f, 5, 5, uv(94, 16, 5, 21, 5))
        };
        ModelPart tail = skinBox(0, 26, 13.5f, -1.5f, -14, 0, 3, 14, 0, uv(122, 0, 3, 14, 0))
                .setDoubleSided(true);
        ModelPart body = animalBody(15, 12, 27, 20, uv(0, 25, 15, 12, 27), neck, legs, tail, hump);
        return new QuadrupedModel(body, neck, tail, legs, AnimalKind.CAMEL);
    }

    /** Chia sẻ nhịp bốn chân và chỉ điều chỉnh khớp theo những tư thế từng loài hỗ trợ. */
    private static final class QuadrupedModel extends MobModel {
        private final ModelPart body;
        private final ModelPart head;
        private final ModelPart tail;
        private final ModelPart[] legs;
        private final AnimalKind kind;

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
            float baseHeadX = switch (kind) {
                case GOAT -> -55f;
                case EQUINE -> -30f;
                default -> 0f;
            };
            head.setRotation(baseHeadX + headPitch, headYaw, 0);
            if (tail != null) {
                float baseTailX = switch (kind) {
                    case WOLF -> -45f;
                    case FOX -> 8f;
                    case EQUINE -> 30f;
                    default -> 0f;
                };
                tail.setRotation(baseTailX, gait(limbSwing + ageInTicks * 0.06f, limbSwingAmount, 19), 0);
            }
            switch (pose) {
                case GRAZE -> head.setRotation(baseHeadX - 55 * amount, headYaw * 0.2f, 0);
                case RAM -> {
                    if (kind == AnimalKind.GOAT) {
                        head.setRotation(-55f - 18 * amount, 0, 0);
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
                            head.setRotation(-55f - 14f * amount, headYaw, 0);
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
                        tail.setRotation(-45f, 0, roll);
                    }
                }
                case ANGRY -> {
                    if (kind == AnimalKind.WOLF) {
                        head.setRotation(headPitch - 12 * amount, headYaw, 0);
                        tail.setRotation(-45f - 25f * amount, 0, 0);
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
            head.setRotation((kind == AnimalKind.EQUINE ? -30f : 0f) + headPitch - 44 * amount, headYaw * 0.6f, 0);
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

    /** Dựng gà ôn đới chuẩn Minecraft với hai chân vẽ hai mặt để luôn rõ từ mọi hướng. */
    private static MobModel chicken() {
        ModelPart head = skinBox(0, 9, -4, -2, 0, -2, 4, 6, 3, 0, 0,
                skinBox(0, 0, 0, -2, 2, -4, 4, 2, 2, 14, 0),
                skinBox(0, 0, 0, -1, 0, -3, 2, 2, 2, 14, 4));
        ModelPart rightWing = skinBox(3, 11, 0, 0, -4, -3, 1, 4, 6, 24, 13);
        ModelPart leftWing = skinBox(-3, 11, 0, -1, -4, -3, 1, 4, 6, 24, 13);
        ModelPart rightLeg = skinBox(1.5f, 5, 1, -1.5f, -5, -2, 3, 5, 3, 26, 0).setDoubleSided(true);
        ModelPart leftLeg = skinBox(-1.5f, 5, 1, -1.5f, -5, -2, 3, 5, 3, 26, 0).setDoubleSided(true);
        ModelPart body = skinBox(0, 0, 0, -3, 5, -4, 6, 6, 8, horizontal(0, 9, 6, 8, 6),
                head, rightWing, leftWing, rightLeg, leftLeg);
        return new ChickenModel(body, head, rightWing, leftWing, rightLeg, leftLeg);
    }

    /** Hoạt ảnh gà vỗ cánh đối xứng khi nhảy hoặc rơi. */
    private static final class ChickenModel extends MobModel {
        private final ModelPart head;
        private final ModelPart rightWing;
        private final ModelPart leftWing;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;

        private ChickenModel(ModelPart body, ModelPart head, ModelPart rightWing, ModelPart leftWing,
                             ModelPart rightLeg, ModelPart leftLeg) {
            super(body);
            this.head = head;
            this.rightWing = rightWing;
            this.leftWing = leftWing;
            this.rightLeg = rightLeg;
            this.leftLeg = leftLeg;
        }

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
            rightLeg.setRotation(-step, 0, 0);
            leftLeg.setRotation(step, 0, 0);
        }
    }

    /** Dựng mũi tên chữ X chuẩn Minecraft từ tấm 32×32, đầu tên hướng về -Z. */
    private static MobModel arrow() {
        ModelPart.CuboidUv arrowUv = new ModelPart.CuboidUv(0, 0, 16, 10, 0, ModelPart.UvOrientation.FLAT_SPRITE);
        return fixed(new ModelPart(0, 1, 0, -2.5f, -2.5f, -8, 5, 5, 16, 0f, arrowUv,
                ModelPart.Shape.CROSSED_ARROW).setDoubleSided(true));
    }

    /** Dựng lọ thuốc ném chữ X chuẩn Minecraft từ sprite 16×16. */
    private static MobModel potion() {
        ModelPart.CuboidUv potionUv = new ModelPart.CuboidUv(0, 0, 16, 16, 0, ModelPart.UvOrientation.FLAT_SPRITE);
        return fixed(new ModelPart(0, 4, 0, -4, -4, -4, 8, 8, 8, 0f, potionUv,
                ModelPart.Shape.CROSSED_POTION).setDoubleSided(true));
    }

    /** Giữ tư thế cục bộ của đạn; hướng bay do biến đổi gốc quyết định. */
    private static MobModel fixed(ModelPart root) {
        return new MobModel(root) {
            @Override
            public void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                       float ageInTicks, float headYaw, float headPitch) {
                // Định hướng chuyển động thuộc thực thể đạn, không thuộc khung khớp.
            }
        };
    }
}
