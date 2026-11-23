package com.bidaboss.healthy.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.bidaboss.healthy.core.update.RemoteApk
import com.bidaboss.healthy.update.data.ApkIdentity
import com.bidaboss.healthy.update.data.UpdateCheck
import com.bidaboss.healthy.update.data.UpdateRepository
import com.bidaboss.healthy.update.data.UpdateStore
import com.bidaboss.healthy.update.ui.UpdateUiState
import com.bidaboss.healthy.update.ui.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** Mise à jour depuis le dossier Drive : détection, vérification de l'APK, vérification au lancement. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpdateTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val cache = File(context.cacheDir, "test-${System.nanoTime()}")
    private val folder = "https://drive.google.com/drive/folders/FAKE_FOLDER_ID_0123456789"
    private val listing = """
        <div class="flip-entry" id="entry-FILE_57_abcdef"><div class="flip-entry-title">healthy-57.apk</div></div>
        <div class="flip-entry" id="entry-FILE_55_abcdef"><div class="flip-entry-title">healthy-55.apk</div></div>
    """
    private var fetched = mutableListOf<String>()
    private var identity: ApkIdentity? = ApkIdentity("com.bidaboss.healthy", 57)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        cache.deleteRecursively()
    }

    private fun store(link: String = folder) = UpdateStore(context, folderLink = link)

    private fun repository(store: UpdateStore = store(), current: Int = 50) = UpdateRepository(
        store = store,
        cacheDir = cache,
        inspectApk = { identity },
        currentVersionCode = current,
        packageName = "com.bidaboss.healthy",
        fetchText = { url -> fetched += url; listing },
        downloadTo = { _, file, progress -> file.writeText("apk"); progress(1f) },
        io = Dispatchers.Unconfined,
    )

    @Test
    fun `une version plus recente du dossier est proposee`() = runBlocking {
        val result = repository().check()
        assertEquals(UpdateCheck.Available(RemoteApk("FILE_57_abcdef", 57, "healthy-57.apk")), result)
        assertTrue(fetched.single().contains("FAKE_FOLDER_ID_0123456789"))
        assertEquals(UpdateCheck.UpToDate, repository(current = 57).check())
        assertEquals(UpdateCheck.NoFolder, repository(store(link = "")).check())
    }

    @Test
    fun `l'APK telecharge doit etre Healthy a la version annoncee`() = runBlocking {
        val apk = RemoteApk("FILE_57_abcdef", 57, "healthy-57.apk")
        val file = repository().download(apk) {}
        assertTrue(file.exists())

        identity = ApkIdentity("com.autre.app", 57)
        val wrongApp = runCatching { repository().download(apk) {} }.exceptionOrNull()
        assertTrue(wrongApp is IOException)

        identity = ApkIdentity("com.bidaboss.healthy", 56)
        val wrongVersion = runCatching { repository().download(apk) {} }.exceptionOrNull()
        assertTrue(wrongVersion?.message.orEmpty().contains("version 56"))
        assertFalse(File(cache, "updates/healthy-57.apk").exists())
    }

    @Test
    fun `verification au retour dans l'app, au plus toutes les 30 minutes`() {
        var now = 1_000_000_000L
        val store = store()
        val viewModel = UpdateViewModel(repository(store), store, now = { now })

        viewModel.checkAutomatically()
        assertTrue(viewModel.state.value is UpdateUiState.Available)
        viewModel.dismiss()

        now += 10 * 60 * 1000L
        viewModel.checkAutomatically()
        assertEquals(UpdateUiState.Hidden, viewModel.state.value)
        assertEquals(1, fetched.size)

        now += UpdateViewModel.AUTO_CHECK_INTERVAL_MS
        viewModel.checkAutomatically()
        assertEquals(2, fetched.size)
        assertTrue(viewModel.state.value is UpdateUiState.Available)
    }

    @Test
    fun `le bandeau montre la version trouvee, meme mise de cote, et disparait une fois a jour`() {
        var now = 1_000_000_000L
        val store = store()
        val viewModel = UpdateViewModel(repository(store), store, now = { now }, currentVersionCode = 50)
        assertEquals(null, viewModel.banner.value)

        viewModel.checkAutomatically()
        assertEquals("0.1.57", viewModel.banner.value)
        viewModel.later()
        assertEquals("0.1.57", viewModel.banner.value)

        // Au prochain lancement, le bandeau se relit de la valeur gardee (aussi posee par la notification du matin).
        assertEquals("0.1.57", UpdateViewModel(repository(store), store, now = { now }, currentVersionCode = 50).banner.value)
        // Une fois la 57 installee, plus de bandeau.
        assertEquals(null, UpdateViewModel(repository(store, current = 57), store, now = { now }, currentVersionCode = 57).banner.value)
    }

    @Test
    fun `checkAndRecord note la version trouvee et l'efface quand l'app est a jour`() = runBlocking {
        val store = store()
        repository(store).checkAndRecord()
        assertEquals(57, store.availableVersionCode)
        repository(store, current = 57).checkAndRecord()
        assertEquals(0, store.availableVersionCode)
    }

    @Test
    fun `sans reseau on reessaie au prochain retour, sans attendre`() {
        var now = 1_000_000_000L
        var offline = true
        val store = store()
        val repository = UpdateRepository(
            store = store,
            cacheDir = cache,
            inspectApk = { identity },
            currentVersionCode = 50,
            packageName = "com.bidaboss.healthy",
            fetchText = { url -> fetched += url; if (offline) throw IOException("hors ligne") else listing },
            io = Dispatchers.Unconfined,
        )
        val viewModel = UpdateViewModel(repository, store, now = { now })

        viewModel.checkAutomatically()
        assertEquals(UpdateUiState.Hidden, viewModel.state.value)

        offline = false
        now += 60 * 1000L
        viewModel.checkAutomatically()
        assertTrue(viewModel.state.value is UpdateUiState.Available)
    }

    @Test
    fun `plus tard met la version de cote pendant 24 heures`() {
        var now = 1_000_000_000L
        val store = store()
        val viewModel = UpdateViewModel(repository(store), store, now = { now })

        viewModel.checkAutomatically()
        viewModel.later()

        now += UpdateViewModel.AUTO_CHECK_INTERVAL_MS
        viewModel.checkAutomatically()
        assertEquals(UpdateUiState.Hidden, viewModel.state.value)

        now += UpdateViewModel.SNOOZE_MS
        viewModel.checkAutomatically()
        assertTrue(viewModel.state.value is UpdateUiState.Available)
        // Le bouton de Réglages, lui, la montre toujours.
        viewModel.dismiss()
        viewModel.checkNow()
        assertTrue(viewModel.state.value is UpdateUiState.Available)
    }

    @Test
    fun `une erreur reseau n'affiche jamais le lien du dossier`() {
        val store = store()
        val failing = UpdateRepository(
            store = store,
            cacheDir = cache,
            inspectApk = { identity },
            fetchText = { url -> throw java.io.FileNotFoundException(url) },
            io = Dispatchers.Unconfined,
        )
        val viewModel = UpdateViewModel(failing, store)
        viewModel.checkNow()
        val message = (viewModel.state.value as UpdateUiState.Failed).message
        assertFalse(message.contains("FAKE_FOLDER_ID_0123456789"))
        assertEquals("Vérification impossible. Vérifie ta connexion.", message)
    }
}
