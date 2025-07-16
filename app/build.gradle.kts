import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.dialergu" // 保持旧项目的namespace
    compileSdk = 36 // 选择两个项目中较高的版本

    defaultConfig {
        applicationId = "com.example.dialergu" // 保持旧项目的applicationId
        minSdk = 24 // 选择两个项目中较高的版本（旧项目24 vs 新项目21）
        targetSdk = 36 // 选择两个项目中较高的版本
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DASH_SCOPE_API_KEY", getLocalProperty("dashscopeApiKey"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // 保持旧项目的较高版本
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11" // 保持旧项目的较高版本
    }
    buildFeatures {
        buildConfig = true // 确保这一行存在且为 true
    }
}
fun getLocalProperty(propertyName: String): String {
    val properties = Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { input ->
            properties.load(input)
        }
    }
    return "\"${properties.getProperty(propertyName) ?: ""}\""
}
dependencies {
    // === 旧项目原有依赖（使用libs版本目录） ===
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // === 新项目的依赖（直接版本号） ===
    // HTTP客户端
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Activity Result API（新项目已有，可能与旧项目重复）
    implementation("androidx.activity:activity-ktx:1.8.2")

    // === 测试依赖 ===
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}