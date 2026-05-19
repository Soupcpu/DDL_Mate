package com.deadlinemate.deepseek

import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.RepeatRule
import com.deadlinemate.domain.model.TaskCategory
import com.deadlinemate.domain.model.TaskDraft
import com.deadlinemate.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class DeepSeekTaskParseDebugResult(
    val prompt: String,
    val rawResponse: String,
    val extractedJson: String,
    val draft: TaskDraft
)

fun buildDeepSeekTaskExtractionPrompt(text: String): String {
    val nowMillis = System.currentTimeMillis()
    val nowText = DateTimeUtils.formatDateTime(nowMillis, "中文")
    return """
    你是 Deadline Mate 的任务信息整理器。你的任务是把用户的语音转文字结果或截图 OCR 文本，整理成 App 新建任务表单可直接预填的 JSON。

    重要规则：
    1. 只返回一个 JSON 对象，不要输出解释、Markdown、代码块或多余文字。
    2. 不要编造用户没有说出的信息；无法判断的字段返回 null。
    3. 缺失但表单重要的字段，请把字段名写入 missingFields。
    4. deadlineDateTimeText 必须保留用户原文里的截止时间表达，例如“明天晚上八点”“5月20日 23:59”。
    5. startDateTimeText 只有用户明确提到开始时间、上课时间、会议开始时间时才填写，否则返回 null。
    6. reminderMinutesBefore 只有用户明确说“提前多久提醒”时才填写整数分钟，否则返回 null。
    7. importance 只能是 HIGH、MEDIUM、LOW 或 null。紧急、必须马上、快截止、重要紧急通常是 HIGH；普通任务通常是 MEDIUM；可选或不急通常是 LOW。
    8. repeatRule 只能是 NONE、DAILY、WEEKLY、MONTHLY、CUSTOM 或 null。用户明确说每天/每周/每月/重复时再填写；明确一次性任务填 NONE；不确定填 null。
    9. category 只能是 STUDY、HOMEWORK、COMPETITION、MEETING、LIFE、OTHER 或 null。
    10. confidence 是 0 到 1 的小数，表示你对整体提取结果的信心。
    11. title 必须保留 OCR 原文里的完整任务名称，不要因为前后多张图片标题相似而省略课程名、项目名前缀、编号或后缀。
    12. 如果原文是“计算机科学实验报告1 / 计算机科学实验报告2 / 计算机科学实验报告3”，title 必须分别返回完整的“计算机科学实验报告1”“计算机科学实验报告2”“计算机科学实验报告3”，不能返回“实验报告2”或“报告3”。
    13. 当标题行里同时包含前缀和编号时，以完整标题行为准；不要把后续图片当成上一张图片的续写。

    当前本地时间：
    $nowText

    当前本地时间戳（毫秒）：
    $nowMillis

    需要返回的 JSON 结构：
    {
      "title": "任务名称或 null",
      "description": "备注、提交要求、材料要求或 null",
      "startDateTimeText": "开始时间原文或 null",
      "deadlineDateTimeText": "截止时间原文或 null",
      "importance": "HIGH / MEDIUM / LOW / null",
      "repeatRule": "NONE / DAILY / WEEKLY / MONTHLY / CUSTOM / null",
      "category": "STUDY / HOMEWORK / COMPETITION / MEETING / LIFE / OTHER / null",
      "reminderMinutesBefore": 1440,
      "confidence": 0.0,
      "missingFields": ["缺失字段名"]
    }

    用户输入文本：
    $text
""".trimIndent()
}

class DeepSeekApiClient(
    private val httpClient: OkHttpClient
) {
    suspend fun parseTaskText(
        config: DeepSeekConfig,
        text: String
    ): Result<TaskDraft> = withContext(Dispatchers.IO) {
        runCatching {
            parseTaskTextDebug(config, text).getOrThrow().draft
        }
    }

    suspend fun parseTaskTextDebug(
        config: DeepSeekConfig,
        text: String
    ): Result<DeepSeekTaskParseDebugResult> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildDeepSeekTaskExtractionPrompt(text)
            val content = requestChat(config, prompt, maxTokens = 900)
            val extracted = extractJsonObject(content)
            val json = JSONObject(extracted)
            DeepSeekTaskParseDebugResult(
                prompt = prompt,
                rawResponse = content,
                extractedJson = json.toString(2),
                draft = json.toTaskDraft(text)
            )
        }
    }

    suspend fun testConnection(config: DeepSeekConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requestChat(config, "请只回复 JSON：{\"ok\":true}", maxTokens = 30)
            Unit
        }
    }

    private fun requestChat(config: DeepSeekConfig, userPrompt: String, maxTokens: Int): String {
        val baseUrl = config.baseUrl.trim().trimEnd('/')
        val body = JSONObject()
            .put("model", config.model.trim())
            .put("temperature", 0.1)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "你只返回 JSON，不输出解释。"))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
            )
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("DeepSeek request failed")
            }
            val root = JSONObject(responseText)
            return root
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private fun extractJsonObject(content: String): String {
        val trimmed = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("DeepSeek response is not JSON")
        return trimmed.substring(start, end + 1)
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return optDouble(name).takeIf { !it.isNaN() }
    }

    private fun JSONObject.optStringArray(name: String): List<String> {
        if (!has(name) || isNull(name)) return emptyList()
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.toTaskDraft(rawText: String): TaskDraft {
        return TaskDraft(
            title = expandTitleFromRaw(optStringOrNull("title"), rawText),
            description = optStringOrNull("description"),
            deadlineDateTime = DeadlineTextInterpreter.parse(optStringOrNull("deadlineDateTimeText")),
            deadlineDateTimeText = optStringOrNull("deadlineDateTimeText"),
            importance = optStringOrNull("importance")?.toEnumOrNull<ImportanceLevel>(),
            repeatRule = optStringOrNull("repeatRule")?.toEnumOrNull<RepeatRule>(),
            category = optStringOrNull("category")?.toEnumOrNull<TaskCategory>(),
            confidence = optDoubleOrNull("confidence"),
            missingFields = optStringArray("missingFields"),
            rawText = rawText
        )
    }

    private fun expandTitleFromRaw(title: String?, rawText: String): String? {
        val modelTitle = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val normalizedTitle = normalizeTitle(modelTitle)
        if (normalizedTitle.length < 2) return modelTitle
        val lines = rawText
            .lineSequence()
            .map { cleanTitleCandidate(it) }
            .filter { it.length in 3..48 }
            .filterNot { line ->
                listOf("截止", "时间", "提交", "提醒", "要求", "备注", "地点").any { line.contains(it) }
            }
            .toList()

        return lines.firstOrNull { line ->
            val normalizedLine = normalizeTitle(line)
            normalizedLine != normalizedTitle &&
                normalizedLine.contains(normalizedTitle) &&
                normalizedLine.length <= normalizedTitle.length + 18
        } ?: modelTitle
    }

    private fun cleanTitleCandidate(line: String): String {
        return line.trim()
            .replace(Regex("^(任务名称|任务|标题|题目|DDL|Deadline)\\s*[:：]\\s*"), "")
            .trim(' ', '\t', ':', '：', '。', '.', '，', ',', ';', '；')
    }

    private fun normalizeTitle(value: String): String {
        return value.lowercase()
            .replace(Regex("[\\s\\p{Punct}，。；：、（）()【】\\[\\]《》\"“”'‘’]+"), "")
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? {
        if (this == "null") return null
        return runCatching { enumValueOf<T>(this) }.getOrNull()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
