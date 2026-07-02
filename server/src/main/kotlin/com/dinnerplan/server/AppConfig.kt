package com.dinnerplan.server

import java.io.File

data class AppConfig(
    val aiBaseUrl: String,
    val aiApiKey: String,
    val aiModel: String,
    val amapWebKey: String,
    val port: Int,
    val recipeCorpusJsonlPath: String = "food/recipe_corpus_full.json",
    val recipeCorpusDbPath: String = "server/data/recipe_corpus.sqlite"
) {
    val aiConfigured: Boolean = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModel.isNotBlank()
    val amapConfigured: Boolean = amapWebKey.isNotBlank()

    fun missingRequired(): List<String> {
        return buildList {
            if (!aiConfigured) add("AI_BASE_URL/AI_API_KEY/AI_MODEL")
            if (!amapConfigured) add("AMAP_WEB_KEY")
        }
    }

    companion object {
        fun load(): AppConfig {
            val fileValues = readEnvFile(File(".env")) + readEnvFile(File("server/.env"))
            fun value(name: String, default: String = ""): String {
                return System.getenv(name)?.takeIf { it.isNotBlank() }
                    ?: fileValues[name]?.takeIf { it.isNotBlank() }
                    ?: default
            }

            return AppConfig(
                aiBaseUrl = value("AI_BASE_URL", "https://api.openai.com/v1").trimEnd('/'),
                aiApiKey = value("AI_API_KEY"),
                aiModel = value("AI_MODEL", "gpt-4o-mini"),
                amapWebKey = value("AMAP_WEB_KEY"),
                port = value("PORT", "8080").toIntOrNull() ?: 8080,
                recipeCorpusJsonlPath = value("RECIPE_CORPUS_JSONL", "food/recipe_corpus_full.json"),
                recipeCorpusDbPath = value("RECIPE_CORPUS_DB", "server/data/recipe_corpus.sqlite")
            )
        }

        private fun readEnvFile(file: File): Map<String, String> {
            if (!file.isFile) return emptyMap()
            return file.readLines()
                .mapNotNull { raw ->
                    val line = raw.trim()
                    if (line.isBlank() || line.startsWith("#") || "=" !in line) {
                        null
                    } else {
                        val key = line.substringBefore("=").trim()
                        val value = line.substringAfter("=").trim().trim('"')
                        key to value
                    }
                }
                .toMap()
        }
    }
}
