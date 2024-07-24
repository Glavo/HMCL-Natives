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

import java.io.InputStream
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
