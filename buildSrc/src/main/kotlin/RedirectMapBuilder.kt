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

class RedirectMapBuilder {
    private val map: MutableMap<String, MavenLibrary?> = LinkedHashMap()

    fun build(): Map<String, Map<String, Any>?> = map

    fun redirect(lib: String, newLibrary: MavenLibrary?) {
        map[lib] = newLibrary
    }

    fun redirectToEmpty(lib: String) {
        redirect(lib, null)
    }

    fun redirectAllToEmpty(vararg libs: String) {
        for (lib in libs)
            redirectToEmpty(lib)
    }
}

inline fun buildRedirectMap(f: RedirectMapBuilder.() -> Unit): Map<String, MavenLibrary?> =
    RedirectMapBuilder().apply { f() }.build()
