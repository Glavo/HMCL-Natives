import org.gradle.api.Project
import java.io.InputStream
import java.net.URL
import java.security.*

internal val sha1MessageDigest = MessageDigest.getInstance("SHA-1")
internal val buffer = ByteArray(2048)

fun sha1ByteArrayToString(arr: ByteArray) = arr.joinToString("") { b -> String.format("%02x", b) }
fun sha1(input: InputStream): String {
    sha1MessageDigest.reset()

    var n: Int
    while (input.read(buffer).also { n = it } > 0) {
        sha1MessageDigest.update(buffer, 0, n)
    }

    return sha1ByteArrayToString(sha1MessageDigest.digest())
}


internal lateinit var project: Project

fun initProject(p: Project) {
    project = p
}