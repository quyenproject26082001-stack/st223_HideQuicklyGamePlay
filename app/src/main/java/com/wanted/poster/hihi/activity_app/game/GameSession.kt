package com.wanted.poster.hihi.activity_app.game

object GameSession {
    var players: List<PlayerSetupModel> = emptyList()
    var mapIndex: Int = 1
    var killerAssetPath: String? = null

    // Indices into MapData.hiderSpawns that are shown on screen (size up to 10)
    var shownSpawnIndices: IntArray = intArrayOf()

    // playerAssignments[playerIndex] = displayNum (1-based), -1 if not assigned
    var playerAssignments: IntArray = intArrayOf()

    fun setup(players: List<PlayerSetupModel>, mapIndex: Int, killerAssetPath: String?) {
        this.players = players
        this.mapIndex = mapIndex
        this.killerAssetPath = killerAssetPath
        this.playerAssignments = IntArray(players.size) { -1 }
        this.shownSpawnIndices = intArrayOf()
    }

    /** Returns the player index (0-based) assigned to the given displayNum, or -1. */
    fun playerIndexForDisplayNum(displayNum: Int): Int =
        playerAssignments.indexOfFirst { it == displayNum }

    /** Returns which displayNums have been assigned (not -1). */
    fun assignedDisplayNums(): Set<Int> =
        playerAssignments.filter { it != -1 }.toSet()
}
