package com.mobilealarm.data

import com.mobilealarm.data.local.AlarmDao
import com.mobilealarm.data.local.toDomain
import com.mobilealarm.data.local.toEntity
import com.mobilealarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(private val dao: AlarmDao) {
    val alarms: Flow<List<Alarm>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Alarm? = dao.findById(id)?.toDomain()

    suspend fun getAll(): List<Alarm> = dao.getAll().map { it.toDomain() }

    suspend fun save(alarm: Alarm): Long {
        return if (alarm.id == 0L) {
            dao.insert(alarm.toEntity())
        } else {
            dao.update(alarm.toEntity())
            alarm.id
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
}