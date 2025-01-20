/*
 * Copyright 2024 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("UNCHECKED_CAST")

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

fun lwjglNatives(os: String, arch: String, version: String) = buildMap {
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

fun generate(): Map<String, Map<String, MavenLibrary?>> = mapOf(
    "linux-arm64" to buildRedirectMap {
        // Minecraft 1.13
        for (lib in LWJGL.base0) {
            redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
            redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
        }

        // Minecraft 1.14 ~ 1.14.2
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.1", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
            redirect("$lib:3.2.1:natives", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
        }

        // Minecraft 1.14.3 ~ 1.18
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
            redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
        }

        // Minecraft 1.19 ~ 1.20.1
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.1", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
            redirect("$lib:3.3.1:natives-linux", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
        }

        // Minecraft 1.20.2+
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.2:natives-linux", mavenLibrary("$lib:3.3.2:natives-linux-arm64"))
        }

        // Minecraft 1.20.5+
        for (lib in LWJGL.base1) {
            redirect("$lib:3.3.3:natives-linux", mavenLibrary("$lib:3.3.3:natives-linux-arm64"))
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
        for (lib in LWJGL.base0) {
            redirect("$lib:3.1.6", mavenLibrary("$lib:3.2.3"))
            redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.2.3:natives-linux-arm32"))
        }

        // Minecraft 1.14 ~ 1.14.2
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.1", mavenLibrary("$lib:3.2.3"))
            redirect("$lib:3.2.1:natives", mavenLibrary("$lib:3.2.3:natives-linux-arm32"))
        }

        // Minecraft 1.14.3 ~ 1.18
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.2", mavenLibrary("$lib:3.2.3"))
            redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.2.3:natives-linux-arm32"))
        }

        // Minecraft 1.19+
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.1:natives-linux", mavenLibrary("$lib:3.3.1:natives-linux-arm32"))
        }

        // Minecraft 1.6 ~ 1.12
        val lwjgl2Natives = buildMap {
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
        for (lib in LWJGL.base0) {
            redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.1.6:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.1.6:natives")
        }

        // Minecraft 1.14 ~ 1.14.2
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.1", mavenLibrary("$lib:3.3.1"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.2.1:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.1.6:natives")
        }

        // Minecraft 1.14.3 ~ 1.18
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.1"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.2.2:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.2.2:natives")
        }

        // Minecraft 1.19+
        for (lib in LWJGL.base0) {
            if (lib == LWJGL.BASE)
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
        val lwjgl3Version = "3.3.4"
        val lwjgl3Natives = lwjglNatives("linux", "loongarch64", "3.3.4-rc2")

        // Minecraft 1.13
        for (lib in LWJGL.base0) {
            redirect("$lib:3.1.6", mavenLibrary("$lib:$lwjgl3Version"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.1.6:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.1.6:natives")
        }

        // Minecraft 1.14 ~ 1.14.2
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.1", mavenLibrary("$lib:$lwjgl3Version"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.2.1:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.2.1:natives")
        }

        // Minecraft 1.14.3 ~ 1.18
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.2", mavenLibrary("$lib:$lwjgl3Version"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.2.2:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.2.2:natives")
        }

        // Minecraft 1.19 ~ 1.20.1
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.1", mavenLibrary("$lib:$lwjgl3Version"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.3.1:natives-linux", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.3.1:natives-linux")
        }

        // Minecraft 1.20.2~1.20.4
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.2", mavenLibrary("$lib:$lwjgl3Version"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.3.2:natives-linux", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.3.2:natives-linux")
        }

        // Minecraft 1.20.5+
        for (lib in LWJGL.base1) {
            redirect("$lib:3.3.3", mavenLibrary("$lib:$lwjgl3Version"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.3.3:natives-linux", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.3.3:natives-linux")
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
        for (lib in LWJGL.base0) {
            redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.1.6:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.1.6:natives")
        }

        // Minecraft 1.14 ~ 1.14.2
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.1", mavenLibrary("$lib:3.3.1"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.2.1:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.2.1:natives")
        }

        // Minecraft 1.14.3 ~ 1.18
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.1"))
            if (lib == LWJGL.BASE)
                redirect("$lib:3.2.2:natives", lwjgl3Natives)
            else
                redirectToEmpty("$lib:3.2.2:natives")
        }

        // Minecraft 1.19+
        for (lib in LWJGL.base0) {
            if (lib == LWJGL.BASE)
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
        // Minecraft 1.13
        for (lib in LWJGL.base0) {
            redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.3.4:natives-linux-riscv64"))
        }

        // Minecraft 1.14 ~ 1.14.2
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.1", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.2.1:natives", mavenLibrary("$lib:3.3.4:natives-linux-riscv64"))
        }

        // Minecraft 1.14.3 ~ 1.18
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.3.4:natives-linux-riscv64"))
        }

        // Minecraft 1.19 ~ 1.20.1
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.1", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.3.1:natives-linux", mavenLibrary("$lib:3.3.4:natives-linux-riscv64"))
        }

        // Minecraft 1.20.2~1.20.4
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.2", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.3.2:natives-linux", mavenLibrary("$lib:3.3.4:natives-linux-riscv64"))
        }

        // Minecraft 1.20.5+
        for (lib in LWJGL.base1) {
            redirect("$lib:3.3.3", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.3.3:natives-linux", mavenLibrary("$lib:3.3.4:natives-linux-riscv64"))
        }

        redirectToEmpty("net.java.jinput:jinput-platform:2.0.5:natives")
        redirectAllToEmpty(*allLinuxText2speech)
    },
    "windows-x86_64" to buildRedirectMap {
        redirect("software-renderer-loader", mavenLibrary("org.glavo:llvmpipe-loader:1.0"))
        redirect("mesa-loader", mavenLibrary("org.glavo:mesa-loader-windows:0.3.0:x64"))
    },
    "windows-x86" to buildRedirectMap {
        redirect("mesa-loader", mavenLibrary("org.glavo:mesa-loader-windows:0.3.0:x86"))
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
        for (lib in LWJGL.base0) {
            redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
            redirect(
                "$lib:3.1.6:natives",
                mavenLibrary("$lib:3.3.2:natives-windows-arm64", repo = MavenRepo.MOJANG)
            )
        }

        // Minecraft 1.14 ~ 1.18
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.2", repo = MavenRepo.MOJANG))
            redirect(
                "$lib:3.2.2:natives",
                mavenLibrary("$lib:3.3.2:natives-windows-arm64", repo = MavenRepo.MOJANG)
            )
        }

        redirectAllToEmpty(
            "net.java.jinput:jinput-platform:2.0.5:natives",
            "com.mojang:text2speech:1.10.3:natives",
            "com.mojang:text2speech:1.11.3:natives",
            "com.mojang:text2speech:1.12.4:natives",
            "com.mojang:text2speech:1.13.9:natives-windows"
        )

        redirect("mesa-loader", mavenLibrary("org.glavo:mesa-loader-windows:0.3.0:arm64"))
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
        for (lib in LWJGL.base0) {
            if (lib == LWJGL.GLFW)
                redirect("$lib:3.1.6", mavenLibrary("org.glavo.hmcl.mmachina:lwjgl-glfw:3.3.1-mmachina.1"))
            else
                redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.1"))
            redirect(
                "$lib:3.1.6:natives",
                mavenLibrary("$lib:3.3.1:natives-macos-arm64", repo = MavenRepo.MOJANG)
            )
        }

        // Minecraft 1.14 ~ 1.18
        for (lib in LWJGL.base0) {
            if (lib == LWJGL.GLFW)
                redirect("$lib:3.2.1", mavenLibrary("org.glavo.hmcl.mmachina:lwjgl-glfw:3.3.1-mmachina.1"))
            else
                redirect("$lib:3.2.1", mavenLibrary("$lib:3.3.1"))
            redirect(
                "$lib:3.2.1:natives",
                mavenLibrary("$lib:3.3.1:natives-macos-arm64", repo = MavenRepo.MOJANG)
            )
        }

        redirect(
            "ca.weblite:java-objc-bridge:1.0.0",
            mavenLibrary("org.glavo.hmcl.mmachina:java-objc-bridge:1.1.0-mmachina.1")
        )
        redirectToEmpty("ca.weblite:java-objc-bridge:1.0.0:natives")


        redirect(
            "com.mojang:text2speech:1.10.3",
            mavenLibrary("com.mojang:text2speech:1.11.3", repo = MavenRepo.MOJANG)
        )

        redirectAllToEmpty(
            "net.java.jinput:jinput-platform:2.0.5:natives",
            "com.mojang:text2speech:1.10.3:natives",
            "com.mojang:text2speech:1.11.3:natives",
            "com.mojang:text2speech:1.12.4:natives"
        )
    },
    "freebsd-x86_64" to buildRedirectMap {
        // Minecraft 1.13
        for (lib in LWJGL.base0) {
            redirect("$lib:3.1.6", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.1.6:natives", mavenLibrary("$lib:3.3.4:natives-freebsd"))
        }

        // Minecraft 1.14 ~ 1.14.2
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.1", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.2.1:natives", mavenLibrary("$lib:3.3.4:natives-freebsd"))
        }

        // Minecraft 1.14.3 ~ 1.18
        for (lib in LWJGL.base0) {
            redirect("$lib:3.2.2", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.2.2:natives", mavenLibrary("$lib:3.3.4:natives-freebsd"))
        }

        // Minecraft 1.19~1.20.1
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.1", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.3.1:natives", mavenLibrary("$lib:3.3.4:natives-freebsd"))
        }

        // Minecraft 1.20.2+
        for (lib in LWJGL.base0) {
            redirect("$lib:3.3.2", mavenLibrary("$lib:3.3.4"))
            redirect("$lib:3.3.2:natives", mavenLibrary("$lib:3.3.4:natives-freebsd"))
        }

        redirectToEmpty("net.java.jinput:jinput-platform:2.0.5:natives")
        redirectAllToEmpty(*allLinuxText2speech)
    }
)

val jsonFile = rootProject.layout.buildDirectory.asFile.get().resolve("natives.json")
rootProject.tasks.create("generateJson") {
    doLast {
        jsonFile.parentFile.mkdirs()
        jsonFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(generate()))
    }
}