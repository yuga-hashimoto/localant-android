plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
}

val buildTsnetBridge = providers.environmentVariable("LOCALANT_BUILD_TSNET").orNull == "1"
val nativeBridgeAar = layout.projectDirectory.file("libs/localant-native.aar").asFile
val releaseStoreFile = providers.environmentVariable("LOCALANT_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("LOCALANT_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("LOCALANT_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("LOCALANT_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.localant.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.localant.android"
        minSdk = 30
        targetSdk = 36
        versionCode = providers.environmentVariable("LOCALANT_VERSION_CODE").orNull?.toIntOrNull() ?: 7
        versionName = providers.environmentVariable("LOCALANT_VERSION_NAME").orNull ?: "0.1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "NATIVE_TSNET_ENABLED", buildTsnetBridge.toString())
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            if (buildTsnetBridge) {
                java.srcDir("src/native/java")
            }
        }
    }
}

val buildNativeBridge by tasks.registering(Exec::class) {
    group = "build"
    description = "Build the gomobile Tailscale Funnel AAR."
    workingDir(rootProject.projectDir)
    commandLine("bash", "scripts/build-native.sh")
}

if (buildTsnetBridge) {
    tasks.named("preBuild").configure {
        dependsOn(buildNativeBridge)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")

    if (buildTsnetBridge) {
        implementation(files(nativeBridgeAar))
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.7.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

val releaseTaskNames = setOf("assembleRelease", "bundleRelease", "packageRelease")

tasks.configureEach {
    if (name in releaseTaskNames) {
        doFirst {
            if (!releaseSigningConfigured) {
                throw GradleException(
                    "Release signing is not configured. Set LOCALANT_RELEASE_STORE_FILE, " +
                        "LOCALANT_RELEASE_STORE_PASSWORD, LOCALANT_RELEASE_KEY_ALIAS, and " +
                        "LOCALANT_RELEASE_KEY_PASSWORD.",
                )
            }
        }
    }
}
