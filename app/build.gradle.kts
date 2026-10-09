import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.kapt") }
val localConfig = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
val oauthToken = localConfig.getProperty("messenger.oauthToken") ?: System.getenv("MESSENGER_OAUTH_TOKEN") ?: ""
android {
    namespace = "ru.messenger.app"
    compileSdk = 35
    defaultConfig { minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationId = "ru.messenger.app"
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "OAUTH_TOKEN", "\"" + oauthToken.replace("\\", "\\\\").replace("\"", "\\\"") + "\"") }
    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
kotlin { jvmToolchain(21) }
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(project(":core-common"))
    implementation(project(":feature-chats"))
    implementation(project(":feature-messages"))
    implementation(project(":core-network"))
    implementation("com.google.dagger:dagger:2.56.2")
    kapt("com.google.dagger:dagger-compiler:2.56.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}

// Optional trust anchor for the managed cloud's HTTPS proxy, debug builds only.
// Never disables TLS validation; ordinary builds continue to trust system CAs.
val cloudCaFile = System.getenv("MESSENGER_CLOUD_CA_FILE")?.let(::file)?.takeIf { it.isFile }
android.buildTypes.getByName("debug").manifestPlaceholders["debugNetworkConfig"] =
    if (cloudCaFile == null) "@xml/debug_network_security_config" else "@xml/cloud_proxy_network_security_config"
if (cloudCaFile != null) {
    val generatedRes = layout.buildDirectory.dir("generated/cloudDebugRes")
    val prepareCloudTrust by tasks.registering {
        inputs.file(cloudCaFile)
        outputs.dir(generatedRes)
        doLast {
            val root = generatedRes.get().asFile
            root.resolve("raw").mkdirs()
            root.resolve("xml").mkdirs()
            cloudCaFile.copyTo(root.resolve("raw/cloud_proxy_ca.pem"), overwrite = true)
            root.resolve("xml/cloud_proxy_network_security_config.xml").writeText("""
                <network-security-config><base-config cleartextTrafficPermitted="false"><trust-anchors>
                <certificates src="system" /><certificates src="@raw/cloud_proxy_ca" />
                </trust-anchors></base-config></network-security-config>
            """.trimIndent())
        }
    }
    android.sourceSets.getByName("debug").res.srcDir(generatedRes)
    tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(prepareCloudTrust) }
}
