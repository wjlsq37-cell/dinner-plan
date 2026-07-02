package com.dinnerplan.server

import com.dinnerplan.shared.IngredientDto
import com.dinnerplan.shared.RecipeDto
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class RecipeCorpusImportResult(
    val imported: Int,
    val skipped: Int,
    val dbPath: String,
    val ftsTokenizer: String
)

data class RecipeCorpusStatus(
    val ready: Boolean,
    val count: Long,
    val path: String
)

data class RecipeCorpusSearchResult(
    val recipes: List<RecipeDto>,
    val totalMatches: Int
)

class RecipeCorpusImporter(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun importJsonl(source: Path, dbPath: Path): RecipeCorpusImportResult {
        require(Files.isRegularFile(source)) { "Recipe corpus JSONL not found: $source" }
        dbPath.parent?.createDirectories()
        Files.deleteIfExists(dbPath)

        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection(jdbcUrl(dbPath)).use { connection ->
            connection.createSchema()
            connection.autoCommit = false
            connection.prepareStatement(
                """
                INSERT OR REPLACE INTO recipes(
                    id, name, dish, description, ingredients_json, instructions_json,
                    keywords_json, author, search_text, rating_stars, line_number
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                var imported = 0
                var skipped = 0
                Files.newBufferedReader(source, Charsets.UTF_8).useLines { lines ->
                    lines.forEachIndexed { index, rawLine ->
                        val line = rawLine.trim()
                        if (line.isBlank()) return@forEachIndexed
                        val item = parseLine(line)
                        if (item == null || item.name.isBlank()) {
                            skipped++
                            return@forEachIndexed
                        }

                        statement.setString(1, stableId(item))
                        statement.setString(2, item.name)
                        statement.setString(3, item.dish)
                        statement.setString(4, item.description)
                        statement.setString(5, encodeList(item.ingredients))
                        statement.setString(6, encodeList(item.instructions))
                        statement.setString(7, encodeList(item.keywords))
                        statement.setString(8, item.author)
                        statement.setString(9, item.searchText())
                        statement.setDouble(10, ratingStars(item))
                        statement.setInt(11, index + 1)
                        statement.addBatch()
                        imported++

                        if (imported % 1000 == 0) {
                            statement.executeBatch()
                            connection.commit()
                        }
                    }
                }
                statement.executeBatch()
                connection.commit()

                val tokenizer = createFtsIndex(connection)
                connection.upsertMeta("source_path", source.toAbsolutePath().toString())
                connection.upsertMeta("source_last_modified", Files.getLastModifiedTime(source).toMillis().toString())
                connection.upsertMeta("fts_tokenizer", tokenizer)
                connection.commit()
                return RecipeCorpusImportResult(
                    imported = imported,
                    skipped = skipped,
                    dbPath = dbPath.toAbsolutePath().toString(),
                    ftsTokenizer = tokenizer
                )
            }
        }
    }

    private fun parseLine(line: String): CorpusLine? {
        return runCatching {
            val obj = json.decodeFromString(JsonObject.serializer(), line)
            CorpusLine(
                name = obj.string("name").cleanText(),
                dish = obj.string("dish").cleanText().takeUnless { it.equals("Unknown", ignoreCase = true) }.orEmpty(),
                description = obj.string("description").cleanText(),
                ingredients = obj.stringList("recipeIngredient").map { it.cleanText() }.filter { it.isNotBlank() },
                instructions = obj.stringList("recipeInstructions").map { it.cleanText() }.filter { it.isNotBlank() },
                author = obj.string("author").cleanText(),
                keywords = obj.stringList("keywords").map { it.cleanText() }.filter { it.isNotBlank() }
            )
        }.getOrNull()
    }

    private fun encodeList(values: List<String>): String {
        return json.encodeToString(ListSerializer(String.serializer()), values)
    }

    private fun ratingStars(item: CorpusLine): Double {
        var score = 2.6
        if (item.dish.isNotBlank()) score += 0.4
        if (item.description.length in 8..400) score += 0.25
        score += (item.ingredients.size.coerceAtMost(6) / 6.0) * 0.7
        score += (item.instructions.size.coerceAtMost(5) / 5.0) * 0.9
        if (cleanKeywords(item).isNotEmpty()) score += 0.3
        if (item.instructions.any { it.length >= 12 }) score += 0.25
        if (abnormalRatio(item.description + item.instructions.joinToString()) > 0.28) score -= 0.4
        return (score.coerceIn(1.0, 5.0) * 10).toInt() / 10.0
    }

    private fun stableId(item: CorpusLine): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("${item.name}|${item.author}|${item.ingredients.joinToString("|")}|${item.instructions.joinToString("|")}".toByteArray(Charsets.UTF_8))
        return "recipe_corpus_" + digest.joinToString("") { "%02x".format(it) }.take(20)
    }
}

class RecipeCorpusRepository(
    private val dbPath: Path = Paths.get(AppConfig.load().recipeCorpusDbPath),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun status(): RecipeCorpusStatus {
        if (!dbPath.exists()) {
            return RecipeCorpusStatus(ready = false, count = 0, path = dbPath.toAbsolutePath().toString())
        }
        return runCatching {
            connection().use { connection ->
                RecipeCorpusStatus(
                    ready = connection.hasTable("recipes"),
                    count = connection.scalarLong("SELECT COUNT(*) FROM recipes"),
                    path = dbPath.toAbsolutePath().toString()
                )
            }
        }.getOrElse {
            RecipeCorpusStatus(ready = false, count = 0, path = dbPath.toAbsolutePath().toString())
        }
    }

    fun search(query: String, limit: Int = 24): RecipeCorpusSearchResult {
        if (!dbPath.exists()) return RecipeCorpusSearchResult(emptyList(), 0)
        val normalized = query.trim()
        if (normalized.isBlank()) return topRated(limit)

        return runCatching {
            connection().use { connection ->
                val fts = searchWithFts(connection, normalized, limit)
                val recipes = fts.ifEmpty { searchWithLike(connection, normalized, limit) }
                RecipeCorpusSearchResult(recipes = recipes, totalMatches = recipes.size)
            }
        }.getOrDefault(RecipeCorpusSearchResult(emptyList(), 0))
    }

    fun recipeById(id: String): RecipeDto? {
        if (!dbPath.exists()) return null
        return runCatching {
            connection().use { connection ->
                connection.prepareStatement("SELECT * FROM recipes WHERE id = ?").use { statement ->
                    statement.setString(1, id)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) rs.toRecipe(json) else null
                    }
                }
            }
        }.getOrNull()
    }

    private fun topRated(limit: Int): RecipeCorpusSearchResult {
        return connection().use { connection ->
            connection.prepareStatement("SELECT * FROM recipes ORDER BY rating_stars DESC, line_number ASC LIMIT ?").use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    val recipes = rs.collectRecipes(json)
                    RecipeCorpusSearchResult(recipes, recipes.size)
                }
            }
        }
    }

    private fun searchWithFts(connection: Connection, query: String, limit: Int): List<RecipeDto> {
        if (!connection.hasTable("recipes_fts")) return emptyList()
        val like = "%${query.escapeLike()}%"
        val ftsQuery = quoteFtsQuery(query)
        val sql =
            """
            SELECT r.*,
                ${relevanceSql("r")} AS relevance
            FROM recipes_fts f
            JOIN recipes r ON r.rowid = f.rowid
            WHERE recipes_fts MATCH ?
            ORDER BY r.rating_stars DESC, relevance DESC, r.line_number ASC
            LIMIT ?
            """.trimIndent()
        return runCatching {
            connection.prepareStatement(sql).use { statement ->
                var index = bindRelevanceArgs(statement = statement, startIndex = 1, like = like)
                statement.setString(index++, ftsQuery)
                statement.setInt(index, limit)
                statement.executeQuery().use { it.collectRecipes(json) }
            }
        }.getOrDefault(emptyList())
    }

    private fun searchWithLike(connection: Connection, query: String, limit: Int): List<RecipeDto> {
        val like = "%${query.escapeLike()}%"
        val sql =
            """
            SELECT r.*,
                ${relevanceSql("r")} AS relevance
            FROM recipes r
            WHERE r.name LIKE ? ESCAPE '\'
                OR r.dish LIKE ? ESCAPE '\'
                OR r.keywords_json LIKE ? ESCAPE '\'
                OR r.ingredients_json LIKE ? ESCAPE '\'
                OR r.description LIKE ? ESCAPE '\'
            ORDER BY r.rating_stars DESC, relevance DESC, r.line_number ASC
            LIMIT ?
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            var index = bindRelevanceArgs(statement = statement, startIndex = 1, like = like)
            repeat(5) { statement.setString(index++, like) }
            statement.setInt(index, limit)
            statement.executeQuery().use { it.collectRecipes(json) }
        }
    }

    private fun connection(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection(jdbcUrl(dbPath))
    }
}

fun main() {
    val config = AppConfig.load()
    val result = RecipeCorpusImporter().importJsonl(
        source = Paths.get(config.recipeCorpusJsonlPath),
        dbPath = Paths.get(config.recipeCorpusDbPath)
    )
    println(
        "Imported ${result.imported} recipes, skipped ${result.skipped}, " +
            "fts=${result.ftsTokenizer}, db=${result.dbPath}"
    )
}

private data class CorpusLine(
    val name: String,
    val dish: String,
    val description: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val author: String,
    val keywords: List<String>
) {
    fun searchText(): String {
        return listOf(name, dish, description, ingredients.joinToString(" "), keywords.joinToString(" "))
            .joinToString(" ")
    }
}

private fun Connection.createSchema() {
    createStatement().use { statement ->
        statement.executeUpdate("PRAGMA journal_mode=OFF")
        statement.executeUpdate("PRAGMA synchronous=OFF")
        statement.executeUpdate("PRAGMA temp_store=MEMORY")
        statement.executeUpdate(
            """
            CREATE TABLE recipes(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                dish TEXT NOT NULL,
                description TEXT NOT NULL,
                ingredients_json TEXT NOT NULL,
                instructions_json TEXT NOT NULL,
                keywords_json TEXT NOT NULL,
                author TEXT NOT NULL,
                search_text TEXT NOT NULL,
                rating_stars REAL NOT NULL,
                line_number INTEGER NOT NULL
            )
            """.trimIndent()
        )
        statement.executeUpdate("CREATE INDEX recipes_rating_idx ON recipes(rating_stars DESC)")
        statement.executeUpdate("CREATE TABLE corpus_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
}

private fun createFtsIndex(connection: Connection): String {
    val tokenizers = listOf("trigram", "unicode61")
    for (tokenizer in tokenizers) {
        val created = runCatching {
            connection.createStatement().use { statement ->
                statement.executeUpdate("DROP TABLE IF EXISTS recipes_fts")
                statement.executeUpdate(
                    """
                    CREATE VIRTUAL TABLE recipes_fts USING fts5(
                        name, dish, keywords, ingredients, search_text,
                        content='recipes',
                        content_rowid='rowid',
                        tokenize='$tokenizer'
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    INSERT INTO recipes_fts(rowid, name, dish, keywords, ingredients, search_text)
                    SELECT rowid, name, dish, keywords_json, ingredients_json, search_text FROM recipes
                    """.trimIndent()
                )
            }
        }.isSuccess
        if (created) return tokenizer
    }
    return "none"
}

private fun Connection.upsertMeta(key: String, value: String) {
    prepareStatement("INSERT OR REPLACE INTO corpus_meta(key, value) VALUES(?, ?)").use { statement ->
        statement.setString(1, key)
        statement.setString(2, value)
        statement.executeUpdate()
    }
}

private fun Connection.hasTable(name: String): Boolean {
    return metaData.getTables(null, null, name, null).use { it.next() }
}

private fun Connection.scalarLong(sql: String): Long {
    return createStatement().use { statement ->
        statement.executeQuery(sql).use { rs ->
            if (rs.next()) rs.getLong(1) else 0
        }
    }
}

private fun ResultSet.collectRecipes(json: Json): List<RecipeDto> {
    val recipes = mutableListOf<RecipeDto>()
    while (next()) {
        recipes += toRecipe(json)
    }
    return recipes
}

private fun ResultSet.toRecipe(json: Json): RecipeDto {
    val ingredients = decodeList(json, getString("ingredients_json"))
    val instructions = decodeList(json, getString("instructions_json"))
    val keywords = decodeList(json, getString("keywords_json"))
    val name = getString("name")
    val dish = getString("dish")
    val rating = getDouble("rating_stars")
    val tags = cleanKeywords(name, dish, keywords).take(5)
    return RecipeDto(
        id = getString("id"),
        name = name,
        cuisine = dish.ifBlank { tags.firstOrNull() ?: "本地菜谱" },
        taste = inferTaste(name, getString("description"), keywords),
        tags = buildList {
            add("${"%.1f".format(rating)} 星")
            addAll(tags)
        }.distinct().take(6),
        difficulty = inferDifficulty(ingredients.size, instructions.size),
        cookTime = inferCookTime(name + " " + getString("description") + " " + instructions.joinToString(" ")),
        servings = inferServings(name + " " + getString("description")),
        coverUrl = "https://images.unsplash.com/photo-1543353071-873f17a7a088?auto=format&fit=crop&w=900&q=80",
        reason = getString("description").ifBlank { "来自本地食谱库，按搜索词和星级匹配。" }.take(160),
        ingredients = ingredients.map { IngredientDto(it, "") },
        steps = instructions.ifEmpty { listOf("原菜谱未提供详细步骤，可按食材和常规做法处理。") },
        tips = "来自本地菜谱库；调味和火候可按个人口味微调。",
        ratingStars = rating,
        source = "recipe_corpus"
    )
}

private fun decodeList(json: Json, value: String): List<String> {
    return runCatching {
        json.decodeFromString(ListSerializer(String.serializer()), value)
    }.getOrDefault(emptyList())
}

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject.stringList(key: String): List<String> {
    return when (val value = this[key]) {
        is JsonArray -> value.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        is JsonPrimitive -> value.contentOrNull?.let { listOf(it) }.orEmpty()
        else -> emptyList()
    }
}

private fun String.cleanText(): String {
    return replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun cleanKeywords(item: CorpusLine): List<String> {
    return cleanKeywords(item.name, item.dish, item.keywords)
}

private fun cleanKeywords(name: String, dish: String, keywords: List<String>): List<String> {
    val noisy = listOf("的做法", "家常做法", "详细做法", "怎么做", "最正宗做法")
    return buildList {
        if (dish.isNotBlank()) add(dish)
        keywords.forEach { keyword ->
            var cleaned = keyword
            noisy.forEach { cleaned = cleaned.replace(it, "") }
            cleaned = cleaned.replace(name, "").trim(' ', '，', ',', '、')
            if (cleaned.length in 2..12) add(cleaned)
        }
    }.filter { it.isNotBlank() }.distinct()
}

private fun abnormalRatio(text: String): Double {
    if (text.isBlank()) return 0.0
    val abnormal = text.count { it.code < 9 || (it.code in 14..31) }
    return abnormal.toDouble() / text.length
}

private fun inferTaste(name: String, description: String, keywords: List<String>): List<String> {
    val text = "$name $description ${keywords.joinToString(" ")}"
    return buildList {
        if (text.contains("麻辣")) add("麻辣")
        if (text.contains("微辣") || (text.contains("辣") && "麻辣" !in this)) add("微辣")
        if (text.contains("清淡") || text.contains("低脂") || text.contains("少油")) add("清淡")
        if (text.contains("酸甜") || text.contains("番茄")) add("酸甜")
        if (text.contains("甜") || text.contains("蛋糕") || text.contains("甜品")) add("甜")
    }.ifEmpty { listOf("家常") }.distinct().take(3)
}

private fun inferDifficulty(ingredientCount: Int, stepCount: Int): String {
    return when {
        stepCount <= 2 && ingredientCount <= 4 -> "入门"
        stepCount <= 5 && ingredientCount <= 8 -> "简单"
        else -> "进阶"
    }
}

private fun inferCookTime(text: String): String {
    val minutes = Regex("(\\d{1,3})\\s*(分钟|min)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return minutes?.let { "约 $it 分钟" } ?: "时间未知"
}

private fun inferServings(text: String): String {
    val people = Regex("(\\d{1,2})\\s*人").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return people?.let { "$it 人份" } ?: "按需"
}

private fun relevanceSql(alias: String): String {
    return """
        (CASE WHEN $alias.name LIKE ? ESCAPE '\' THEN 8 ELSE 0 END +
         CASE WHEN $alias.dish LIKE ? ESCAPE '\' THEN 6 ELSE 0 END +
         CASE WHEN $alias.keywords_json LIKE ? ESCAPE '\' THEN 4 ELSE 0 END +
         CASE WHEN $alias.ingredients_json LIKE ? ESCAPE '\' THEN 3 ELSE 0 END +
         CASE WHEN $alias.description LIKE ? ESCAPE '\' THEN 1 ELSE 0 END)
    """.trimIndent()
}

private fun bindRelevanceArgs(
    statement: java.sql.PreparedStatement,
    startIndex: Int,
    like: String
): Int {
    var index = startIndex
    repeat(5) {
        statement.setString(index++, like)
    }
    return index
}

private fun String.escapeLike(): String {
    return replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

private fun quoteFtsQuery(query: String): String {
    return "\"${query.replace("\"", "\"\"")}\""
}

private fun jdbcUrl(path: Path): String {
    return "jdbc:sqlite:${path.toAbsolutePath()}"
}
