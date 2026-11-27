package com.voxelgame.engine;

import com.voxelgame.client.render.Texture;
import com.voxelgame.world.level.block.Block;
import com.voxelgame.world.level.Chunk;
import com.voxelgame.world.level.TranslucentChunkSort;
import com.voxelgame.world.level.World;

import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.utils.FloatArray;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/** Kiểm tra trạng thái chunk và thứ tự kết xuất mà không khởi tạo OpenGL. */
public final class ChunkMeshRegressionChecks {
    /** Chạy kiểm tra trực tiếp khi phát triển mesher. */
    public static void main(String[] args) {
        run();
    }

    /** Chạy nhóm kiểm tra được gọi bởi chương trình hồi quy chung. */
    public static void run() {
        checkChunkMetadata();
        checkFrustumCorners();
        checkTranslucentSort();
        checkLiquidFacesAtChunkBoundary();
        checkLeafAndCactusFaces();
        checkCactusTextureRegion();
        checkCrossPlantAndSnowFaces();
        checkAmbientOcclusionAtChunkCorner();
        checkIndexedQuadPacking();
    }

    /** So kết quả góc xa nhất với cách thử đủ tám góc, kể cả khi mặt phẳng tiếp xúc hộp. */
    private static void checkFrustumCorners() {
        Chunk chunk = new Chunk(92, 4, 51);
        float half = Chunk.SIZE * 0.5f;
        float centerX = 92 * Chunk.SIZE + half;
        float centerY = 4 * Chunk.SIZE + half;
        float centerZ = 51 * Chunk.SIZE + half;
        Frustum frustum = new Frustum();
        Random random = new Random(412);
        for (int sample = 0; sample < 1_000; sample++) {
            for (Plane plane : frustum.planes) {
                plane.normal.set(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
                plane.d = -plane.normal.dot(centerX, centerY, centerZ) + random.nextInt(49) - 24;
            }
            require(chunk.intersectsFrustum(frustum) == frustum.boundsInFrustum(
                    centerX, centerY, centerZ, half, half, half),
                    "Culling giữ cả chunk tiếp xúc mặt phẳng và loại đúng chunk bên ngoài");
        }
    }

    /** Mô phỏng lọc Nearest trên UV thật của sáu mặt, bằng chính PNG xương rồng đóng gói. */
    private static void checkCactusTextureRegion() {
        World world = World.empty();
        try {
            Chunk chunk = new Chunk(0, 0, 0);
            FloatArray opaque = new FloatArray();
            place(world, chunk, 2, 1, 1, Block.CACTUS, 0);
            chunk.buildVertices(world, opaque, new FloatArray());
            String[] names = {"top", "bottom", "side", "side", "side", "side"};
            Block.Face[] faces = Block.Face.values();
            boolean sharedUvs = true;
            int floatsPerFace = 6 * Chunk.FLOATS_PER_VERTEX;
            for (int face = 0; face < faces.length; face++) {
                int tile = Block.CACTUS.getTileIndex(faces[face]);
                int tileX = tile % Texture.ATLAS_COLS * Texture.TILE_SIZE;
                int tileY = tile / Texture.ATLAS_COLS * Texture.TILE_SIZE;
                float[] itemUv = Block.CACTUS.getFaceUV(faces[face]);
                try (InputStream source = ChunkMeshRegressionChecks.class.getResourceAsStream(
                        "/textures/cactus_" + names[face] + ".png")) {
                    require(source != null, "Thiếu PNG xương rồng " + names[face]);
                    BufferedImage pixels = ImageIO.read(source);
                    for (int i = face * floatsPerFace; i < (face + 1) * floatsPerFace; i += Chunk.FLOATS_PER_VERTEX) {
                        float u = opaque.items[i + 3];
                        float v = opaque.items[i + 4];
                        int x = (int) (u * Texture.ATLAS_WIDTH) - tileX;
                        int y = (int) (v * Texture.ATLAS_HEIGHT) - tileY;
                        require((pixels.getRGB(x, y) >>> 24) == 255, "UV cactus chạm texel trong suốt: "
                                + faces[face] + " tại " + x + ", " + y);
                        require(x == 1 || x == 14, "Chiều ngang UV phải phủ đủ 14 texel của cactus");
                        sharedUvs &= (u == itemUv[0] || u == itemUv[2]) && (v == itemUv[1] || v == itemUv[3]);
                    }
                }
            }
            require(sharedUvs, "Cactus trong thế giới và vật phẩm phải dùng cùng vùng UV");
        } catch (IOException failure) {
            throw new AssertionError("Không đọc được PNG xương rồng để kiểm tra UV", failure);
        } finally {
            world.dispose();
        }
    }

    /** Khối chéo qua ba biên chỉ làm tối góc tiếp xúc và ảnh hưởng đường chéo của hai tam giác. */
    private static void checkAmbientOcclusionAtChunkCorner() {
        World world = World.empty();
        try {
            Chunk chunk = new Chunk(0, 0, 0);
            FloatArray opaque = new FloatArray();
            FloatArray translucent = new FloatArray();
            place(world, chunk, 15, 15, 15, Block.STONE, 0);
            world.setBlockInternal(16, 16, 16, Block.STONE.getId(), 0);
            chunk.buildVertices(world, opaque, translucent);

            float shaded = shadeOnQuad(opaque, 0, 16, 16, 16);
            float clear = shadeOnQuad(opaque, 0, 15, 16, 15);
            require(shaded < clear && clear == 1.0f, "AO phải đọc ô chắn ở góc chéo của chunk");
            require(opaque.items[0] == 16.0f && opaque.items[2] == 16.0f,
                    "Mặt có một góc che phải dùng đường chéo qua góc tối");
            require(opaque.items[Chunk.SHADE_OFFSET] == shaded
                            && opaque.items[3 * Chunk.FLOATS_PER_VERTEX + Chunk.SHADE_OFFSET] == shaded,
                    "Hai tam giác dùng chung giá trị AO tại đỉnh trùng");
            Chunk.compactQuads(opaque, 6);
            require(opaque.items[Chunk.SHADE_OFFSET] == shaded
                            && opaque.items[2 * Chunk.FLOATS_PER_VERTEX + Chunk.SHADE_OFFSET] == clear,
                    "Nén mesh phải giữ nguyên độ sáng hai đầu đường chéo");
        } finally {
            world.dispose();
        }
    }

    /** Đọc giá trị độ sáng của một góc thuộc mặt đang kiểm tra. */
    private static float shadeOnQuad(FloatArray vertices, int first, float x, float y, float z) {
        for (int i = first; i < first + 6 * Chunk.FLOATS_PER_VERTEX; i += Chunk.FLOATS_PER_VERTEX) {
            if (vertices.items[i] == x && vertices.items[i + 1] == y && vertices.items[i + 2] == z) {
                return vertices.items[i + Chunk.SHADE_OFFSET];
            }
        }
        throw new AssertionError("Không tìm thấy góc cần đo độ sáng");
    }

    /** Kiểm tra việc nén tại chỗ giữ đủ vị trí, UV và độ sáng của bốn đỉnh qua nhiều mặt. */
    private static void checkIndexedQuadPacking() {
        int floatsPerVertex = Chunk.FLOATS_PER_VERTEX;
        int quads = 3;
        int[] inputCorners = {0, 1, 2, 0, 2, 3};
        FloatArray vertices = new FloatArray();
        for (int quad = 0; quad < quads; quad++) {
            for (int corner : inputCorners) {
                for (int attribute = 0; attribute < floatsPerVertex; attribute++) {
                    vertices.add((quad * 4 + corner) * floatsPerVertex + attribute);
                }
            }
        }

        float[] backingArray = vertices.items;
        Chunk.compactQuads(vertices, quads);
        require(vertices.items == backingArray, "Nén mesh phải dùng lại mảng đỉnh");
        require(vertices.size == quads * 4 * floatsPerVertex, "Nén mesh phải giữ bốn đỉnh mỗi mặt");
        for (int attribute = 0; attribute < vertices.size; attribute++) {
            require(vertices.items[attribute] == attribute, "Nén mesh làm sai thứ tự hoặc dữ liệu đỉnh");
        }
    }

    /** Xác nhận lần ghi riêng metadata làm bẩn mesh và mặc định xóa trạng thái cũ. */
    private static void checkChunkMetadata() {
        Chunk chunk = new Chunk(0, 0, 0);
        byte id = Block.STONE.getId();
        require(chunk.getStateLocal(15, 15, 15) == 0, "Ô chưa có metadata phải đọc được 0");
        require(chunk.setBlockLocal(15, 15, 15, id, 5), "Ghi block có metadata phải báo thay đổi");
        require(chunk.getStateLocal(15, 15, 15) == 5, "Ghi thiếu metadata");
        require(!chunk.setBlockLocal(15, 15, 15, id, 5), "Lần ghi y hệt phải được bỏ qua");
        require(chunk.setBlockLocal(15, 15, 15, id), "Ghi mặc định phải xóa cả metadata cùng ID");
        require(chunk.getStateLocal(15, 15, 15) == 0, "Metadata cũ chưa được xóa");
        require(chunk.setBlockLocal(15, 15, 15, Block.AIR.getId()), "Xóa block phải báo thay đổi");
        require(chunk.isEmpty(), "Xóa block cuối phải làm rỗng chunk");
    }

    /** Kiểm tra thứ tự xa đến gần, phá hòa, và giữ tham chiếu cùng khóa. */
    private static void checkTranslucentSort() {
        Chunk first = new Chunk(4, 0, 0);
        Chunk second = new Chunk(9, 0, 0);
        Chunk third = new Chunk(2, 0, 0);
        Chunk fourth = new Chunk(1, 0, 0);
        Chunk untouched = new Chunk(100, 0, 0);
        Chunk[] chunks = {first, second, third, fourth, untouched};
        float[] distances = {4, 25, 25, 1, 100};
        int[] keys = {4, 9, 2, 1, 100};
        TranslucentChunkSort.sort(chunks, distances, keys, 0);
        TranslucentChunkSort.sort(chunks, distances, keys, 1);
        require(chunks[0] == first && keys[0] == 4 && distances[0] == 4, "Sắp xếp một ô làm đổi phần tử");
        for (int pass = 0; pass < 2; pass++) {
            TranslucentChunkSort.sort(chunks, distances, keys, 4);
            require(chunks[0] == third && keys[0] == 2 && distances[0] == 25, "Sai thứ tự khóa hòa đầu");
            require(chunks[1] == second && keys[1] == 9 && distances[1] == 25, "Sai thứ tự khóa hòa sau");
            require(chunks[2] == first && keys[2] == 4 && distances[2] == 4, "Sai thứ tự khoảng cách trung gian");
            require(chunks[3] == fourth && keys[3] == 1 && distances[3] == 1, "Sai chunk gần nhất");
            require(chunks[4] == untouched && keys[4] == 100 && distances[4] == 100, "Sắp xếp ra ngoài vùng hợp lệ");
        }
    }

    /** Xác nhận mặt giữa hai ô nước bị bỏ kể cả qua biên chunk, còn băng giáp nước có mặt riêng. */
    private static void checkLiquidFacesAtChunkBoundary() {
        World world = World.empty();
        Chunk chunk = new Chunk(0, 0, 0);
        FloatArray opaque = new FloatArray();
        FloatArray translucent = new FloatArray();
        place(world, chunk, 15, 0, 0, Block.WATER, 0);
        world.setBlockInternal(16, 0, 0, Block.WATER.getId(), 0);
        int floatsPerFace = 6 * Chunk.FLOATS_PER_VERTEX;
        chunk.buildVertices(world, opaque, translucent);
        require(opaque.size == 0 && translucent.size == 5 * floatsPerFace, "Nước ở biên chunk còn mặt trong");
        require(hasVertex(translucent, 15.0f, 0.88f, 0.0f), "Mặt thoáng nước sai độ cao ở biên thế giới");

        world.setBlockInternal(16, 0, 0, Block.ICE.getId(), 0);
        chunk.buildVertices(world, opaque, translucent);
        require(translucent.size == 6 * floatsPerFace, "Mặt nước giáp băng phải hiển thị");
    }

    /** Xác nhận khối đá còn mặt sau khe lá và hình xương rồng nằm cách biên ô 1/16. */
    private static void checkLeafAndCactusFaces() {
        World world = World.empty();
        Chunk chunk = new Chunk(0, 0, 0);
        FloatArray opaque = new FloatArray();
        FloatArray translucent = new FloatArray();
        int floatsPerFace = 6 * Chunk.FLOATS_PER_VERTEX;
        place(world, chunk, 1, 1, 1, Block.STONE, 0);
        place(world, chunk, 2, 1, 1, Block.OAK_LEAVES, 0);
        chunk.buildVertices(world, opaque, translucent);
        require(opaque.size == 11 * floatsPerFace && translucent.size == 0, "Đá sau lá cần mặt riêng trong pha đục");

        place(world, chunk, 1, 1, 1, Block.AIR, 0);
        place(world, chunk, 2, 1, 1, Block.CACTUS, 0);
        chunk.buildVertices(world, opaque, translucent);
        require(opaque.size == 6 * floatsPerFace, "Sai số mặt xương rồng đứng riêng");
        require(hasVertex(opaque, 2.0625f, 2.0f, 1.9375f), "Xương rồng phải thụt vào 1/16 mỗi bên");
        require(!hasVertex(opaque, 2.0f, 2.0f, 2.0f), "Xương rồng tràn ra tới góc ô");
    }

    /** Xác nhận mỗi mặt phẳng cây có hai hướng và chỉ giữ phần thành tuyết cao lộ ra. */
    private static void checkCrossPlantAndSnowFaces() {
        World world = World.empty();
        Chunk chunk = new Chunk(0, 0, 0);
        FloatArray opaque = new FloatArray();
        FloatArray translucent = new FloatArray();
        int floatsPerFace = 6 * Chunk.FLOATS_PER_VERTEX;
        place(world, chunk, 1, 1, 1, Block.DEAD_BUSH, 0);
        chunk.buildVertices(world, opaque, translucent);
        require(opaque.size == 4 * floatsPerFace && translucent.size == 0, "Cây chữ X thiếu mặt quay ngược");

        place(world, chunk, 1, 1, 1, Block.SNOW_LAYER, 0);
        place(world, chunk, 2, 1, 1, Block.SNOW_LAYER, 2);
        chunk.buildVertices(world, opaque, translucent);
        require(opaque.size == 11 * floatsPerFace, "Hai lớp tuyết chỉ sinh một phần thành chung lộ ra");
        require(hasVertex(opaque, 2.0f, 1.125f, 1.0f), "Thiếu đáy của phần thành tuyết nằm trên lớp thấp");
        require(hasVertex(opaque, 2.0f, 1.375f, 1.0f), "Mặt tuyết 3 lớp sai độ cao");
    }

    /** Ghi một ô thử vào chunk và nguồn dữ liệu lân cận thế giới. */
    private static void place(World world, Chunk chunk, int x, int y, int z, Block block, int state) {
        world.setBlockInternal(x, y, z, block.getId(), state);
        chunk.setBlockLocal(x, y, z, block.getId(), state);
    }

    /** Tìm một đỉnh theo tọa độ vị trí, bỏ qua UV và hệ số ánh sáng. */
    private static boolean hasVertex(FloatArray vertices, float x, float y, float z) {
        for (int i = 0; i < vertices.size; i += Chunk.FLOATS_PER_VERTEX) {
            if (vertices.items[i] == x && vertices.items[i + 1] == y && vertices.items[i + 2] == z) {
                return true;
            }
        }
        return false;
    }

    /** Báo rõ điều kiện sai khi kiểm tra phát hiện hồi quy. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
