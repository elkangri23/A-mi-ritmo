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
    namespace = "com.teleprompter.phone"
    compileSdk = 37

    defaultConfig {
        applicationId = providers.gradleProperty("appId").get()
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

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
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
    implementation(libs.jsoup)
    implementation(libs.pdfbox.android)
    implementation(libs.apache.poi.scratchpad)
    implementation(libs.apache.poi.ooxml) {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    }
    implementation(libs.apache.poi.ooxml.full)
    debugImplementation(libs.compose.ui.tooling)
}
