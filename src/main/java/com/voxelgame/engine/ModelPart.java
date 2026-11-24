package com.voxelgame.engine;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/** Hộp pixel trong cây khớp, với mặt trước theo -Z và trục đứng theo +Y. */
public final class ModelPart {
    /** Chọn vật liệu của bộ phận trong mỗi lượt vẽ mô hình. */
    public enum Layer { BASE, OVERLAY, EYES }

    /** Độ sáng tương đối của sáu mặt theo quy ước hình học chunk. */
    private static final float SHADE_TOP = 1f;
    private static final float SHADE_BOTTOM = 0.55f;
    private static final float SHADE_NORTH_SOUTH = 0.85f;
    private static final float SHADE_EAST_WEST = 0.72f;
    /** Định dạng XYZ, UV, độ sáng của shader thế giới. */
    static final int STRIDE = 6;
    static final int VERTEX_COUNT = 36;

    /** Thông số bất biến để chia sẻ mesh giữa các bộ phận có cùng hình học và UV. */
    record Geometry(float x, float y, float z, int width, int height, int depth, int u, int v, float inflate) {
    }

    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;
    private final Geometry geometry;
    private final ModelPart[] children;
    private float rotationX;
    private float rotationY;
    private float rotationZ;
    private float translationX;
    private float translationY;
    private float translationZ;
    private boolean visible = true;
    private Layer layer = Layer.BASE;

    /** Lưu điểm xoay, kích thước hộp và tọa độ pixel của sơ đồ trải UV. */
    public ModelPart(float pivotX, float pivotY, float pivotZ, float offsetX, float offsetY, float offsetZ,
                     int sizeX, int sizeY, int sizeZ, int texU, int texV, float inflate, ModelPart... children) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || texU < 0 || texV < 0
                || !Float.isFinite(inflate) || Math.min(sizeX, Math.min(sizeY, sizeZ)) + 2 * inflate <= 0f) {
            throw new IllegalArgumentException("Kích thước hộp, độ phồng hoặc gốc UV không hợp lệ");
        }
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        geometry = new Geometry(offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ, texU, texV, inflate);
        this.children = children.clone();
    }

    /** Vật liệu được đặt khi dựng mẫu, độc lập với tư thế từng cá thể. */
    public ModelPart setLayer(Layer layer) {
        this.layer = Objects.requireNonNull(layer);
        return this;
    }

    /** Đặt góc quay Euler cục bộ theo độ. */
    public void setRotation(float x, float y, float z) {
        rotationX = x;
        rotationY = y;
        rotationZ = z;
    }

    /** Dời điểm xoay theo pixel cục bộ, ví dụ để hạ thân khi ngồi. */
    public void setTranslation(float x, float y, float z) {
        translationX = x;
        translationY = y;
        translationZ = z;
    }

    /** Ẩn bộ phận cùng các con của nó trong tư thế hiện tại. */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** Dọn tư thế của cá thể trước để mẫu dùng chung có thể vẽ cá thể kế tiếp. */
    void resetPose() {
        rotationX = rotationY = rotationZ = 0f;
        translationX = translationY = translationZ = 0f;
        visible = true;
        for (ModelPart child : children) {
            child.resetPose();
        }
    }

    /** Ghi phép biến đổi từ cha vào bộ đệm được truyền vào, giữ nguyên ma trận của cha. */
    void writeTransform(Matrix4 parent, Matrix4 out) {
        out.set(parent).translate(pivotX + translationX, pivotY + translationY, pivotZ + translationZ);
        if (rotationZ != 0f) {
            out.rotate(Vector3.Z, rotationZ);
        }
        if (rotationY != 0f) {
            out.rotate(Vector3.Y, rotationY);
        }
        if (rotationX != 0f) {
            out.rotate(Vector3.X, rotationX);
        }
    }

    /** Cho biết footprint pixel có nằm hoàn toàn trong tấm ảnh vật liệu. */
    boolean fitsTexture(int width, int height) {
        return (long) geometry.u + 2L * (geometry.width + (long) geometry.depth) <= width
                && (long) geometry.v + geometry.depth + geometry.height <= height;
    }

    /** Kiểm tra đồng thời các phần dùng cùng một lớp ảnh trong toàn bộ nhánh. */
    boolean fitsLayer(Layer target, int width, int height) {
        if (layer == target && !fitsTexture(width, height)) {
            return false;
        }
        for (ModelPart child : children) {
            if (!child.fitsLayer(target, width, height)) {
                return false;
            }
        }
        return true;
    }

    /** Bán kính quanh điểm xoay của cha khi các khớp tự do quay theo ba trục. */
    float maxReach() {
        float x = Math.max(Math.abs(geometry.x), Math.abs(geometry.x + geometry.width)) + geometry.inflate;
        float y = Math.max(Math.abs(geometry.y), Math.abs(geometry.y + geometry.height)) + geometry.inflate;
        float z = Math.max(Math.abs(geometry.z), Math.abs(geometry.z + geometry.depth)) + geometry.inflate;
        float reach = (float) Math.sqrt(x * x + y * y + z * z);
        for (ModelPart child : children) {
            reach = Math.max(reach, child.maxReach());
        }
        return reach + (float) Math.sqrt(pivotX * pivotX + pivotY * pivotY + pivotZ * pivotZ);
    }

    /** Ghi sáu mặt đúng chiều pháp tuyến, với dải UV độc lập với độ phồng hình học. */
    float[] buildVertices(int texWidth, int texHeight) {
        if (!fitsTexture(texWidth, texHeight)) {
            throw new IllegalArgumentException("Texture không đủ kích thước cho bộ phận");
        }
        float x0 = geometry.x - geometry.inflate;
        float x1 = geometry.x + geometry.width + geometry.inflate;
        float y0 = geometry.y - geometry.inflate;
        float y1 = geometry.y + geometry.height + geometry.inflate;
        float z0 = geometry.z - geometry.inflate;
        float z1 = geometry.z + geometry.depth + geometry.inflate;

        float u0 = (float) geometry.u / texWidth;
        float u1 = (float) (geometry.u + geometry.depth) / texWidth;
        float u2 = (float) (geometry.u + geometry.depth + geometry.width) / texWidth;
        float u3 = (float) (geometry.u + 2 * geometry.depth + geometry.width) / texWidth;
        float u4 = (float) (geometry.u + 2 * (geometry.depth + geometry.width)) / texWidth;
        float bottomU = (float) (geometry.u + geometry.depth + 2 * geometry.width) / texWidth;
        float v0 = (float) geometry.v / texHeight;
        float v1 = (float) (geometry.v + geometry.depth) / texHeight;
        float v2 = (float) (geometry.v + geometry.depth + geometry.height) / texHeight;
        float[] vertices = new float[VERTEX_COUNT * STRIDE];

        // Mỗi mặt bắt đầu ở góc trên trái khi nhìn từ ngoài vào hộp.
        face(vertices, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, u0, v1, u1, v2, SHADE_EAST_WEST);
        face(vertices, 1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, u1, v1, u2, v2, SHADE_NORTH_SOUTH);
        face(vertices, 2, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, u2, v1, u3, v2, SHADE_EAST_WEST);
        face(vertices, 3, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, u3, v1, u4, v2, SHADE_NORTH_SOUTH);
        face(vertices, 4, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u1, v0, u2, v1, SHADE_TOP);
        face(vertices, 5, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, u2, v0, bottomU, v1, SHADE_BOTTOM);
        return vertices;
    }

    /** Viết hai tam giác của mặt, dùng dấu âm độ sáng cho lớp mắt tự phát sáng. */
    private void face(float[] out, int index, float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz,
                      float uMin, float vMin, float uMax, float vMax, float shade) {
        int i = index * 6 * STRIDE;
        float faceShade = layer == Layer.EYES ? -1f : shade;
        vertex(out, i, ax, ay, az, uMin, vMin, faceShade);
        vertex(out, i + STRIDE, bx, by, bz, uMin, vMax, faceShade);
        vertex(out, i + 2 * STRIDE, cx, cy, cz, uMax, vMax, faceShade);
        vertex(out, i + 3 * STRIDE, ax, ay, az, uMin, vMin, faceShade);
        vertex(out, i + 4 * STRIDE, cx, cy, cz, uMax, vMax, faceShade);
        vertex(out, i + 5 * STRIDE, dx, dy, dz, uMax, vMin, faceShade);
    }

    /** Ghi một đỉnh theo định dạng shader. */
    private static void vertex(float[] out, int i, float x, float y, float z, float u, float v, float shade) {
        out[i] = x;
        out[i + 1] = y;
        out[i + 2] = z;
        out[i + 3] = u;
        out[i + 4] = v;
        out[i + 5] = shade;
    }

    /** Đọc hình học để tạo khóa mesh dùng chung. */
    Geometry geometry() { return geometry; }
    /** Đọc các con đã cố định khi khởi tạo. */
    ModelPart[] children() { return children; }
    /** Đọc vật liệu của bộ phận. */
    Layer layer() { return layer; }
    /** Báo bộ phận có mặt trong tư thế hiện tại. */
    boolean isVisible() { return visible; }
}
