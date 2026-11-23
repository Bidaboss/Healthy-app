package com.bidaboss.healthy.meals

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.core.openfoodfacts.OffProduct
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.meals.data.FoodSearchFailure
import com.bidaboss.healthy.meals.data.FoodSearchRepository
import com.bidaboss.healthy.meals.data.HttpResponse
import com.bidaboss.healthy.meals.data.RefreshResult
import com.bidaboss.healthy.meals.data.ScanResult
import com.bidaboss.healthy.meals.data.ScannedProductRepository
import com.bidaboss.healthy.meals.ui.ScanStep
import com.bidaboss.healthy.meals.ui.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import java.net.UnknownHostException

/** Scan d'un code-barres : téléphone d'abord, puis Open Food Facts, dans une vraie base Room en mémoire. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScannedProductRepositoryTest {

    private val nutella = "3017620422003"
    private val requested = mutableListOf<String>()
    private var response: () -> HttpResponse = { error("pas de réseau prévu") }

    private lateinit var database: HealthyDatabase
    private lateinit var repository: ScannedProductRepository

    private fun found(code: String = nutella) = HttpResponse(
        200,
        """{"code":"$code","status":1,"product":{"product_name_fr":"Nutella","brands":"Ferrero","serving_quantity":15,
            "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9}}}""",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScannedProductRepository(
            database.scannedProductDao(),
            FoodSearchRepository { url -> requested += url; response() },
            now = { 1_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `un produit trouve est retenu, puis revient sans reseau`() = runBlocking {
        response = { found() }
        val first = repository.scan(nutella) as ScanResult.Known
        assertFalse(first.remembered)
        assertEquals("Nutella", first.product.product.name)
        assertEquals(1, requested.size)
        assertTrue(requested.single().contains(nutella))

        response = { throw UnknownHostException() }
        val second = repository.scan(nutella) as ScanResult.Known
        assertTrue(second.remembered)
        assertEquals(1, requested.size) // aucune nouvelle requête
        assertEquals(15.0, second.product.product.servingG!!, 0.0)
    }

    @Test
    fun `un UPC-A est cherche sous son code sur 13 chiffres`() = runBlocking {
        response = { found("0012345678905") }
        repository.scan("012345678905")
        assertTrue(requested.single().contains("0012345678905"))
    }

    @Test
    fun `un code inconnu ou sans calories propose la saisie`() = runBlocking {
        response = { HttpResponse(200, """{"status":0,"status_verbose":"product not found"}""") }
        val unknown = repository.scan(nutella) as ScanResult.Unknown
        assertNull(unknown.draft)

        response = { HttpResponse(200, """{"status":1,"product":{"product_name":"Bidule","nutriments":{}}}""") }
        val incomplete = repository.scan("96385074") as ScanResult.Unknown
        assertEquals("Bidule", incomplete.draft!!.name)
        assertNull(database.scannedProductDao().get("96385074"))
    }

    @Test
    fun `sans connexion, un produit jamais vu est une panne et rien n'est retenu`() = runBlocking {
        response = { throw UnknownHostException() }
        val failed = repository.scan(nutella) as ScanResult.Failed
        assertEquals(FoodSearchFailure.NoConnection, failed.reason)
        assertNull(database.scannedProductDao().get(nutella))
    }

    @Test
    fun `un code mal lu est refuse sans requete`() = runBlocking {
        assertEquals(ScanResult.InvalidCode, repository.scan("1234567890123"))
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `un produit saisi depuis l'etiquette est retenu sous son code`() = runBlocking {
        response = { throw UnknownHostException() }
        repository.saveFromLabel(
            "96385074",
            OffProduct("96385074", "Maison", null, 250.0, 5.0, null, null, null, 30.0),
        )
        val again = repository.scan("96385074") as ScanResult.Known
        assertTrue(again.remembered)
        assertFalse(again.product.fromOpenFoodFacts)
        assertNotNull(again.product.product.servingG)
    }

    @Test
    fun `la portion du paquet est proposee, puis l'aliment est calcule pour la quantite`() = runBlocking {
        response = { found() }
        val vm = ScanViewModel(repository)
        vm.onCodeRead(nutella)
        val state = withTimeout(10_000) { vm.uiState.first { it.step == ScanStep.PRODUCT } }
        assertEquals("15", state.quantityG)
        vm.setQuantity("30")
        val food = vm.buildFood()!!
        assertEquals(FoodSource.OPEN_FOOD_FACTS, food.source)
        assertEquals(nutella, food.offCode)
        assertEquals(161.7, food.kcal, 0.0)
        assertEquals("Nutella - Ferrero", food.name)
    }

    @Test
    fun `sans portion, 100 g sont proposes, et le formulaire de l'etiquette produit un aliment manuel`() = runBlocking {
        response = { HttpResponse(200, """{"status":0}""") }
        val vm = ScanViewModel(repository)
        vm.onCodeRead(nutella)
        withTimeout(10_000) { vm.uiState.first { it.step == ScanStep.UNKNOWN } }
        vm.openLabel()

        vm.saveLabel() // nom et calories manquent
        assertEquals(ScanStep.LABEL, vm.uiState.value.step)
        assertNotNull(vm.uiState.value.message)

        vm.setLabel(vm.uiState.value.label.copy(name = "Gâteau", kcal = "400", protein = "5,5"))
        vm.saveLabel()
        val state = withTimeout(10_000) { vm.uiState.first { it.step == ScanStep.PRODUCT } }
        assertEquals("100", state.quantityG)
        val food = vm.buildFood()!!
        assertEquals(FoodSource.MANUAL, food.source)
        assertNull(food.offCode)
        assertEquals(400.0, food.kcal, 0.0)
        assertEquals(5.5, food.proteinG!!, 0.0)
        assertNull(food.carbsG)
    }

    @Test
    fun `un code lu deux fois pendant la visee n'est pris qu'une fois`() = runBlocking {
        response = { found() }
        val vm = ScanViewModel(repository)
        vm.onCodeRead(nutella)
        vm.onCodeRead(nutella)
        withTimeout(10_000) { vm.uiState.first { it.step == ScanStep.PRODUCT } }
        assertEquals(1, requested.size)
    }

    @Test
    fun `corriger un produit garde son origine, le supprimer l'oublie`() = runBlocking {
        response = { found() }
        repository.scan(nutella)
        repository.update(OffProduct(nutella, "Pâte maison", null, 500.0, null, null, null, null, null))
        val list = repository.observeAll().first()
        assertEquals("Pâte maison", list.single().product.name)
        assertTrue(list.single().fromOpenFoodFacts)

        repository.delete(nutella)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `mettre a jour reprend les valeurs d'Open Food Facts, a la demande seulement`() = runBlocking {
        response = { found() }
        repository.scan(nutella)
        repository.update(OffProduct(nutella, "Corrigé", null, 1.0, null, null, null, null, null))
        assertEquals(1, requested.size) // la correction n'appelle pas le réseau

        assertEquals(RefreshResult.Updated, repository.refresh(nutella))
        assertEquals("Nutella", repository.observeAll().first().single().product.name)

        response = { throw UnknownHostException() }
        assertTrue(repository.refresh(nutella) is RefreshResult.Failed)
        assertEquals("Nutella", repository.observeAll().first().single().product.name)
    }
}
