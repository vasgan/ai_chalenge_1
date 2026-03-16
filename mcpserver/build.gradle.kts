plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

dependencies {
    implementation("io.ktor:ktor-server-cio:3.1.1")
    implementation("io.ktor:ktor-server-core:3.1.1")
    implementation("io.ktor:ktor-client-cio:3.1.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")

}
