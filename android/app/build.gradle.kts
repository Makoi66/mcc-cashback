plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Откуда Sync берёт данные. Меняется и в самом приложении (экран «Банки» → «Адрес данных»).
val defaultDataUrl = "https://raw.githubusercontent.com/Makoi66/mcc-cashback/main/data/"

android {
    namespace = "dev.mcc.cashback"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mcc.cashback"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "DEFAULT_DATA_URL", "\"$defaultDataUrl\"")
    }

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    sourceSets["test"].kotlin.srcDirs("src/test/kotlin")
    // Снимок ../../data вшивается в APK как assets/data/ — приложение работает до первого Sync.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/bundledData"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Приложение личное: release подписываем debug-ключом, чтобы ставилось без возни.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val bundleData by tasks.registering(Sync::class) {
    from(rootProject.file("../data"))
    into(layout.buildDirectory.dir("generated/bundledData/data"))
    include("**/*.json", "**/*.png")
}
tasks.named("preBuild") { dependsOn(bundleData) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}

// Тест realDataParses читает ../data — правка JSON должна перезапускать тесты.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("../data"))
}
