plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val numeroVersion = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val cleSignature = System.getenv("CLE_SIGNATURE_FICHIER")

android {
    namespace = "fr.messagerie.unifiee"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.messagerie.unifiee"
        minSdk = 26
        targetSdk = 37
        versionCode = numeroVersion
        versionName = "1.$numeroVersion"
    }

    // Clé de signature fournie par la CI (secrets GitHub) : les nouvelles versions
    // s'installent par-dessus l'ancienne. Sans elle, on signe avec la clé de débogage.
    signingConfigs {
        if (cleSignature != null) {
            create("publication") {
                storeFile = file(cleSignature)
                storePassword = System.getenv("CLE_SIGNATURE_MDP")
                keyAlias = System.getenv("CLE_SIGNATURE_ALIAS")
                keyPassword = System.getenv("CLE_SIGNATURE_MDP")
            }
        }
    }

    buildTypes {
        release {
            // R8 : retire le code inutilisé (la bibliothèque d'icônes est très grosse sinon).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("publication") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
}
