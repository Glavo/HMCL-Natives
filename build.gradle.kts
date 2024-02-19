@file:Suppress("UNCHECKED_CAST")
@file:OptIn(kotlin.ExperimentalStdlibApi::class)

import java.util.Properties

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.google.code.gson:gson:2.10.1")
    }
}

plugins {
    id("java")
}

var secretPropsFile = project.rootProject.file("gradle/maven-central-publish.properties")
if (!secretPropsFile.exists()) {
    secretPropsFile =
        file(System.getProperty("user.home")).resolve(".gradle").resolve("maven-central-publish.properties")
}

if (secretPropsFile.exists()) {
    // Read local.properties file first if it exists
    val p = Properties()
    secretPropsFile.reader().use {
        p.load(it)
    }

    p.forEach { (name, value) ->
        rootProject.ext[name.toString()] = value
    }
}

listOf(
    "sonatypeUsername" to "SONATYPE_USERNAME",
    "sonatypePassword" to "SONATYPE_PASSWORD",
    "sonatypeStagingProfileId" to "SONATYPE_STAGING_PROFILE_ID",
    "signing.keyId" to "SIGNING_KEY_ID",
    "signing.password" to "SIGNING_PASSWORD",
    "signing.key" to "SIGNING_KEY"
).forEach { (p, e) ->
    if (!rootProject.ext.has(p)) {
        rootProject.ext[p] = System.getenv(e)
    }
}

allprojects {
    group = "org.glavo.hmcl"
}
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

initProject(rootProject)

val pattern = Regex("(?<groupId>[^:]+):(?<artifactId>[^:]+):(?<version>[^:]+)(:(?<classifier>[^:]+))?").toPattern()
fun mavenLibrary(name: String, snapshot: String? = null, repo: MavenRepo = MavenRepo.MAVEN_CENTRAL): Map<String, Any> {
    val matcher = pattern.matcher(name)
    if (!matcher.matches())
        throw AssertionError("name=$name")

    val groupId = matcher.group("groupId")!!
    val artifactId = matcher.group("artifactId")!!
    val version = matcher.group("version")!!
    val classifier = matcher.group("classifier")

    val path = buildString {
        append(groupId.replace('.', '/'))
        append("/")
        append(artifactId)
        append("/")
        append(version)
        append("/")

        append(artifactId)
        append('-')

        if (snapshot == null)
            append(version)
        else
            append(snapshot)
        if (classifier != null) {
            append('-')
            append(classifier)
        }
        append(".jar")
    }
    val url = "${repo.url}/$path"

    val (fileSize, sha1) = repo.downloadFile(path)

    return mapOf(
        "name" to name,
        "downloads" to mapOf(
            "artifact" to mapOf(
                "path" to path,
                "url" to url,
                "sha1" to sha1,
                "size" to fileSize
            )
        )
    )
}

fun emptyLibrary(name: String): Map<String, Any> = mapOf("name" to name)

class MapBuilder {
    private val map: MutableMap<String, Map<String, Any>?> = LinkedHashMap()

    fun build(): Map<String, Map<String, Any>?> = map

    fun redirect(lib: String, newLibrary: Map<String, Any>?) {
        map[lib] = newLibrary
    }

    fun redirectToEmpty(lib: String) {
        redirect(lib, null)
    }

    fun redirectAllToEmpty(vararg libs: String) {
        for (lib in libs)
            redirectToEmpty(lib)
    }

    companion object {
        val noVersionPattern = Regex("(?<groupId>[^:]+):(?<artifactId>[^:]+)(:(?<classifier>[^:]+))?").toPattern()
    }
}

inline fun buildRedirectMap(f: MapBuilder.() -> Unit): Map<String, Map<String, Any>?> =
    MapBuilder().apply { f() }.build()

fun lwjglNatives(os: String, arch: String, version: String) = buildMap<String, Any> {
    val artifactId = when {
        version.startsWith('2') -> "lwjgl2-natives"
        version.startsWith('3') -> "lwjgl3-natives"
        else -> throw AssertionError()
    }

    val artifact =
        (mavenLibrary("org.glavo.hmcl:$artifactId:$version-$os-$arch")["downloads"] as Map<String, Any>)["artifact"] as Map<String, Any>

    put("name", "org.glavo.hmcl:$artifactId:$version")
    put(
        "downloads", mapOf(
            "classifiers" to mapOf(
                "$os-$arch" to mapOf(
                    "path" to "org/glavo/hmcl/$artifactId/$version/$artifactId-$version-$os-$arch.jar",
                    "url" to artifact["url"],
                    "sha1" to artifact["sha1"],
                    "size" to artifact["size"]
                )
            )
        )
    )

    put(
        "extract", mapOf(
            "exclude" to listOf("META-INF/")
        )
    )
    put(
        "natives", mapOf(
            os to "$os-$arch"
        )
    )
}

val allLinuxText2speech = arrayOf(
    "com.mojang:text2speech:1.10.3:natives",
    "com.mojang:text2speech:1.11.3:natives",
    "com.mojang:text2speech:1.12.4:natives",
    "com.mojang:text2speech:1.13.9:natives-linux"
)

val lwjgl3BaseLibraries = listOf(
    "org.lwjgl:lwjgl",
    "org.lwjgl:lwjgl-jemalloc",
    "org.lwjgl:lwjgl-openal",
    "org.lwjgl:lwjgl-opengl",
    "org.lwjgl:lwjgl-glfw",
    "org.lwjgl:lwjgl-stb",
    "org.lwjgl:lwjgl-tinyfd"
)

fun lwjgl3_3_4SnapshotVersion(lib: String) = if (lib == "org.lwjgl:lwjgl-stb" || lib == "org.lwjgl:lwjgl-tinyfd")
    "3.3.4-20231218.151521-3"
else
    "3.3.4-20231218.151521-4"

val jsonFile = rootProject.layout.buildDirectory.asFile.get().resolve("natives.json")
rootProject.tasks.create("generateJson") {
    doLast {
        val map: Map<String, Map<String, Map<String, Any>?>> = mapOf(
            "linux-arm64" to buildRedirectMap {
                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
                    redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
                    redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
                }

                // Minecraft 1.19~1.20.1
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.3.1", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
                    redirect("$lib:3.3.1:natives-linux", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
                }

                // Minecraft 1.20.2+
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.3.2:natives-linux", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
                }

                // Minecraft 1.6~1.12
                val lwjgl2Natives = lwjglNatives("linux", "arm64", "2.9.3")

                for (v in listOf(
                    "2.9.0",
                    "2.9.1",
                    "2.9.4-nightly-20150209"
                )) {
                    redirect("org.lwjgl.lwjgl:lwjgl-platform:$v:natives", lwjgl2Natives)
                }

                redirectToEmpty("net.java.jinput:jinput-platform:2.0.5:natives")
                redirectAllToEmpty(*allLinuxText2speech)
            },
            "linux-arm32" to buildRedirectMap {
                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.2.3"))
                    redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.2.3:natives-linux-arm32"))
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.2.3"))
                    redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.2.3:natives-linux-arm32"))
                }

                // Minecraft 1.19+
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.3.1:natives-linux", mavenLibrary("$lib:3.3.1:natives-linux-arm32"))
                }

                // Minecraft 1.6~1.12
                val lwjgl2Natives = buildMap<String, Any> {
                    val artifact =
                        (mavenLibrary("org.glavo.hmcl:lwjgl2-natives:2.9.3-linux-arm32")["downloads"] as Map<String, Any>)["artifact"] as Map<String, Any>

                    put("name", "org.glavo.hmcl:lwjgl2-natives:2.9.3")
                    put(
                        "downloads", mapOf(
                            "classifiers" to mapOf(
                                "linux-arm32" to mapOf(
                                    "path" to "org/glavo/hmcl/lwjgl2-natives/2.9.3/lwjgl2-natives-2.9.3-linux-arm32.jar",
                                    "url" to artifact["url"],
                                    "sha1" to artifact["sha1"],
                                    "size" to artifact["size"]
                                )
                            )
                        )
                    )

                    put(
                        "extract", mapOf(
                            "exclude" to listOf("META-INF/")
                        )
                    )
                    put(
                        "natives", mapOf(
                            "linux" to "linux-arm32"
                        )
                    )
                }

                for (v in listOf(
                    "2.9.0",
                    "2.9.1",
                    "2.9.4-nightly-20150209"
                )) {
                    redirect("org.lwjgl.lwjgl:lwjgl-platform:$v:natives", lwjgl2Natives)
                }

                redirectToEmpty("net.java.jinput:jinput-platform:2.0.5:natives")
                redirectAllToEmpty(*allLinuxText2speech)
            },
            "linux-mips64el" to buildRedirectMap {
                // Minecraft 1.6~1.12
                val lwjgl2Natives = lwjglNatives("linux", "mips64el", "2.9.3-rc2")

                for (v in listOf(
                    "2.9.0",
                    "2.9.1",
                    "2.9.4-nightly-20150209"
                )) {
                    redirect("org.lwjgl.lwjgl:lwjgl-platform:$v:natives", lwjgl2Natives)
                }

                // Minecraft 1.13~1.19+
                val lwjgl3Natives = lwjglNatives("linux", "mips64el", "3.3.1-rc2")

                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.1.6:natives", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.1.6:natives")
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.1"))
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.2.2:natives", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.2.2:natives")
                }

                // Minecraft 1.19+
                for (lib in lwjgl3BaseLibraries) {
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.3.1:natives-linux", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.3.1:natives-linux")
                }

                redirectAllToEmpty(
                    "net.java.jinput:jinput-platform:2.0.5:natives",
                    "com.mojang:text2speech:1.10.3:natives",
                    "com.mojang:text2speech:1.11.3:natives",
                    "com.mojang:text2speech:1.12.4:natives",
                    "com.mojang:text2speech:1.13.9:natives-linux"
                )
            },
            "linux-loongarch64" to buildRedirectMap {
                // Minecraft 1.6~1.12
                val lwjgl2Natives = lwjglNatives("linux", "loongarch64", "2.9.3-rc2")

                for (v in listOf(
                    "2.9.0",
                    "2.9.1",
                    "2.9.4-nightly-20150209"
                )) {
                    redirect("org.lwjgl.lwjgl:lwjgl-platform:$v:natives", lwjgl2Natives)
                }

                // Minecraft 1.13~1.19+
                val lwjgl3Natives = lwjglNatives("linux", "loongarch64", "3.3.1-rc1")

                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.1.6:natives", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.1.6:natives")
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.1"))
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.2.2:natives", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.2.2:natives")
                }

                // Minecraft 1.19+
                for (lib in lwjgl3BaseLibraries) {
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.3.1:natives-linux", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.3.1:natives-linux")
                }


                // JNA
                val jna = mavenLibrary("net.java.dev.jna:jna:5.13.0");
                val jnaPlatform = mavenLibrary("net.java.dev.jna:jna-platform:5.13.0")

                val jnaVersions = listOf("5.8.0", "5.10.0", "5.12.1")
                for (jnaVersion in jnaVersions) {
                    redirect("net.java.dev.jna:jna:$jnaVersion", jna)
                    redirect("net.java.dev.jna:jna-platform:$jnaVersion", jnaPlatform)
                }

                redirectToEmpty("net.java.jinput:jinput-platform:2.0.5:natives")
                redirectAllToEmpty(*allLinuxText2speech)
            },
            "linux-loongarch64_ow" to buildRedirectMap {
                // Minecraft 1.6~1.12
                val lwjgl2Natives = lwjglNatives("linux", "loongarch64", "2.9.3-rc1")

                for (v in listOf(
                    "2.9.0",
                    "2.9.1",
                    "2.9.4-nightly-20150209"
                )) {
                    redirect("org.lwjgl.lwjgl:lwjgl-platform:$v:natives", lwjgl2Natives)
                }

                // Minecraft 1.13~1.19+
                val lwjgl3Natives = lwjglNatives("linux", "loongarch64_ow", "3.3.1-rc1")

                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.1.6:natives", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.1.6:natives")
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.1"))
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.2.2:natives", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.2.2:natives")
                }

                // Minecraft 1.19+
                for (lib in lwjgl3BaseLibraries) {
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.3.1:natives-linux", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.3.1:natives-linux")
                }

                // JNA
                val jna = mavenLibrary("org.glavo.hmcl:jna:5.13.0-rc1-linux-loongarch64_ow");
                val jnaPlatform = mavenLibrary("net.java.dev.jna:jna-platform:5.13.0")

                val jnaVersions = listOf("5.8.0", "5.10.0", "5.12.1")
                for (jnaVersion in jnaVersions) {
                    redirect("net.java.dev.jna:jna:$jnaVersion", jna)
                    redirect("net.java.dev.jna:jna-platform:$jnaVersion", jnaPlatform)
                }

                redirectToEmpty("net.java.jinput:jinput-platform:2.0.5:natives")
                redirectAllToEmpty(*allLinuxText2speech)
            },
            "linux-riscv64" to buildRedirectMap {
                // Minecraft 1.13~1.19+
                val lwjgl3Natives = lwjglNatives("linux", "riscv64", "3.3.1-rc1")

                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.1.6:natives", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.1.6:natives")
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.1"))
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.2.2:natives", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.2.2:natives")
                }

                // Minecraft 1.19~1.20.1
                for (lib in lwjgl3BaseLibraries) {
                    if (lib == "org.lwjgl:lwjgl")
                        redirect("$lib:3.3.1:natives-linux", lwjgl3Natives)
                    else
                        redirectToEmpty("$lib:3.3.1:natives-linux")
                }


                fun officialMavenLibrary(lib: String, natives: Boolean): Map<String, Any> {
                    return mavenLibrary("$lib:3.3.4-SNAPSHOT" + (if (natives) ":natives-linux-riscv64" else ""),
                        snapshot = lwjgl3_3_4SnapshotVersion(lib), repo = MavenRepo.SONATYPE_SNAPSHOTS)
                }

                // Minecraft 1.20.2+
                for (lib in lwjgl3BaseLibraries) {
                    val snapshot = lwjgl3_3_4SnapshotVersion(lib)

                    redirect("$lib:3.3.2", mavenLibrary("$lib:3.3.4-SNAPSHOT",
                        snapshot = snapshot, repo = MavenRepo.SONATYPE_SNAPSHOTS))

                    redirect("$lib:3.3.2:natives-linux", mavenLibrary(
                        "$lib:3.3.4-SNAPSHOT:natives-linux-riscv64",
                        snapshot = snapshot, repo = MavenRepo.SONATYPE_SNAPSHOTS))
                }


                redirectToEmpty("net.java.jinput:jinput-platform:2.0.5:natives")
                redirectAllToEmpty(*allLinuxText2speech)
            },
            "windows-x86_64" to buildRedirectMap {
                redirect("software-renderer-loader", mavenLibrary("org.glavo:llvmpipe-loader:1.0"))
                redirect("mesa-loader", mavenLibrary("org.glavo:mesa-loader-windows:0.2.0:x64"))
            },
            "windows-x86" to buildRedirectMap {
                redirect("mesa-loader", mavenLibrary("org.glavo:mesa-loader-windows:0.2.0:x86"))
            },
            "windows-arm64" to buildRedirectMap {
                // Minecraft 1.6~1.12
                val lwjgl2Natives = lwjglNatives("windows", "arm64", "2.9.3-rc1")

                for (v in listOf(
                    "2.9.0",
                    "2.9.1",
                    "2.9.4-nightly-20150209"
                )) {
                    redirect("org.lwjgl.lwjgl:lwjgl-platform:$v:natives", lwjgl2Natives)
                }

                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
                    redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.3.2:natives-windows-arm64", repo = MavenRepo.MOJANG))
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
                    redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.3.2:natives-windows-arm64", repo = MavenRepo.MOJANG))
                }

//                // Minecraft 1.19~1.20.1
//                for (lib in lwjgl3BaseLibraries) {
//                    redirect("$lib:3.3.1", mavenLibrary("$lib:3.3.2"))
//                    redirect("$lib:3.3.1:natives-windows", mavenLibrary("$lib:3.3.2:natives-windows-arm64"))
//                    redirectToEmpty("$lib:3.3.1:natives-windows-x86")
//                }

                redirectAllToEmpty(
                    "net.java.jinput:jinput-platform:2.0.5:natives",
                    "com.mojang:text2speech:1.10.3:natives",
                    "com.mojang:text2speech:1.11.3:natives",
                    "com.mojang:text2speech:1.12.4:natives",
                    "com.mojang:text2speech:1.13.9:natives-windows"
                )
            },
            "osx-arm64" to buildRedirectMap {
                // Minecraft 1.6~1.12
                val lwjgl2Natives = lwjglNatives("osx", "arm64", "2.9.3-rc1")

                for (v in listOf(
                    "2.9.1-nightly-20130708-debug3",
                    "2.9.1",
                    "2.9.2-nightly-20140822"
                )) {
                    redirect("org.lwjgl.lwjgl:lwjgl-platform:$v:natives", lwjgl2Natives)
                }

                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    if (lib == "org.lwjgl:lwjgl-glfw")
                        redirect("$lib:3.1.6", mavenLibrary("org.glavo.hmcl.mmachina:lwjgl-glfw:3.3.1-mmachina.1"))
                    else
                        redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
                    redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.3.1:natives-macos-arm64", repo = MavenRepo.MOJANG))
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    if (lib == "org.lwjgl:lwjgl-glfw")
                        redirect("$lib:3.2.1", mavenLibrary("org.glavo.hmcl.mmachina:lwjgl-glfw:3.3.1-mmachina.1"))
                    else
                        redirect("$lib:3.2.1", mavenLibrary("$lib:3.3.1"))
                    redirect("$lib:3.2.1:natives", mavenLibrary("$lib:3.3.1:natives-macos-arm64", repo = MavenRepo.MOJANG))
                }

                redirect("ca.weblite:java-objc-bridge:1.0.0", mavenLibrary("org.glavo.hmcl.mmachina:java-objc-bridge:1.1.0-mmachina.1"))
                redirectToEmpty("ca.weblite:java-objc-bridge:1.0.0:natives")


                redirect("com.mojang:text2speech:1.10.3", mavenLibrary("com.mojang:text2speech:1.11.3", repo = MavenRepo.MOJANG))

                redirectAllToEmpty(
                    "net.java.jinput:jinput-platform:2.0.5:natives",
                    "com.mojang:text2speech:1.10.3:natives",
                    "com.mojang:text2speech:1.11.3:natives",
                    "com.mojang:text2speech:1.12.4:natives"
                )
            },
            "freebsd-x86_64" to buildRedirectMap {

                fun freebsdMavenLibrary(lib: String, natives: Boolean): Map<String, Any> {
                    return mavenLibrary("$lib:3.3.4-SNAPSHOT" + (if (natives) ":natives-freebsd" else ""),
                        snapshot = lwjgl3_3_4SnapshotVersion(lib), repo = MavenRepo.SONATYPE_SNAPSHOTS)
                }

                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", freebsdMavenLibrary(lib, false))
                    redirect("$lib:3.1.6:natives", freebsdMavenLibrary(lib, true))
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", freebsdMavenLibrary(lib, false))
                    redirect("$lib:3.2.2:natives", freebsdMavenLibrary(lib, true))
                }

                // Minecraft 1.19~1.20.1
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.3.1", freebsdMavenLibrary(lib, false))
                    redirect("$lib:3.3.1:natives-linux", freebsdMavenLibrary(lib, true))
                }

                // Minecraft 1.20.2+
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.3.2", freebsdMavenLibrary(lib, false))
                    redirect("$lib:3.3.2:natives-linux", freebsdMavenLibrary(lib, true))
                }

                redirectToEmpty("net.java.jinput:jinput-platform:2.0.5:natives")
                redirectAllToEmpty(*allLinuxText2speech)
            }
        )

        jsonFile.parentFile.mkdirs()
        jsonFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(map))
    }
}