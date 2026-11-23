package com.bidaboss.healthy.workout.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SportProfileRepository(private val dao: SportProfileDao) {

    /** null tant que l'accueil sport n'a pas été rempli. */
    fun observe(): Flow<SportProfile?> = dao.observe().map { e ->
        e?.let {
            SportProfile(
                level = SportLevel.entries.firstOrNull { l -> l.code == it.level } ?: SportLevel.BEGINNER,
                sessionsPerWeek = it.sessionsPerWeek,
                days = it.days.split(',').mapNotNull { d -> d.trim().toIntOrNull()?.takeIf { n -> n in 1..7 } }.sorted(),
                durationMin = it.durationMin,
                goal = SportGoal.entries.firstOrNull { g -> g.code == it.goal } ?: SportGoal.GENERAL,
                painZones = codes<PainZone>(it.painZones) { z -> z.code },
                painOther = it.painOther,
                medicalFollowUp = it.medicalFollowUp,
                medicalNotes = it.medicalNotes,
                equipment = codes<Equipment>(it.equipment) { q -> q.code },
                avoid = it.avoid,
            )
        }
    }

    suspend fun save(p: SportProfile) = dao.save(
        SportProfileEntity(
            level = p.level.code,
            sessionsPerWeek = p.sessionsPerWeek,
            days = p.days.sorted().joinToString(","),
            durationMin = p.durationMin,
            goal = p.goal.code,
            painZones = p.painZones.joinToString(",") { it.code },
            painOther = p.painOther.trim(),
            medicalFollowUp = p.medicalFollowUp,
            medicalNotes = p.medicalNotes.trim(),
            equipment = p.equipment.joinToString(",") { it.code },
            avoid = p.avoid.trim(),
        ),
    )
}
