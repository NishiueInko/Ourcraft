package com.voxelgame.engine;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/** Hộp pixel trong cây khớp, với mặt trước theo -Z và trục đứng theo +Y. */
public final class ModelPart {
    /** Chọn vật liệu của bộ phận trong mỗi lượt vẽ mô hình. */
    public enum Layer { BASE, OVERLAY, EYES, EQUIPMENT }

    /** Chiều trải ảnh gốc của hộp, kể cả thân nằm ngang và chi trái đối xứng. */
    enum UvOrientation { OURCRAFT, MINECRAFT, HORIZONTAL, MIRRORED }

    /** Kích thước hình trải tính theo ảnh nguồn, độc lập với tỉ lệ hộp khi vẽ. */
    record CuboidUv(int u, int v, int width, int height, int depth, UvOrientation orientation) {
    }

    /** Độ sáng tương đối của sáu mặt theo quy ước hình học chunk. */
    private static final float SHADE_TOP = 1f;
    private static final float SHADE_BOTTOM = 0.55f;
    private static final float SHADE_NORTH_SOUTH = 0.85f;
    private static final float SHADE_EAST_WEST = 0.72f;
    /** Định dạng XYZ, UV, độ sáng của shader thế giới. */
    static final int STRIDE = 6;
    static final int VERTEX_COUNT = 36;

    /** Thông số bất biến để chia sẻ mesh giữa các bộ phận có cùng hình học và UV. */
    record Geometry(float x, float y, float z, int width, int height, int depth, float inflate, CuboidUv uv) {
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
        this(pivotX, pivotY, pivotZ, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ, inflate,
                new CuboidUv(texU, texV, sizeX, sizeY, sizeZ, UvOrientation.OURCRAFT), children);
    }

    /** Lưu hộp hiển thị cùng kích thước và chiều trải UV của ảnh nguồn. */
    ModelPart(float pivotX, float pivotY, float pivotZ, float offsetX, float offsetY, float offsetZ,
              int sizeX, int sizeY, int sizeZ, float inflate, CuboidUv uv, ModelPart... children) {
        int shortestSide = Math.min(sizeX, Math.min(sizeY, sizeZ));
        float expandedSide = shortestSide + 2 * inflate;
        // Râu dê và đuôi lạc đà dùng mặt phẳng. Hình học Ourcraft vẫn dùng hộp đặc.
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0 || uv.u < 0 || uv.v < 0
                || uv.width < 0 || uv.height < 0 || uv.depth < 0
                || !Float.isFinite(inflate) || expandedSide < 0f
                || (uv.orientation == UvOrientation.OURCRAFT && (shortestSide == 0 || expandedSide == 0f))) {
            throw new IllegalArgumentException("Kích thước hộp, độ phồng hoặc gốc UV không hợp lệ");
        }
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        geometry = new Geometry(offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ, inflate, uv);
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
        CuboidUv uv = geometry.uv;
        return (long) uv.u + 2L * (uv.width + (long) uv.depth) <= width
                && (long) uv.v + uv.depth + uv.height <= height;
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

        CuboidUv uv = geometry.uv;
        float u0 = (float) uv.u / texWidth;
        float u1 = (float) (uv.u + uv.depth) / texWidth;
        float u2 = (float) (uv.u + uv.depth + uv.width) / texWidth;
        float u3 = (float) (uv.u + 2 * uv.depth + uv.width) / texWidth;
        float u4 = (float) (uv.u + 2 * (uv.depth + uv.width)) / texWidth;
        float bottomU = (float) (uv.u + uv.depth + 2 * uv.width) / texWidth;
        float v0 = (float) uv.v / texHeight;
        float v1 = (float) (uv.v + uv.depth) / texHeight;
        float v2 = (float) (uv.v + uv.depth + uv.height) / texHeight;
        float[] vertices = new float[VERTEX_COUNT * STRIDE];
        boolean horizontal = uv.orientation == UvOrientation.HORIZONTAL;
        boolean vanilla = uv.orientation != UvOrientation.OURCRAFT;
        if (horizontal) {
            // Dựng trong hệ của hình trải rồi xoay quanh X để đặt thân lên bốn chân.
            float bottom = y0;
            float top = y1;
            y0 = -z1;
            y1 = -z0;
            z0 = bottom;
            z1 = top;
        }

        face(vertices, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1,
                vanilla ? u1 : u0, v1, vanilla ? u0 : u1, v2, SHADE_EAST_WEST);
        face(vertices, 1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0,
                vanilla ? u2 : u1, v1, vanilla ? u1 : u2, v2, horizontal ? SHADE_BOTTOM : SHADE_NORTH_SOUTH);
        face(vertices, 2, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0,
                vanilla ? u3 : u2, v1, vanilla ? u2 : u3, v2, SHADE_EAST_WEST);
        face(vertices, 3, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1,
                vanilla ? u4 : u3, v1, vanilla ? u3 : u4, v2, horizontal ? SHADE_TOP : SHADE_NORTH_SOUTH);
        face(vertices, 4, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                u1, vanilla ? v1 : v0, u2, vanilla ? v0 : v1, horizontal ? SHADE_NORTH_SOUTH : SHADE_TOP);
        face(vertices, 5, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1,
                u2, v0, bottomU, v1, horizontal ? SHADE_NORTH_SOUTH : SHADE_BOTTOM);

        if (uv.orientation == UvOrientation.MIRRORED) {
            // Phản chiếu vị trí đi kèm UV và đảo thứ tự đỉnh để mặt vẫn quay ra ngoài.
            float twiceCenterX = 2f * geometry.x + geometry.width;
            for (int i = 0; i < vertices.length; i += 3 * STRIDE) {
                for (int vertex = i; vertex < i + 3 * STRIDE; vertex += STRIDE) {
                    vertices[vertex] = twiceCenterX - vertices[vertex];
                }
                for (int attribute = 0; attribute < STRIDE; attribute++) {
                    int first = i + STRIDE + attribute;
                    int second = first + STRIDE;
                    float value = vertices[first];
                    vertices[first] = vertices[second];
                    vertices[second] = value;
                }
            }
        }
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
    private void vertex(float[] out, int i, float x, float y, float z, float u, float v, float shade) {
        out[i] = x;
        boolean horizontal = geometry.uv.orientation == UvOrientation.HORIZONTAL;
        out[i + 1] = horizontal ? z : y;
        out[i + 2] = horizontal ? -y : z;
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
