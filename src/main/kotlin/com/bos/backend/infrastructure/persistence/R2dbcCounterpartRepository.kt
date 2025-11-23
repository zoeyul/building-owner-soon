package com.bos.backend.infrastructure.persistence

import com.bos.backend.domain.counterpart.entity.Counterpart
import com.bos.backend.domain.counterpart.repository.CounterpartRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria.where
import org.springframework.data.relational.core.query.Query.query
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcCounterpartRepository(
    private val template: R2dbcEntityTemplate,
    private val databaseClient: DatabaseClient,
) : CounterpartRepository {
    override suspend fun save(counterpart: Counterpart): Counterpart {
        return template.insert(counterpart).awaitSingle()
    }

    override suspend fun findById(id: Long): Counterpart? {
        return template.selectOne(
            query(where("id").`is`(id)),
            Counterpart::class.java,
        ).awaitFirstOrNull()
    }

    override fun findByUserId(userId: Long): Flow<Counterpart> {
        return template.select(
            query(where("user_id").`is`(userId)),
            Counterpart::class.java,
        ).asFlow()
    }

    override suspend fun update(counterpart: Counterpart): Counterpart {
        return template.update(counterpart).awaitSingle()
    }

    override suspend fun deleteById(id: Long) {
        template.delete(
            query(where("id").`is`(id)),
            Counterpart::class.java,
        ).awaitFirstOrNull()
    }

    override suspend fun countTransactionsByCounterpartId(counterpartId: Long): Long {
        val result =
            databaseClient.sql(
                """
                SELECT COUNT(*) as count
                FROM transactions
                WHERE counterpart_id = :counterpartId
                  AND deleted_at IS NULL
                """.trimIndent(),
            )
                .bind("counterpartId", counterpartId)
                .fetch()
                .one()
                .awaitSingle()

        return (result["count"] as? Number)?.toLong() ?: 0L
    }
}
