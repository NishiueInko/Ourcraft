package com.voxelgame.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Quản lý việc biên dịch (Compile), liên kết (Link) chương trình GLSL Shader trên GPU
 * và truyền các biến Uniform từ Java xuống Card đồ họa, triển khai giao diện {@link Cleanable}.
 *
 * THỂ HIỆN CÁC TÍNH CHẤT OOP:
 * 1. Tính Trừu tượng (Abstraction):
 *    - Ẩn toàn bộ lời gọi hàm OpenGL cấp thấp (`glCreateShader`, `glShaderSource`, `glCompileShader`,
 *      `glLinkProgram`, `glGetUniformLocation`) đằng sau các phương thức trực quan như
 *      `enable3DWorldMode()`, `enable2DUIMode()`, `setUniformMatrix4f()`.
 * 2. Tính Đa hình lúc biên dịch (Compile-time Polymorphism / Method Overloading):
 *    - Nạp chồng phương thức `setUniformVec4(String, Vector4f)` và `setUniformVec4(String, float, float, float, float)`
 *      giúp truyền màu sắc hoặc vector 4 chiều linh hoạt theo ngữ cảnh sử dụng.
 * 3. Tính Đóng gói (Encapsulation):
 *    - Đóng gói `programId` và bộ nhớ đệm `uniformLocations` ở phạm vi `private final`.
 */
public class ShaderProgram implements Cleanable {

    private final int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

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
            out float vFogDist;

            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;

            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                vec4 viewPos = uView * worldPos;

                gl_Position = uProjection * viewPos;

                vTexCoord = aTexCoord;
                vShade = aShade;
                // Khoảng cách Euclid từ mắt Camera (gốc tọa độ trong View Space) tới điểm hiện tại
                vFogDist = length(viewPos.xyz);
            }
            """;

    /**
     * Mã nguồn Fragment Shader (GLSL 330 Core).
     */
    private static final String FRAGMENT_SHADER_SOURCE = """
            #version 330 core
            in vec2 vTexCoord;
            in float vShade;
            in float vFogDist;

            out vec4 FragColor;

            uniform sampler2D uTexture;
            uniform int uUseTexture;
            uniform int uUseFog;
            uniform vec4 uTintColor;

            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;

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

                // 3. Nếu đang bật sương mù (khi vẽ 3D World), hòa trộn màu khối với màu bầu trời ở xa
                vec3 finalRgb = litColor;
                if (uUseFog == 1) {
                    float denom = max(0.0001, uFogEnd - uFogStart);
                    float fogFactor = clamp((uFogEnd - vFogDist) / denom, 0.0, 1.0);
                    finalRgb = mix(uFogColor, litColor, fogFactor);
                }

                FragColor = vec4(finalRgb, baseColor.a);
            }
            """;

    public ShaderProgram() {
        programId = glCreateProgram();
        if (programId == 0) {
            throw new RuntimeException("Không thể khởi tạo chương trình OpenGL Shader!");
        }

        int vertexShaderId = compileShader(VERTEX_SHADER_SOURCE, GL_VERTEX_SHADER);
        int fragmentShaderId = compileShader(FRAGMENT_SHADER_SOURCE, GL_FRAGMENT_SHADER);

        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Lỗi khi liên kết Shader Program: " + glGetProgramInfoLog(programId, 1024));
        }

        glValidateProgram(programId);
        glDetachShader(programId, vertexShaderId);
        glDetachShader(programId, fragmentShaderId);
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);

        bind();
        setUniform1i("uTexture", 0);
        setUniform1i("uUseTexture", 1);
        setUniform1i("uUseFog", 1);
        setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        unbind();
    }

    private int compileShader(String shaderCode, int shaderType) {
        int shaderId = glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new RuntimeException("Lỗi khi tạo đối tượng Shader kiểu: " + shaderType);
        }

        glShaderSource(shaderId, shaderCode);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Lỗi biên dịch mã GLSL: " + glGetShaderInfoLog(shaderId, 1024));
        }

        return shaderId;
    }

    private int getUniformLocation(String uniformName) {
        return uniformLocations.computeIfAbsent(uniformName, name -> {
            int location = glGetUniformLocation(programId, name);
            if (location < 0) {
                System.err.println("Cảnh báo: Không tìm thấy biến Uniform '" + name + "' trong Shader.");
            }
            return location;
        });
    }

    public void setUniformMatrix4f(String name, Matrix4f matrix) {
        try (MemoryStack stack = stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            matrix.get(fb);
            glUniformMatrix4fv(getUniformLocation(name), false, fb);
        }
    }

    public void setUniformVec3(String name, Vector3f vec) {
        glUniform3f(getUniformLocation(name), vec.x, vec.y, vec.z);
    }

    /**
     * Đa hình lúc biên dịch (Method Overloading 1): Truyền Vector4f vào biến Uniform.
     */
    public void setUniformVec4(String name, Vector4f vec) {
        glUniform4f(getUniformLocation(name), vec.x, vec.y, vec.z, vec.w);
    }

    /**
     * Đa hình lúc biên dịch (Method Overloading 2): Truyền trực tiếp 4 thành phần (R, G, B, A) vào biến Uniform.
     */
    public void setUniformVec4(String name, float r, float g, float b, float a) {
        glUniform4f(getUniformLocation(name), r, g, b, a);
    }

    public void setUniform1f(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }

    public void setUniform1i(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }

    /**
     * Chuyển nhanh Shader sang chế độ vẽ Thế giới 3D (Bật Texture Atlas, bật Sương mù, màu gốc).
     */
    public void enable3DWorldMode() {
        setUniform1i("uUseTexture", 1);
        setUniform1i("uUseFog", 1);
        setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Chuyển nhanh Shader sang chế độ vẽ Giao diện 2D (Tắt Sương mù, đặt View & Model về ma trận đơn vị).
     */
    public void enable2DUIMode(Matrix4f orthoProjectionMatrix) {
        Matrix4f identity = new Matrix4f().identity();
        setUniformMatrix4f("uProjection", orthoProjectionMatrix);
        setUniformMatrix4f("uView", identity);
        setUniformMatrix4f("uModel", identity);
        setUniform1i("uUseFog", 0);
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    @Override
    public void cleanup() {
        unbind();
        if (programId != 0) {
            glDeleteProgram(programId);
        }
    }
}
