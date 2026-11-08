package com.vokie.data

import com.vokie.data.local.PairRequestDao
import com.vokie.data.local.PairRequestEntity
import com.vokie.domain.model.PairRequest
import com.vokie.domain.model.PairRequestStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface PairingRepository {
    fun observePendingRequests(): Flow<List<PairRequest>>
    suspend fun getRequest(requestId: String): PairRequest?
    suspend fun sendPairRequest(senderPeerId: String, receiverPeerId: String): PairRequest
    suspend fun acceptPairRequest(requestId: String)
    suspend fun declinePairRequest(requestId: String)
    suspend fun cancelPairRequest(requestId: String)
}

class RoomPairingRepository(
    private val dao: PairRequestDao,
    @Suppress("UNUSED_PARAMETER") private val peerRepository: PeerRepository? = null,
) : PairingRepository {
    override fun observePendingRequests(): Flow<List<PairRequest>> = dao.observePending().map { list -> list.map { it.toDomain() } }

    override suspend fun getRequest(requestId: String): PairRequest? = dao.find(requestId)?.toDomain()

    override suspend fun sendPairRequest(senderPeerId: String, receiverPeerId: String): PairRequest {
        val now = System.currentTimeMillis()
        val request = PairRequest(UUID.randomUUID().toString(), senderPeerId, receiverPeerId, now, now + 10 * 60_000, PairRequestStatus.PENDING)
        dao.upsert(request.toEntity())
        return request
    }

    override suspend fun acceptPairRequest(requestId: String) = dao.updateStatus(requestId, PairRequestStatus.ACCEPTED.name)
    override suspend fun declinePairRequest(requestId: String) = dao.updateStatus(requestId, PairRequestStatus.DECLINED.name)
    override suspend fun cancelPairRequest(requestId: String) = dao.updateStatus(requestId, PairRequestStatus.CANCELLED.name)
}

private fun PairRequestEntity.toDomain() = PairRequest(requestId, senderPeerId, receiverPeerId, createdAt, expiresAt, runCatching { PairRequestStatus.valueOf(status) }.getOrDefault(PairRequestStatus.PENDING))
private fun PairRequest.toEntity() = PairRequestEntity(requestId, senderPeerId, receiverPeerId, createdAt, expiresAt, status.name)
