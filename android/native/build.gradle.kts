plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "app.filmmerkezi.tv"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.filmmerkezi.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 34
        versionName = "0.11.4"
        ndk { abiFilters += listOf("armeabi-v7a","arm64-v8a","x86_64") }
        testInstrumentationRunner = if(providers.gradleProperty("frameworkProbe").orNull=="true") "app.filmmerkezi.tv.probe.PreservationProbe" else "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main").java.srcDir("../shared/src")
    sourceSets.getByName("main").java.srcDir("../sharedCatalog/src")
    sourceSets.getByName("main").res.srcDir("../shared/res")
    sourceSets.getByName("test").java.srcDir("../shared/test")
    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("unsignedDebug").orNull == "true") signingConfig = null
        }
        getByName("release") {
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Distribution keys stay outside Gradle, the checkout and CI.
            signingConfig = null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    lint { abortOnError = true }
    packaging { resources.excludes += listOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jsoup:jsoup:1.23.2")
    compileOnly("org.jspecify:jspecify:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.leanback:leanback:1.2.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.16.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
