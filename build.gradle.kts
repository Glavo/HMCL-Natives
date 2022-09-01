@file:Suppress("UNCHECKED_CAST")
@file:OptIn(kotlin.ExperimentalStdlibApi::class)

import java.util.Properties

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.google.code.gson:gson:2.8.1")
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
fun mavenLibrary(name: String, repo: MavenRepo = MavenRepo.MAVEN_CENTRAL): Map<String, Any> {
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
        append(version)
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


val lwjgl3BaseLibraries = listOf(
    "org.lwjgl:lwjgl",
    "org.lwjgl:lwjgl-jemalloc",
    "org.lwjgl:lwjgl-openal",
    "org.lwjgl:lwjgl-opengl",
    "org.lwjgl:lwjgl-glfw",
    "org.lwjgl:lwjgl-stb",
    "org.lwjgl:lwjgl-tinyfd"
)

val jsonFile = rootProject.buildDir.resolve("natives.json")
rootProject.tasks.create("generateJson") {
    doLast {
        val map: Map<String, Map<String, Map<String, Any>?>> = mapOf(
            "linux-arm64" to buildRedirectMap {
                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.2.3"))
                    redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.2.3:natives-linux-arm64"))
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.2.3"))
                    redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.2.3:natives-linux-arm64"))
                }

                // Minecraft 1.19+
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.3.1:natives-linux", mavenLibrary("$lib:3.3.1:natives-linux-arm64"))
                }

                // Minecraft 1.6~1.12
                val lwjgl2Natives = buildMap<String, Any> {
                    val artifact =
                        (mavenLibrary("org.glavo.hmcl:lwjgl2-natives:2.9.3-linux-arm64")["downloads"] as Map<String, Any>)["artifact"] as Map<String, Any>

                    put("name", "org.glavo.hmcl:lwjgl2-natives:2.9.3")
                    put(
                        "downloads", mapOf(
                            "classifiers" to mapOf(
                                "linux-arm64" to mapOf(
                                    "path" to "org/glavo/hmcl/lwjgl2-natives/2.9.3/lwjgl2-natives-2.9.3-linux-arm64.jar",
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
                            "linux" to "linux-arm64"
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

                redirectAllToEmpty(
                    "net.java.jinput:jinput-platform:2.0.5:natives",
                    "com.mojang:text2speech:1.10.3:natives",
                    "com.mojang:text2speech:1.11.3:natives",
                    "com.mojang:text2speech:1.12.4:natives",
                    "com.mojang:text2speech:1.13.9:natives-linux"
                )
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

                redirectAllToEmpty(
                    "net.java.jinput:jinput-platform:2.0.5:natives",
                    "com.mojang:text2speech:1.10.3:natives",
                    "com.mojang:text2speech:1.11.3:natives",
                    "com.mojang:text2speech:1.12.4:natives",
                    "com.mojang:text2speech:1.13.9:natives-linux"
                )
            },
            "linux-mips64el" to buildRedirectMap {
                // Minecraft 1.6~1.12
                val lwjgl2Natives = buildMap<String, Any> {
                    val artifact =
                        (mavenLibrary("org.glavo.hmcl:lwjgl2-natives:2.9.3-rc1-linux-mips64el")["downloads"] as Map<String, Any>)["artifact"] as Map<String, Any>

                    put("name", "org.glavo.hmcl:lwjgl2-natives:2.9.3-rc1")
                    put(
                        "downloads", mapOf(
                            "classifiers" to mapOf(
                                "linux-mips64el" to mapOf(
                                    "path" to "org/glavo/hmcl/lwjgl2-natives/2.9.3-rc1/lwjgl2-natives-2.9.3-rc1-linux-mips64el.jar",
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
                            "linux" to "linux-mips64el"
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

                // Minecraft 1.13~1.19+
                val lwjgl3Natives = buildMap<String, Any> {
                    val artifact =
                        (mavenLibrary("org.glavo.hmcl:lwjgl3-natives:3.3.1-rc1-linux-mips64el")["downloads"] as Map<String, Any>)["artifact"] as Map<String, Any>

                    put("name", "org.glavo.hmcl:lwjgl3-natives:3.3.1-rc1")
                    put(
                        "downloads", mapOf(
                            "classifiers" to mapOf(
                                "linux-mips64el" to mapOf(
                                    "path" to "org/glavo/hmcl/lwjgl3-natives/3.3.1-rc1/lwjgl3-natives-3.3.1-rc1-linux-mips64el.jar",
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
                            "linux" to "linux-mips64el"
                        )
                    )
                }

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
            "linux-loongarch64_ow" to buildRedirectMap {
                // Minecraft 1.6~1.12
                val lwjgl2Natives = buildMap<String, Any> {
                    val artifact =
                        (mavenLibrary("org.glavo.hmcl:lwjgl2-natives:2.9.3-rc1-linux-loongarch64")["downloads"] as Map<String, Any>)["artifact"] as Map<String, Any>

                    put("name", "org.glavo.hmcl:lwjgl2-natives:2.9.3-rc1")
                    put(
                        "downloads", mapOf(
                            "classifiers" to mapOf(
                                "linux-loongarch64" to mapOf(
                                    "path" to "org/glavo/hmcl/lwjgl2-natives/2.9.3-rc1/lwjgl2-natives-2.9.3-rc1-linux-loongarch64.jar",
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
                            "linux" to "linux-loongarch64"
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

                // Minecraft 1.13~1.19+
                val lwjgl3Natives = buildMap<String, Any> {
                    val artifact =
                        (mavenLibrary("org.glavo.hmcl:lwjgl3-natives:3.3.1-rc1-linux-loongarch64_ow")["downloads"] as Map<String, Any>)["artifact"] as Map<String, Any>

                    put("name", "org.glavo.hmcl:lwjgl3-natives:3.3.1-rc1")
                    put(
                        "downloads", mapOf(
                            "classifiers" to mapOf(
                                "linux-loongarch64_ow" to mapOf(
                                    "path" to "org/glavo/hmcl/lwjgl3-natives/3.3.1-rc1/lwjgl3-natives-3.3.1-rc1-linux-loongarch64_ow.jar",
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
                            "linux" to "linux-loongarch64_ow"
                        )
                    )
                }

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
            "windows-x86_64" to buildRedirectMap {
                redirect("software-renderer-loader", mavenLibrary("org.glavo:llvmpipe-loader:1.0"))
            },
            "windows-arm64" to buildRedirectMap {
                // Minecraft 1.6~1.12
                val lwjgl2Natives = buildMap<String, Any> {
                    val artifact =
                        (mavenLibrary("org.glavo.hmcl:lwjgl2-natives:2.9.3-rc1-windows-arm64")["downloads"] as Map<String, Any>)["artifact"] as Map<String, Any>

                    put("name", "org.glavo.hmcl:lwjgl2-natives:2.9.3-rc1")
                    put(
                        "downloads", mapOf(
                            "classifiers" to mapOf(
                                "windows-arm64" to mapOf(
                                    "path" to "org/glavo/hmcl/lwjgl2-natives/2.9.3-rc1/lwjgl2-natives-2.9.3-rc1-windows-arm64.jar",
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
                            "windows" to "windows-arm64"
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


                // Minecraft 1.13
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
                    redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.3.1:natives-windows-arm64"))
                }

                // Minecraft 1.14 ~ 1.18
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.1"))
                    redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.3.1:natives-windows-arm64"))
                }

                // Minecraft 1.19+
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.3.1:natives-windows", mavenLibrary("$lib:3.3.1:natives-windows-arm64"))
                    redirectToEmpty("$lib:3.3.1:natives-windows-x86")
                }

                redirectAllToEmpty(
                    "net.java.jinput:jinput-platform:2.0.5:natives",
                    "com.mojang:text2speech:1.10.3:natives",
                    "com.mojang:text2speech:1.11.3:natives",
                    "com.mojang:text2speech:1.12.4:natives",
                    "com.mojang:text2speech:1.13.9:natives-windows"
                )
            },
            "osx-arm64" to buildRedirectMap {

            }
        )

        jsonFile.parentFile.mkdirs()
        jsonFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(map))
    }
}