package com.voxelgame.client.render;

import com.voxelgame.world.level.Chunk;
import com.voxelgame.world.level.WorldTime;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;
import static com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest;
import static com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge;
import static com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat;

/** Vẽ vòm trời, quỹ đạo thiên thể và tầng mây 3D dày 4 block, đồng thời tính ánh sáng môi trường. */
public final class SkyRenderer implements Disposable {
    /** Vòm và thiên thể đủ xa để dùng chung phép chiếu của camera. */
    private static final float SKY_RADIUS = 400f;
    private static final float ORBIT_RADIUS = 320f;
    /** Cao độ đáy tầng mây (Y=140) và độ dày khối mây 3D (4 block: Y=140..144). */
    public static final float CLOUD_HEIGHT = 140f;
    public static final float CLOUD_THICKNESS = 4.0f;
    /** Chu kỳ lặp lại lưới mây theo trục X/Z tính bằng block thế giới (256 texel × 3 block/texel). */
    private static final float CLOUD_PERIOD = 768f;
    private static final float CLOUD_RADIUS = 500f;
    /** Ngưỡng alpha để coi một ô trên {@code clouds.png} là khối mây đặc. */
    private static final int CLOUD_ALPHA_THRESHOLD = 128;
    /** Độ sáng tương đối theo 6 hướng mặt của khối mây 3D (âm để shader tự phát sáng không nhân uWorldLight). */
    private static final float CLOUD_SHADE_TOP = -1.00f;
    private static final float CLOUD_SHADE_BOTTOM = -0.72f;
    private static final float CLOUD_SHADE_NORTH_SOUTH = -0.85f;
    private static final float CLOUD_SHADE_EAST_WEST = -0.90f;

    /** Sương trời quang giữ được đường nét núi xa và hòa vào trời trước mặt phẳng cắt. */
    private static final float CLEAR_FOG_START = 288f;
    private static final float CLEAR_FOG_END = 640f;
    /** Khi giáng thủy đạt đỉnh, vùng chuyển màu tiến gần camera hơn. */
    private static final float STORM_FOG_START = 96f;
    private static final float STORM_FOG_END = 384f;
    /** Thứ tự tám pha từ trăng tròn đến lưỡi liềm cuối tháng. */
    private static final String[] MOON_PHASES = {
            "full_moon", "waning_gibbous", "third_quarter", "waning_crescent",
            "new_moon", "waxing_crescent", "first_quarter", "waxing_gibbous"
    };

    private final EnvironmentMesh dome;
    private final EnvironmentMesh quad;
    private final EnvironmentMesh cloudMesh;
    private final Matrix4 rotationView = new Matrix4();
    private final Matrix4 cloudModelMatrix = new Matrix4();
    private final Matrix4 identity = new Matrix4();
    private final Vector3 worldLight = new Vector3();
    private final Vector3 cloudCenterLocal = new Vector3();
    private Texture sunTexture;
    private Texture cloudTexture;
    private final Texture[] moonTextures = new Texture[MOON_PHASES.length];
    private final int exposedCloudQuads;
    private float daylight;
    private float twilight;
    private boolean disposed;

    /**
     * Nạp ảnh thiên thể, đọc {@code clouds.png} một lần để dựng lưới khối mây 3D dày 4 block
     * có loại bỏ mặt trong giữa các ô liền kề, và tải vòm tĩnh lên GPU khi có ngữ cảnh OpenGL.
     * Hỗ trợ khởi tạo an toàn trong kiểm thử CPU không có OpenGL ({@code Gdx.gl == null}).
     */
    public SkyRenderer() {
        GdxNativesLoader.load();
        Pixmap cloudsPixmap = null;
        EnvironmentMesh createdDome = null;
        EnvironmentMesh createdQuad = null;
        EnvironmentMesh createdCloudMesh = null;
        int quadCount = 0;

        try {
            cloudsPixmap = com.voxelgame.client.render.Texture.loadPixmapFlexible(
                    "textures/environment/clouds.png", "environment/clouds.png", "clouds.png");
            boolean[][] cloudMask = cloudsPixmap != null ? extractCloudMask(cloudsPixmap) : new boolean[0][0];
            quadCount = cloudsPixmap != null ? countExposedCloudQuads(cloudMask) : 0;

            if (Gdx.gl != null) {
                createdDome = new EnvironmentMesh(6);
                createdQuad = new EnvironmentMesh(1);
                sunTexture = EnvironmentMesh.loadTexture("celestial/sun.png", ClampToEdge, ClampToEdge);
                if (cloudsPixmap != null) {
                    cloudTexture = createCloudTexture(cloudsPixmap);
                } else {
                    cloudTexture = EnvironmentMesh.loadTexture("clouds.png", Repeat, Repeat);
                }
                for (int i = 0; i < MOON_PHASES.length; i++) {
                    moonTextures[i] = EnvironmentMesh.loadTexture(
                            "celestial/moon/" + MOON_PHASES[i] + ".png", ClampToEdge, ClampToEdge);
                }
                buildDome(createdDome);
                createdCloudMesh = new EnvironmentMesh(Math.max(1, quadCount));
                if (cloudsPixmap != null && quadCount > 0) {
                    buildCloudVolumeMesh(cloudsPixmap, cloudMask, createdCloudMesh);
                    createdCloudMesh.upload();
                }
            }
        } catch (RuntimeException | Error error) {
            if (createdDome != null) {
                createdDome.dispose();
            }
            if (createdQuad != null) {
                createdQuad.dispose();
            }
            if (createdCloudMesh != null) {
                createdCloudMesh.dispose();
            }
            disposeTextures();
            throw error;
        } finally {
            if (cloudsPixmap != null) {
                cloudsPixmap.dispose();
            }
        }

        this.dome = createdDome;
        this.quad = createdQuad;
        this.cloudMesh = createdCloudMesh;
        this.exposedCloudQuads = quadCount;
    }

    /** Tạo {@link Texture} lặp cạnh Nearest từ {@link Pixmap} của {@code clouds.png} đã đọc sẵn. */
    private static Texture createCloudTexture(Pixmap cloudsPixmap) {
        Texture texture = new Texture(cloudsPixmap);
        try {
            texture.setFilter(Nearest, Nearest);
            texture.setWrap(Repeat, Repeat);
            return texture;
        } catch (RuntimeException | Error failure) {
            texture.dispose();
            throw failure;
        }
    }

    /**
     * Trích xuất mặt nạ nhị phân {@code [x][z]} từ {@code clouds.png}: ô có {@code alpha > 128}
     * được đánh dấu là ô mây đặc.
     */
    public static boolean[][] extractCloudMask(Pixmap cloudsPixmap) {
        if (cloudsPixmap == null) {
            return new boolean[0][0];
        }
        int width = cloudsPixmap.getWidth();
        int height = cloudsPixmap.getHeight();
        boolean[][] mask = new boolean[width][height];
        for (int gx = 0; gx < width; gx++) {
            for (int gz = 0; gz < height; gz++) {
                int rgba = com.voxelgame.client.render.Texture.readNormalizedPixel(cloudsPixmap, gx, gz);
                int alpha = rgba & 0xFF;
                mask[gx][gz] = alpha > CLOUD_ALPHA_THRESHOLD;
            }
        }
        return mask;
    }

    /**
     * Đếm tổng số mặt tứ giác lộ thiên của tầng mây 3D sau khi loại bỏ mặt tiếp giáp giữa
     * hai ô mây liền kề (kể cả qua biên tuần hoàn của ảnh).
     */
    public static int countExposedCloudQuads(boolean[][] mask) {
        if (mask == null || mask.length == 0 || mask[0].length == 0) {
            return 0;
        }
        int width = mask.length;
        int height = mask[0].length;
        int count = 0;
        for (int gx = 0; gx < width; gx++) {
            int nextX = (gx + 1) % width;
            int prevX = (gx - 1 + width) % width;
            for (int gz = 0; gz < height; gz++) {
                if (!mask[gx][gz]) {
                    continue;
                }
                // Mặt trên (+Y) và mặt dưới (-Y) luôn lộ thiên vì tầng mây chỉ dày một lớp.
                count += 2;
                int nextZ = (gz + 1) % height;
                int prevZ = (gz - 1 + height) % height;
                if (!mask[nextX][gz]) {
                    count++;
                }
                if (!mask[prevX][gz]) {
                    count++;
                }
                if (!mask[gx][nextZ]) {
                    count++;
                }
                if (!mask[gx][prevZ]) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Trả về số lượng mặt tứ giác của mesh mây 3D đã tính khi khởi tạo. */
    public int getExposedCloudQuadCount() {
        return exposedCloudQuads;
    }

    /** Dựng mesh mây 3D dày {@link #CLOUD_THICKNESS} (Y=140..144) một lần vào {@link EnvironmentMesh}. */
    private static void buildCloudVolumeMesh(Pixmap cloudsPixmap, boolean[][] mask, EnvironmentMesh target) {
        int width = mask.length;
        int height = mask[0].length;
        float cellWidth = CLOUD_PERIOD / width;
        float cellDepth = CLOUD_PERIOD / height;
        float y0 = CLOUD_HEIGHT;
        float y1 = CLOUD_HEIGHT + CLOUD_THICKNESS;

        target.clear();
        for (int gx = 0; gx < width; gx++) {
            float x0 = gx * cellWidth;
            float x1 = (gx + 1) * cellWidth;
            float u = (gx + 0.5f) / width;
            int nextX = (gx + 1) % width;
            int prevX = (gx - 1 + width) % width;

            for (int gz = 0; gz < height; gz++) {
                if (!mask[gx][gz]) {
                    continue;
                }
                float z0 = gz * cellDepth;
                float z1 = (gz + 1) * cellDepth;
                float v = (gz + 0.5f) / height;
                int nextZ = (gz + 1) % height;
                int prevZ = (gz - 1 + height) % height;

                // Mặt trên (+Y) và mặt dưới (-Y) với thứ tự đỉnh ngược chiều kim đồng hồ nhìn từ ngoài.
                target.quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u, v, u, v, CLOUD_SHADE_TOP);
                target.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u, v, u, v, CLOUD_SHADE_BOTTOM);

                // Bốn thành bên chỉ dựng khi ô kề theo hướng tương ứng không phải mây đặc.
                if (!mask[nextX][gz]) {
                    target.quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u, v, u, v, CLOUD_SHADE_EAST_WEST);
                }
                if (!mask[prevX][gz]) {
                    target.quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u, v, u, v, CLOUD_SHADE_EAST_WEST);
                }
                if (!mask[gx][nextZ]) {
                    target.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u, v, u, v, CLOUD_SHADE_NORTH_SOUTH);
                }
                if (!mask[gx][prevZ]) {
                    target.quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u, v, u, v, CLOUD_SHADE_NORTH_SOUTH);
                }
            }
        }
    }

    /** Dựng hộp sáu mặt; shader chuẩn hóa vectơ nhìn để tạo màu trời theo hướng. */
    private static void buildDome(EnvironmentMesh target) {
        float a = -SKY_RADIUS;
        float b = SKY_RADIUS;
        target.quad(a, b, a, a, a, a, b, a, a, b, b, a, 0, 0, 1, 1, -1);
        target.quad(a, b, b, a, a, b, b, a, b, b, b, b, 0, 0, 1, 1, -1);
        target.quad(a, b, a, a, a, a, a, a, b, a, b, b, 0, 0, 1, 1, -1);
        target.quad(b, b, a, b, a, a, b, a, b, b, b, b, 0, 0, 1, 1, -1);
        target.quad(a, b, a, a, b, b, b, b, b, b, b, a, 0, 0, 1, 1, -1);
        target.quad(a, a, a, a, a, b, b, a, b, b, a, a, 0, 0, 1, 1, -1);
        target.upload();
    }

    /** Tính màu đêm, ngày và ánh ấm chuyển tiếp rồi vẽ nền trời không ghi chiều sâu. */
    public void renderSky(ShaderProgram shader, Matrix4 view, WorldTime time, float storm) {
        if (disposed || dome == null || quad == null) {
            return;
        }
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

    /** Bật sương trong lượt kết xuất thế giới, dùng màu vừa đặt cho vòm trời. */
    public float enableWorldFog(ShaderProgram shader, Vector3 eye, float storm) {
        float end = MathUtils.lerp(CLEAR_FOG_END, STORM_FOG_END, storm);
        shader.enableDistanceFog(eye, MathUtils.lerp(CLEAR_FOG_START, STORM_FOG_START, storm), end);
        // Vẫn giữ depth của địa hình trước toàn bộ vòng mây 3D dày 4 block còn nhìn thấy.
        float cloudDeltaY = Math.max(
                Math.abs(CLOUD_HEIGHT - eye.y),
                Math.abs((CLOUD_HEIGHT + CLOUD_THICKNESS) - eye.y));
        float cloudDistance = (float) Math.sqrt(CLOUD_RADIUS * CLOUD_RADIUS + cloudDeltaY * cloudDeltaY);
        return Math.max(end + 2 * Chunk.SIZE, cloudDistance);
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

    /**
     * Vẽ mây 3D dày 4 block trong không gian thế giới bằng một {@link EnvironmentMesh} tĩnh,
     * chỉ dịch {@code uModel} theo chu kỳ và vị trí mắt (zero-GC), kết hợp depth pre-pass
     * để mây bán trong suốt không bị chồng mờ hai lớp.
     */
    public void renderClouds(ShaderProgram shader, Vector3 eye, double seconds, float storm) {
        if (disposed || cloudMesh == null || cloudTexture == null || exposedCloudQuads <= 0) {
            return;
        }

        float red = MathUtils.lerp(0.22f, 1f, daylight);
        float green = MathUtils.lerp(0.27f, 0.98f, daylight) * (1f - 0.13f * twilight);
        float blue = MathUtils.lerp(0.38f, 0.97f, daylight) * (1f - 0.22f * twilight);
        shader.setUniformVec4("uTintColor", MathUtils.lerp(red, red * 0.55f, storm),
                MathUtils.lerp(green, green * 0.60f, storm),
                MathUtils.lerp(blue, blue * 0.66f, storm), 0.85f + 0.13f * storm);
        shader.setUniform1i("uEnvironmentMode", 2);

        // Dịch lưới mây về phía -X theo thời gian, giữ phần dư trong [0, CLOUD_PERIOD).
        float driftBlocks = (float) (((seconds * 0.65) % CLOUD_PERIOD + CLOUD_PERIOD) % CLOUD_PERIOD);
        int minTx = (int) Math.floor((eye.x - CLOUD_RADIUS + driftBlocks) / CLOUD_PERIOD);
        int maxTx = (int) Math.floor((eye.x + CLOUD_RADIUS + driftBlocks) / CLOUD_PERIOD);
        int minTz = (int) Math.floor((eye.z - CLOUD_RADIUS) / CLOUD_PERIOD);
        int maxTz = (int) Math.floor((eye.z + CLOUD_RADIUS) / CLOUD_PERIOD);
        boolean insideCloudBand = eye.y >= CLOUD_HEIGHT - 0.5f && eye.y <= CLOUD_HEIGHT + CLOUD_THICKNESS + 0.5f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (insideCloudBand) {
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        } else {
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        }
        try {
            cloudTexture.bind(0);
            // Lượt 1: ghi độ sâu bề mặt mây gần nhất để ngăn tự chồng alpha bên trong khối mây 3D.
            Gdx.gl.glColorMask(false, false, false, false);
            Gdx.gl.glDepthMask(true);
            drawVisibleCloudTiles(shader, eye, driftBlocks, minTx, maxTx, minTz, maxTz);

            // Lượt 2: chỉ tô màu những điểm ảnh khớp đúng độ sâu gần nhất vừa ghi.
            Gdx.gl.glColorMask(true, true, true, true);
            Gdx.gl.glDepthMask(false);
            Gdx.gl.glDepthFunc(GL20.GL_EQUAL);
            drawVisibleCloudTiles(shader, eye, driftBlocks, minTx, maxTx, minTz, maxTz);
        } finally {
            Gdx.gl.glColorMask(true, true, true, true);
            Gdx.gl.glDepthFunc(GL20.GL_LESS);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_BLEND);
            shader.setUniformMatrix4f("uModel", identity);
            shader.setUniform1i("uEnvironmentMode", 0);
            shader.enable3DWorldMode();
        }
    }

    /** Vẽ các ô chu kỳ mây giao với vùng nhìn tròn bán kính {@link #CLOUD_RADIUS} quanh mắt. */
    private void drawVisibleCloudTiles(ShaderProgram shader, Vector3 eye, float driftBlocks,
                                       int minTx, int maxTx, int minTz, int maxTz) {
        float maxDistSq = CLOUD_RADIUS * CLOUD_RADIUS;
        for (int tx = minTx; tx <= maxTx; tx++) {
            float shiftX = tx * CLOUD_PERIOD - driftBlocks;
            float nearestX = MathUtils.clamp(eye.x, shiftX, shiftX + CLOUD_PERIOD);
            float dx = eye.x - nearestX;
            for (int tz = minTz; tz <= maxTz; tz++) {
                float shiftZ = tz * CLOUD_PERIOD;
                float nearestZ = MathUtils.clamp(eye.z, shiftZ, shiftZ + CLOUD_PERIOD);
                float dz = eye.z - nearestZ;
                if (dx * dx + dz * dz > maxDistSq) {
                    continue;
                }
                cloudModelMatrix.setToTranslation(shiftX, 0f, shiftZ);
                // Chuyển tâm mắt về hệ tọa độ cục bộ của mesh để phép tính khoảng cách mờ rìa khớp 100%.
                cloudCenterLocal.set(eye.x - shiftX, eye.y, eye.z - shiftZ);
                shader.setUniformMatrix4f("uModel", cloudModelMatrix);
                shader.setUniformVec3("uCloudCenter", cloudCenterLocal);
                cloudMesh.draw(shader);
            }
        }
    }

    /** Đường cong bậc ba có đạo hàm bằng 0 ở hai đầu vùng chuyển. */
    private static float smoothStep(float start, float end, float value) {
        float ratio = MathUtils.clamp((value - start) / (end - start), 0f, 1f);
        return ratio * ratio * (3f - 2f * ratio);
    }

    /** Giải phóng các ảnh thiên thể nếu đã tạo. */
    private void disposeTextures() {
        if (sunTexture != null) {
            sunTexture.dispose();
            sunTexture = null;
        }
        if (cloudTexture != null) {
            cloudTexture.dispose();
            cloudTexture = null;
        }
        for (int i = 0; i < moonTextures.length; i++) {
            if (moonTextures[i] != null) {
                moonTextures[i].dispose();
                moonTextures[i] = null;
            }
        }
    }

    /** Giải phóng toàn bộ ảnh pha trăng, mặt trời, mây và các mesh trên GPU. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (dome != null) {
            dome.dispose();
        }
        if (quad != null) {
            quad.dispose();
        }
        if (cloudMesh != null) {
            cloudMesh.dispose();
        }
        disposeTextures();
    }
}
