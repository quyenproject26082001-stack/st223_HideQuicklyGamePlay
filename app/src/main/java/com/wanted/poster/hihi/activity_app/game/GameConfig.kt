package com.wanted.poster.hihi.activity_app.game

import android.graphics.PointF
import kotlin.math.roundToLong
import kotlin.math.sqrt

object GameConfig {

    // Debug

    /**
     * Hiển thị overlay collision bitmap của killer lên trên map.
     * Dùng để kiểm tra vùng killer không đi được có khớp với map thật không.
     * Đặt true khi cần debug pathfinding, false khi build release.
     */
    const val DEBUG_KILLER_COLLISION = true

    // Killer trail

    /**
     * Hiển thị đường đi của killer trên map.
     * Đặt false để ẩn đường đi, giúp killer di chuyển âm thầm hơn.
     */
    const val SHOW_KILLER_TRAIL = true

    // Killer speed

    /**
     * Hệ số nhân tốc độ di chuyển của killer.
     *   1.0 = tốc độ mặc định
     *   0.5 = chậm hơn
     *   2.0 = nhanh hơn
     */
    const val KILLER_SPEED_MULTIPLIER = 1.0f

    /**
     * Tốc độ gốc của killer tính bằng đơn vị normalized trên giây.
     * Không chỉnh trực tiếp, hãy dùng KILLER_SPEED_MULTIPLIER.
     */
    private const val BASE_KILLER_PATH_SPEED_PER_SECOND = 0.16f

    /**
     * Bước di chuyển gốc mỗi tick trong chế độ debug không dùng animation.
     * Không chỉnh trực tiếp.
     */
    private const val BASE_DEBUG_KILLER_STEP_PER_TICK = 0.004f

    /**
     * Thời gian animation tối thiểu cho mỗi đoạn đường của killer.
     * Tránh trường hợp đường quá ngắn khiến killer dịch chuyển tức thì.
     */
    private const val MIN_KILLER_ANIMATION_DURATION_MS = 900L

    // Killer size

    /**
     * Chiều rộng hình killer = chiều rộng map x ratio.
     * Chiều cao tự động tính theo tỉ lệ ảnh gốc.
     */
    const val KILLER_SIZE_RATIO = 0.15f

    // Spawn badge / avatar size

    /**
     * Bán kính vùng vẽ số phòng hoặc avatar = chiều rộng map x ratio.
     * Ảnh hưởng đến kích thước circle avatar và vùng tap để chọn số.
     */
    const val SPAWN_AVATAR_SIZE_RATIO = 0.055f

    // Tốc độ player chạy từ killerSpawn vào hideSpawn lúc mở màn (normalized units/giây).
    // Tăng → player chạy nhanh hơn; giảm → chạy chậm hơn.
    // So sánh: killer đi tốc độ BASE_KILLER_PATH_SPEED_PER_SECOND = 0.16.
    const val SPAWN_ENTRANCE_SPEED = 0.25f

    // Thời gian tối thiểu mỗi player di chuyển (ms), tránh path quá ngắn chạy tức thì.
    const val SPAWN_ENTRANCE_MIN_TRAVEL_MS = 450L

    // Khoảng trễ giữa hai player liên tiếp bắt đầu rời killerSpawn (ms).
    // Giá trị lớn → hiệu ứng "lần lượt" rõ hơn.
    const val SPAWN_ENTRANCE_STAGGER_MS = 150L

    // Cụm sound spot của phòng:
    // spot chỉ phát khi có player thật nằm trong bán kính của spot đó.
    // Sóng âm sẽ bung ra từ player đang nằm trong vùng, không bung từ tâm phòng nữa.

    // Xác suất killer quyết định đi nghe một sound spot hợp lệ ở mỗi lượt kiểm tra.
    const val ROOM_SOUND_EVENT_CHANCE = 0.35f

    // Bán kính nhận player cho từng loại phòng, tính theo tọa độ normalized của map.
    const val ROOM_SOUND_RADIUS_BEDROOM = 0.12f
    const val ROOM_SOUND_RADIUS_BATHROOM = 0.10f
    const val ROOM_SOUND_RADIUS_TOILET = 0.085f
    const val ROOM_SOUND_RADIUS_KITCHEN = 0.12f
    const val ROOM_SOUND_RADIUS_LIVING = 0.14f

    // Luồng phản kháng của player:
    // ném vật -> đẩy killer lùi lại -> làm choáng -> chạy sang chỗ khác.

    // Công tắc bật hoặc tắt toàn bộ tính năng phản kháng.
    const val PLAYER_RESISTANCE_ENABLED = true

    // Xác suất một player bị nhắm tới sẽ phản kháng thành công.
    const val PLAYER_RESISTANCE_CHANCE = 0.2f

    // Kích thước hiển thị của vật ném, tính theo tỉ lệ chiều rộng map.
    const val PLAYER_RESISTANCE_THROWABLE_SIZE_RATIO = 0.1f

    // Thời gian vật ném bay tới killer.
    const val PLAYER_RESISTANCE_THROW_DURATION_MS = 540L

    // Thời gian killer bị bật lùi ra sau.
    const val PLAYER_RESISTANCE_RECOIL_DURATION_MS = 320L

    // Thời gian killer bị choáng sau khi đã bật lùi.
    const val PLAYER_RESISTANCE_STUN_DURATION_MS = 850L

    // Khoảng cách killer bị đẩy lùi theo hướng vừa lao tới.
    const val PLAYER_RESISTANCE_RECOIL_DISTANCE = 0.08f

    // Thời gian animation player chạy sang vị trí mới.
    const val PLAYER_RESISTANCE_RELOCATE_DURATION_MS = 3000L

    // Khoảng cách tối thiểu ưu tiên giữa chỗ cũ và chỗ mới của player.
    const val PLAYER_RESISTANCE_RELOCATE_MIN_DISTANCE = 0.18f

    // ── Bom (Bomb resistance) ──────────────────────────────────────────────
    // Khoảng cách killer bị đẩy lùi khi trúng bom (normalized 0..1).
    // Lớn hơn PLAYER_RESISTANCE_RECOIL_DISTANCE (0.08) → killer bật ra xa hơn.
    const val BOMB_RESISTANCE_RECOIL_DISTANCE = 0.80f

    // Thời gian animation killer bị bật lùi sau khi trúng bom (ms).
    const val BOMB_RESISTANCE_RECOIL_DURATION_MS = 480L

    // Thời gian killer bị choáng sau khi trúng bom (ms).
    // Lớn hơn PLAYER_RESISTANCE_STUN_DURATION_MS (850) → choáng lâu hơn.
    const val BOMB_RESISTANCE_STUN_DURATION_MS = 2000L

    // Thời gian animation hiệu ứng nổ bung ra tại vị trí killer (ms).
    const val BOMB_EXPLOSION_ANIMATION_DURATION_MS = 2250L

    // Xác suất bom được chọn thay vì đồ vật thường mỗi lần player phản kháng.
    // 0.0 = không bao giờ bom, 1.0 = luôn luôn bom.
    const val BOMB_PICK_CHANCE = 0.3f

    /**
     * Kích thước cạnh tối thiểu của ô số ở màn chọn phòng.
     * Đảm bảo số nhỏ không bị ô quá nhỏ và khó bấm.
     */
    const val SPAWN_BADGE_MIN_SIZE_DP = 28f

    // Game logic

    /**
     * Xác suất killer giết thêm hider gần nhất khi đi qua phòng trống.
     *   0.0 = không bao giờ giết thêm
     *   1.0 = luôn luôn giết thêm
     */
    const val SWEEP_KILL_CHANCE = 0.80f

    // Số người còn sống cần giữ lại ở chế độ multi khi kết thúc ván.
    const val MULTI_SURVIVORS_TO_KEEP = 1

    // ── Killer exit ──────────────────────────────────────────────────────────
    // Khoảng cách killer đi thêm sau khi qua cửa khi kết thúc ván (normalized 0..1).
    const val KILLER_EXIT_PAST_DOOR_DISTANCE = 0.06f

    // Thời gian animation đoạn đi qua cửa (ms). ~0.2s theo yêu cầu.
    const val KILLER_EXIT_PAST_DOOR_MS = 200L

    // Functions

    /** Bước di chuyển mỗi tick đã nhân hệ số tốc độ, dùng trong chế độ debug. */
    fun debugKillerStep(): Float =
        BASE_DEBUG_KILLER_STEP_PER_TICK * KILLER_SPEED_MULTIPLIER

    /**
     * Tính thời gian animation để killer đi hết path với tốc độ hiện tại.
     * Đường dài hơn sẽ chạy lâu hơn, nhưng luôn giữ tối thiểu một ngưỡng.
     */
    fun killerAnimationDurationMs(path: List<PointF>): Long {
        if (path.size < 2) return MIN_KILLER_ANIMATION_DURATION_MS

        var totalDistance = 0f
        for (i in 0 until path.lastIndex) {
            val from = path[i]
            val to = path[i + 1]
            val dx = to.x - from.x
            val dy = to.y - from.y
            totalDistance += sqrt(dx * dx + dy * dy)
        }

        if (totalDistance <= 0f) return MIN_KILLER_ANIMATION_DURATION_MS

        val speedPerSecond = BASE_KILLER_PATH_SPEED_PER_SECOND * KILLER_SPEED_MULTIPLIER
        return ((totalDistance / speedPerSecond) * 1000f)
            .roundToLong()
            .coerceAtLeast(MIN_KILLER_ANIMATION_DURATION_MS)
    }

    fun roomSoundRadius(type: RoomType): Float = when (type) {
        RoomType.BEDROOM -> ROOM_SOUND_RADIUS_BEDROOM
        RoomType.BATHROOM -> ROOM_SOUND_RADIUS_BATHROOM
        RoomType.TOILET -> ROOM_SOUND_RADIUS_TOILET
        RoomType.KITCHEN -> ROOM_SOUND_RADIUS_KITCHEN
        RoomType.LIVING -> ROOM_SOUND_RADIUS_LIVING
    }

    /**
     * Khoảng cách normalized tối thiểu giữa 2 spawn point được chọn.
     * Tránh 2 số xuất hiện quá gần nhau trên map.
     */
    const val SPAWN_MIN_DISTANCE = 0.2f

    /**
     * Chọn [count] spawn index từ [allSpawns] sao cho không có 2 spawn nào
     * cách nhau dưới [minDistance] (tọa độ normalized).
     * Nếu không đủ spawn thỏa điều kiện, fallback thêm spawn gần nhất còn lại.
     */
    fun selectSpawnIndices(
        allSpawns: List<PointF>,
        count: Int,
        minDistance: Float = SPAWN_MIN_DISTANCE
    ): List<Int> {
        if (allSpawns.isEmpty() || count <= 0) return emptyList()
        val shuffled = (allSpawns.indices).shuffled()
        val selected = mutableListOf<Int>()
        val minDistSq = minDistance * minDistance

        for (idx in shuffled) {
            if (selected.size >= count) break
            val c = allSpawns[idx]
            val tooClose = selected.any { i ->
                val e = allSpawns[i]
                val dx = c.x - e.x; val dy = c.y - e.y
                dx * dx + dy * dy < minDistSq
            }
            if (!tooClose) selected.add(idx)
        }

        // Fallback: thêm spawn còn thiếu bỏ qua constraint
        if (selected.size < count) {
            for (idx in shuffled) {
                if (selected.size >= count) break
                if (idx !in selected) selected.add(idx)
            }
        }

        return selected
    }

    fun multiSurvivorsToKeep(totalAssignedPlayers: Int): Int =
        if (totalAssignedPlayers <= 0) 0
        else MULTI_SURVIVORS_TO_KEEP.coerceIn(1, totalAssignedPlayers)

    fun multiKillsNeededToFinish(totalAssignedPlayers: Int): Int =
        (totalAssignedPlayers - multiSurvivorsToKeep(totalAssignedPlayers)).coerceAtLeast(0)
}
