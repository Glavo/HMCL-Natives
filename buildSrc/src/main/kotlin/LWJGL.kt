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

enum class LWJGL(isBase: Boolean = false) {
    BASE(true),

    JEMALLOC,
    OPENAL,
    OPENGL,
    GLFW,
    STB,
    TINYFD,

    FREETYPE;

    val artifactId: String = if (isBase) "lwjgl" else "lwjgl-" + name.lowercase()
    val fullName = "org.lwjgl:$artifactId"

    override fun toString(): String = fullName

    companion object {
        val base0 = listOf(BASE, JEMALLOC, OPENAL, OPENGL, GLFW, STB, TINYFD)
        val base1 = base0 + FREETYPE

        @JvmStatic
        fun main(args: Array<String>) {
            base1.forEach { println(it.fullName) }
        }
    }
}