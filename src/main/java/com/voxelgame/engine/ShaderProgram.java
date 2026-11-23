package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/**
 * Quản lý việc biên dịch (Compile), liên kết (Link) chương trình GLSL Shader 330 Core trên GPU
 * và truyền các biến Uniform từ Java xuống GPU thông qua libGDX, triển khai {@link Disposable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Ẩn toàn bộ thao tác GLSL cấp thấp đằng sau các phương thức trực quan như
 *      `enable3DWorldMode()`, `enable2DUIMode()`, `setUniformMatrix4f()`.
 * 2. Tính Đa hình lúc biên dịch (Compile-time Polymorphism / Method Overloading):
 *    - Nạp chồng phương thức `setUniformVec4(String, float, float, float, float)` và
 *      `setUniformVec3(String, Vector3)` giúp truyền màu sắc hoặc vector linh hoạt.
 * 3. Tính Đóng gói (Encapsulation):
 *    - Đóng gói đối tượng `com.badlogic.gdx.graphics.glutils.ShaderProgram` ở phạm vi `private final`.
 */
public class ShaderProgram implements Disposable {

    private final com.badlogic.gdx.graphics.glutils.ShaderProgram gdxShader;
    private final Matrix4 identityMatrix = new Matrix4();

    /**
     * Mã nguồn Vertex Shader (GLSL 330 Core).
     */
    private static final String VERTEX_SHADER_SOURCE = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in float aShade;

            out vec2 vTexCoord;
            out float vShade;

            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;

            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                vec4 viewPos = uView * worldPos;

                gl_Position = uProjection * viewPos;

                vTexCoord = aTexCoord;
                vShade = aShade;
            }
            """;

    /**
     * Mã nguồn Fragment Shader (GLSL 330 Core).
     */
    private static final String FRAGMENT_SHADER_SOURCE = """
            #version 330 core
            in vec2 vTexCoord;
            in float vShade;

            out vec4 FragColor;

            uniform sampler2D uTexture;
            uniform int uUseTexture;
            uniform vec4 uTintColor;

            void main() {
                // 1. Xác định màu cơ sở (lấy từ Texture Atlas hoặc dùng màu thuần túy cho 2D UI)
                vec4 baseColor;
                if (uUseTexture == 1) {
                    baseColor = texture(uTexture, vTexCoord) * uTintColor;
                } else {
                    baseColor = uTintColor;
                }

                // Loại bỏ các điểm ảnh hoàn toàn trong suốt (Alpha Testing cơ bản)
                if (baseColor.a < 0.05) {
                    discard;
                }

                // 2. Nhân với hệ số độ sáng theo hướng mặt khối (Ambient/Directional Face Shading)
                vec3 litColor = baseColor.rgb * vShade;

                FragColor = vec4(litColor, baseColor.a);
            }
            """;

    public ShaderProgram() {
        // Tắt chuỗi tiền tố mặc định của libGDX để giữ nguyên #version 330 core ở dòng 1
        com.badlogic.gdx.graphics.glutils.ShaderProgram.prependVertexCode = "";
        com.badlogic.gdx.graphics.glutils.ShaderProgram.prependFragmentCode = "";

        gdxShader = new com.badlogic.gdx.graphics.glutils.ShaderProgram(
                VERTEX_SHADER_SOURCE,
                FRAGMENT_SHADER_SOURCE
        );

        if (!gdxShader.isCompiled()) {
            throw new RuntimeException("Lỗi biên dịch GLSL Shader:\n" + gdxShader.getLog());
        }

        bind();
        setUniform1i("uTexture", 0);
        setUniform1i("uUseTexture", 1);
        setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        unbind();
    }

    public void setUniformMatrix4f(String name, Matrix4 matrix) {
        gdxShader.setUniformMatrix(name, matrix);
    }

    public void setUniformVec3(String name, Vector3 vec) {
        gdxShader.setUniformf(name, vec);
    }

    public void setUniformVec3(String name, float x, float y, float z) {
        gdxShader.setUniformf(name, x, y, z);
    }

    public void setUniformVec4(String name, float r, float g, float b, float a) {
        gdxShader.setUniformf(name, r, g, b, a);
    }

    public void setUniform1f(String name, float value) {
        gdxShader.setUniformf(name, value);
    }

    public void setUniform1i(String name, int value) {
        gdxShader.setUniformi(name, value);
    }

    /**
     * Chuyển Shader sang chế độ vẽ thế giới 3D với Texture Atlas và màu gốc.
     */
    public void enable3DWorldMode() {
        setUniform1i("uUseTexture", 1);
        setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Chuyển Shader sang phép chiếu 2D và đặt View, Model về ma trận đơn vị.
     */
    public void enable2DUIMode(Matrix4 orthoProjectionMatrix) {
        setUniformMatrix4f("uProjection", orthoProjectionMatrix);
        setUniformMatrix4f("uView", identityMatrix);
        setUniformMatrix4f("uModel", identityMatrix);
    }

    public void bind() {
        gdxShader.bind();
    }

    public void unbind() {
        Gdx.gl.glUseProgram(0);
    }

    public com.badlogic.gdx.graphics.glutils.ShaderProgram getGdxShader() {
        return gdxShader;
    }

    @Override
    public void dispose() {
        if (gdxShader != null) {
            gdxShader.dispose();
        }
    }
}
