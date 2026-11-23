package com.bidaboss.healthy.meals

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.importformat.FoodSource
import com.bidaboss.healthy.data.HealthyDatabase
import com.bidaboss.healthy.data.RoomTransactionRunner
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.meals.data.RecipeDraft
import com.bidaboss.healthy.meals.data.RecipeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/** « Mes plats » dans une vraie base Room en mémoire, et le calcul d'une part au prorata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecipeRepositoryTest {

    private lateinit var database: HealthyDatabase
    private lateinit var recipes: RecipeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HealthyDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .build()
        recipes = RecipeRepository(database.recipeDao(), RoomTransactionRunner(database))
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Bolognaise : 1 200 kcal pour 1 000 g cuits, 4 parts de 250 g.
    private val bolognese = RecipeDraft(
        id = null,
        name = "Bolognaise maison",
        cookedWeightG = 1000.0,
        servings = 4,
        ingredients = listOf(
            Food("Bœuf haché 5 %", quantityG = 500.0, kcal = 700.0, proteinG = 105.0, fatG = 25.0),
            Food("Sauce tomate", quantityG = 600.0, kcal = 300.0, carbsG = 48.0, fiberG = 9.0),
            Food("Huile d'olive", quantityG = 22.0, kcal = 200.0, fatG = 22.0),
        ),
    )

    @Test
    fun `un plat se relit, une part vaut un quart, au prorata du poids cuit`() = runBlocking {
        val id = recipes.save(bolognese)
        val recipe = recipes.observeRecipes().first().single()
        assertEquals(id, recipe.id)
        assertEquals(listOf("Bœuf haché 5 %", "Sauce tomate", "Huile d'olive"), recipe.ingredients.map { it.name })

        val part = recipe.portion(recipe.servingWeightG!!)
        assertEquals("Bolognaise maison", part.name)
        assertEquals(250.0, part.quantityG!!, 0.0)
        assertEquals("1 part", part.portion)
        assertEquals(300.0, part.kcal, 0.0)
        assertEquals(26.3, part.proteinG!!, 0.0)
        assertEquals(11.8, part.fatG!!, 0.0)
        assertEquals(FoodSource.MANUAL, part.source)

        val grams = recipe.portion(150.0)
        assertEquals(180.0, grams.kcal, 0.0)
        assertEquals("0,5 part", recipe.portion(125.0).portion)
    }

    @Test
    fun `un plat sans parts se compte en grammes, se modifie et se supprime`() = runBlocking {
        val id = recipes.save(bolognese.copy(servings = null))
        val recipe = recipes.getRecipe(id)!!
        assertNull(recipe.servingWeightG)
        assertNull(recipe.portion(200.0).portion)

        recipes.save(bolognese.copy(id = id, name = "Bolo", ingredients = bolognese.ingredients.take(1)))
        assertEquals(listOf("Bœuf haché 5 %"), recipes.getRecipe(id)!!.ingredients.map { it.name })

        recipes.delete(id)
        assertTrue(recipes.observeRecipes().first().isEmpty())
    }
}
