package com.voxelgame.engine;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;

/** Hàng đợi FIFO tọa độ voxel, dùng lại bộ đệm và gộp các ô còn đang chờ. */
final class BlockUpdateQueue {
    private final IntArray entries = new IntArray(64);
    private final IntSet pending = new IntSet(64);
    private int head;

    /** Chỉ thêm tọa độ hợp lệ chưa có trong phần hàng đợi đang chờ. */
    void add(int x, int y, int z) {
        if (x >= 0 && x < World.WORLD_SIZE_X && y >= 0 && y < World.WORLD_SIZE_Y
                && z >= 0 && z < World.WORLD_SIZE_Z) {
            int packed = (x << 19) | (z << 8) | y;
            if (pending.add(packed)) {
                entries.add(packed);
            }
        }
    }

    /** Báo còn ô cần cập nhật. */
    boolean hasNext() {
        return head < entries.size;
    }

    /** Bỏ cờ đang chờ để ô được xét lại nếu một luật vừa thay đổi hàng xóm. */
    int next() {
        int packed = entries.get(head++);
        pending.remove(packed);
        return packed;
    }

    /** Giữ dung lượng đã cấp khi kết thúc lô cập nhật. */
    void clear() {
        head = 0;
        entries.clear();
        pending.clear();
    }
}
