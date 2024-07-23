import java.io.File
import java.net.URI

enum class MavenRepo(val url: String, val mirrorURL: String = url) {
    MAVEN_CENTRAL("https://repo1.maven.org/maven2", mirrorURL = "https://maven.aliyun.com/repository/central"),
    MOJANG("https://libraries.minecraft.net"),
    SONATYPE_SNAPSHOTS("https://oss.sonatype.org/content/repositories/snapshots");

    private fun downloadSHA1(path: String): String {
        val baseDir: File = project.layout.buildDirectory.asFile.get().resolve("cache")
        val targetFile = baseDir.resolve(path)

        if (targetFile.exists()) {
            val res = targetFile.readText().trim()
            if (res.length == 40)
                return res
        }

        val bytes = URI("$mirrorURL/$path").toURL().readBytes()

        val res = String(bytes).trim()
        if (res.length != 40) {
            throw AssertionError("sha1=$res")
        }

        targetFile.parentFile.mkdirs()
        targetFile.writeBytes(bytes)
        return res
    }

    fun downloadFile(path: String): Pair<Long, String> {
        val baseDir = project.layout.buildDirectory.asFile.get().resolve("cache")
        val targetFile = baseDir.resolve(path)

        val expectedSHA1 = downloadSHA1("$path.sha1")
        if (targetFile.exists() && targetFile.inputStream().use { sha1(it) } == expectedSHA1) {
            return Pair(targetFile.length(), expectedSHA1)
        }

        var fileSize: Long = 0

        sha1MessageDigest.reset()
        targetFile.parentFile.mkdirs()

        println("Downloading $mirrorURL/$path")
        targetFile.outputStream().use { output ->
            URI("$mirrorURL/$path").toURL().openStream().use { input ->
                var n: Int
                while (input.read(buffer).also { n = it } > 0) {
                    output.write(buffer, 0, n)
                    sha1MessageDigest.update(buffer, 0, n)
                    fileSize += n
                }
            }
        }

        val sha1 = sha1ByteArrayToString(sha1MessageDigest.digest())
        if (sha1 != expectedSHA1)
            throw AssertionError("path=$path, sha1($sha1) != expectedSHA1($expectedSHA1)")

        return Pair(fileSize, sha1)
    }
}