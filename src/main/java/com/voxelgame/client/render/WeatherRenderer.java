package com.voxelgame.client.render;

import com.voxelgame.world.level.biome.BiomeType;
import com.voxelgame.world.level.weather.WeatherManager;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import static com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge;
import static com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat;

/** Kết xuất hai lô giáng thủy theo cột, dùng chung bộ đệm qua mọi khung hình. */
public final class WeatherRenderer implements Disposable {
    /** Bán kính ngang và số dải tối đa của một lượt vẽ. */
    private static final int RADIUS = 14;
    private static final int MAX_COLUMNS = (RADIUS * 2 + 1) * (RADIUS * 2 + 1);
    /** Giới hạn số dải chồng trên mỗi pixel, nhất là khi nhìn ngang qua cả vùng mưa. */
    private static final float RAIN_DENSITY = 0.35f;
    private static final float SNOW_DENSITY = 0.29f;
    private final EnvironmentMesh rain = new EnvironmentMesh(MAX_COLUMNS);
    private final EnvironmentMesh snow = new EnvironmentMesh(MAX_COLUMNS);
    private Texture rainTexture;
    private Texture snowTexture;

    /** Nạp nguyên ảnh mưa và tuyết đã có sẵn trong dự án. */
    public WeatherRenderer() {
        try {
            rainTexture = EnvironmentMesh.loadTexture("rain.png", ClampToEdge, Repeat);
            snowTexture = EnvironmentMesh.loadTexture("snow.png", Repeat, Repeat);
        } catch (RuntimeException error) {
            dispose();
            throw error;
        }
    }

    /** Dựng dải tại các cột nhìn quanh camera, cắt chân dải theo bề mặt cao nhất. */
    public void render(ShaderProgram shader, World world, Vector3 eye, WeatherManager weather) {
        float strength = weather.strength();
        if (strength <= 0f) {
            return;
        }
        rain.clear();
        snow.clear();
        int centerX = MathUtils.floor(eye.x);
        int centerZ = MathUtils.floor(eye.z);
        double seconds = weather.animationSeconds();
        float rainScroll = (float) ((seconds * 2.25) % 1.0);
        float snowScroll = (float) ((seconds * 0.26) % 1.0);
        float snowSway = (float) ((seconds * 1.55) % (Math.PI * 2));
        float top = eye.y + 18f;
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!world.isInsideWorld(x, 0, z)) {
                    continue;
                }
                BiomeType biome = world.getBiome(x, z);
                if (biome.isDry()) {
                    continue;
                }
                int hash = x * 374761393 + z * 668265263;
                hash = (hash ^ (hash >>> 13)) * 1274126177;
                boolean flakes = biome.hasSnow();
                float density = flakes ? SNOW_DENSITY : RAIN_DENSITY;
                // Các cột xuất hiện theo thứ tự cố định khi cường độ tăng.
                if (((hash >>> 12) & 255) / 256f >= density * strength) {
                    continue;
                }
                float columnX = x + 0.5f;
                float columnZ = z + 0.5f;
                float deltaX = columnX - eye.x;
                float deltaZ = columnZ - eye.z;
                float distance2 = deltaX * deltaX + deltaZ * deltaZ;
                // Bỏ dải quá sát mắt, nơi một tứ giác có thể phủ gần như cả màn hình.
                if (distance2 < 1f) {
                    continue;
                }
                float bottom = Math.max(eye.y - 16f, world.getPrecipitationSurface(x, z));
                if (bottom >= top) {
                    continue;
                }
                float phase = (hash & 255) / 256f;
                float uOffset = flakes ? MathUtils.sin(snowSway + phase * MathUtils.PI2) * 0.17f : 0f;
                float vOffset = phase - (flakes ? snowScroll : rainScroll);
                float distance = (float) Math.sqrt(distance2);
                // Mờ dần ở sát mắt và rìa vùng vẽ để tránh dải bật/tắt khi di chuyển.
                float opacity = Math.min(Math.min(distance - 1f, 1f), (RADIUS + 0.5f - distance) * 0.25f);
                if (opacity > 0f) {
                    addColumn(flakes ? snow : rain, columnX, columnZ,
                            bottom, top, deltaX, deltaZ, distance, uOffset, vOffset, opacity);
                }
            }
        }
        rain.upload();
        snow.upload();

        shader.setUniform1i("uEnvironmentMode", 4);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(false);
        // Kiểm tra chiều sâu vẫn bật theo hợp đồng khung hình của thế giới.
        try {
            rainTexture.bind(0);
            shader.setUniformVec4("uTintColor", 0.88f, 0.93f, 1f, 0.80f * strength);
            rain.draw(shader);
            snowTexture.bind(0);
            shader.setUniformVec4("uTintColor", 1f, 1f, 1f, 0.90f * strength);
            snow.draw(shader);
        } finally {
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_BLEND);
            shader.setUniform1i("uEnvironmentMode", 0);
            shader.enable3DWorldMode();
        }
    }

    /** Quay dải về phía mắt; giữ toàn bộ chiều ngang bên trong cột để mái che không bị lọt hạt. */
    private static void addColumn(EnvironmentMesh mesh, float x, float z, float bottom, float top,
                                  float dx, float dz, float distance,
                                  float uOffset, float vOffset, float opacity) {
        float scale = 0.49f / distance;
        float sideX = dz * scale;
        float sideZ = -dx * scale;
        // Dùng tọa độ Y tuyệt đối để UV không đổi khi điểm cắt thay đổi.
        mesh.quad(x - sideX, top, z - sideZ, x - sideX, bottom, z - sideZ,
                x + sideX, bottom, z + sideZ, x + sideX, top, z + sideZ,
                uOffset, vOffset - top * 0.25f, 1f + uOffset, vOffset - bottom * 0.25f, opacity);
    }

    /** Hủy hai texture và hai bộ đệm lô. */
    @Override
    public void dispose() {
        rain.dispose();
        snow.dispose();
        if (rainTexture != null) {
            rainTexture.dispose();
        }
        if (snowTexture != null) {
            snowTexture.dispose();
        }
    }
}
