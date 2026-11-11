plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.visionica"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.visionica"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // IMPORTANTE: Para TensorFlow Lite
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        aaptOptions {
            noCompress ("tflite")
            noCompress ("lite")
        }

        buildFeatures {
            viewBinding = true
            mlModelBinding = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    // =========================
    // DEPENDENCIAS BASE
    // =========================
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // =========================
    // FIREBASE - SOLO UNA BOM
    // =========================
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")

    // Firebase Cloud Messaging (Notificaciones)
    implementation("com.google.firebase:firebase-messaging:23.4.0")
// WorkManager para notificaciones programadas
    implementation("androidx.work:work-runtime:2.9.0")

    implementation("androidx.core:core:1.13.1")

    implementation ("androidx.room:room-runtime:2.6.1")
    annotationProcessor ("androidx.room:room-compiler:2.6.1")

    // Repetidos eliminados:
    // implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    // implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    // implementation(platform("com.google.firebase:firebase-bom:32.3.1"))
    // implementation("com.google.firebase:firebase-analytics")
    // implementation("com.google.firebase:firebase-auth")
    // implementation("com.google.firebase:firebase-firestore")
    // implementation("com.google.firebase:firebase-database")

    // =========================
    // GOOGLE SIGN IN
    // =========================
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Repetidos eliminados:
    // implementation("com.google.android.gms:play-services-auth:20.7.0")
    // implementation("com.google.android.gms:play-services-auth:20.7.0")

    // =========================
    // FACEBOOK LOGIN
    // =========================
    implementation("com.facebook.android:facebook-login:16.3.0")

    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // =========================
    // IMÁGENES
    // =========================
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Repetidos eliminados:
    // implementation("com.github.bumptech.glide:glide:4.15.1")
    // implementation("de.hdodenhof:circleimageview:3.1.0")

    // =========================
    // ANDROIDX EXTRA
    // =========================
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // Repetidos eliminados:
    // implementation("androidx.appcompat:appcompat:1.6.1")
    // implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // implementation("com.google.android.material:material:1.9.0")
    // implementation("com.google.android.material:material:1.10.0")
    // implementation("androidx.recyclerview:recyclerview:1.3.2")
    // implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    // =========================
    // QR
    // =========================
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // =========================
    // EXCEL
    // =========================
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("net.sourceforge.jexcelapi:jxl:2.6.12")

    // Repetido eliminado:
    // implementation("net.sourceforge.jexcelapi:jxl:2.6.12")

    // =========================
    // CAMERAX
    // =========================
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")

    // =========================
    // ML KIT ROSTRO
    // =========================
    implementation("com.google.mlkit:face-detection:16.1.5")
    implementation("com.google.mlkit:face-mesh-detection:16.0.0-beta3")

    // Repetido eliminado:
    // implementation("com.google.mlkit:face-detection:16.1.5")

    // =========================
    // TENSORFLOW LITE - SIN GPU POR AHORA
    // =========================
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // TensorFlow repetido o conflictivo eliminado:
    // implementation("org.tensorflow:tensorflow-lite:2.14.0")
    // implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    // implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // implementation("org.tensorflow:tensorflow-lite:2.13.0")
    // implementation("com.google.android.gms:play-services-tflite-gpu:16.5.0")

    // =========================
    // TESTING
    // =========================
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("org.tensorflow:tensorflow-lite-gpu:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.13.0")


    // Cloudinary
    implementation ("com.cloudinary:cloudinary-android:2.3.1")

    // Glide (para cargar imágenes desde URL — si no lo tenés ya)
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.github.yalantis:ucrop:2.2.8")

    implementation("com.airbnb.android:lottie:6.4.0")



    // Repetidos eliminados:
    // testImplementation("junit:junit:4.13.2")
    // androidTestImplementation("androidx.test.ext:junit:1.1.5")
    // androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}