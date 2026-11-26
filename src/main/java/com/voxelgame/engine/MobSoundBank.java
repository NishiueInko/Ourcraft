package com.voxelgame.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Nạp và sở hữu các bộ đệm WAV và OGG của mob, tra mẫu theo ID và sự kiện. */
final class MobSoundBank implements Disposable {
    /** Vị trí cố định, số biến thể bắt buộc và số biến thể tối đa cho từng nhóm mẫu. */
    private static final String ROOT = "audio/sfx/mob/";
    private static final String SHARED = "shared";
    private static final int REQUIRED_VARIANTS = 2;
    private static final int MAX_VARIANTS = 4;

    /** Danh mục cố định đọc được cả khi tài nguyên nằm bên trong JAR. */
    private static final String[] MOB_IDS = {
            "zombie", "zombie_villager", "skeleton", "creeper", "spider", "enderman", "witch",
            "cow", "sheep", "pig", "chicken", "horse", "donkey", "rabbit", "camel", "camel_husk",
            "husk", "parched", "goat", "polar_bear", "wolf", "fox", "stray"
    };
    /** Bốn sự kiện chung được đóng gói cho mọi mob trong danh mục. */
    private static final MobSoundEvent[] COMMON_EVENTS = {
            MobSoundEvent.AMBIENT, MobSoundEvent.HURT, MobSoundEvent.DEATH, MobSoundEvent.STEP
    };
    /** Số khe sự kiện để tra mẫu trực tiếp qua ordinal của enum. */
    private static final int EVENT_COUNT = MobSoundEvent.values().length;

    /** Một bảng theo loài và một bảng riêng cho hiệu ứng dùng chung. */
    private final Map<String, Sound[][]> samplesByMob = new HashMap<>();
    private final Sound[][] sharedSamples = new Sound[EVENT_COUNT][];

    /** Nạp từng đường dẫn đúng một lần theo thứ tự cố định khi mở game. */
    MobSoundBank() {
        for (String mobId : MOB_IDS) {
            Sound[][] events = new Sound[EVENT_COUNT][];
            samplesByMob.put(mobId, events);
            for (MobSoundEvent event : COMMON_EVENTS) {
                if (hasSpeciesSample(mobId, event)) {
                    events[event.ordinal()] = loadVariants(mobId, event);
                }
            }
        }
        loadSpecial("creeper", MobSoundEvent.CREEPER_PRIMED);
        loadSpecial(SHARED, MobSoundEvent.EXPLODE);
        loadSpecial(SHARED, MobSoundEvent.BOW_SHOOT);
        loadSpecial("enderman", MobSoundEvent.ENDERMAN_STARE);
        loadSpecial("enderman", MobSoundEvent.ENDERMAN_TELEPORT);
        loadSpecial("enderman", MobSoundEvent.ENDERMAN_SCREAM);
        loadSpecial("witch", MobSoundEvent.WITCH_THROW);
        loadSpecial("witch", MobSoundEvent.WITCH_DRINK);
        loadSpecial("goat", MobSoundEvent.GOAT_RAM);
        loadSpecial("goat", MobSoundEvent.GOAT_SCREAM);
        loadSpecial("wolf", MobSoundEvent.WOLF_GROWL);
        loadSpecial("camel", MobSoundEvent.CAMEL_DASH);
        loadSpecial("camel_husk", MobSoundEvent.CAMEL_DASH);
        loadSpecial("husk", MobSoundEvent.HUSK_CONVERT);
        loadSpecial(SHARED, MobSoundEvent.SPEAR_CHARGE);
    }

    /** Creeper im lặng lúc chờ. Bốn loài không có mẫu bước riêng dùng âm của khối dưới chân. */
    static boolean hasSpeciesSample(String id, MobSoundEvent event) {
        if (event == MobSoundEvent.AMBIENT) return !id.equals("creeper");
        return event != MobSoundEvent.STEP || !MobSoundEvent.usesBlockSteps(id);
    }

    /** Lưu nhóm đặc thù đúng một nơi, kể cả hiệu ứng nhiều loài dùng chung. */
    private void loadSpecial(String folder, MobSoundEvent event) {
        Sound[][] events = SHARED.equals(folder) ? sharedSamples : samplesByMob.get(folder);
        events[event.ordinal()] = loadVariants(folder, event);
    }

    /** Bỏ qua mẫu thiếu hoặc lỗi và giữ liền các phần tử đã nạp để chọn đồng đều (hỗ trợ 2–4 biến thể). */
    private static Sound[] loadVariants(String folder, MobSoundEvent event) {
        boolean originalOgg = event == MobSoundEvent.BOW_SHOOT || event == MobSoundEvent.SPEAR_CHARGE;
        int limit = originalOgg ? 1 : MAX_VARIANTS;
        Sound[] variants = new Sound[limit];
        int loaded = 0;
        for (int variant = 1; variant <= limit; variant++) {
            String path = ROOT + folder + "/" + event.assetName() + (originalOgg ? ".ogg" : variant + ".wav");
            FileHandle file = AudioManager.findAsset(path);
            if (file == null) {
                if (variant <= REQUIRED_VARIANTS) {
                    System.err.println("[AudioManager] Thiếu mẫu âm thanh " + path);
                }
                continue;
            }
            try {
                variants[loaded] = Gdx.audio.newSound(file);
                loaded++;
            } catch (RuntimeException error) {
                AudioManager.logLoadError(file, error);
            }
        }
        if (loaded == 0) {
            return null;
        }
        return loaded == variants.length ? variants : Arrays.copyOf(variants, loaded);
    }

    /** Chọn biến thể thành công trong danh mục đã nạp, không tạo đối tượng khi phát. */
    Sound choose(String mobId, MobSoundEvent event, ThreadLocalRandom random) {
        Sound[][] events = samplesByMob.get(mobId);
        if (events == null) {
            return null;
        }
        int index = event.ordinal();
        Sound[] variants = events[index];
        if (variants == null && event == MobSoundEvent.CAMEL_DASH && "camel_husk".equals(mobId)) {
            variants = samplesByMob.get("camel")[index];
        }
        if (variants == null) {
            variants = sharedSamples[index];
        }
        return variants == null ? null : variants[random.nextInt(variants.length)];
    }

    /** Giải phóng các mẫu đã mở thành công, gồm cả nhóm chỉ nạp được một biến thể. */
    @Override
    public void dispose() {
        for (Sound[][] events : samplesByMob.values()) {
            disposeEvents(events);
        }
        samplesByMob.clear();
        disposeEvents(sharedSamples);
    }

    /** Xóa khe sở hữu sau khi giải phóng, cho phép gọi dọn lại an toàn. */
    private static void disposeEvents(Sound[][] events) {
        for (int i = 0; i < events.length; i++) {
            Sound[] variants = events[i];
            if (variants != null) {
                for (Sound variant : variants) {
                    variant.dispose();
                }
                events[i] = null;
            }
        }
    }
}
