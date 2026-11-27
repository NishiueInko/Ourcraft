package com.voxelgame.client.model;

import com.voxelgame.client.render.Texture;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import java.util.Objects;

/** Hộp pixel hoặc mặt phẳng trang bị trong cây khớp, với mặt trước theo -Z và trục đứng theo +Y. */
public final class ModelPart {
    /** Chọn vật liệu của bộ phận trong mỗi lượt vẽ mô hình. */
    public enum Layer { BASE, OVERLAY, EYES, EQUIPMENT }

    /** Chiều trải ảnh gốc của hộp, kể cả thân nằm ngang, chi trái đối xứng và sprite phẳng. */
    public enum UvOrientation { OURCRAFT, MINECRAFT, HORIZONTAL, MIRRORED, FLAT_SPRITE }

    /** Dạng hình học dựng lưới đỉnh của bộ phận. */
    public enum Shape { BOX, FLAT_SPRITE, CROSSED_POTION, CROSSED_ARROW }

    /** Kích thước hình trải tính theo ảnh nguồn, độc lập với tỉ lệ hộp khi vẽ. */
    public record CuboidUv(int u, int v, int width, int height, int depth, UvOrientation orientation) {
    }

    /** Độ sáng tương đối của sáu mặt theo quy ước hình học chunk. */
    private static final float SHADE_TOP = 1f;
    private static final float SHADE_BOTTOM = 0.55f;
    private static final float SHADE_NORTH_SOUTH = 0.85f;
    private static final float SHADE_EAST_WEST = 0.72f;
    /** Định dạng XYZ, UV, độ sáng của shader thế giới. */
    public static final int STRIDE = 6;
    static final int VERTEX_COUNT = 36;

    /** Thông số bất biến để chia sẻ mesh giữa các bộ phận có cùng hình học và UV. */
    public record Geometry(float x, float y, float z, int width, int height, int depth,
                    float inflate, CuboidUv uv, Shape shape) {
    }

    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;
    private final Geometry geometry;
    private final ModelPart[] children;
    private float baseRotationX;
    private float baseRotationY;
    private float baseRotationZ;
    private float rotationX;
    private float rotationY;
    private float rotationZ;
    private float translationX;
    private float translationY;
    private float translationZ;
    private boolean visible = true;
    private boolean doubleSided;
    private Layer layer = Layer.BASE;

    /** Lưu điểm xoay, kích thước hộp và tọa độ pixel của sơ đồ trải UV theo bố cục Ourcraft. */
    public ModelPart(float pivotX, float pivotY, float pivotZ, float offsetX, float offsetY, float offsetZ,
                     int sizeX, int sizeY, int sizeZ, int texU, int texV, float inflate, ModelPart... children) {
        this(pivotX, pivotY, pivotZ, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ, inflate,
                new CuboidUv(texU, texV, sizeX, sizeY, sizeZ, UvOrientation.OURCRAFT), Shape.BOX, children);
    }

    /** Lưu hộp hiển thị cùng kích thước và chiều trải UV của ảnh nguồn. */
    public ModelPart(float pivotX, float pivotY, float pivotZ, float offsetX, float offsetY, float offsetZ,
              int sizeX, int sizeY, int sizeZ, float inflate, CuboidUv uv, ModelPart... children) {
        this(pivotX, pivotY, pivotZ, offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ, inflate,
                uv, Shape.BOX, children);
    }

    /** Lưu bộ phận với dạng hình học chỉ định (hộp, sprite phẳng hoặc chữ thập đạn đạo). */
    ModelPart(float pivotX, float pivotY, float pivotZ, float offsetX, float offsetY, float offsetZ,
              int sizeX, int sizeY, int sizeZ, float inflate, CuboidUv uv, Shape shape, ModelPart... children) {
        int shortestSide = Math.min(sizeX, Math.min(sizeY, sizeZ));
        float expandedSide = shortestSide + 2 * inflate;
        // Râu dê, đuôi lạc đà và sprite phẳng dùng mặt có độ dày bằng 0. Hộp Ourcraft vẫn dùng hộp đặc.
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0 || uv.u < 0 || uv.v < 0
                || uv.width < 0 || uv.height < 0 || uv.depth < 0
                || !Float.isFinite(inflate) || expandedSide < 0f
                || (uv.orientation == UvOrientation.OURCRAFT && (shortestSide == 0 || expandedSide == 0f))) {
            throw new IllegalArgumentException("Kích thước hộp, độ phồng hoặc gốc UV không hợp lệ");
        }
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        geometry = new Geometry(offsetX, offsetY, offsetZ, sizeX, sizeY, sizeZ, inflate, uv, shape);
        this.children = children.clone();
    }

    /** Vật liệu được đặt khi dựng mẫu, độc lập với tư thế từng cá thể. */
    public ModelPart setLayer(Layer layer) {
        this.layer = Objects.requireNonNull(layer);
        return this;
    }

    /** Đánh dấu bộ phận cần vẽ cả hai mặt (tắt culling) cho mặt phẳng hoặc phần có khe rỗng. */
    public ModelPart setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        return this;
    }

    /** Đặt góc nghỉ mặc định theo độ để {@link #resetPose()} luôn khôi phục về góc chuẩn của loài. */
    public ModelPart setBaseRotation(float x, float y, float z) {
        baseRotationX = x;
        baseRotationY = y;
        baseRotationZ = z;
        rotationX = x;
        rotationY = y;
        rotationZ = z;
        return this;
    }

    /** Đặt góc quay Euler cục bộ theo độ trong tư thế hiện tại. */
    public void setRotation(float x, float y, float z) {
        rotationX = x;
        rotationY = y;
        rotationZ = z;
    }

    /** Dời điểm xoay theo pixel cục bộ, ví dụ để hạ thân khi ngồi hoặc trượt đầu Enderman. */
    public void setTranslation(float x, float y, float z) {
        translationX = x;
        translationY = y;
        translationZ = z;
    }

    /** Ẩn bộ phận cùng các con của nó trong tư thế hiện tại. */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** Dọn tư thế của cá thể trước về đúng góc nghỉ mặc định để dùng lại cho cá thể kế tiếp. */
    public void resetPose() {
        rotationX = baseRotationX;
        rotationY = baseRotationY;
        rotationZ = baseRotationZ;
        translationX = translationY = translationZ = 0f;
        visible = true;
        for (ModelPart child : children) {
            child.resetPose();
        }
    }

    /** Ghi phép biến đổi từ cha vào bộ đệm được truyền vào, giữ nguyên ma trận của cha. */
    public void writeTransform(Matrix4 parent, Matrix4 out) {
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

    /** Cho biết vùng pixel nguồn có nằm hoàn toàn trong tấm ảnh vật liệu hay không. */
    public boolean fitsTexture(int width, int height) {
        CuboidUv uv = geometry.uv;
        if (geometry.shape == Shape.CROSSED_ARROW) {
            return (long) uv.u + 16L <= width && (long) uv.v + 10L <= height;
        }
        if (geometry.shape != Shape.BOX || uv.orientation == UvOrientation.FLAT_SPRITE) {
            return (long) uv.u + uv.width <= width && (long) uv.v + uv.height <= height;
        }
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

    /** Ghi đỉnh của khối hộp, mặt phẳng hoặc chữ thập đạn đạo với UV chuẩn Minecraft. */
    public float[] buildVertices(int texWidth, int texHeight) {
        if (!fitsTexture(texWidth, texHeight)) {
            throw new IllegalArgumentException("Texture không đủ kích thước cho bộ phận");
        }
        return switch (geometry.shape) {
            case FLAT_SPRITE -> buildFlatSpriteVertices(texWidth, texHeight);
            case CROSSED_POTION -> buildCrossedPotionVertices(texWidth, texHeight);
            case CROSSED_ARROW -> buildCrossedArrowVertices(texWidth, texHeight);
            case BOX -> buildBoxVertices(texWidth, texHeight);
        };
    }

    /** Dựng một mặt phẳng 2D đơn sắc theo UV trực tiếp trên tấm sprite trang bị. */
    private float[] buildFlatSpriteVertices(int texWidth, int texHeight) {
        CuboidUv uv = geometry.uv;
        float x0 = geometry.x;
        float x1 = geometry.x + geometry.width;
        float y0 = geometry.y;
        float y1 = geometry.y + geometry.height;
        float z = geometry.z + geometry.depth * 0.5f;
        float u0 = (float) uv.u / texWidth;
        float u1 = (float) (uv.u + uv.width) / texWidth;
        float v0 = (float) uv.v / texHeight;
        float v1 = (float) (uv.v + uv.height) / texHeight;
        float[] vertices = new float[6 * STRIDE];
        face(vertices, 0, x1, y1, z, x1, y0, z, x0, y0, z, x0, y1, z, u0, v0, u1, v1, SHADE_TOP);
        return vertices;
    }

    /** Dựng hai mặt phẳng giao nhau hình chữ X cho lọ thuốc ném chuẩn Minecraft. */
    private float[] buildCrossedPotionVertices(int texWidth, int texHeight) {
        CuboidUv uv = geometry.uv;
        float x0 = geometry.x;
        float x1 = geometry.x + geometry.width;
        float y0 = geometry.y;
        float y1 = geometry.y + geometry.height;
        float z0 = geometry.z;
        float z1 = geometry.z + geometry.depth;
        float u0 = (float) uv.u / texWidth;
        float u1 = (float) (uv.u + uv.width) / texWidth;
        float v0 = (float) uv.v / texHeight;
        float v1 = (float) (uv.v + uv.height) / texHeight;
        float[] vertices = new float[12 * STRIDE];
        face(vertices, 0, x0, y1, z0, x0, y0, z0, x1, y0, z1, x1, y1, z1, u0, v0, u1, v1, SHADE_TOP);
        face(vertices, 1, x1, y1, z0, x1, y0, z0, x0, y0, z1, x0, y1, z1, u0, v0, u1, v1, SHADE_NORTH_SOUTH);
        return vertices;
    }

    /** Dựng mũi tên chữ X chuẩn Minecraft từ tấm 32×32 (hai cánh dọc + hai cánh đuôi ngang). */
    private float[] buildCrossedArrowVertices(int texWidth, int texHeight) {
        float x0 = geometry.x;
        float x1 = geometry.x + geometry.width;
        float y0 = geometry.y;
        float y1 = geometry.y + geometry.height;
        float z0 = geometry.z;
        float z1 = geometry.z + geometry.depth;
        float tailZ = z1 - 1.5f;

        // Thân tên nằm ngang từ (0, 0) đến (16, 5) trong tấm 32x32, đầu tên tại u=16, đuôi tại u=0.
        float bodyU0 = 0f / texWidth;
        float bodyU1 = 16f / texWidth;
        float bodyV0 = 0f / texHeight;
        float bodyV1 = 5f / texHeight;
        // Chữ thập lông đuôi tại (0, 5) kích thước 5x5.
        float tailU0 = 0f / texWidth;
        float tailU1 = 5f / texWidth;
        float tailV0 = 5f / texHeight;
        float tailV1 = 10f / texHeight;

        float[] vertices = new float[18 * STRIDE];
        // Cánh chéo 1 (+X/+Y nối -X/-Y) dọc theo trục Z, đầu nhọn quay về -Z.
        face(vertices, 0, x1, y1, z0, x0, y0, z0, x0, y0, z1, x1, y1, z1,
                bodyU1, bodyV0, bodyU0, bodyV1, SHADE_TOP);
        // Cánh chéo 2 (-X/+Y nối +X/-Y) dọc theo trục Z.
        face(vertices, 1, x0, y1, z0, x1, y0, z0, x1, y0, z1, x0, y1, z1,
                bodyU1, bodyV0, bodyU0, bodyV1, SHADE_NORTH_SOUTH);
        // Hai mặt lông đuôi vuông góc tại cuối mũi tên.
        face(vertices, 2, x1, y1, tailZ, x1, y0, tailZ, x0, y0, tailZ, x0, y1, tailZ,
                tailU0, tailV0, tailU1, tailV1, SHADE_EAST_WEST);
        return vertices;
    }

    /** Ghi sáu mặt đúng chiều pháp tuyến và đúng chiều UV Minecraft (không lật gương). */
    private float[] buildBoxVertices(int texWidth, int texHeight) {
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

        if (vanilla) {
            // Hệ trục VoxelGame: -Z là mặt trước, +X là bên phải của mob (trái màn hình khi nhìn trực diện), +Y lên trên.
            face(vertices, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1,
                    u2, v1, u3, v2, SHADE_EAST_WEST);
            face(vertices, 1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0,
                    u1, v1, u2, v2, horizontal ? SHADE_BOTTOM : SHADE_NORTH_SOUTH);
            face(vertices, 2, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0,
                    u0, v1, u1, v2, SHADE_EAST_WEST);
            face(vertices, 3, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1,
                    u3, v1, u4, v2, horizontal ? SHADE_TOP : SHADE_NORTH_SOUTH);
            face(vertices, 4, x1, y1, z1, x1, y1, z0, x0, y1, z0, x0, y1, z1,
                    u1, v0, u2, v1, horizontal ? SHADE_NORTH_SOUTH : SHADE_TOP);
            face(vertices, 5, x1, y0, z0, x1, y0, z1, x0, y0, z1, x0, y0, z0,
                    u2, v1, bottomU, v0, horizontal ? SHADE_NORTH_SOUTH : SHADE_BOTTOM);
        } else {
            face(vertices, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1,
                    u0, v1, u1, v2, SHADE_EAST_WEST);
            face(vertices, 1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0,
                    u1, v1, u2, v2, horizontal ? SHADE_BOTTOM : SHADE_NORTH_SOUTH);
            face(vertices, 2, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0,
                    u2, v1, u3, v2, SHADE_EAST_WEST);
            face(vertices, 3, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1,
                    u3, v1, u4, v2, horizontal ? SHADE_TOP : SHADE_NORTH_SOUTH);
            face(vertices, 4, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                    u1, v0, u2, v1, horizontal ? SHADE_NORTH_SOUTH : SHADE_TOP);
            face(vertices, 5, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1,
                    u2, v0, bottomU, v1, horizontal ? SHADE_NORTH_SOUTH : SHADE_BOTTOM);
        }

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
    public Geometry geometry() { return geometry; }
    /** Đọc các con đã cố định khi khởi tạo. */
    public ModelPart[] children() { return children; }
    /** Đọc vật liệu của bộ phận. */
    public Layer layer() { return layer; }
    /** Báo bộ phận có mặt trong tư thế hiện tại. */
    public boolean isVisible() { return visible; }
    /** Báo bộ phận có cần vẽ hai mặt không bị face culling hay không. */
    public boolean isDoubleSided() { return doubleSided; }
}
