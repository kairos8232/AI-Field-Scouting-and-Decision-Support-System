package com.alleyz15.farmtwinai.data.farm

import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft

data class TimelinePhotoCacheEntry(
    val dayNumber: Int,
    val photoBase64: String,
    val photoMimeType: String,
    val updatedAtEpochMs: Long,
)

data class TimelineStageVisualCacheEntry(
    val dayNumber: Int,
    val title: String,
    val description: String,
    val imageDataUrl: String,
    val provider: String,
    val updatedAtEpochMs: Long,
)

data class FarmConfigFarmEntry(
    val id: String,
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
)

data class FarmConfigDraft(
    val userId: String,
    val activeFarmId: String,
    val farms: List<FarmConfigFarmEntry>,
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
    val timelinePhotoCache: List<TimelinePhotoCacheEntry> = emptyList(),
    val timelineStageVisualCache: List<TimelineStageVisualCacheEntry> = emptyList(),
)

data class FarmConfigRemote(
    val activeFarmId: String,
    val farms: List<FarmConfigFarmEntry>,
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
    val timelinePhotoCache: List<TimelinePhotoCacheEntry> = emptyList(),
    val timelineStageVisualCache: List<TimelineStageVisualCacheEntry> = emptyList(),
)

interface FarmConfigRepository {
    suspend fun upsertFarmConfig(draft: FarmConfigDraft)

    suspend fun fetchLatestFarmConfig(userId: String): FarmConfigRemote?
}
