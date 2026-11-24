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

    /** Perlin hai chiều với gradient băm theo seed và trọng số bậc năm. */
    static float perlin(long seed, float x, float z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        float dx = x - ix;
        float dz = z - iz;
        float u = fade(dx);
        float v = fade(dz);
        float near = mix(gradient(seed, ix, iz, dx, dz), gradient(seed, ix + 1, iz, dx - 1f, dz), u);
        float far = mix(gradient(seed, ix, iz + 1, dx, dz - 1f),
                gradient(seed, ix + 1, iz + 1, dx - 1f, dz - 1f), u);
        return mix(near, far, v);
    }

    /** Tích lũy các đỉnh nhọn và dùng octave trước làm trọng số cho octave sau. */
    static float ridged(long seed, float x, float z) {
        float sum = 0f;
        float weight = 1f;
        float amplitude = 1f;
        for (int octave = 0; octave < 4; octave++) {
            float ridge = 1f - Math.abs(perlin(seed + octave, x, z));
            float signal = ridge * ridge * weight;
            sum += signal * amplitude;
            weight = Math.min(1f, signal * 1.65f);
            amplitude *= 0.5f;
            x *= 2.06f;
            z *= 2.06f;
        }
        return sum / 1.875f;
    }

    /** Simplex hai chiều lấy mẫu ba đỉnh tam giác để tạo các mảng hoa liên tục. */
    static float simplex(long seed, float x, float z) {
        float skew = (x + z) * 0.3660254f;
        int i = (int) Math.floor(x + skew);
        int j = (int) Math.floor(z + skew);
        float unskew = (i + j) * 0.21132487f;
        float dx = x - (i - unskew);
        float dz = z - (j - unskew);
        int ix = dx > dz ? 1 : 0;
        int iz = 1 - ix;
        return 70f * (simplexCorner(seed, i, j, dx, dz)
                + simplexCorner(seed, i + ix, j + iz, dx - ix + 0.21132487f, dz - iz + 0.21132487f)
                + simplexCorner(seed, i + 1, j + 1, dx - 0.57735026f, dz - 0.57735026f));
    }

    /** Làm suy giảm đóng góp gradient theo khoảng cách đến đỉnh Simplex. */
    private static float simplexCorner(long seed, int x, int z, float dx, float dz) {
        float t = Math.max(0f, 0.5f - dx * dx - dz * dz);
        t *= t;
        return t * t * gradient(seed, x, z, dx, dz);
    }

    /** Đạo hàm bậc nhất và bậc hai bằng 0 ở đường biên ô Perlin. */
    private static float fade(float t) {
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    /** Tích vô hướng với một trong tám hướng có độ dài đơn vị. */
    private static float gradient(long seed, int x, int z, float dx, float dz) {
        return switch ((int) (hash(seed, x, z) & 7)) {
            case 0 -> dx;
            case 1 -> -dx;
            case 2 -> dz;
            case 3 -> -dz;
            case 4 -> (dx + dz) * 0.70710677f;
            case 5 -> (dx - dz) * 0.70710677f;
            case 6 -> (-dx + dz) * 0.70710677f;
            default -> (-dx - dz) * 0.70710677f;
        };
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
