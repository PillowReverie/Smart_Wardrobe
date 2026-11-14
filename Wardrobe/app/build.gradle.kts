import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release 签名信息保存在项目根目录的本机配置文件中，不跟随源码分发。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val appVersionCode = providers.gradleProperty("APP_VERSION_CODE").get().toInt()
val betaSequence = providers.gradleProperty("APP_BETA_SEQUENCE")
        .getOrElse("1")

android {
    namespace = "com.example.wardrobe"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.wardrobe"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        manifestPlaceholders["appLabel"] = "智能衣橱"
        buildConfigField("boolean", "SHOW_AI_DESCRIPTION", "false")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(
                    keystoreProperties.getProperty("storeFile")
                )
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // 开发包同样显示隐藏描述，方便调试 Prompt 注入结果。
            buildConfigField("boolean", "SHOW_AI_DESCRIPTION", "true")
        }
        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta.$betaSequence"
            isDebuggable = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            manifestPlaceholders["appLabel"] = "智能衣橱 Beta"
            buildConfigField("boolean", "SHOW_AI_DESCRIPTION", "true")
        }
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            manifestPlaceholders["appLabel"] = "智能衣橱"
            buildConfigField("boolean", "SHOW_AI_DESCRIPTION", "false")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

configurations.configureEach {
    // 离线缓存中同时存在旧版 annotation 与新版 annotation-jvm，二者类名相同。
    // 统一保留 annotation-jvm，避免 checkDebugDuplicateClasses 失败。
    exclude(group = "androidx.annotation", module = "annotation")
}

dependencies {
    implementation("com.github.yalantis:ucrop:2.2.8")
    implementation(libs.annotation)
    implementation(libs.errorprone)
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.core)
    implementation(libs.core.ktx)
    implementation(libs.exifinterface)
    implementation(libs.glide)
    implementation(libs.gson)
    implementation(libs.material)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.okhttp)
    implementation(libs.recyclerview)
    implementation(libs.retrofit)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.converter.gson)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
