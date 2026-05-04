plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.qi.smbshare"
    compileSdk = 36

    val releaseStoreFilePath = providers.gradleProperty("SMB_SHARE_RELEASE_STORE_FILE")
        .orElse(providers.environmentVariable("SMB_SHARE_RELEASE_STORE_FILE"))
        .orNull
    val releaseStorePassword = providers.gradleProperty("SMB_SHARE_RELEASE_STORE_PASSWORD")
        .orElse(providers.environmentVariable("SMB_SHARE_RELEASE_STORE_PASSWORD"))
        .orNull
    val releaseKeyAlias = providers.gradleProperty("SMB_SHARE_RELEASE_KEY_ALIAS")
        .orElse(providers.environmentVariable("SMB_SHARE_RELEASE_KEY_ALIAS"))
        .orNull
    val releaseKeyPassword = providers.gradleProperty("SMB_SHARE_RELEASE_KEY_PASSWORD")
        .orElse(providers.environmentVariable("SMB_SHARE_RELEASE_KEY_PASSWORD"))
        .orNull
    val hasReleaseSigningConfig = listOf(
        releaseStoreFilePath,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    defaultConfig {
        applicationId = "com.qi.smbshare"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // 生成原生调试符号文件，用于 Google Play 崩溃分析
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            // 避免 AndroidTest 合并资源时因重复 LICENSE 文件冲突
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.foundation)
    
    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // DataStore
    implementation(libs.datastore.preferences)
    
    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)
    
    // SMB
    implementation(libs.smbj)
    
    // Coil（图片加载：异步、内存/磁盘缓存、GIF 支持）
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Media3 ExoPlayer（视频在线预览：兼容常见视频格式）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    
    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
