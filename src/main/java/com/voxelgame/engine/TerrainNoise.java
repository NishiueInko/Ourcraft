package com.voxelgame.engine;

/** Noise giá trị hai chiều từ seed và tọa độ, chỉ dùng kiểu nguyên thủy. */
final class TerrainNoise {
    /** Tổng biên độ của ba octave có trọng số 1, 1/2 và 1/4. */
    private static final float OCTAVE_WEIGHT = 1.75f;

    /** Lớp tiện ích chỉ cung cấp các phép tính tĩnh. */
    private TerrainNoise() {
    }

    /** Trộn tọa độ và seed trên 64 bit để các vùng không phụ thuộc thứ tự duyệt. */
    static long hash(long seed, int x, int z) {
        long value = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /** Lấy 24 bit cao của giá trị băm để tạo số thực trong [0, 1). */
    static float unit(long seed, int x, int z) {
        return (hash(seed, x, z) >>> 40) * 0x1.0p-24f;
    }

    /** Làm mượt trọng số nội suy, với đạo hàm bằng 0 ở hai đầu. */
    static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Nội suy giá trị giữa hai mốc. */
    static float mix(float start, float end, float t) {
        return start + (end - start) * t;
    }

    /** Tổng ba octave chuẩn hóa vào khoảng [-1, 1]. */
    static float fractal(long seed, float x, float z) {
        return (value(seed, x, z)
                + 0.5f * value(seed + 1, x * 2f, z * 2f)
                + 0.25f * value(seed + 2, x * 4f, z * 4f)) / OCTAVE_WEIGHT;
    }

    /** Băm bốn đỉnh ô rồi nội suy theo X và Z để không có khe tại biên ô. */
    private static float value(long seed, float x, float z) {
        int cellX = (int) Math.floor(x);
        int cellZ = (int) Math.floor(z);
        float amountX = smooth(x - cellX);
        float amountZ = smooth(z - cellZ);
        float near = mix(unit(seed, cellX, cellZ), unit(seed, cellX + 1, cellZ), amountX);
        float far = mix(unit(seed, cellX, cellZ + 1), unit(seed, cellX + 1, cellZ + 1), amountX);
        return mix(near, far, amountZ) * 2f - 1f;
    }
}
