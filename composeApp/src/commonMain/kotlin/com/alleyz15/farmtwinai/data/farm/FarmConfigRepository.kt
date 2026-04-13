package com.alleyz15.farmtwinai.data.farm

import com.alleyz15.farmtwinai.domain.model.AppMode
import com.alleyz15.farmtwinai.domain.model.FarmPoint
import com.alleyz15.farmtwinai.domain.model.LotSectionDraft

data class FarmConfigDraft(
    val userId: String,
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
)

data class FarmConfigRemote(
    val farmName: String,
    val address: String,
    val mapQuery: String,
    val totalAreaInput: String,
    val mode: AppMode,
    val boundaryPoints: List<FarmPoint>,
    val lots: List<LotSectionDraft>,
)

interface FarmConfigRepository {
    suspend fun upsertFarmConfig(draft: FarmConfigDraft)

    suspend fun fetchLatestFarmConfig(userId: String): FarmConfigRemote?
}
