import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Copie docs/format-import.md et les exemples d'import dans les assets de l'app : les prompts à copier
 * (Réglages) contiennent ainsi toujours le format à jour, sans deuxième copie à maintenir.
 */
abstract class CopyFormatDocs : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("format").apply { deleteRecursively(); mkdirs() }
        sources.files.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bidaboss.healthy"
    compileSdk = 36

    defaultConfig {
        // Ne jamais changer : un autre identifiant = une autre app pour Android.
        applicationId = "com.bidaboss.healthy"
        minSdk = 26
        targetSdk = 35
        // Numéro de build calculé par GitHub Actions (BUILD_NUMBER, voir le workflow) : chaque APK publié a un
        // numéro plus grand, visible en bas de Réglages (« Healthy version 0.1.1042 »). En local, 1. Il doit
        // toujours augmenter : Android refuse d'installer une version plus petite par-dessus.
        val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        // Dossier Google Drive des mises à jour, partagé par lien (lecture). Le lien n'est pas dans le dépôt :
        // GitHub Actions l'injecte au build (secret UPDATE_FOLDER_LINK). Sans lui (build local), il est vide et
        // la vérification des mises à jour dit simplement qu'aucun dossier n'est configuré.
        val updateFolderLink = System.getenv("UPDATE_FOLDER_LINK").orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "UPDATE_FOLDER_LINK", "\"$updateFolderLink\"")
    }

    // Clé de signature unique et stable (voir docs/architecture.md, « Signature de l'APK »).
    // La remplacer empêcherait toutes les mises à jour : ne jamais la régénérer. Elle n'est pas dans le dépôt :
    // GitHub Actions recrée le fichier depuis ses secrets et le passe par ces variables d'environnement.
    val keystorePath = System.getenv("HEALTHY_KEYSTORE_FILE")
    val hasSigningKey = !keystorePath.isNullOrBlank() && file(keystorePath).exists()
    signingConfigs {
        if (hasSigningKey) {
            create("healthy") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("HEALTHY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HEALTHY_KEY_ALIAS")
                keyPassword = System.getenv("HEALTHY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Avec la clé (GitHub Actions), les deux variantes sont signées pareil : on peut passer de l'une à
        // l'autre sans désinstaller. Sans clé (build local), debug prend la clé de debug d'Android et release
        // reste non signé : c'est voulu, un APK à installer ne se construit qu'avec la vraie clé.
        debug {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("healthy")
        }
        release {
            isMinifyEnabled = false
            if (hasSigningKey) signingConfig = signingConfigs.getByName("healthy")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Les schémas Room servent d'« assets » aux tests de migration. Les tests unitaires (Robolectric)
    // lisent les assets de la variante debug, pas ceux du dossier de test : l'APK publié (release)
    // ne les contient pas.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val copyFormatDocs = tasks.register<CopyFormatDocs>("copyFormatDocs") {
    sources.from(
        rootProject.file("docs/format-import.md"),
        rootProject.file("docs/examples/repas-dejeuner.json"),
        rootProject.file("docs/examples/plan-sport-maison.json"),
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyFormatDocs, CopyFormatDocs::outputDir)
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    // Schémas Room versionnés dans le dépôt : base des migrations futures.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.health.connect)
    // Lecteur de code-barres intégré : CameraX (aperçu) et ZXing (lecture), sans services Google ni réseau.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava)
    implementation(libs.zxing.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}
