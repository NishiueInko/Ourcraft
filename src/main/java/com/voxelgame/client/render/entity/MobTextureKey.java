package com.voxelgame.client.render.entity;

/** Định danh một ảnh thực thể theo nhóm, tên tệp và màu khi ảnh thiếu. */
public record MobTextureKey(String group, String name, int fallbackRgb) {
    /** Dựng đường dẫn classpath khi nạp trước và kiểm tra tài nguyên. */
    public String path() {
        return "textures/entity/" + group + "/" + name + ".png";
    }
}
