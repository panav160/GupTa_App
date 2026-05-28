package com.voicecommand.app.correction

import kotlinx.coroutines.flow.Flow

class CorrectionRepository(private val dao: CorrectionDao) {

    val all: Flow<List<CorrectionEntity>> = dao.getAll()

    val allCached: List<CorrectionEntity>
        get() = dao.getAll() as? List<CorrectionEntity> ?: emptyList()

    suspend fun save(rawText: String, correctedText: String) {
        val trimmedRaw = rawText.trim()
        val trimmedCorrected = correctedText.trim()
        if (trimmedRaw.isBlank() || trimmedCorrected.isBlank()) return

        val existing = dao.findByRawText(trimmedRaw)
        if (existing != null) {
            dao.incrementCount(existing.id)
        } else {
            dao.insert(
                CorrectionEntity(
                    rawText = trimmedRaw,
                    correctedText = trimmedCorrected
                )
            )
        }
    }

    suspend fun incrementCount(id: Long) {
        dao.incrementCount(id)
    }
}
