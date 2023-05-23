@file:Suppress("UNCHECKED_CAST")
@file:OptIn(kotlin.ExperimentalStdlibApi::class)

plugins {
    id("java")
    signing
    `maven-publish`
}

group = "org.glavo.hmcl"
version = "3.3.1"
description = "LWJGL 3 native libraries"

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

val platforms = listOf(
    "linux-mips64el", "linux-loongarch64_ow", "linux-riscv64"
)

val platformJarVersion: Map<String, String> = mutableMapOf<String, String>().apply{
    put("linux-loongarch64_ow", "${project.version}-rc1")
    put("linux-mips64el", "${project.version}-rc2")
    put("linux-riscv64", "${project.version}-rc1")

    for (platform in platforms) {
        if (platform !in this)
            put(platform, project.version.toString())
    }
}

val platformJars = tasks.create("platformJars") { group = "platform jars" }

for (platform in platforms) {
    val platformJar = tasks.create<Jar>("$platform-jar") {
        group = "platform jars"
        archiveVersion.set("${platformJarVersion[platform]}-$platform")
        platformJars.dependsOn(this)

        from(platform)
    }

    val platformSourceJar = tasks.create<Jar>("$platform-sourceJar") {
        group = "platform jars"
        archiveVersion.set("${platformJarVersion[platform]}-$platform")
        archiveClassifier.set("sources")
    }

    val platformJavadocJar = tasks.create<Jar>("$platform-javadocJar") {
        group = "platform jars"
        archiveVersion.set("${platformJarVersion[platform]}-$platform")
        archiveClassifier.set("javadoc")
    }

    configure<PublishingExtension> {
        repositories {
            maven {
                name = "build"
                url = uri(layout.buildDirectory.dir("repo"))
            }
        }

        publications {
            create<MavenPublication>(platform) {

                groupId = project.group.toString()
                version = platformJar.archiveVersion.get()
                artifactId = project.name
                artifact(platformJar)
                artifact(platformSourceJar)
                artifact(platformJavadocJar)

                pom {
                    name.set(project.name)
                    description.set(project.description)
                    url.set("https://github.com/Glavo/HMCL-Natives")

                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("glavo")
                            name.set("Glavo")
                            email.set("zjx001202@gmail.com")
                        }
                    }

                    scm {
                        url.set("https://github.com/Glavo/HMCL-Natives")
                    }
                }
            }
        }
    }

    tasks.create("$platform-bundleJar") {
        group = "platform jars"
        dependsOn(tasks["publishAllPublicationsToBuildRepository"])
    }
}

if (rootProject.ext.has("signing.key")) {
    signing {
        useInMemoryPgpKeys(
            rootProject.ext["signing.keyId"].toString(),
            rootProject.ext["signing.key"].toString(),
            rootProject.ext["signing.password"].toString(),
        )
        sign(*platforms.map { publishing.publications[it] }.toTypedArray())
    }
}

