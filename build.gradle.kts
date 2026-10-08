// Fort Conquer — Modern Android preservation build.
//
// Pipeline (all tools pinned in gradle/libs.versions.toml + gradle/verification-metadata.xml):
//
//   reference APK (SHA-256 verified)
//     -> apktool decode                 build/fc/decoded   (pristine smali + resources)
//     -> apply patches/series           build/fc/patched   (strict unified diffs, no fuzz)
//     -> + compat layer (Java -> dx -> baksmali, merged into classes.dex)
//                                        build/fc/stage
//     -> apktool build (smali + aapt2)  build/fc/apk/unsigned.apk
//     -> portable zipalign (stored entries 4-byte aligned)
//     -> apksigner sign (v1+v2+v3)
//     -> apksigner verify + alignment check
//     -> dist/FortConquer-1.2.4-Modern-Android.apk (+ .sha256, .metadata.txt)
//
// Entry point:  ./gradlew clean assembleRelease

import fortconquer.build.ApkZip
import fortconquer.build.ApplyPatchesTask
import fortconquer.build.ZipAlign
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Properties

plugins {
    base
}

val referenceApkName = "com.droidhen.fortconquer_v1.2.4-31_Android-4.1.apk"
val referenceApkSha256 = "933557dc1b5ba6c900b078689d269faf47c622fb3ea23acc3efa11b7c753cc92"
// Optional build variant: ./gradlew -Pfc.variant=<name> clean assembleRelease applies
// patches/variants/<name>/series after patches/series, builds in build/fc-<name> and publishes to
// dist/<name>/. Without the property the preservation build is produced, unchanged.
val variants = mapOf(
    "unlimited-gems-coins" to ("UnlimitedGemsCoins" to
        "UNLIMITED GEMS + COINS CHEAT VARIANT (crystals pinned at 99999, coins at 9999999; online Arena disabled)"),
)
val fcVariant: String? = providers.gradleProperty("fc.variant").orNull?.takeIf { it.isNotBlank() }
check(fcVariant == null || fcVariant in variants) { "unknown fc.variant '$fcVariant' (known: ${variants.keys})" }
val releaseBaseName = "FortConquer-1.2.4-Modern-Android" + (fcVariant?.let { "-" + variants.getValue(it).first } ?: "")

val referenceApk = layout.projectDirectory.file(referenceApkName)
val fcDir = layout.buildDirectory.dir(if (fcVariant == null) "fc" else "fc-$fcVariant")
val distDir = layout.projectDirectory.dir(if (fcVariant == null) "dist" else "dist/$fcVariant")

val apktool: Configuration by configurations.creating { isTransitive = false }
val uberApkSigner: Configuration by configurations.creating { isTransitive = false }
val dalvikDx: Configuration by configurations.creating { isTransitive = false }
val baksmali: Configuration by configurations.creating
val compatJar: Configuration by configurations.creating { isCanBeConsumed = false }

dependencies {
    apktool(libs.apktool) { artifact { type = "jar" } }
    uberApkSigner(libs.uber.apk.signer) { artifact { type = "jar" } }
    dalvikDx(libs.dalvik.dx)
    baksmali(libs.baksmali)
    compatJar(project(":compat"))
}

// --------------------------------------------------------------------------------------------
// 1. Reference APK
// --------------------------------------------------------------------------------------------
val verifyReferenceApk by tasks.registering {
    group = "preservation"
    description = "Verifies the SHA-256 of the original Fort Conquer APK used as build input."
    val apk = referenceApk
    inputs.file(apk)
    doLast {
        val actual = ApkZip.sha256(apk.asFile.toPath())
        check(actual == referenceApkSha256) {
            "Reference APK checksum mismatch!\n  expected $referenceApkSha256\n  actual   $actual"
        }
        logger.lifecycle("reference APK OK: $referenceApkName ($actual)")
    }
}

// --------------------------------------------------------------------------------------------
// 2. Decode with apktool (framework dir kept inside build/ so the build is hermetic)
// --------------------------------------------------------------------------------------------
val decodeReferenceApk by tasks.registering(JavaExec::class) {
    group = "preservation"
    description = "Decodes the reference APK to smali + resources (pristine, unmodified)."
    dependsOn(verifyReferenceApk)
    val out = fcDir.map { it.dir("decoded") }
    val frameworks = fcDir.map { it.dir("apktool-framework") }
    inputs.file(referenceApk)
    inputs.files(apktool)
    outputs.dir(out)
    classpath = apktool
    mainClass.set("brut.apktool.Main")
    doFirst { delete(out); delete(frameworks) }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("d", "-f", "-p", frameworks.get().asFile.path, "-o", out.get().asFile.path,
            referenceApk.asFile.path)
    })
}

// --------------------------------------------------------------------------------------------
// 3. Apply the ordered compatibility patch series
// --------------------------------------------------------------------------------------------
val preparePatchedTree by tasks.registering(ApplyPatchesTask::class) {
    group = "preservation"
    description = "Applies patches/series to the pristine decode (strict, no fuzz)."
    decodedDir.set(layout.dir(decodeReferenceApk.map { fcDir.get().dir("decoded").asFile }))
    patchesDir.set(layout.projectDirectory.dir("patches"))
    fcVariant?.let { variantPatchesDir.set(layout.projectDirectory.dir("patches/variants/$it")) }
    outputDir.set(fcDir.map { it.dir("patched") })
}

// --------------------------------------------------------------------------------------------
// 4. Compatibility layer: Java -> dex (AOSP dx) -> smali (baksmali)
// --------------------------------------------------------------------------------------------
val dexCompat by tasks.registering(JavaExec::class) {
    group = "preservation"
    description = "Converts the compat layer to Dalvik bytecode with AOSP dx."
    val out = fcDir.map { it.file("compat/compat.dex") }
    inputs.files(compatJar)
    outputs.file(out)
    classpath = dalvikDx
    mainClass.set("com.android.dx.command.Main")
    doFirst { out.get().asFile.parentFile.mkdirs() }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--dex", "--min-sdk-version=16", "--output=" + out.get().asFile.path,
            compatJar.singleFile.path)
    })
}

val baksmaliCompat by tasks.registering(JavaExec::class) {
    group = "preservation"
    description = "Disassembles the compat dex so apktool can merge it into classes.dex."
    val dex = fcDir.map { it.file("compat/compat.dex") }
    val out = fcDir.map { it.dir("compat/smali") }
    dependsOn(dexCompat)
    inputs.file(dex)
    outputs.dir(out)
    classpath = baksmali
    mainClass.set("org.jf.baksmali.Main")
    doFirst { delete(out) }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("d", "-o", out.get().asFile.path, dex.get().asFile.path)
    })
}

val stageApkTree by tasks.registering(Sync::class) {
    group = "preservation"
    description = "Combines the patched tree and the compat smali into the final apktool project."
    from(preparePatchedTree)
    from(baksmaliCompat) { into("smali") }
    into(fcDir.map { it.dir("stage") })
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

// --------------------------------------------------------------------------------------------
// 5. Reassemble with apktool (smali assembler + aapt2 bundled in the pinned apktool jar)
// --------------------------------------------------------------------------------------------
val buildUnsignedApk by tasks.registering(JavaExec::class) {
    group = "preservation"
    description = "Reassembles the staged project into an unsigned APK."
    val stage = fcDir.map { it.dir("stage") }
    val frameworks = fcDir.map { it.dir("apktool-framework") }
    val out = fcDir.map { it.file("apk/unsigned.apk") }
    dependsOn(stageApkTree, decodeReferenceApk)
    inputs.dir(stage)
    outputs.file(out)
    classpath = apktool
    mainClass.set("brut.apktool.Main")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("b", "-f", "-p", frameworks.get().asFile.path, "-o", out.get().asFile.path,
            stage.get().asFile.path)
    })
}

val alignApk by tasks.registering {
    group = "preservation"
    description = "4-byte aligns stored entries (portable zipalign) before signing."
    val unsigned = fcDir.map { it.file("apk/unsigned.apk") }
    val aligned = fcDir.map { it.file("apk/aligned.apk") }
    dependsOn(buildUnsignedApk)
    inputs.file(unsigned)
    outputs.file(aligned)
    doLast {
        ZipAlign.align(unsigned.get().asFile.toPath(), aligned.get().asFile.toPath())
        val problems = ApkZip.alignmentProblems(aligned.get().asFile.toPath())
        check(problems.isEmpty()) { "alignment failed:\n" + problems.joinToString("\n") }
    }
}

// --------------------------------------------------------------------------------------------
// 6. Signing. Keys are never stored in the repository. Resolution order:
//    a) environment: FC_KEYSTORE, FC_KEYSTORE_PASSWORD, FC_KEY_ALIAS, FC_KEY_PASSWORD
//    b) keystore.properties (gitignored): storeFile, storePassword, keyAlias, keyPassword
//    c) otherwise a local, machine-specific preservation key is generated in .signing/
// --------------------------------------------------------------------------------------------
data class SigningConfig(val storeFile: File, val storePassword: String, val keyAlias: String,
                         val keyPassword: String, val origin: String)

fun resolveSigning(): SigningConfig {
    val env = System.getenv()
    if (!env["FC_KEYSTORE"].isNullOrBlank()) {
        return SigningConfig(file(env.getValue("FC_KEYSTORE")), env["FC_KEYSTORE_PASSWORD"] ?: "",
            env["FC_KEY_ALIAS"] ?: "fortconquer-preservation",
            env["FC_KEY_PASSWORD"] ?: env["FC_KEYSTORE_PASSWORD"] ?: "", "environment")
    }
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.isFile) {
        val p = Properties().apply { propsFile.inputStream().use { load(it) } }
        return SigningConfig(rootProject.file(p.getProperty("storeFile")), p.getProperty("storePassword"),
            p.getProperty("keyAlias"), p.getProperty("keyPassword") ?: p.getProperty("storePassword"),
            "keystore.properties")
    }
    val dir = rootProject.file(".signing").apply { mkdirs() }
    val ks = File(dir, "local-preservation.p12")
    val pw = File(dir, "local-preservation.password")
    if (!ks.isFile) {
        val password = ByteArray(24).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        pw.writeText(password)
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val proc = ProcessBuilder(keytool, "-genkeypair", "-keystore", ks.path, "-storetype", "PKCS12",
            "-storepass", password, "-alias", "fortconquer-local", "-keyalg", "RSA", "-keysize", "4096",
            "-sigalg", "SHA256withRSA", "-validity", "18250",
            "-dname", "CN=Fort Conquer Local Preservation Build, O=Unofficial preservation build, C=XX")
            .redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        check(proc.waitFor() == 0) { "keytool failed:\n$output" }
        logger.warn("Generated a LOCAL signing key in ${ks.path}. APKs signed with it cannot update " +
            "APKs signed with any other key. See docs/signing.md.")
    }
    return SigningConfig(ks, pw.readText().trim(), "fortconquer-local", pw.readText().trim(), "local key (.signing/)")
}

val apksignerJar = layout.buildDirectory.file("tools/apksigner.jar")

val extractApksigner by tasks.registering(Copy::class) {
    group = "preservation"
    description = "Extracts the AOSP build-tools 33.0.2 apksigner.jar from the pinned uber-apk-signer release."
    from(zipTree(uberApkSigner.singleFile)) {
        include("lib/apksigner_33_0_2.jar")
        eachFile { path = "apksigner.jar" }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("tools"))
}

val extractTools by tasks.registering(Copy::class) {
    group = "preservation"
    description = "Copies the pinned apktool jar, apksigner jar and aapt2 binary to build/tools for the scripts/."
    dependsOn(extractApksigner)
    from(apktool) { rename { "apktool.jar" } }
    from(zipTree(apktool.singleFile)) {
        val os = System.getProperty("os.name").lowercase()
        include(if (os.contains("mac")) "prebuilt/macosx/aapt2_64" else "prebuilt/linux/aapt2_64")
        eachFile { path = "aapt2" }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("tools"))
    doLast { layout.buildDirectory.file("tools/aapt2").get().asFile.setExecutable(true) }
}

val signReleaseApk by tasks.registering(JavaExec::class) {
    group = "preservation"
    description = "Signs the aligned APK (v1+v2+v3) with the AOSP apksigner."
    val unsigned = fcDir.map { it.file("apk/aligned.apk") }
    val signed = fcDir.map { it.file("apk/signed.apk") }
    dependsOn(alignApk, extractApksigner)
    inputs.file(unsigned)
    outputs.file(signed)
    outputs.upToDateWhen { false }
    classpath = files(apksignerJar)
    mainClass.set("com.android.apksigner.ApkSignerTool")
    doFirst {
        val s = resolveSigning()
        logger.lifecycle("signing with ${s.origin}: ${s.storeFile}")
        environment("FC_KS_PASS", s.storePassword)
        environment("FC_KEY_PASS", s.keyPassword)
        args("sign", "--ks", s.storeFile.path, "--ks-key-alias", s.keyAlias,
            "--ks-pass", "env:FC_KS_PASS", "--key-pass", "env:FC_KEY_PASS",
            "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true", "--v4-signing-enabled", "false",
            "--out", signed.get().asFile.path, unsigned.get().asFile.path)
    }
}

val verifyReleaseApk by tasks.registering(JavaExec::class) {
    group = "preservation"
    description = "Verifies signatures (apksigner) and ZIP alignment of the signed APK."
    val signed = fcDir.map { it.file("apk/signed.apk") }
    val report = fcDir.map { it.file("apk/apksigner-verify.txt") }
    dependsOn(signReleaseApk)
    inputs.file(signed)
    outputs.file(report)
    classpath = files(apksignerJar)
    mainClass.set("com.android.apksigner.ApkSignerTool")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("verify", "--verbose", "--print-certs", signed.get().asFile.path)
    })
    val buffer = ByteArrayOutputStream()
    standardOutput = buffer
    doLast {
        report.get().asFile.writeText(buffer.toString())
        val text = buffer.toString()
        check(text.contains("Verified using v2 scheme (APK Signature Scheme v2): true")) { "v2 signature missing:\n$text" }
        val problems = ApkZip.alignmentProblems(signed.get().asFile.toPath())
        check(problems.isEmpty()) { "ZIP alignment problems:\n" + problems.joinToString("\n") }
        logger.lifecycle(text.lines().filter { it.startsWith("Verifie") || it.startsWith("Signer #1 certificate") }
            .joinToString("\n"))
        logger.lifecycle("alignment OK")
    }
}

// --------------------------------------------------------------------------------------------
// 7. Publish to dist/
// --------------------------------------------------------------------------------------------
fun git(vararg args: String): String? = try {
    val p = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText().trim()
    if (p.waitFor() == 0) out else null
} catch (e: Exception) { null }

// HEAD, suffixed with "-dirty" when tracked sources (anything outside dist/) have local changes.
fun gitDescribe(): String {
    val head = git("rev-parse", "HEAD") ?: return "unknown"
    val changes = git("status", "--porcelain", "--untracked-files=no", "--", ".", ":(exclude)dist")
    return if (changes.isNullOrEmpty()) head else "$head-dirty"
}

val assembleRelease by tasks.registering {
    group = "preservation"
    description = "Builds, signs and verifies the preservation APK and publishes it to dist/."
    dependsOn(verifyReleaseApk)
    val signed = fcDir.map { it.file("apk/signed.apk") }
    val verifyReport = fcDir.map { it.file("apk/apksigner-verify.txt") }
    val apktoolYml = fcDir.map { it.file("stage/apktool.yml") }
    doLast {
        val dist = distDir.asFile.apply { mkdirs() }
        val apk = File(dist, "$releaseBaseName.apk")
        signed.get().asFile.copyTo(apk, overwrite = true)
        val sha = ApkZip.sha256(apk.toPath())
        File(dist, "$releaseBaseName.sha256").writeText("$sha  ${apk.name}\n")
        val yml = apktoolYml.get().asFile.readText()
        fun yml(key: String) = Regex("""(?m)^\s*$key:\s*'?([^'\n]+)'?\s*$""").find(yml)?.groupValues?.get(1) ?: "?"
        val certLines = verifyReport.get().asFile.readLines().filter { it.startsWith("Signer #1 certificate") }
        val catalog = rootProject.file("gradle/libs.versions.toml").readLines()
            .filter { Regex("""^\w+ = "[^"]+"$""").matches(it.trim()) }
        File(dist, "$releaseBaseName.metadata.txt").writeText(buildString {
            appendLine("Fort Conquer - Modern Android preservation build (UNOFFICIAL, not a DroidHen release)")
            fcVariant?.let {
                appendLine(variants.getValue(it).second)
                appendLine("variant patches:     patches/variants/$it/series (applied after patches/series)")
            }
            appendLine()
            appendLine("artifact:            ${apk.name}")
            appendLine("sha256:              $sha")
            appendLine("size:                ${apk.length()} bytes")
            appendLine("package:             com.droidhen.fortconquer")
            appendLine("versionName:         ${yml("versionName")}")
            appendLine("versionCode:         ${yml("versionCode")}")
            appendLine("minSdkVersion:       ${yml("minSdkVersion")}")
            appendLine("targetSdkVersion:    ${yml("targetSdkVersion")}")
            appendLine("compile API surface: 36 (Android 16) for the compat layer")
            appendLine()
            appendLine("reference APK:       $referenceApkName")
            appendLine("reference sha256:    $referenceApkSha256")
            appendLine("source commit:       ${gitDescribe()}")
            appendLine()
            appendLine("signing (preservation key, NOT the original DroidHen/hzstudio key):")
            certLines.forEach { appendLine("  $it") }
            appendLine()
            appendLine("toolchain:")
            catalog.forEach { appendLine("  ${it.trim()}") }
            appendLine("  gradle = \"${gradle.gradleVersion}\"")
            appendLine("  java   = \"${System.getProperty("java.version")}\"")
        })
        logger.lifecycle("published ${apk.path}\nsha256 $sha")
    }
}
