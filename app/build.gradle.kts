import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: keystore.properties on a dev machine, environment variables in CI.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.towerduel.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.towerduel.game"
        minSdk = 26
        targetSdk = 34
        // The release workflow passes the real version; local builds are marked "dev".
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "dev"
    }

    signingConfigs {
        create("release") {
            signingValue("storeFile", "KEYSTORE_FILE")?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without a keystore (a contributor's machine) the release APK is left unsigned.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile?.exists() == true }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Tells Compose the game's content classes are immutable, so UI that takes them can be skipped.
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${projectDir.absolutePath}/compose_stability.conf"
        )
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.all {
            // The balance simulation prints a results table; show it in the build log.
            it.testLogging.showStandardStreams = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Android ships org.json, but unit tests run on the plain JVM where it is only a stub.
    testImplementation("org.json:json:20240303")
}
