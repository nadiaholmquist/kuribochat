plugins {
    kotlin("jvm") version "1.8.20"
    application
}

group = "sh.nhp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("ch.qos.logback:logback-classic:1.4.6")
    implementation("com.aallam.openai:openai-client:3.2.0")
    implementation("dev.kord:kord-core:0.8.2")
}

kotlin {
    jvmToolchain(17)
}

tasks.wrapper {
    version = "8.0.10"
}

application {
    mainClass.set("sh.nhp.kuribochat.MainKt")
}
