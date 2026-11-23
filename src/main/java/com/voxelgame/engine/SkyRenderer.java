package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import static com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge;
import static com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat;

/** Vẽ vòm trời, quỹ đạo thiên thể và tầng mây, đồng thời tính ánh sáng môi trường. */
public final class SkyRenderer implements Disposable {
    /** Vòm và thiên thể đủ xa để dùng chung phép chiếu của camera. */
    private static final float SKY_RADIUS = 400f;
    private static final float ORBIT_RADIUS = 320f;
    /** Mây cố định ở cao độ này, mỗi chu kỳ UV chiếm 768 block thế giới. */
    private static final float CLOUD_HEIGHT = 140f;
    private static final float CLOUD_PERIOD = 768f;
    private static final float CLOUD_RADIUS = 500f;
    /** Thứ tự tám pha từ trăng tròn đến lưỡi liềm cuối tháng. */
    private static final String[] MOON_PHASES = {
            "full_moon", "waning_gibbous", "third_quarter", "waning_crescent",
            "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"
    };

    private final EnvironmentMesh dome = new EnvironmentMesh(6);
    private final EnvironmentMesh quad = new EnvironmentMesh(1);
    private final Matrix4 rotationView = new Matrix4();
    private final Matrix4 identity = new Matrix4();
    private final Vector3 worldLight = new Vector3();
    private Texture sunTexture;
    private Texture cloudTexture;
    private final Texture[] moonTextures = new Texture[MOON_PHASES.length];
    private float daylight;
    private float twilight;

    /** Nạp ảnh và tải vòm tĩnh; giải phóng phần đã nạp nếu khởi tạo thất bại. */
    public SkyRenderer() {
        try {
            sunTexture = EnvironmentMesh.loadTexture("celestial/sun.png", ClampToEdge, ClampToEdge);
            cloudTexture = EnvironmentMesh.loadTexture("clouds.png", Repeat, Repeat);
            for (int i = 0; i < MOON_PHASES.length; i++) {
                moonTextures[i] = EnvironmentMesh.loadTexture(
                        "celestial/moon/" + MOON_PHASES[i] + ".png", ClampToEdge, ClampToEdge);
            }
            buildDome();
        } catch (RuntimeException error) {
            dispose();
            throw error;
        }
    }

    /** Dựng hộp sáu mặt; shader chuẩn hóa vectơ nhìn để tạo màu trời theo hướng. */
    private void buildDome() {
        float a = -SKY_RADIUS;
        float b = SKY_RADIUS;
        dome.quad(a,b,a, a,a,a, b,a,a, b,b,a, 0,0,1,1, -1);
        dome.quad(a,b,b, a,a,b, b,a,b, b,b,b, 0,0,1,1, -1);
        dome.quad(a,b,a, a,a,a, a,a,b, a,b,b, 0,0,1,1, -1);
        dome.quad(b,b,a, b,a,a, b,a,b, b,b,b, 0,0,1,1, -1);
        dome.quad(a,b,a, a,b,b, b,b,b, b,b,a, 0,0,1,1, -1);
        dome.quad(a,a,a, a,a,b, b,a,b, b,a,a, 0,0,1,1, -1);
        dome.upload();
    }

    /** Tính màu đêm, ngày và ánh ấm chuyển tiếp rồi vẽ nền trời không ghi chiều sâu. */
    public void renderSky(ShaderProgram shader, Matrix4 view, WorldTime time, float storm) {
        float sunX = time.sunX();
        float sunY = time.sunY();
        daylight = smoothStep(-0.20f, 0.33f, sunY);
        twilight = 1f - smoothStep(0f, 0.32f, Math.abs(sunY));
        float skyR = MathUtils.lerp(0.02f, 0.53f, daylight);
        float skyG = MathUtils.lerp(0.03f, 0.81f, daylight);
        float skyB = MathUtils.lerp(0.08f, 0.92f, daylight);
        shader.setUniformVec3("uSkyColor", MathUtils.lerp(skyR, 0.055f + daylight * 0.30f, storm),
                MathUtils.lerp(skyG, 0.066f + daylight * 0.34f, storm),
                MathUtils.lerp(skyB, 0.09f + daylight * 0.38f, storm));
        shader.setUniformVec3("uHorizonColor", 1f, 0.40f, 0.24f);
        shader.setUniformVec3("uSunDirection", sunX, 0, 0);
        shader.setUniform1f("uHorizonGlow", twilight * (1f - 0.82f * storm) * 0.85f);

        // Bỏ tịnh tiến để vòm và thiên thể chỉ chuyển khi góc nhìn thay đổi.
        rotationView.set(view);
        rotationView.val[Matrix4.M03] = 0;
        rotationView.val[Matrix4.M13] = 0;
        rotationView.val[Matrix4.M23] = 0;
        shader.setUniformMatrix4f("uView", rotationView);
        shader.setUniformMatrix4f("uModel", identity);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(false);
        try {
            shader.setUniform1i("uEnvironmentMode", 1);
            dome.draw(shader);
            shader.setUniform1i("uEnvironmentMode", 3);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            float visibility = 1f - storm;
            renderCelestial(shader, sunTexture, sunX, sunY, 26f, visibility);
            renderCelestial(shader, moonTextures[(int) (time.dayCount() % MOON_PHASES.length)],
                    -sunX, -sunY, 19f, visibility);
        } finally {
            shader.setUniform1i("uEnvironmentMode", 0);
            shader.setUniformMatrix4f("uView", view);
            shader.enable3DWorldMode();
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glDisable(GL20.GL_BLEND);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        }

        // Hệ số mặt khối được nhân với ánh sáng nền; ánh đêm vẫn giữ chi tiết.
        float dim = 1f - 0.24f * storm;
        worldLight.set(MathUtils.lerp(0.22f, 1f, daylight) * dim,
                MathUtils.lerp(0.25f, 1f, daylight) * dim * (1f - 0.10f * twilight),
                MathUtils.lerp(0.34f, 1f, daylight) * dim * (1f - 0.20f * twilight));
        shader.setUniformVec3("uWorldLight", worldLight);
    }

    /** Dựng ảnh trên mặt phẳng tiếp xúc quỹ đạo; trục Z giữ cạnh ngang của ảnh. */
    private void renderCelestial(ShaderProgram shader, Texture image, float x, float y,
                                 float size, float visibility) {
        if (visibility <= 0f || y * ORBIT_RADIUS < -size) {
            return;
        }
        float centerX = x * ORBIT_RADIUS;
        float centerY = y * ORBIT_RADIUS;
        float tangentX = -y * size;
        float tangentY = x * size;
        quad.clear();
        quad.quad(centerX + tangentX, centerY + tangentY, -size,
                centerX - tangentX, centerY - tangentY, -size,
                centerX - tangentX, centerY - tangentY, size,
                centerX + tangentX, centerY + tangentY, size, 0, 0, 1, 1, -1);
        quad.upload();
        image.bind(0);
        shader.setUniformVec4("uTintColor", 1f, 1f, 1f, visibility);
        quad.draw(shader);
    }

    /** Vẽ mây trong không gian thế giới, có kiểm tra độ sâu với địa hình. */
    public void renderClouds(ShaderProgram shader, Vector3 eye, double seconds, float storm) {
        float left = eye.x - CLOUD_RADIUS;
        float right = eye.x + CLOUD_RADIUS;
        float near = eye.z - CLOUD_RADIUS;
        float far = eye.z + CLOUD_RADIUS;
        // Cộng UV theo thời gian để họa tiết di chuyển về phía -X.
        float drift = (float) ((seconds * 0.65 / CLOUD_PERIOD) % 1.0);
        quad.clear();
        quad.quad(left, CLOUD_HEIGHT, near, left, CLOUD_HEIGHT, far,
                right, CLOUD_HEIGHT, far, right, CLOUD_HEIGHT, near,
                left / CLOUD_PERIOD + drift, near / CLOUD_PERIOD,
                right / CLOUD_PERIOD + drift, far / CLOUD_PERIOD, -1);
        quad.upload();

        float red = MathUtils.lerp(0.22f, 1f, daylight);
        float green = MathUtils.lerp(0.27f, 0.98f, daylight) * (1f - 0.13f * twilight);
        float blue = MathUtils.lerp(0.38f, 0.97f, daylight) * (1f - 0.22f * twilight);
        shader.setUniformVec4("uTintColor", MathUtils.lerp(red, red * 0.55f, storm),
                MathUtils.lerp(green, green * 0.60f, storm),
                MathUtils.lerp(blue, blue * 0.66f, storm), 0.85f + 0.13f * storm);
        shader.setUniformVec3("uCloudCenter", eye);
        shader.setUniform1i("uEnvironmentMode", 2);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(false);
        try {
            cloudTexture.bind(0);
            quad.draw(shader);
        } finally {
            shader.setUniform1i("uEnvironmentMode", 0);
            shader.enable3DWorldMode();
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    /** Đường cong bậc ba có đạo hàm bằng 0 ở hai đầu vùng chuyển. */
    private static float smoothStep(float start, float end, float value) {
        float ratio = MathUtils.clamp((value - start) / (end - start), 0f, 1f);
        return ratio * ratio * (3f - 2f * ratio);
    }

    /** Giải phóng cả các ảnh pha trăng và hai mesh. */
    @Override
    public void dispose() {
        dome.dispose();
        quad.dispose();
        if (sunTexture != null) {
            sunTexture.dispose();
        }
        if (cloudTexture != null) {
            cloudTexture.dispose();
        }
        for (Texture moon : moonTextures) {
            if (moon != null) {
                moon.dispose();
            }
        }
    }
}
