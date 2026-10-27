plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.nuvio.tv.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("distribution", "full")
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("release") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val buildArtifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
        val targetApplicationId = variant.testedApks.map { testedApks ->
            buildArtifactsLoader.load(testedApks)?.applicationId
                ?.takeIf(String::isNotBlank)
                ?: error("Could not resolve the target application ID for ${variant.name}")
        }
        checkNotNull(variant.buildConfigFields) {
            "BuildConfig generation must be enabled for ${variant.name}"
        }.put(
            "TARGET_APPLICATION_ID",
            targetApplicationId.map { applicationId ->
                com.android.build.api.variant.BuildConfigField(
                    "String",
                    "\"$applicationId\"",
                    "Application ID of the APK whose baseline profile is collected."
                )
            }
        )
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
