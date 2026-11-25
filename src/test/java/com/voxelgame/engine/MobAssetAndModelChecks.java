package com.voxelgame.engine;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.voxelgame.entity.LivingEntity;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Kiểm tra hình học và phép biến đổi của mô hình mob trên CPU. */
final class MobAssetAndModelChecks {
    /** Chạy các phép kiểm tra mà không cần ngữ cảnh OpenGL hoặc OpenAL. */
    static void run() {
        checkFaceUvs();
        checkParentAndChildTransform();
        checkAppearanceAssets();
        checkAnimatedModelBounds();
        checkAudioAssets();
        checkSpatialAudio();
    }

    /** Sáu mặt có UV hữu hạn, đúng dải pixel và độc lập với kích thước texture. */
    private static void checkFaceUvs() {
        ModelPart part = new ModelPart(0, 0, 0, -4, 0, -2, 8, 12, 4, 2, 3, 0.5f);
        for (int[] size : new int[][]{{64, 32}, {64, 64}, {128, 64}}) {
            float[] vertices = part.buildVertices(size[0], size[1]);
            require(vertices.length == 36 * 6, "Hộp phải có sáu mặt, mỗi mặt hai tam giác");
            for (int i = 0; i < vertices.length; i += 6) {
                float u = vertices[i + 3];
                float v = vertices[i + 4];
                require(u >= 0 && u <= 1 && v >= 0 && v <= 1, "UV phải hữu hạn trong [0,1]");
                near(u * size[0], Math.round(u * size[0]), "U phải khớp tọa độ pixel");
                near(v * size[1], Math.round(v * size[1]), "V phải khớp tọa độ pixel");
            }
            near(vertices[0], -4.5f, "Độ phồng mở rộng vị trí, giữ footprint UV");
            checkWinding(vertices);
        }
    }

    /** Các tam giác phải hướng ra sáu phía để hoạt động khi bật culling. */
    private static void checkWinding(float[] vertices) {
        Vector3[] normals = {
                new Vector3(-1, 0, 0), new Vector3(0, 0, -1), new Vector3(1, 0, 0),
                new Vector3(0, 0, 1), new Vector3(0, 1, 0), new Vector3(0, -1, 0)
        };
        Vector3 ab = new Vector3();
        Vector3 ac = new Vector3();
        for (int face = 0; face < 6; face++) {
            int i = face * 36;
            ab.set(vertices[i + 6] - vertices[i], vertices[i + 7] - vertices[i + 1],
                    vertices[i + 8] - vertices[i + 2]);
            ac.set(vertices[i + 12] - vertices[i], vertices[i + 13] - vertices[i + 1],
                    vertices[i + 14] - vertices[i + 2]);
            require(ab.crs(ac).dot(normals[face]) > 0f, "Tam giác phải quay ra ngoài hộp");
        }
    }

    /** Trẻ kế thừa phép quay và dịch chuyển của cha mà không đổi ma trận đầu vào. */
    private static void checkParentAndChildTransform() {
        ModelPart child = new ModelPart(0, 2, -3, 0, 0, 0, 1, 1, 1, 0, 0, 0);
        ModelPart parent = new ModelPart(4, 0, 0, 0, 0, 0, 2, 2, 2, 0, 0, 0, child);
        parent.setRotation(0, 90, 0);
        Matrix4 root = new Matrix4().setToTranslation(10, 20, 30);
        Matrix4 parentTransform = new Matrix4();
        Matrix4 childTransform = new Matrix4();
        parent.writeTransform(root, parentTransform);
        child.writeTransform(parentTransform, childTransform);
        Vector3 point = childTransform.getTranslation(new Vector3());
        near(point.x, 11, "Điểm xoay con phải xoay theo cha");
        near(point.y, 22, "Điểm xoay con phải cộng cao độ");
        near(point.z, 30, "Điểm xoay con sau khi quay 90 độ");
        near(root.val[Matrix4.M03], 10, "Ma trận gốc phải được giữ nguyên");

        parent.setTranslation(3, 1, 2);
        parent.setVisible(false);
        parent.resetPose();
        parent.writeTransform(root, parentTransform);
        child.writeTransform(parentTransform, childTransform);
        childTransform.getTranslation(point);
        require(parent.isVisible(), "Tư thế cũ phải trả lại trạng thái hiện của bộ phận");
        near(point.x, 14f, "Đặt lại tư thế phải xóa góc quay và phần dịch chuyển cũ");
        near(point.y, 22f, "Đặt lại tư thế phải giữ cao độ gốc của con");
        near(point.z, 27f, "Đặt lại tư thế phải giữ điểm xoay đã khai báo");
    }

    /** Kiểm tra hình học từng ngoại hình khớp kích thước thật của ảnh được đóng gói. */
    private static void checkAppearanceAssets() {
        BufferedImage equipment = readTexture(MobTextureManager.HELD_EQUIPMENT);
        for (MobAppearance appearance : MobAppearance.values()) {
            BufferedImage base = readTexture(appearance.texture());
            BufferedImage overlay = readTexture(appearance.overlay());
            BufferedImage eyes = readTexture(appearance.eyes());
            ModelPart[] roots = appearance.model().roots();
            if (overlay != null) {
                require(hasLayerPixels(roots, ModelPart.Layer.OVERLAY, overlay),
                        "Lớp phủ không có pixel trên hình học: " + appearance);
            }
            for (ModelPart root : roots) {
                checkPartImage(root, base, overlay, eyes, equipment, appearance);
            }
        }
        for (MobAppearance.HorseMarkings markings : MobAppearance.HorseMarkings.values()) {
            BufferedImage image = readTexture(markings.texture());
            if (image != null) {
                MobModel horse = MobAppearance.HORSE_BROWN.model();
                require(horse.fitsLayer(ModelPart.Layer.OVERLAY, image.getWidth(), image.getHeight()),
                        "UV ngoài ảnh của hoa văn " + markings);
                require(image.getColorModel().hasAlpha(), "Lớp đốm ngựa cần kênh alpha");
                require(hasLayerPixels(horse.roots(), ModelPart.Layer.OVERLAY, image),
                        "Hoa văn không có pixel trên hình học ngựa: " + markings);
            }
        }
    }

    /** Kiểm tra UV từng nhánh đối với ảnh tương ứng và kênh alpha của lớp phủ. */
    private static void checkPartImage(ModelPart part, BufferedImage base, BufferedImage overlay,
                                       BufferedImage eyes, BufferedImage equipment, MobAppearance appearance) {
        BufferedImage image = switch (part.layer()) {
            case BASE -> base;
            case OVERLAY -> overlay;
            case EYES -> eyes;
            case EQUIPMENT -> equipment;
        };
        if (image != null) {
            require(part.fitsTexture(image.getWidth(), image.getHeight()), "UV ngoài ảnh của " + appearance);
            require(part.layer() == ModelPart.Layer.BASE || image.getColorModel().hasAlpha(),
                    "Lớp phủ, mắt và trang bị cần kênh alpha ở " + appearance);
            if (part.layer() != ModelPart.Layer.OVERLAY) {
                require(hasTexturePixels(part, image),
                        "Không có pixel ở " + appearance + " " + part.layer() + " " + part.geometry().uv());
            }
        }
        for (ModelPart child : part.children()) {
            checkPartImage(child, base, overlay, eyes, equipment, appearance);
        }
    }

    /** Tìm ít nhất một vùng nhìn thấy của lớp trên các nhánh đã gắn hình học. */
    private static boolean hasLayerPixels(ModelPart[] parts, ModelPart.Layer layer, BufferedImage image) {
        for (ModelPart part : parts) {
            if ((part.layer() == layer && hasTexturePixels(part, image))
                    || hasLayerPixels(part.children(), layer, image)) {
                return true;
            }
        }
        return false;
    }

    /** Kiểm tra pixel thật trong sáu mặt UV, bỏ qua vùng trống giữa các mặt trong ảnh. */
    private static boolean hasTexturePixels(ModelPart part, BufferedImage image) {
        float[] vertices = part.buildVertices(image.getWidth(), image.getHeight());
        int faceStride = 6 * ModelPart.STRIDE;
        for (int face = 0; face < vertices.length; face += faceStride) {
            float minU = 1f;
            float minV = 1f;
            float maxU = 0f;
            float maxV = 0f;
            for (int vertex = face; vertex < face + faceStride; vertex += ModelPart.STRIDE) {
                minU = Math.min(minU, vertices[vertex + 3]);
                maxU = Math.max(maxU, vertices[vertex + 3]);
                minV = Math.min(minV, vertices[vertex + 4]);
                maxV = Math.max(maxV, vertices[vertex + 4]);
            }
            for (int y = Math.round(minV * image.getHeight()); y < Math.round(maxV * image.getHeight()); y++) {
                for (int x = Math.round(minU * image.getWidth()); x < Math.round(maxU * image.getWidth()); x++) {
                    if ((image.getRGB(x, y) >>> 24) >= 13) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Tai, sừng, chân và trang bị phải nằm trong cầu bao kể cả ở biên tư thế. */
    private static void checkAnimatedModelBounds() {
        Matrix4 transform = new Matrix4();
        Vector3 point = new Vector3();
        for (MobAppearance appearance : MobAppearance.values()) {
            PreviewMob entity = new PreviewMob(appearance);
            MobModel model = appearance.model();
            for (MobVisualState.Pose pose : MobVisualState.Pose.values()) {
                entity.state.setPose(pose, 1f);
                model.resetPose();
                model.setupAnimation(entity, 3.1f, 1f, 37f, 35f, -25f);
                for (ModelPart root : model.roots()) {
                    checkReach(root, transform, point, model.renderRadius(), appearance, pose);
                }
            }
            model.resetPose();
        }
    }

    /** Tính vị trí thực của các đỉnh với đầy đủ phép biến đổi của cha và con. */
    private static void checkReach(ModelPart part, Matrix4 parent, Vector3 point, float radius,
                                   MobAppearance appearance, MobVisualState.Pose pose) {
        if (!part.isVisible()) {
            return;
        }
        Matrix4 transform = new Matrix4();
        part.writeTransform(parent, transform);
        float[] vertices = part.buildVertices(128, 128);
        for (int i = 0; i < vertices.length; i += ModelPart.STRIDE) {
            point.set(vertices[i], vertices[i + 1], vertices[i + 2]).mul(transform);
            if (point.len2() > radius * radius) {
                throw new AssertionError("Cầu bao phải phủ đỉnh của " + appearance + " ở " + pose);
            }
        }
        for (ModelPart child : part.children()) {
            checkReach(child, transform, point, radius, appearance, pose);
        }
    }

    /** Giải mã ảnh thật từ classpath và báo chính xác tệp lỗi. */
    private static BufferedImage readTexture(MobTextureKey key) {
        if (key == null) {
            return null;
        }
        try (InputStream asset = openAsset(key.path())) {
            BufferedImage image = ImageIO.read(asset);
            require(image != null, "Không giải mã được PNG " + key.path());
            return image;
        } catch (IOException error) {
            throw new AssertionError(key.path(), error);
        }
    }

    /** Kiểm tra hai biến thể WAV cho mọi loài và tất cả hiệu ứng đặc thù. */
    private static void checkAudioAssets() {
        String[] mobs = {"zombie", "zombie_villager", "skeleton", "creeper", "spider", "enderman", "witch",
                "cow", "sheep", "pig", "chicken", "horse", "donkey", "rabbit", "camel", "camel_husk", "husk",
                "parched", "goat", "polar_bear", "wolf", "fox", "stray"};
        MobSoundEvent[] common = {MobSoundEvent.AMBIENT, MobSoundEvent.HURT, MobSoundEvent.DEATH, MobSoundEvent.STEP};
        for (String mob : mobs) {
            for (MobSoundEvent event : common) {
                checkSoundVariants(mob, event);
            }
        }
        for (MobSoundEvent event : MobSoundEvent.values()) {
            String group = switch (event) {
                case AMBIENT, HURT, DEATH, STEP -> null;
                case CREEPER_PRIMED -> "creeper";
                case EXPLODE, BOW_SHOOT, SPEAR_CHARGE -> "shared";
                case ENDERMAN_STARE, ENDERMAN_TELEPORT, ENDERMAN_SCREAM -> "enderman";
                case WITCH_THROW, WITCH_DRINK -> "witch";
                case GOAT_RAM, GOAT_SCREAM -> "goat";
                case WOLF_GROWL -> "wolf";
                case CAMEL_DASH -> "camel";
                case HUSK_CONVERT -> "husk";
            };
            if (group != null) {
                checkSoundVariants(group, event);
            }
        }
    }

    /** Kiểm tra WAV giải mã được thành PCM mono và có đủ dữ liệu để phát. */
    private static void checkSoundVariants(String group, MobSoundEvent event) {
        for (int i = 1; i <= 2; i++) {
            String path = "audio/sfx/mob/" + group + "/" + event.assetName() + i + ".wav";
            try (var input = new BufferedInputStream(openAsset(path)); var audio = AudioSystem.getAudioInputStream(input)) {
                AudioFormat format = audio.getFormat();
                require(format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                        && format.getSampleSizeInBits() == 16 && format.getChannels() == 1
                        && audio.getFrameLength() > 0, "Dữ liệu WAV không hợp lệ: " + path);
            } catch (UnsupportedAudioFileException | IOException error) {
                throw new AssertionError(path, error);
            }
        }
    }

    /** Mở đúng đường dẫn đóng gói thay vì dựa vào thư mục chạy Gradle. */
    private static InputStream openAsset(String path) {
        InputStream stream = MobAssetAndModelChecks.class.getClassLoader().getResourceAsStream(path);
        require(stream != null, "Thiếu tài nguyên " + path);
        return stream;
    }

    /** Kiểm tra độ dốc suy giảm, khoảng nghe mặc định và cao độ mob non. */
    private static void checkSpatialAudio() {
        near(AudioManager.mobGain(0f, 16f), 1f, "Trùng điểm nghe phải dùng âm lượng đầy đủ");
        near(AudioManager.mobGain(64f, 16f), 0.5f, "Nửa bán kính còn nửa âm lượng");
        near(AudioManager.mobGain(256f, 16f), 0f, "Tại bán kính thường phải tắt");
        near(AudioManager.mobGain(256f, MobSoundEvent.EXPLODE.defaultRange()), 0.5f,
                "Tiếng nổ phải nghe xa hơn âm thường");
        near(MobSoundEvent.GOAT_SCREAM.defaultRange(), 28f, "Tiếng dê hét có bán kính riêng");
        near(AudioManager.mobPitch(1.5f, 0.5f), 1.5f, "Mob non cao hơn 1,5 lần ở giữa dải biến thiên");
        require(AudioManager.mobPitch(1.5f, 0f) < 1.5f && AudioManager.mobPitch(1.5f, 1f) > 1.5f,
                "Cao độ phải dao động nhẹ theo mẫu");
    }

    /** So sánh số thực với sai số dấu phẩy động nhỏ. */
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.0001f, message + " (" + actual + ")");
    }

    /** Ném lỗi khi sai đặc tả. */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Thực thể kiểm thử chỉ cung cấp tư thế cho mẫu, không cần chạy thế giới. */
    private static final class PreviewMob extends LivingEntity implements MobVisual {
        private final MobVisualState state;

        /** Đặt diện mạo cần kiểm tra. */
        PreviewMob(MobAppearance appearance) {
            super(0, 0, 0, 0.3f, 1f, 1f, 0f, false);
            state = new MobVisualState(appearance);
        }

        /** Truyền tư thế cho bộ dựng mẫu phân khớp. */
        @Override
        public MobVisualState mobVisualState() {
            return state;
        }

        /** Thực thể thử đứng tại gốc. */
        @Override
        protected void updateLiving(World world, float deltaTime) {
        }

        /** Kiểm thử CPU không gọi lượt vẽ trực tiếp. */
        @Override
        public void render(ShaderProgram shader) {
        }
    }
}
