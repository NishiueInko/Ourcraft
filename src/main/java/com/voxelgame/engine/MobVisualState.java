package com.voxelgame.engine;

import com.badlogic.gdx.math.MathUtils;

import java.util.Objects;

/** Trạng thái hiển thị của một cá thể, tách khỏi mẫu hình học dùng chung. */
public final class MobVisualState {
    /** Tư thế do hành vi của cá thể chọn trước khi vẽ. */
    public enum Pose {
        IDLE, UNDEAD, AIM_BOW, THRUST, GRAZE, RAM, REAR, FLAP, HOP, TILT_HEAD, POUNCE, SLEEP, SIT, ANGRY
    }

    private MobAppearance appearance;
    private MobAppearance.HorseMarkings horseMarkings;
    private Pose pose = Pose.IDLE;
    private float poseAmount;
    private float bodyYaw;
    private float bodyPitch;
    private float headYaw;
    private float headPitch;
    private float limbSwing;
    private float limbSwingAmount;
    private float ageInTicks;
    private float scale = 1f;
    private float fuseProgress;

    /** Tạo một trạng thái tái sử dụng cho cá thể với ngoại hình ban đầu. */
    public MobVisualState(MobAppearance appearance) {
        setAppearance(appearance);
    }

    /** Chọn model và biến thể texture đã nạp trước, ví dụ ngoại hình sói đang giận. */
    public void setAppearance(MobAppearance appearance) {
        this.appearance = Objects.requireNonNull(appearance);
    }

    /** Chọn hoa văn lông ngựa hoặc truyền null để bỏ lớp hoa văn. */
    public void setHorseMarkings(MobAppearance.HorseMarkings markings) {
        horseMarkings = markings;
    }

    /** Chọn tư thế và tỷ lệ nội suy từ tư thế thường tới tư thế đó. */
    public void setPose(Pose pose, float amount) {
        this.pose = Objects.requireNonNull(pose);
        poseAmount = MathUtils.clamp(amount, 0f, 1f);
    }

    /** Đặt hướng thân, góc đầu tương đối theo độ và nhịp chân theo radian. */
    public void setAnimation(float bodyYaw, float headYaw, float headPitch,
                             float limbSwing, float swingAmount, float ageInTicks) {
        this.bodyYaw = bodyYaw;
        this.headYaw = headYaw;
        this.headPitch = headPitch;
        this.limbSwing = limbSwing;
        limbSwingAmount = MathUtils.clamp(swingAmount, 0f, 1f);
        this.ageInTicks = ageInTicks;
    }

    /** Đặt kích thước hiển thị tương đối để dùng lại mẫu cho cá thể non. */
    public void setScale(float scale) {
        if (!(scale > 0f) || !Float.isFinite(scale)) {
            throw new IllegalArgumentException("Hệ số vẽ mob phải dương và hữu hạn");
        }
        this.scale = scale;
    }

    /** Xoay thân theo trục ngang, ví dụ để hướng mũi tên theo góc bay. */
    public void setBodyPitch(float pitch) {
        bodyPitch = pitch;
    }

    /** Đặt phần tiến độ phồng và chớp trước nổ từ 0 đến 1. */
    public void setFuseProgress(float progress) {
        fuseProgress = MathUtils.clamp(progress, 0f, 1f);
    }

    /** Đọc mẫu và biến thể vật liệu đang dùng. */
    public MobAppearance appearance() { return appearance; }
    /** Đọc hoa văn lông ngựa hiện tại. */
    public MobAppearance.HorseMarkings horseMarkings() { return horseMarkings; }
    /** Đọc tư thế hiện tại. */
    public Pose pose() { return pose; }
    /** Đọc tỷ lệ nội suy của tư thế. */
    public float poseAmount() { return poseAmount; }
    /** Đọc góc quay của thân theo trục đứng. */
    public float bodyYaw() { return bodyYaw; }
    /** Đọc góc dọc của thân theo độ. */
    public float bodyPitch() { return bodyPitch; }
    /** Đọc góc ngang của đầu so với thân. */
    public float headYaw() { return headYaw; }
    /** Đọc góc dọc của đầu. */
    public float headPitch() { return headPitch; }
    /** Đọc pha vung chân của bước hiện tại. */
    public float limbSwing() { return limbSwing; }
    /** Đọc biên độ vung chân đã chặn từ 0 đến 1. */
    public float limbSwingAmount() { return limbSwingAmount; }
    /** Đọc đồng hồ hoạt ảnh tính bằng tick. */
    public float ageInTicks() { return ageInTicks; }
    /** Đọc hệ số kích thước hiển thị. */
    public float scale() { return scale; }
    /** Đọc tiến độ phồng trước khi nổ. */
    public float fuseProgress() { return fuseProgress; }
}
