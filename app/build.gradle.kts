plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dte.masteriot.accessiblebus"
    compileSdk = 37

    defaultConfig {
        applicationId = "dte.masteriot.accessiblebus"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Until I1 the app reads assets/samples instead of the API. The URL and token are
        // placeholders until the connection profile (C07) provides them. To use a running API:
        // ./gradlew installDebug -PuseSampleApi=false -PapiBaseUrl=http://192.168.1.50:8000/
        //   -PmqttBroker=tcp://192.168.1.50:1883
        val useSampleApi = (project.findProperty("useSampleApi") as String?) ?: "true"
        val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:8000/"
        buildConfigField("boolean", "USE_SAMPLE_API", useSampleApi)
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "API_TOKEN", "\"demo-pasajero-1\"")
        val mqttBroker = (project.findProperty("mqttBroker") as String?) ?: "tcp://10.0.2.2:1883"
        buildConfigField("String", "MQTT_BROKER", "\"$mqttBroker\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(platform(libs.mqtt.client.bom))
    implementation(libs.mqtt.client.core)
    implementation(libs.mqtt.client.transport.tcp)
    implementation(libs.kotlinx.coroutines.android)
}