import java.util.Random
import java.util.function.BinaryOperator
import java.util.function.IntFunction

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "com.obs.yl"
    compileSdk = 34

    signingConfigs {
        getByName("debug") {
            storeFile = file("$rootDir/11.jks")
            storePassword = "111111"
            keyAlias = "key0"
            keyPassword = "111111"
        }
    }

    defaultConfig {
        // applicationId = "com.obs.yl"
        applicationId = randomPackage()
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.txt"
            )
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.txt"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.kotlinx.coroutines.core) // 协程(版本自定)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp) // 要求OkHttp4以上
    implementation(libs.net)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)

}

fun randomPackage(): String {
    val random = Random()
    //第一位为包的层级  23为包名长度
    val result = random.ints(3, 3, 6).mapToObj(object : IntFunction<String> {
        override fun apply(p0: Int): String {
            val sb = StringBuilder()
            for (i in 0..p0) {
                sb.append((97 + random.nextInt(26)).toChar())
            }
            return sb.toString()
        }
    }).reduce("com", object : BinaryOperator<String> {
        override fun apply(p0: String, p1: String): String {
            return "$p0.$p1"
        }
    })
    println("result=" + result)
    return result
}
