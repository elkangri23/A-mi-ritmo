plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val releaseSigningEnvNames = listOf(
    "AMR_RELEASE_STORE_FILE",
    "AMR_RELEASE_STORE_PASSWORD",
    "AMR_RELEASE_KEY_ALIAS",
    "AMR_RELEASE_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningEnvNames.associateWith { providers.environmentVariable(it).orNull }
val configuredSigningValues = releaseSigningValues.values.count { it != null }
if (configuredSigningValues != 0 && configuredSigningValues != releaseSigningEnvNames.size) {
    throw GradleException("Set all AMR_RELEASE_* signing variables or leave all of them unset.")
}
val hasReleaseSigning = configuredSigningValues == releaseSigningEnvNames.size

android {
    namespace = "com.teleprompter.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = providers.gradleProperty("appId").get()
        minSdk = 30
        targetSdk = 35
        versionCode = 1000001
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("AMR_RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningValues.getValue("AMR_RELEASE_STORE_PASSWORD")!!
                keyAlias = releaseSigningValues.getValue("AMR_RELEASE_KEY_ALIAS")!!
                keyPassword = releaseSigningValues.getValue("AMR_RELEASE_KEY_PASSWORD")!!
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.wear.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.play.services.wearable)
}
