import java.security.*

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

                redirectAllToEmpty(
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

                redirectAllToEmpty(
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
                // Minecraft 1.19+
                for (lib in lwjgl3BaseLibraries) {
                    redirect("$lib:3.3.1:natives-windows", mavenLibrary("$lib:3.3.1:natives-windows-arm64"))
                }
                redirectToEmpty("com.mojang:text2speech:1.13.9:natives-windows")
            }
        )

        jsonFile.parentFile.mkdirs()
        jsonFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(map))
    }
}