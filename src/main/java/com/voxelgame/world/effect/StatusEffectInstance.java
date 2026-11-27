package com.voxelgame.world.effect;

/** Khe hiệu ứng có thể tái sử dụng, với cấp I biểu diễn bằng amplifier 0. */
public final class StatusEffectInstance {
    private int amplifier;
    private float duration;

    /** Ghi cấp và thời lượng mới, tính bằng giây. */
    public void set(int amplifier, float duration) {
        this.amplifier = amplifier;
        this.duration = duration;
    }

    /** Trừ thời gian thực sự hiệu lực trong bước hiện tại và trả lại phần đó. */
    public float advance(float deltaTime) {
        float activeTime = Math.min(deltaTime, duration);
        duration -= activeTime;
        return activeTime;
    }

    /** Trả amplifier, trong đó 0 tương ứng cấp I. */
    public int amplifier() {
        return amplifier;
    }

    /** Trả thời lượng còn lại tính bằng giây. */
    public float duration() {
        return duration;
    }
}
