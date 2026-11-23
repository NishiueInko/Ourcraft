package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.Disposable;

/** Bộ đệm tam giác dùng lại cho vòm trời, thiên thể, mây và các dải giáng thủy. */
final class EnvironmentMesh implements Disposable {
    /** Cùng định dạng sáu số thực với shader của thế giới. */
    private static final int STRIDE = 6;
    private final Mesh mesh;
    private final float[] data;
    private int size;

    /** Cấp phát một lần theo số tứ giác tối đa của một lượt vẽ. */
    EnvironmentMesh(int maxQuads) {
        int maxVertices = maxQuads * 6;
        mesh = new Mesh(Mesh.VertexDataType.VertexBufferObjectWithVAO, false, maxVertices, 0,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "aPos"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "aTexCoord"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "aShade"));
        data = new float[maxVertices * STRIDE];
    }

    /** Đưa con trỏ về đầu mảng để dựng hình cho lượt kế tiếp. */
    void clear() {
        size = 0;
    }

    /** Thêm hai tam giác theo thứ tự góc trên trái, dưới trái, dưới phải, trên phải. */
    void quad(float ax, float ay, float az, float bx, float by, float bz,
              float cx, float cy, float cz, float dx, float dy, float dz,
              float u0, float v0, float u1, float v1, float shade) {
        vertex(ax, ay, az, u0, v0, shade);
        vertex(bx, by, bz, u0, v1, shade);
        vertex(cx, cy, cz, u1, v1, shade);
        vertex(ax, ay, az, u0, v0, shade);
        vertex(cx, cy, cz, u1, v1, shade);
        vertex(dx, dy, dz, u1, v0, shade);
    }

    /** Ghi tọa độ, UV và hệ số sáng vào vị trí đỉnh kế tiếp. */
    private void vertex(float x, float y, float z, float u, float v, float shade) {
        data[size++] = x;
        data[size++] = y;
        data[size++] = z;
        data[size++] = u;
        data[size++] = v;
        data[size++] = shade;
    }

    /** Gửi phần mảng đang dùng tới VBO. */
    void upload() {
        if (size > 0) {
            mesh.setVertices(data, 0, size);
        }
    }

    /** Vẽ số đỉnh vừa dựng, ngay cả khi VBO còn chứa dữ liệu từ lượt trước. */
    void draw(ShaderProgram shader) {
        if (size > 0) {
            mesh.render(shader.getGdxShader(), GL20.GL_TRIANGLES, 0, size / STRIDE);
        }
    }

    /** Đọc ảnh từ classpath; cạnh lặp dùng cho mây và giáng thủy. */
    static Texture loadTexture(String name, Texture.TextureWrap wrapU, Texture.TextureWrap wrapV) {
        Texture texture = new Texture(Gdx.files.classpath("textures/environment/" + name));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texture.setWrap(wrapU, wrapV);
        return texture;
    }

    /** Hủy VAO và VBO của lượt vẽ. */
    @Override
    public void dispose() {
        mesh.dispose();
    }
}
