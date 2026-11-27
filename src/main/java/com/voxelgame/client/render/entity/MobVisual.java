package com.voxelgame.client.render.entity;

/** Điểm nối giữa thực thể sống và lượt vẽ mob. */
public interface MobVisual {
    /** Trả trạng thái hiển thị đã cấp phát của cá thể. */
    MobVisualState mobVisualState();
}
