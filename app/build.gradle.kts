plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.julie.bithub"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.julie.bithub"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }
    // assinatura fixa (vem dos segredos do GitHub): assim cada versão nova instala por cima da anterior
    signingConfigs {
        create("bit") {
            storeFile = file(System.getenv("CHAVE_ARQUIVO") ?: "chave.p12")
            storePassword = System.getenv("CHAVE_SENHA")
            keyAlias = "bit"
            keyPassword = System.getenv("CHAVE_SENHA")
            storeType = "pkcs12"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("bit")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
