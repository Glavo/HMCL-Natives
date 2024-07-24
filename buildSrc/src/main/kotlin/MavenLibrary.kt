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

typealias MavenLibrary = Map<String, Any>

private val pattern = Regex("(?<groupId>[^:]+):(?<artifactId>[^:]+):(?<version>[^:]+)(:(?<classifier>[^:]+))?").toPattern()

fun mavenLibrary(name: String, snapshot: String? = null, repo: MavenRepo = MavenRepo.MAVEN_CENTRAL): MavenLibrary {
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

fun emptyLibrary(name: String): MavenLibrary = mapOf("name" to name)
