package com.bidaboss.healthy.body

import androidx.compose.ui.text.AnnotatedString
import com.bidaboss.healthy.body.ui.BirthDateTransformation
import com.bidaboss.healthy.body.ui.ProfileForm
import com.bidaboss.healthy.body.ui.WeighInForm
import com.bidaboss.healthy.body.ui.birthDateDigits
import com.bidaboss.healthy.body.ui.parseBirthDate
import com.bidaboss.healthy.body.ui.toWeighIn
import com.bidaboss.healthy.body.ui.validate
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Saisie du profil et des pesées : valeurs acceptées et messages d'erreur. */
class BodyFormsTest {

    private val today = LocalDate.of(2026, 11, 11)
    private val complete = ProfileForm("Alex", Sex.MALE, "15/07/1988", "176", "75,5", ActivityLevel.ACTIVE)

    @Test
    fun `profil complet accepte, virgule comprise`() {
        val profile = complete.validate(today).getOrThrow()
        assertEquals(75.5, profile.weightKg, 0.0)
        assertEquals(LocalDate.of(1988, 7, 15), profile.birthDate)
        assertEquals("Alex", profile.firstName)
        assertEquals(LocalDate.of(1988, 7, 15), parseBirthDate("15/7/1988"))
        assertEquals(LocalDate.of(1988, 7, 15), parseBirthDate("15-07-1988"))
        assertEquals(LocalDate.of(1987, 12, 7), parseBirthDate("07121987"))
        assertNull(parseBirthDate("31021987"))
    }

    @Test
    fun `date de naissance tapee en chiffres, barres ajoutees`() {
        assertEquals("07121987", birthDateDigits("07/12/1987 99"))
        fun shown(digits: String) = BirthDateTransformation.filter(AnnotatedString(digits))
        assertEquals("07/12/1987", shown("07121987").text.text)
        assertEquals("07/1", shown("071").text.text)
        assertEquals("07", shown("07").text.text)
        val mapping = shown("07121987").offsetMapping
        assertEquals(10, mapping.originalToTransformed(8))
        assertEquals(3, mapping.originalToTransformed(2))
        assertEquals(8, mapping.transformedToOriginal(10))
        assertEquals(2, mapping.transformedToOriginal(3))
        // Curseur juste après « 07 », barre pas encore tapée.
        assertEquals(2, shown("07").offsetMapping.originalToTransformed(2))
    }

    @Test
    fun `profil incomplet ou absurde refuse avec un message clair`() {
        fun message(form: ProfileForm) = form.validate(today).exceptionOrNull()?.message.orEmpty()
        assertTrue(message(complete.copy(sex = null)).contains("homme ou femme"))
        assertTrue(message(complete.copy(firstName = " ")).contains("prénom"))
        assertTrue(message(complete.copy(birthDate = "01/03/2024")).contains("date de naissance"))
        assertTrue(message(complete.copy(birthDate = "31/02/1988")).contains("date de naissance"))
        assertTrue(message(complete.copy(birthDate = "1988")).contains("date de naissance"))
        assertTrue(message(complete.copy(heightCm = "1,80")).contains("centimètres"))
        assertTrue(message(complete.copy(weightKg = "")).contains("kilos"))
        assertTrue(message(complete.copy(activity = null)).contains("activité"))
    }

    @Test
    fun `pesee avec ou sans tour de taille`() {
        val withWaist = WeighInForm("79,4", "88").toWeighIn(today).getOrThrow()
        assertEquals(79.4, withWaist.weightKg, 0.0)
        assertEquals(88.0, withWaist.waistCm!!, 0.0)
        assertNull(WeighInForm("79", " ").toWeighIn(today).getOrThrow().waistCm)
    }

    @Test
    fun `pesee illisible refusee`() {
        assertTrue(WeighInForm("", "").toWeighIn(today).isFailure)
        assertTrue(WeighInForm("80", "abc").toWeighIn(today).isFailure)
        assertTrue(WeighInForm("80", "8").toWeighIn(today).isFailure)
    }
}
