package com.voxelgame.client.model;

import com.voxelgame.world.entity.LivingEntity;

/** Mẫu phân khớp với một hay nhiều phần gốc, dùng chung giữa các cá thể. */
public abstract class MobModel {
    /** Dư địa pixel cho dịch gốc của nhảy, đứng hai chân và ngồi trong các mẫu hiện có. */
    private static final float ANIMATION_MARGIN = 16f;
    private final ModelPart[] roots;
    private final float renderRadius;

    /** Khóa cấu trúc các phần gốc khi tạo mẫu, chưa sử dụng OpenGL. */
    protected MobModel(ModelPart... roots) {
        this.roots = roots.clone();
        float maxReach = 0f;
        for (ModelPart root : this.roots) {
            maxReach = Math.max(maxReach, root.maxReach());
        }
        renderRadius = maxReach + ANIMATION_MARGIN;
    }

    /** Bán kính bao mẫu quanh chân theo pixel, gồm cả chuyển động của điểm xoay. */
    public final float renderRadius() {
        return renderRadius;
    }

    /** Kiểm tra tấm ảnh có chứa đầy đủ các mặt của lớp tương ứng. */
    public final boolean fitsLayer(ModelPart.Layer layer, int width, int height) {
        for (ModelPart root : roots) {
            if (!root.fitsLayer(layer, width, height)) {
                return false;
            }
        }
        return true;
    }

    /** Đọc các phần gốc để dựng và vẽ cây mô hình. */
    public final ModelPart[] roots() {
        return roots;
    }

    /** Trả phần đầu có điểm ngậm vật, nếu mẫu có hỗ trợ. */
    public ModelPart heldBlockPart() {
        return null;
    }

    /** Đặt lại mẫu trước khi áp dụng góc khớp của cá thể mới. */
    public final void resetPose() {
        for (ModelPart root : roots) {
            root.resetPose();
        }
    }

    /** Tính tư thế từ nhịp chân theo radian, thời gian tick và góc đầu theo độ. */
    public abstract void setupAnimation(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                        float ageInTicks, float headYaw, float headPitch);
}
