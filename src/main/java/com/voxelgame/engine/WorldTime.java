package com.voxelgame.engine;

/** Đồng hồ thế giới chạy liên tục, với 24.000 tick mỗi ngày và 20 tick mỗi giây. */
public final class WorldTime {
    /** Bốn mốc quỹ đạo cách nhau đúng một phần tư ngày. */
    private static final int TICKS_PER_DAY = 24_000;
    private static final int TICKS_PER_QUARTER = TICKS_PER_DAY / 4;
    private static final double FULL_TURN = Math.PI * 2.0;

    /** Dùng tick lẻ cho chuyển động mượt giữa hai khung hình. */
    private double tick = 1_000;
    private long dayCount;
    private boolean fastForward;
    private boolean visitedMilestone;
    private float sunX;
    private float sunY;

    /** Cập nhật ngay quỹ đạo theo giờ sáng khi khởi tạo. */
    public WorldTime() {
        updateOrbit();
    }

    /** Tiến đồng hồ khi đang chơi và đếm số lần qua bình minh. */
    public void update(double seconds) {
        tick += seconds * (fastForward ? 400 : 20);
        long elapsedDays = (long) (tick / TICKS_PER_DAY);
        dayCount += elapsedDays;
        tick -= elapsedDays * TICKS_PER_DAY;
        updateOrbit();
    }

    /** Lần đầu đưa về bình minh; các lần tiếp theo nhảy tới mốc chuẩn kế tiếp. */
    public void nextMilestone() {
        tick = visitedMilestone ? ((int) tick / TICKS_PER_QUARTER + 1) * TICKS_PER_QUARTER : 0;
        visitedMilestone = true;
        update(0);
    }

    /** Chuyển tốc độ đồng hồ giữa 1× và 20×. */
    public void toggleFastForward() {
        fastForward = !fastForward;
    }

    /** Làm chậm quỹ đạo gần chân trời, giữ nguyên bốn hướng tại các mốc chuẩn. */
    private void updateOrbit() {
        double angle = tick / TICKS_PER_DAY * FULL_TURN;
        double curved = angle - 0.08 * Math.sin(2 * angle);
        sunX = (float) Math.cos(curved);
        sunY = (float) Math.sin(curved);
    }

    /** Thành phần Đông–Tây của hướng mặt trời, dương về Đông. */
    public float sunX() {
        return sunX;
    }

    /** Thành phần độ cao của mặt trời, dương phía trên chân trời. */
    public float sunY() {
        return sunY;
    }

    /** Số ngày đã trôi qua kể từ lần vào thế giới đầu tiên. */
    public long dayCount() {
        return dayCount;
    }

    /** Trả chỉ số tick trong ngày, dùng để đánh giá khoảng tối của bầu trời. */
    public int tickOfDay() {
        return (int) tick;
    }

    /** Giờ dân dụng bắt đầu ở 06:00 khi tick trong ngày bằng 0. */
    public int minuteOfDay() {
        return ((int) (tick * 1_440 / TICKS_PER_DAY) + 360) % 1_440;
    }
}
