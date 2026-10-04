rootProject.name = "fort-conquer-preservation"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Official release jars that are not published to Maven Central. Each repository is
        // exclusive to exactly one module, and the downloaded files are checksum-pinned in
        // gradle/verification-metadata.xml.
        exclusiveContent {
            forRepository {
                ivy {
                    name = "ApktoolGitHubReleases"
                    url = uri("https://github.com/iBotPeaches/Apktool/releases/download/")
                    patternLayout { artifact("v[revision]/[module]_[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.apktool.release", "apktool") }
        }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "UberApkSignerGitHubReleases"
                    url = uri("https://github.com/patrickfav/uber-apk-signer/releases/download/")
                    patternLayout { artifact("v[revision]/[module]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("at.favre.tools.release", "uber-apk-signer") }
        }
    }
}

// Compatibility layer (plain Java, compiled against the Android API 36 framework surface,
// converted to Dalvik bytecode and merged into the original classes.dex).
include(":compat")
