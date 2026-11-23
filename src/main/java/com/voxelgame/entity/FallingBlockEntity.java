package com.voxelgame.entity;

import com.badlogic.gdx.math.Matrix4;
import com.voxelgame.engine.AudioManager;
import com.voxelgame.engine.ShaderProgram;
import com.voxelgame.engine.World;

/** Đại diện một ô cát hoặc sỏi đang rơi trước khi được ghi lại vào thế giới. */
public final class FallingBlockEntity extends Entity {
    /** Cùng quy mô trọng lực với người chơi và giới hạn vận tốc rơi. */
    private static final float GRAVITY = 26.0f;
    private static final float TERMINAL_SPEED = 45.0f;

    private final int blockX;
    private final int blockZ;
    private final Block block;
    private final Matrix4 model = new Matrix4();
    private boolean finished;

    /** Giữ tâm ngang của ô nguồn và đặt đáy thực thể tại đáy ô. */
    public FallingBlockEntity(int x, int y, int z, Block block) {
        super(x + 0.5f, y, z + 0.5f, 0.5f, 1.0f);
        blockX = x;
        blockZ = z;
        this.block = block;
    }

    /** Quét dọc qua tất cả bề mặt mà đáy thực thể đi qua trong bước này. */
    @Override
    public void update(World world, float deltaTime) {
        if (finished) {
            return;
        }
        velocity.y = Math.max(-TERMINAL_SPEED, velocity.y - GRAVITY * deltaTime);
        float nextY = position.y + velocity.y * deltaTime;
        int floor = Math.max(0, (int) Math.floor(nextY) - 1);
        for (int y = (int) Math.floor(position.y); y >= floor; y--) {
            Block.Bounds support = world.getCollisionBounds(blockX, y, blockZ);
            if (support == null) {
                continue;
            }
            float top = y + support.maxY();
            if (top <= position.y && top >= nextY) {
                // Chỉ đặt lại khi đỉnh nền khớp lưới; mặt thấp làm rơi vật phẩm.
                if (support.maxY() < 1.0f || !world.tryPlaceBlock(blockX, y + 1, blockZ, block, world.getPlayer())) {
                    world.spawnDroppedItem(new ItemEntity(position.x, top, position.z, block));
                }
                // Dùng vật liệu đang rơi và vị trí tiếp xúc, kể cả khi phải rơi thành vật phẩm.
                AudioManager audio = world.getAudio();
                if (audio != null) {
                    audio.playLanding(block, world.getAudioGain(position.x, top, position.z));
                }
                finished = true;
                return;
            }
        }
        position.y = nextY;
        if (nextY < -25.0f) {
            finished = true;
        }
    }

    /** Vẽ ô đang rơi ở kích thước thật, không xoay hoặc nhấp nhô. */
    @Override
    public void render(ShaderProgram shader) {
        model.setToTranslation(position.x, position.y + 0.5f, position.z);
        ItemEntity.renderBlockMesh(shader, block, model);
    }

    /** Cho World thu hồi thực thể sau khi đặt lại hoặc rơi khỏi bản đồ. */
    public boolean isFinished() {
        return finished;
    }
}
