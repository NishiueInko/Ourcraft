package com.voxelgame.core;

import com.voxelgame.world.entity.player.Player;
import com.voxelgame.world.item.Item;
import com.voxelgame.world.level.World;

/**
 * Giao diện hợp đồng chuẩn hóa tương tác chuột phải giữa người chơi và đối tượng trong thế giới.
 * Dùng để phân biệt hành vi "có thể tương tác" (can-do) khỏi cây kế thừa lớp (is-a),
 * ví dụ: cắt len Cừu, vắt sữa Bò/Dê, thuần hóa Sói, hoặc lên yên thú cưỡi.
 */
public interface Interactable {

    /**
     * Xử lý tương tác chuột phải từ người chơi với vật phẩm đang cầm trên tay.
     *
     * @param world    Thế giới hiện tại.
     * @param player   Người chơi thực hiện tương tác.
     * @param heldItem Vật phẩm đang cầm ở ô Hotbar hiện tại (có thể null nếu tay không).
     * @return {@code true} nếu tương tác đã được xử lý và tiêu thụ sự kiện; ngược lại {@code false}.
     */
    boolean onInteract(World world, Player player, Item heldItem);
}
