package com.bidaboss.healthy.today

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.body.WeighIn
import com.bidaboss.healthy.core.importformat.MealType
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.MealDraft
import com.bidaboss.healthy.meals.data.MealRepository
import com.bidaboss.healthy.today.data.MaintenanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/** Maintien réel : ajustement appliqué en un seul endroit, décisions gardées, proposition calculée sur les repas et les pesées. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MaintenanceRepositoryTest {
    private val today = LocalDate.of(2026, 11, 23)
    private lateinit var db: HealthyDatabase
    private lateinit var body: BodyRepository
    private lateinit var meals: MealRepository
    private lateinit var repo: MaintenanceRepository
    private val profile = BodyProfile(Sex.MALE, LocalDate.of(1988, 7, 15), 176.0, 75.0, ActivityLevel.ACTIVE, firstName = "Alex")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries().build()
        body = BodyRepository(db.bodyDao())
        meals = MealRepository(db.mealDao(), RoomTransactionRunner(db))
        repo = MaintenanceRepository(meals, body)
    }

    @After
    fun tearDown() = db.close()

    /** 21 jours de repas à 2 900 kcal (3 repas) et des pesées stables. */
    private suspend fun seedStableWeekCycle(kcalPerDay: Double = 2900.0) {
        (1..21).forEach { back ->
            val d = today.minusDays(back.toLong())
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER).forEachIndexed { i, type ->
                meals.saveMeal(MealDraft(null, type, LocalDateTime.of(d, java.time.LocalTime.of(8 + i * 5, 0)), null, null, listOf(Food("repas", kcal = kcalPerDay / 3))))
            }
            if (back % 2 == 0) body.saveWeighIn(WeighIn(d, 75.0))
        }
    }

    @Test
    fun `sans profil, pas de maintien`() = runBlocking {
        assertNull(repo.observe(today).first())
    }

    @Test
    fun `l'ajustement s'applique au maintien, au seul endroit ou il est calcule`() = runBlocking {
        body.saveProfile(profile)
        assertEquals(2580, body.observeMaintenanceKcal(today).first())
        body.adoptAdjustment(180, today)
        assertEquals(2760, body.observeMaintenanceKcal(today).first())
        // Un nouvel enregistrement du profil garde l'ajustement et les décisions.
        body.saveProfile(profile.copy(heightCm = 181.0))
        val saved = body.observeProfile().first()!!
        assertEquals(180, saved.maintenanceAdjustmentKcal)
        assertEquals(ActivityLevel.ACTIVE, saved.adjustmentActivity)
        assertEquals(today, body.observeProposalState().first().decidedOn)
        // Revenir à la formule retire l'ajustement.
        body.clearAdjustment(today)
        assertEquals(0, body.observeProfile().first()!!.maintenanceAdjustmentKcal)
    }

    @Test
    fun `une proposition apparait avec 21 jours de repas et de pesees, et Pas maintenant la masque 14 jours`() = runBlocking {
        body.saveProfile(profile)
        seedStableWeekCycle()
        val info = repo.observe(today).first()!!
        assertTrue(info.proposal.estimate.isReady)
        assertEquals(2900, info.proposal.estimate.estimateKcal)
        assertTrue(info.showCard)
        assertEquals(320, info.proposal.adjustmentKcal)

        body.snoozeProposal(today)
        val snoozed = repo.observe(today).first()!!
        assertFalse(snoozed.showCard)
        assertTrue(snoozed.proposal.available) // le détail du profil reste disponible
        // 14 jours plus tard la carte peut revenir (l'estimation reste prête).
        assertTrue(repo.observe(today.plusDays(14)).first()!!.snoozed.not())
    }

    @Test
    fun `apres adoption, rien de nouveau, et le rappel d'activite apparait si le niveau change`() = runBlocking {
        body.saveProfile(profile)
        seedStableWeekCycle()
        body.adoptAdjustment(180, today)
        val after = repo.observe(today).first()!!
        assertFalse(after.proposal.available)
        assertFalse(after.activityChanged)
        assertFalse(after.showCard)

        body.saveProfile(profile.copy(activity = ActivityLevel.LIGHT))
        val changed = repo.observe(today).first()!!
        assertTrue(changed.activityChanged)
        assertTrue(changed.showCard)
        body.clearAdjustment(today)
        assertFalse(repo.observe(today).first()!!.activityChanged)
    }

    @Test
    fun `garder la formule ferme la proposition pendant 21 jours`() = runBlocking {
        body.saveProfile(profile)
        seedStableWeekCycle()
        body.declineProposal(today)
        assertFalse(repo.observe(today).first()!!.proposal.available)
        assertNotNull(repo.observe(today).first()!!.proposal.lockedUntil)
        assertEquals(2580, Maintenance.maintenanceKcal(body.observeProfile().first()!!, emptyList(), today))
    }

    @Test
    fun `pas assez de donnees, pas de proposition`() = runBlocking {
        body.saveProfile(profile)
        val info = repo.observe(today).first()!!
        assertFalse(info.proposal.available)
        assertFalse(info.showCard)
    }
}
