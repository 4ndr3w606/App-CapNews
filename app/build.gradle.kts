import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}
val gnewsApiKey = (providers.environmentVariable("GNEWS_API_KEY").orNull
    ?: localProperties.getProperty("GNEWS_API_KEY")).orEmpty().trim()
require(gnewsApiKey.matches(Regex("[0-9a-fA-F]{32}"))) {
    "Configura una clave válida de GNews en local.properties o en la variable de entorno GNEWS_API_KEY."
}

android {
    namespace = "com.example.newsapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.newsapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GNEWS_API_KEY", "\"$gnewsApiKey\"")
    }

    // 1. Habilitar ViewBinding para vinculación rápida de vistas
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Dependencias base de tu proyecto
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.runtime.saved.instance.state)
    implementation(libs.material)

    implementation("androidx.browser:browser:1.8.0")

    // 2. Consumo de API REST (Retrofit + Gson Converter)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 3. Carga eficiente de imágenes (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")

    // 4. Persistencia de Datos Offline (Room Database)
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // 5. Corrutinas y soporte de ciclo de vida (Lifecycle)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Pruebas
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
