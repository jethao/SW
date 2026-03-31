import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")

if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun signingValue(propertyKey: String, envKey: String): String? {
    val propertyValue = keystoreProperties.getProperty(propertyKey)?.trim().orEmpty()
    if (propertyValue.isNotEmpty()) {
        return propertyValue
    }

    val envValue = System.getenv(envKey)?.trim().orEmpty()
    return envValue.ifEmpty { null }
}

val releaseStoreFile = signingValue("storeFile", "AIRHEALTH_ANDROID_KEYSTORE_PATH")
val releaseStorePassword = signingValue("storePassword", "AIRHEALTH_ANDROID_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "AIRHEALTH_ANDROID_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "AIRHEALTH_ANDROID_KEY_PASSWORD")
val hasAndroidReleaseSigning =
    !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.airhealth.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.airhealth.app"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasAndroidReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
