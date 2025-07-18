plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.16.1"
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
}

group = "com.bitwarden"
version = "2025.7.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.slf4j:slf4j-api:2.0.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

intellij {
    version.set("2023.2.5")
    type.set("IC") // Edition type (IntelliJ IDEA Community Edition)

    plugins.set(listOf(
        "com.intellij.java",
        "org.jetbrains.kotlin",
        "JavaScript",
        "org.jetbrains.plugins.terminal"
    ))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    buildPlugin {
        archiveFileName.set("bitwarden-jetbrains-plugin-${version}.zip")
    }
}