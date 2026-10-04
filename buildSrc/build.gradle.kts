plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        // buildSrc runs inside the Gradle daemon; any JDK 17+ works.
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}
