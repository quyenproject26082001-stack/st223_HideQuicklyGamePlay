package com.wanted.poster.hihi.activity_app.game

import android.content.Context
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.sqrt

class KillerRunner(
    private val mapData: MapData,
    private val shownIndices: List<Int>,
    private val idxToDisplayNum: Map<Int, Int>,
    private val playerDisplayNums: Set<Int>,
    private val mapView: MapNumberView,
    private val context: Context,
    private val isPaused: () -> Boolean
) {
    private data class RoomSoundTrigger(
        val room: RoomInfo,
        val displayNum: Int,
        val spawnIdx: Int,
        val sourcePos: PointF
    )

    private val rng = java.util.Random()
    private val soundChance = GameConfig.ROOM_SOUND_EVENT_CHANCE
    // Dùng chung 1 instance — pixels đã được trích xuất sẵn, tránh tạo lại mỗi lần tính path.
    private val pathfinder = KillerPathfinder(mapData.collisionBitmap)

    suspend fun run(
        onKill: (spawnIdx: Int, displayNum: Int?) -> Unit,
        shouldStop: () -> Boolean,
        onStatusChange: ((String?) -> Unit)? = null
    ) {
        val allSpawns = mapData.hiderSpawns
        val toVisit = (0 until allSpawns.size).shuffled().take(minOf(11, allSpawns.size))

        var currentPos = PointF(mapData.killerSpawn.x, mapData.killerSpawn.y)
        val trail = mutableListOf<PointF>()

        // displayNum vẫn gắn với cùng một player, nhưng vị trí spawn thật có thể đổi
        // sau khi phản kháng thành công và chạy sang chỗ khác.
        val displayNumToSpawnIdx = idxToDisplayNum.entries
            .associate { (spawnIdx, displayNum) -> displayNum to spawnIdx }
            .toMutableMap()
        val alivePlayerDisplayNums = playerDisplayNums
            .filter { it in displayNumToSpawnIdx }
            .toMutableSet()
        val clearedDisplayNums = mutableSetOf<Int>()

        fun displayNumAtSpawn(spawnIdx: Int): Int? =
            displayNumToSpawnIdx.entries.firstOrNull { it.value == spawnIdx }?.key

        fun aliveSpawnIndices(): List<Int> =
            alivePlayerDisplayNums.mapNotNull { displayNumToSpawnIdx[it] }
                .filter { it in allSpawns.indices }

        fun distanceSq(from: PointF, to: PointF): Float {
            val dx = from.x - to.x
            val dy = from.y - to.y
            return dx * dx + dy * dy
        }

        fun playerSourcePosition(displayNum: Int, spawnIdx: Int): PointF {
            val animated = mapView.currentDisplayPosition(displayNum)
            if (animated != null) {
                return PointF(animated.x, animated.y)
            }
            val spawn = allSpawns.getOrNull(spawnIdx) ?: return PointF()
            return PointF(spawn.x, spawn.y)
        }

        fun pickRoomSoundTrigger(): RoomSoundTrigger? {
            if (mapData.rooms.isEmpty()) return null

            val candidates = alivePlayerDisplayNums.mapNotNull { displayNum ->
                val spawnIdx = displayNumToSpawnIdx[displayNum] ?: return@mapNotNull null
                val sourcePos = playerSourcePosition(displayNum, spawnIdx)
                val room = mapData.rooms
                    .filter { soundSpot ->
                        distanceSq(sourcePos, soundSpot.position) <= soundSpot.radius * soundSpot.radius
                    }
                    .minByOrNull { soundSpot ->
                        distanceSq(sourcePos, soundSpot.position)
                    }
                    ?: return@mapNotNull null

                RoomSoundTrigger(
                    room = room,
                    displayNum = displayNum,
                    spawnIdx = spawnIdx,
                    sourcePos = sourcePos
                )
            }

            if (candidates.isEmpty()) return null
            return candidates[rng.nextInt(candidates.size)]
        }

        fun markKilled(spawnIdx: Int, displayNum: Int?) {
            if (displayNum != null) {
                clearedDisplayNums.add(displayNum)
                alivePlayerDisplayNums.remove(displayNum)
                mapView.killNumber(displayNum)
            }
            onKill(spawnIdx, displayNum)
            GameAudio.playKill(context)
        }

        // Cho player một cơ hội chặn pha giết:
        // 1) ném vật
        // 2) đẩy killer lùi lại và làm choáng
        // 3) chạy sang một spawn trống mới
        suspend fun tryPlayerResistance(displayNum: Int, spawnIdx: Int, approachPath: List<PointF>): PointF? {
            if (!GameConfig.PLAYER_RESISTANCE_ENABLED) return null
            if (displayNum !in alivePlayerDisplayNums) return null
            val chance = GameConfig.PLAYER_RESISTANCE_CHANCE.coerceIn(0f, 1f)
            if (chance <= 0f || rng.nextFloat() > chance) return null

            val currentSpawnPos = allSpawns.getOrNull(spawnIdx) ?: return null

            val relocationSpawnIdx = findRelocationSpawnIdx(
                allSpawns = allSpawns,
                displayNumToSpawnIdx = displayNumToSpawnIdx,
                currentSpawnIdx = spawnIdx,
                currentPos = currentSpawnPos,
                killerPos = currentPos
            ) ?: return null

            val relocationPos = allSpawns[relocationSpawnIdx]
            val relocationPath = withContext(Dispatchers.IO) {
                computePath(currentSpawnPos, relocationPos).ifEmpty {
                    listOf(currentSpawnPos, relocationPos)
                }
            }
            val otherTools = ResistanceToolType.entries.filter { it != ResistanceToolType.BOMB }.toTypedArray()
            val toolType = if (rng.nextFloat() < GameConfig.BOMB_PICK_CHANCE) ResistanceToolType.BOMB
                           else otherTools[rng.nextInt(otherTools.size)]
            val isBomb = toolType == ResistanceToolType.BOMB
            val recoilTarget = pointBackAlongPath(
                path = approachPath,
                distance = if (isBomb) GameConfig.BOMB_RESISTANCE_RECOIL_DISTANCE
                           else GameConfig.PLAYER_RESISTANCE_RECOIL_DISTANCE
            ) ?: return null

            onStatusChange?.invoke("Player $displayNum phan khang!")
            awaitResistanceThrow(currentSpawnPos, currentPos, toolType)

            if (isBomb) {
                GameAudio.playExplosion(context)
                mapView.animateExplosion(currentPos) {}
            }

            appendTrail(trail, listOf(currentPos, recoilTarget))
            mapView.setKillerTrail(trail.toList())

            coroutineScope {
                val recoilJob = async {
                    if (isBomb) awaitKillerRecoilAndStun(
                        recoilTarget,
                        GameConfig.BOMB_RESISTANCE_RECOIL_DURATION_MS,
                        GameConfig.BOMB_RESISTANCE_STUN_DURATION_MS
                    ) else awaitKillerRecoilAndStun(recoilTarget)
                }
                val relocateJob = async {
                    awaitDisplayRelocation(displayNum, relocationPath)
                }
                relocateJob.await()
                displayNumToSpawnIdx[displayNum] = relocationSpawnIdx
                recoilJob.await()
            }

            onStatusChange?.invoke("Player $displayNum chay tron!")
            pausedDelay(180L)
            onStatusChange?.invoke(null)
            return recoilTarget
        }

        // Một spawn đang hiển thị lúc này có thể là:
        // - một pha giết bình thường
        // - một player đã phản kháng và đã chạy khỏi vị trí đó
        suspend fun resolveDisplayedSpawn(spawnIdx: Int, approachPath: List<PointF>): PointF {
            val displayNum = displayNumAtSpawn(spawnIdx) ?: return allSpawns[spawnIdx]
            if (displayNum in clearedDisplayNums) return allSpawns[spawnIdx]

            val recoilPos = tryPlayerResistance(displayNum, spawnIdx, approachPath)
            if (recoilPos != null) {
                return recoilPos
            }

            awaitKillShake()
            markKilled(spawnIdx, displayNum)
            return allSpawns[spawnIdx]
        }

        for (spawnIdx in toVisit) {
            if (shouldStop()) break

            val displayNum = displayNumAtSpawn(spawnIdx)
            if (displayNum != null && displayNum in clearedDisplayNums) continue
            waitIfPaused()

            val spawnPos = allSpawns[spawnIdx]
            val soundInfo = if (mapData.rooms.isNotEmpty() && rng.nextFloat() < soundChance) {
                pickRoomSoundTrigger()
            } else null

            if (soundInfo != null) {
                waitIfPaused()
                GameAudio.playRoom(context, soundInfo.room.type)
                mapView.showSoundIndicator(soundInfo.sourcePos)
                val targetPos = allSpawns[soundInfo.spawnIdx]
                val pathToSound = withContext(Dispatchers.IO) { computePath(currentPos, targetPos) }
                appendTrail(trail, pathToSound)
                mapView.setKillerTrail(trail.toList())
                awaitAnimation(pathToSound)
                currentPos = PointF(targetPos.x, targetPos.y)
                mapView.clearSoundIndicator()
                GameAudio.stopRoom()

                val currentTargetSpawnIdx = displayNumToSpawnIdx[soundInfo.displayNum]
                if (currentTargetSpawnIdx == soundInfo.spawnIdx && soundInfo.displayNum in alivePlayerDisplayNums) {
                    currentPos = resolveDisplayedSpawn(soundInfo.spawnIdx, pathToSound)
                    if (shouldStop()) break
                }
                pausedDelay(500L)
                continue
            }

            onStatusChange?.invoke(
                if (displayNum != null) "Killer vao phong $displayNum..." else "Killer kiem tra..."
            )
            val path = withContext(Dispatchers.IO) { computePath(currentPos, spawnPos) }
            appendTrail(trail, path)
            mapView.setKillerTrail(trail.toList())
            awaitAnimation(path, onCrossDoor = { GameAudio.playDoor(context) })
            currentPos = PointF(spawnPos.x, spawnPos.y)

            if (displayNum != null) {
                currentPos = resolveDisplayedSpawn(spawnIdx, path)
                if (shouldStop()) break
            } else if (rng.nextFloat() < GameConfig.SWEEP_KILL_CHANCE) {
                val nearestIdx = aliveSpawnIndices()
                    .minByOrNull { idx ->
                        val p = allSpawns[idx]
                        val dx = p.x - currentPos.x
                        val dy = p.y - currentPos.y
                        dx * dx + dy * dy
                    }
                if (nearestIdx != null) {
                    val nearestPos = allSpawns[nearestIdx]
                    val sweepPath = withContext(Dispatchers.IO) { computePath(currentPos, nearestPos) }
                    appendTrail(trail, sweepPath)
                    mapView.setKillerTrail(trail.toList())
                    awaitAnimation(sweepPath)
                    currentPos = resolveDisplayedSpawn(nearestIdx, sweepPath)
                    if (shouldStop()) break
                }
            }
            pausedDelay(500L)
        }

        // Sau khi kết thúc ván: killer chạy ra cửa gần nhất rồi đi thêm một đoạn ngắn.
        // Không giết thêm ai — đây chỉ là animation thoát.
        val doors = mapData.doorLines
        if (doors.isNotEmpty()) {
            waitIfPaused()
            val nearestDoor = doors.minByOrNull { (p1, p2) ->
                val mx = (p1.x + p2.x) / 2f
                val my = (p1.y + p2.y) / 2f
                val dx = mx - currentPos.x
                val dy = my - currentPos.y
                dx * dx + dy * dy
            }!!
            val doorMid = PointF(
                (nearestDoor.first.x + nearestDoor.second.x) / 2f,
                (nearestDoor.first.y + nearestDoor.second.y) / 2f
            )
            val toDoor = withContext(Dispatchers.IO) { computePath(currentPos, doorMid) }
            appendTrail(trail, toDoor)
            mapView.setKillerTrail(trail.toList())
            awaitAnimation(toDoor, onCrossDoor = { GameAudio.playDoor(context) })
            currentPos = PointF(doorMid.x, doorMid.y)

            // Tiếp tục đi thêm một đoạn ngắn qua cửa (~0.2s).
            if (toDoor.size >= 2) {
                val last = toDoor.last()
                val prev = toDoor[toDoor.size - 2]
                val dx = last.x - prev.x
                val dy = last.y - prev.y
                val len = sqrt(dx * dx + dy * dy)
                if (len > 0.0001f) {
                    val dist = GameConfig.KILLER_EXIT_PAST_DOOR_DISTANCE
                    val pastPoint = PointF(last.x + dx / len * dist, last.y + dy / len * dist)
                    val pastPath = listOf(last, pastPoint)
                    appendTrail(trail, pastPath)
                    mapView.setKillerTrail(trail.toList())
                    awaitAnimation(pastPath, durationMs = GameConfig.KILLER_EXIT_PAST_DOOR_MS)
                }
            }
        }
    }

    private fun appendTrail(trail: MutableList<PointF>, path: List<PointF>) {
        if (path.isEmpty()) return
        if (trail.isEmpty()) trail.addAll(path) else trail.addAll(path.drop(1))
    }

    private fun pointBackAlongPath(path: List<PointF>, distance: Float): PointF? {
        if (path.isEmpty()) return null
        if (path.size == 1) return PointF(path[0].x, path[0].y)

        var remaining = distance.coerceAtLeast(0f)
        for (i in path.lastIndex downTo 1) {
            val from = path[i]
            val to = path[i - 1]
            val dx = to.x - from.x
            val dy = to.y - from.y
            val segmentLength = sqrt(dx * dx + dy * dy)
            if (segmentLength <= 0f) continue
            if (remaining <= segmentLength) {
                val t = (remaining / segmentLength).coerceIn(0f, 1f)
                return PointF(from.x + dx * t, from.y + dy * t)
            }
            remaining -= segmentLength
        }

        return PointF(path.first().x, path.first().y)
    }

    private fun findRelocationSpawnIdx(
        allSpawns: List<PointF>,
        displayNumToSpawnIdx: Map<Int, Int>,
        currentSpawnIdx: Int,
        currentPos: PointF,
        killerPos: PointF
    ): Int? {
        // Chỉ cho chạy sang spawn trống, và ưu tiên các vị trí
        // vừa đủ xa chỗ cũ vừa xa killer hơn.
        val occupied = displayNumToSpawnIdx.values.toSet()
        val candidates = allSpawns.indices.filter { idx ->
            idx != currentSpawnIdx && idx !in occupied
        }
        if (candidates.isEmpty()) return null

        fun distanceSq(a: PointF, b: PointF): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return dx * dx + dy * dy
        }

        val preferred = candidates.filter { idx ->
            val candidate = allSpawns[idx]
            distanceSq(candidate, currentPos) >=
                GameConfig.PLAYER_RESISTANCE_RELOCATE_MIN_DISTANCE *
                GameConfig.PLAYER_RESISTANCE_RELOCATE_MIN_DISTANCE
        }.ifEmpty { candidates }

        return preferred.maxByOrNull { idx ->
            val candidate = allSpawns[idx]
            distanceSq(candidate, currentPos) * 0.65f + distanceSq(candidate, killerPos) * 1.15f
        }
    }

    private suspend fun awaitKillShake() = suspendCancellableCoroutine<Unit> { cont ->
        mapView.animateKillShake { if (cont.isActive) cont.resume(Unit) }
        cont.invokeOnCancellation { mapView.cancelAnimation() }
    }

    private suspend fun awaitAnimation(
        path: List<PointF>,
        durationMs: Long = GameConfig.killerAnimationDurationMs(path),
        onCrossDoor: (() -> Unit)? = null
    ) {
        waitIfPaused()
        suspendCancellableCoroutine<Unit> { cont ->
            mapView.animateKillerAlongPath(
                path,
                durationMs = durationMs,
                doorLines = mapData.doorLines,
                onCrossDoor = onCrossDoor
            ) { if (cont.isActive) cont.resume(Unit) }
            cont.invokeOnCancellation { mapView.cancelAnimation() }
        }
    }

    private suspend fun awaitResistanceThrow(
        from: PointF,
        to: PointF,
        toolType: ResistanceToolType
    ) {
        waitIfPaused()
        suspendCancellableCoroutine<Unit> { cont ->
            mapView.animateResistanceThrow(from, to, toolType) {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { mapView.cancelAnimation() }
        }
    }

    private suspend fun awaitKillerRecoilAndStun(
        recoilTarget: PointF,
        recoilDurationMs: Long = GameConfig.PLAYER_RESISTANCE_RECOIL_DURATION_MS,
        stunDurationMs: Long = GameConfig.PLAYER_RESISTANCE_STUN_DURATION_MS
    ) {
        waitIfPaused()
        suspendCancellableCoroutine<Unit> { cont ->
            mapView.animateKillerRecoilAndStun(recoilTarget, recoilDurationMs, stunDurationMs) {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { mapView.cancelAnimation() }
        }
    }

    private suspend fun awaitDisplayRelocation(displayNum: Int, path: List<PointF>) {
        waitIfPaused()
        suspendCancellableCoroutine<Unit> { cont ->
            mapView.animateDisplayRelocation(displayNum, path) {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { mapView.cancelAnimation() }
        }
    }

    private suspend fun waitIfPaused() { while (isPaused()) delay(100L) }

    private suspend fun pausedDelay(ms: Long) {
        var remaining = ms
        while (remaining > 0L) {
            waitIfPaused()
            val step = minOf(100L, remaining)
            delay(step)
            remaining -= step
        }
    }

    private fun computePath(from: PointF, to: PointF): List<PointF> =
        try {
            pathfinder.findPath(from.x, from.y, to.x, to.y).ifEmpty { listOf(from, to) }
        } catch (_: Exception) { listOf(from, to) }
}
