plugins {
    `java-library`
}

// The compatibility layer must run on every Android release from the original minSdk (16)
// up to API 36+, so it is compiled as Java 8 bytecode (no lambdas / invokedynamic, which
// legacy dx cannot desugar) against the API 36 framework. Newer APIs are only reached
// behind Build.VERSION.SDK_INT checks.
dependencies {
    // Robolectric's "android-all" artifact is the complete Android 16 (API 36) framework
    // jar published by Google to Maven Central. Used for compilation only; never shipped.
    compileOnly(libs.android.all.api36)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-options,-serial,-classfile", "-Werror"))
}

tasks.named<Jar>("jar") {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
