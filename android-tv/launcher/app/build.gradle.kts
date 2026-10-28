plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "tech.futureme.tvhome"
    compileSdk = 34

    defaultConfig {
        applicationId = "tech.futureme.tvhome"
        minSdk = 28          // Android TV 9+
        targetSdk = 34
        versionCode = 15
        versionName = "0.10.1"
    }

    // Chave FIXA versionada no repositório, para que todo build (CI ou local)
    // tenha a mesma assinatura e o Android aceite atualizar por cima.
    // É uma chave de uso pessoal; para publicar, troque por uma keystore secreta
    // (ex.: variável de ambiente TVHOME_KEYSTORE apontando para outro arquivo).
    signingConfigs {
        create("local") {
            storeFile = file(System.getenv("TVHOME_KEYSTORE") ?: "../keystore/tvhome-local.jks")
            storePassword = System.getenv("TVHOME_KEYSTORE_PASSWORD") ?: "tvhome-local"
            keyAlias = System.getenv("TVHOME_KEY_ALIAS") ?: "tvhome"
            keyPassword = System.getenv("TVHOME_KEY_PASSWORD") ?: "tvhome-local"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("local")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("local")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")   // ícones do menu nativo (R8 remove o que não é usado)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Compose for TV: Surface/Card com foco e escala, tema para 10-foot UI
    implementation("androidx.tv:tv-material:1.0.0")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Pôsteres: os apps publicam URLs http(s); Coil carrega e cacheia.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // QR code da notícia (abrir a matéria completa no celular): só o núcleo, sem câmera
    implementation("com.google.zxing:core:3.5.3")
}
