plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.thiengkin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.thiengkin"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Foursquare Places API key (Pro tier, default fields = FREE)
        // - ถ้า key ว่าง → Repository จะ skip FSQ fetch (ทำงานได้แค่ OSM)
        // - วิธีตั้ง: สร้าง Foursquare Developer Account → https://foursquare.com/products/places-api/
        //   → คัดลอก API Key → ใส่ใน gradle.properties หรือ env FOURSQUARE_API_KEY
        val foursquareKey: String =
            (findProperty("FOURSQUARE_API_KEY") as? String)
                ?: System.getenv("FOURSQUARE_API_KEY")
                ?: ""
        buildConfigField("String", "FOURSQUARE_API_KEY", "\"$foursquareKey\"")

        // Supabase config (M3.d — read restaurants from PostgREST mirror)
        // - URL: project ref → https://<ref>.supabase.co (already public, safe to embed)
        // - ANON KEY = Publishable key (RLS-enforced, safe to ship in client)
        //   * NEVER use the Secret/service_role key in client — bypasses RLS
        //   * Get from: https://supabase.com/dashboard/project/zlntknagzrcoduzxngmx/settings/api
        //     → "Publishable and secret API keys" section → Publishable key
        // - ถ้า ANON_KEY ว่าง → Supabase client จะถูก disable ใน ThiengKinApp (เหมือน FSQ)
        //   → Repository.refreshArea() จะ skip fetch → UI แสดง "ยังไม่มีข้อมูล"
        val supabaseUrl: String =
            (findProperty("SUPABASE_URL") as? String)
                ?: System.getenv("SUPABASE_URL")
                ?: ""
        val supabaseAnonKey: String =
            (findProperty("SUPABASE_ANON_KEY") as? String)
                ?: System.getenv("SUPABASE_ANON_KEY")
                ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    composeOptions {
        // Kotlin 2.0+ ใช้ org.jetbrains.kotlin.plugin.compose (ไม่ต้องตั้ง version ที่นี่)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    // play-services-fonts ไม่ต้องการ — GoogleFont.Provider ใช้ GMS content provider (com.google.android.gms.fonts) ที่มีอยู่แล้วบน GMS device
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization (สำหรับ JSON import)
    implementation(libs.kotlinx.serialization.json)

    // HTTP (OSM Overpass + Foursquare)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
}
