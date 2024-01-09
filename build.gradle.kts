plugins {
    kotlin("jvm") version "1.8.20"
    application
}

group = "sh.nhp"
version = "1.1.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.aallam.openai:openai-client:3.6.2")
    implementation("dev.kord:kord-core:0.12.0")
    implementation("com.knuddels:jtokkit:0.6.1")
}

kotlin {
    jvmToolchain(17)
}

tasks.wrapper {
    version = "8.0.10"
}

tasks.jar {
    manifest.attributes["Main-Class"] = "sh.nhp.kuribochat.MainKt"
    val dependencies = configurations.runtimeClasspath.get().map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

application {
    mainClass.set("sh.nhp.kuribochat.MainKt")
}

val distZip by tasks
val distTar by tasks
distZip.enabled = false
distTar.enabled = false
