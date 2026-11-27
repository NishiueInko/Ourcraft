package com.voxelgame.client.render;

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
    /** Giá trị {@code uWorldLight} hiện tại để các bộ vẽ tự phát sáng lưu và khôi phục an toàn. */
    private final Vector3 currentWorldLight = new Vector3(1f, 1f, 1f);

    /**
     * Mã nguồn Vertex Shader (GLSL 330 Core).
     */
    private static final String VERTEX_SHADER_SOURCE = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in float aShade;
            layout (location = 3) in vec4 aColor;

            out vec2 vTexCoord;
            out float vShade;
            out vec3 vPosition;
            out vec3 vEyeOffset;
            out vec4 vColor;

            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProjection;
            uniform vec3 uFogEye;
            uniform int uUseVertexColor;

            void main() {
                vec4 worldPos = uModel * vec4(aPos, 1.0);
                vec4 viewPos = uView * worldPos;

                gl_Position = uProjection * viewPos;

                vTexCoord = aTexCoord;
                vShade = aShade;
                vPosition = aPos;
                // Dùng tọa độ sau biến đổi model để cả mesh chunk và thực thể nhận cùng khoảng cách.
                vEyeOffset = worldPos.xyz - uFogEye;
                // Chỉ đọc aColor khi vẽ chunk (7 floats/đỉnh); các mesh 6 floats mặc định dùng trắng (1, 1, 1, 1).
                if (uUseVertexColor == 1) {
                    vColor = aColor.a > 0.992
                            ? vec4(aColor.rgb, 1.0)
                            : vec4(aColor.rgb / max(aColor.a, 0.0039), 1.0);
                } else {
                    vColor = vec4(1.0);
                }
            }
            """;

    /**
     * Mã nguồn Fragment Shader (GLSL 330 Core).
     */
    private static final String FRAGMENT_SHADER_SOURCE = """
            #version 330 core
            in vec2 vTexCoord;
            in float vShade;
            in vec3 vPosition;
            in vec3 vEyeOffset;
            in vec4 vColor;

            out vec4 FragColor;

            uniform sampler2D uTexture;
            uniform int uUseTexture;
            uniform vec4 uTintColor;
            uniform float uMobWhiteFlash;
            uniform vec3 uWorldLight;
            uniform int uEnvironmentMode;
            uniform vec3 uSkyColor;
            uniform vec3 uHorizonColor;
            uniform vec3 uSunDirection;
            uniform float uHorizonGlow;
            uniform vec3 uCloudCenter;
            uniform int uFogEnabled;
            uniform vec2 uFogRange;
            uniform vec3 uLiquidFogColor;

            // Dùng chung màu theo hướng nhìn để địa hình xa nối liền với nền trời.
            vec3 atmosphereColor(vec3 direction) {
                float band = 1.0 - smoothstep(0.0, 0.33, abs(direction.y));
                float facing = smoothstep(-0.25, 1.0, dot(direction, uSunDirection));
                float glow = band * facing * uHorizonGlow;
                float zenith = smoothstep(0.0, 0.9, direction.y);
                vec3 sky = mix(uSkyColor, uSkyColor * vec3(0.70, 0.86, 0.98), zenith);
                return mix(sky, uHorizonColor, glow);
            }

            void main() {
                if (uEnvironmentMode == 1) {
                    // Mắt chìm thấy đúng màu nền, kể cả qua khoảng trống giữa các chunk.
                    vec3 sky = uFogEnabled == 2 ? uLiquidFogColor : atmosphereColor(normalize(vPosition));
                    FragColor = vec4(sky, 1.0);
                    return;
                }

                // Thiên thể dùng blend cộng, nên ẩn hẳn bên dưới mặt chất lỏng.
                if (uFogEnabled == 2 && uEnvironmentMode == 3) {
                    discard;
                }

                // 1. Xác định màu cơ sở (lấy từ Texture Atlas hoặc dùng màu thuần túy cho 2D UI)
                vec4 baseColor;
                if (uUseTexture == 1) {
                    baseColor = texture(uTexture, vTexCoord) * uTintColor * vColor;
                } else {
                    baseColor = uTintColor * vColor;
                }

                // Loại bỏ các điểm ảnh hoàn toàn trong suốt (Alpha Testing cơ bản)
                if (uEnvironmentMode == 2) {
                    // Làm mờ mép mặt phẳng mây ngoài vùng nhìn lân cận.
                    float distanceToEye = length(vPosition.xz - uCloudCenter.xz);
                    baseColor.a *= 1.0 - smoothstep(300.0, 500.0, distanceToEye);
                }
                if (uEnvironmentMode == 3) {
                    // Che phần thiên thể nằm dưới đường chân trời.
                    baseColor.a *= smoothstep(-1.0, 1.0, vPosition.y);
                }
                if (uEnvironmentMode == 4) {
                    // Với giáng thủy, hệ số đỉnh điều chỉnh alpha thay cho độ sáng theo mặt.
                    baseColor.a *= vShade;
                }
                if (baseColor.a < (uEnvironmentMode == 0 ? 0.05 : 0.015)) {
                    discard;
                }

                // 2. Nhân với hệ số độ sáng theo hướng mặt khối (Ambient/Directional Face Shading)
                // Dấu âm của hệ số mặt dành cho khối và mắt mob phát sáng tự thân.
                float faceShade = uEnvironmentMode == 4 ? 1.0 : abs(vShade);
                vec3 litColor = baseColor.rgb * faceShade * (vShade < 0.0 ? vec3(1.0) : uWorldLight);
                if (uMobWhiteFlash > 0.0) {
                    litColor = mix(litColor, vec3(1.0), uMobWhiteFlash);
                }

                if (uFogEnabled == 2 || (uEnvironmentMode == 0 && uFogEnabled == 1)) {
                    float distanceToEye = length(vEyeOffset);
                    if (distanceToEye > uFogRange.x) {
                        float fog = clamp((distanceToEye - uFogRange.x) / (uFogRange.y - uFogRange.x), 0.0, 1.0);
                        // Trộn RGB sau khi tính ánh sáng và giữ nguyên độ trong của mặt khối.
                        vec3 fogColor = uFogEnabled == 2 ? uLiquidFogColor
                                : atmosphereColor(vEyeOffset / distanceToEye);
                        litColor = mix(litColor, fogColor, fog);
                        // Mây và giáng thủy xa chìm hẳn vào nền, để giữ màu của hình học phía trước.
                        if (uFogEnabled == 2 && uEnvironmentMode != 0) {
                            baseColor.a *= 1.0 - fog;
                        }
                    }
                }

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
        setUniform1i("uUseVertexColor", 0);
        setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
        setUniform1f("uMobWhiteFlash", 0f);
        setUniformVec3("uWorldLight", 1f, 1f, 1f);
        setUniform1i("uEnvironmentMode", 0);
        disableDistanceFog();
        unbind();
    }

    public void setUniformMatrix4f(String name, Matrix4 matrix) {
        gdxShader.setUniformMatrix(name, matrix);
    }

    public void setUniformVec3(String name, Vector3 vec) {
        if ("uWorldLight".equals(name) && vec != null) {
            currentWorldLight.set(vec);
        }
        gdxShader.setUniformf(name, vec);
    }

    public void setUniformVec3(String name, float x, float y, float z) {
        if ("uWorldLight".equals(name)) {
            currentWorldLight.set(x, y, z);
        }
        gdxShader.setUniformf(name, x, y, z);
    }

    /** Sao chép giá trị {@code uWorldLight} hiện tại vào {@code out} mà không cấp phát bộ nhớ mới. */
    public void copyWorldLight(Vector3 out) {
        if (out != null) {
            out.set(currentWorldLight);
        }
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
        setUniform1i("uUseVertexColor", 0);
        setUniformVec4("uTintColor", 1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Bật hoặc tắt chế độ đọc màu đỉnh {@code aColor} (location 3) khi vẽ mesh chunk.
     * Mặc định tắt ({@code false}) để các mesh 6 floats (UIRenderer, MobRenderer, EnvironmentMesh, ItemEntity)
     * luôn dùng màu trắng {@code vec4(1.0)} mà không bị lỗi định dạng đỉnh.
     */
    public void enableChunkColorMode(boolean enabled) {
        setUniform1i("uUseVertexColor", enabled ? 1 : 0);
    }

    /** Cập nhật vị trí mắt và hai mốc sương, tính bằng block thế giới. */
    public void enableDistanceFog(Vector3 eye, float start, float end) {
        setUniformVec3("uFogEye", eye);
        gdxShader.setUniformf("uFogRange", start, end);
        setUniform1i("uFogEnabled", 1);
    }

    /** Bật sương màu chất lỏng cho hình học, nền trời, mây và giáng thủy. */
    public void enableLiquidFog(Vector3 eye, Camera.LiquidView liquid) {
        setUniformVec3("uFogEye", eye);
        gdxShader.setUniformf("uFogRange", liquid.fogStart, liquid.fogEnd);
        setUniformVec3("uLiquidFogColor", liquid.red, liquid.green, liquid.blue);
        setUniform1i("uFogEnabled", 2);
    }

    /** Kết thúc sương thế giới trước khi chuyển sang lượt vẽ không dùng sương. */
    public void disableDistanceFog() {
        setUniform1i("uFogEnabled", 0);
    }

    /**
     * Chuyển Shader sang phép chiếu 2D và đặt View, Model về ma trận đơn vị.
     */
    public void enable2DUIMode(Matrix4 orthoProjectionMatrix) {
        setUniform1i("uUseVertexColor", 0);
        setUniformVec3("uWorldLight", 1f, 1f, 1f);
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
