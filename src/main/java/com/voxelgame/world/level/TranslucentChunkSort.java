package com.voxelgame.world.level;

/** Sắp thứ tự chunk trong suốt ngay trên các mảng dùng lại giữa các khung hình. */
public final class TranslucentChunkSort {
    /** Tiện ích sắp xếp không lưu trạng thái giữa các lần gọi. */
    private TranslucentChunkSort() {
    }

    /** Dùng heapsort để đưa chunk xa lên trước, phá hòa bằng khóa tọa độ tăng dần. */
    public static void sort(Chunk[] chunks, float[] distances, int[] keys, int count) {
        // Gốc heap là phần tử cần được vẽ muộn nhất trong vùng còn lại.
        for (int root = count / 2 - 1; root >= 0; root--) {
            siftDown(chunks, distances, keys, root, count);
        }
        for (int end = count - 1; end > 0; end--) {
            swap(chunks, distances, keys, 0, end);
            siftDown(chunks, distances, keys, 0, end);
        }
    }

    /** Hạ phần tử gốc cho đến khi mỗi nút đứng sau các nút con trong thứ tự vẽ. */
    private static void siftDown(Chunk[] chunks, float[] distances, int[] keys, int root, int end) {
        int child = root * 2 + 1;
        while (child < end) {
            int right = child + 1;
            if (right < end && drawsLater(distances, keys, right, child)) {
                child = right;
            }
            if (!drawsLater(distances, keys, child, root)) {
                break;
            }
            swap(chunks, distances, keys, root, child);
            root = child;
            child = root * 2 + 1;
        }
    }

    /** Ưu tiên chunk gần hơn hoặc có khóa tọa độ lớn hơn để đặt cuối vùng chưa sắp. */
    private static boolean drawsLater(float[] distances, int[] keys, int first, int second) {
        return distances[first] < distances[second]
                || (distances[first] == distances[second] && keys[first] > keys[second]);
    }

    /** Hoán đổi cả tham chiếu chunk, khoảng cách và khóa để giữ ba mảng đồng bộ. */
    private static void swap(Chunk[] chunks, float[] distances, int[] keys, int first, int second) {
        Chunk chunk = chunks[first];
        chunks[first] = chunks[second];
        chunks[second] = chunk;

        float distance = distances[first];
        distances[first] = distances[second];
        distances[second] = distance;

        int key = keys[first];
        keys[first] = keys[second];
        keys[second] = key;
    }
}
