package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val gameDao: GameDao) {
    val gameSaveFlow: Flow<GameSave?> = gameDao.getGameSaveFlow()

    suspend fun getGameSaveSync(): GameSave? {
        return gameDao.getGameSaveSync()
    }

    suspend fun saveGame(gameSave: GameSave) {
        gameDao.saveGame(gameSave)
    }

    suspend fun resetGame() {
        gameDao.deleteGame()
        gameDao.saveGame(GameSave()) // Save fresh empty state
    }
}
