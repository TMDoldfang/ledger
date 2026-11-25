plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.ledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ledger"
        minSdk = 26
        targetSdk = 35
        // 本機開發用預設值;GitHub Actions 會用 -PversionCode=… -PversionName=… 覆蓋
        // (versionCode 就是 Release 的 tag 數字,App 靠它判斷有沒有新版)
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.2-dev"
    }

    // 線上更新要能「覆蓋安裝」,每個版本都必須用同一把金鑰簽章。
    // 預設用專案裡的 keystore/ledger.jks(個人專案用;金鑰與密碼是公開在 Repo 裡的)。
    // 之後想改用 GitHub Secrets,只要在 Actions 設定下面四個環境變數,不用改這個檔案。
    signingConfigs {
        create("shared") {
            storeFile = file(System.getenv("LEDGER_STORE_FILE") ?: "../keystore/ledger.jks")
            storePassword = System.getenv("LEDGER_STORE_PASSWORD") ?: "ledger-personal"
            keyAlias = System.getenv("LEDGER_KEY_ALIAS") ?: "ledger"
            keyPassword = System.getenv("LEDGER_KEY_PASSWORD") ?: "ledger-personal"
        }
    }

    buildTypes {
        debug {
            // debug 也用同一把金鑰,這樣從 Android Studio 裝的版本之後可以被線上更新覆蓋
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
