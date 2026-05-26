package com.gzzz.tochat.data.repository

import android.content.Context
import android.net.Uri
import com.gzzz.tochat.data.local.KnowledgeChunkEntity
import com.gzzz.tochat.data.local.KnowledgeDao
import com.gzzz.tochat.data.local.KnowledgeDocumentEntity
import com.gzzz.tochat.util.FileTextExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val KNOWLEDGE_STATUS_INDEXING = "indexing"
const val KNOWLEDGE_STATUS_READY = "ready"
const val KNOWLEDGE_STATUS_FAILED = "failed"

data class KnowledgeSnippet(
    val documentId: String,
    val documentTitle: String,
    val fileName: String,
    val chunkIndex: Int,
    val text: String,
    val score: Double
)

@Singleton
class KnowledgeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val knowledgeDao: KnowledgeDao
) {
    fun observeDocuments(): Flow<List<KnowledgeDocumentEntity>> = knowledgeDao.observeDocuments()

    suspend fun importDocument(uri: Uri): Result<KnowledgeDocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val extracted = FileTextExtractor.extractText(context, uri, MAX_DOCUMENT_CHARS).getOrThrow()
            val normalizedText = normalizeText(extracted.text)
            if (normalizedText.isBlank()) {
                throw IllegalStateException("文件内容为空")
            }

            val hash = sha256(normalizedText)
            knowledgeDao.getDocumentByHash(hash)?.let { return@runCatching it }

            val now = System.currentTimeMillis()
            val documentId = UUID.randomUUID().toString()
            val title = extracted.fileName.substringBeforeLast('.', extracted.fileName)
            val indexingDocument = KnowledgeDocumentEntity(
                id = documentId,
                title = title,
                fileName = extracted.fileName,
                mimeType = extracted.mimeType,
                contentHash = hash,
                charCount = normalizedText.length,
                chunkCount = 0,
                status = KNOWLEDGE_STATUS_INDEXING,
                errorMessage = null,
                createdAt = now,
                updatedAt = now
            )
            knowledgeDao.insertDocument(indexingDocument)

            try {
                val chunks = chunkText(documentId, normalizedText, now)
                if (chunks.isEmpty()) {
                    throw IllegalStateException("文件内容过短，无法建立知识库")
                }
                knowledgeDao.insertChunks(chunks)
                val readyDocument = indexingDocument.copy(
                    chunkCount = chunks.size,
                    status = KNOWLEDGE_STATUS_READY,
                    updatedAt = System.currentTimeMillis()
                )
                knowledgeDao.updateDocument(readyDocument)
                readyDocument
            } catch (e: Exception) {
                knowledgeDao.updateDocument(
                    indexingDocument.copy(
                        status = KNOWLEDGE_STATUS_FAILED,
                        errorMessage = e.message ?: "导入失败",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                throw e
            }
        }
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        knowledgeDao.deleteChunksByDocumentId(id)
        knowledgeDao.deleteDocument(id)
    }

    suspend fun retrieve(
        query: String,
        selectedDocumentIds: Set<String>,
        topK: Int = 5,
        maxContextChars: Int = 6000
    ): List<KnowledgeSnippet> = withContext(Dispatchers.IO) {
        if (query.isBlank() || selectedDocumentIds.isEmpty()) return@withContext emptyList()

        val documents = knowledgeDao.getDocumentsByIds(selectedDocumentIds.toList())
            .filter { it.status == KNOWLEDGE_STATUS_READY }
        if (documents.isEmpty()) return@withContext emptyList()

        val chunks = knowledgeDao.getChunksByDocumentIds(documents.map { it.id })
        if (chunks.isEmpty()) return@withContext emptyList()

        val queryTokens = tokenize(query).distinct()
        if (queryTokens.isEmpty()) return@withContext emptyList()

        val chunkTokens = chunks.associateWith { tokenize(it.text) }
        val totalChunks = chunks.size.toDouble()
        val avgLength = chunkTokens.values.map { it.size }.average().takeIf { it > 0.0 } ?: 1.0
        val documentFrequency = queryTokens.associateWith { token ->
            chunkTokens.values.count { tokens -> token in tokens }.coerceAtLeast(1)
        }
        val documentsById = documents.associateBy { it.id }
        val normalizedQuery = query.trim().lowercase()

        val scored = chunks.mapNotNull { chunk ->
            val tokens = chunkTokens.getValue(chunk)
            if (tokens.isEmpty()) return@mapNotNull null

            val frequencies = tokens.groupingBy { it }.eachCount()
            var score = 0.0
            for (token in queryTokens) {
                val tf = frequencies[token] ?: 0
                if (tf == 0) continue
                val df = documentFrequency.getValue(token).toDouble()
                val idf = ln(1.0 + (totalChunks - df + 0.5) / (df + 0.5))
                val tfNorm = tf * (BM25_K1 + 1.0) /
                    (tf + BM25_K1 * (1.0 - BM25_B + BM25_B * tokens.size / avgLength))
                score += idf * tfNorm
            }

            val document = documentsById[chunk.documentId] ?: return@mapNotNull null
            if (chunk.text.lowercase().contains(normalizedQuery)) score += 2.0
            if (queryTokens.any { document.title.lowercase().contains(it) || document.fileName.lowercase().contains(it) }) {
                score += 0.2
            }
            if (score <= 0.0) return@mapNotNull null

            KnowledgeSnippet(
                documentId = chunk.documentId,
                documentTitle = document.title,
                fileName = document.fileName,
                chunkIndex = chunk.chunkIndex,
                text = chunk.text,
                score = score
            )
        }.sortedByDescending { it.score }

        val perDocumentCount = mutableMapOf<String, Int>()
        val result = mutableListOf<KnowledgeSnippet>()
        var usedChars = 0
        for (snippet in scored) {
            if (result.size >= topK) break
            val count = perDocumentCount[snippet.documentId] ?: 0
            if (count >= MAX_CHUNKS_PER_RESULT_DOCUMENT) continue
            if (usedChars + snippet.text.length > maxContextChars && result.isNotEmpty()) continue
            result += snippet
            perDocumentCount[snippet.documentId] = count + 1
            usedChars += snippet.text.length
        }
        result
    }

    private fun chunkText(documentId: String, text: String, createdAt: Long): List<KnowledgeChunkEntity> {
        val chunks = mutableListOf<KnowledgeChunkEntity>()
        var start = 0
        var index = 0
        while (start < text.length && chunks.size < MAX_CHUNKS_PER_DOCUMENT) {
            val end = findChunkEnd(text, start)
            val chunk = text.substring(start, end).trim()
            if (chunk.length >= MIN_CHUNK_CHARS) {
                chunks += KnowledgeChunkEntity(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    chunkIndex = index,
                    text = chunk,
                    charStart = start,
                    charEnd = end,
                    tokenCount = tokenize(chunk).size,
                    createdAt = createdAt
                )
                index++
            }
            if (end >= text.length) break
            start = (end - CHUNK_OVERLAP_CHARS).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private fun findChunkEnd(text: String, start: Int): Int {
        val hardEnd = (start + CHUNK_SIZE_CHARS).coerceAtMost(text.length)
        if (hardEnd >= text.length) return text.length
        val paragraphEnd = text.lastIndexOf("\n\n", hardEnd).takeIf { it > start + MIN_CHUNK_CHARS }
        if (paragraphEnd != null) return paragraphEnd + 2
        val lineEnd = text.lastIndexOf('\n', hardEnd).takeIf { it > start + MIN_CHUNK_CHARS }
        if (lineEnd != null) return lineEnd + 1
        val punctuationEnd = text.lastIndexOfAny(charArrayOf('。', '！', '？', '.', '!', '?'), hardEnd)
            .takeIf { it > start + MIN_CHUNK_CHARS }
        if (punctuationEnd != null) return punctuationEnd + 1
        return hardEnd
    }

    private fun normalizeText(text: String): String = text
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(Regex("[ \t]+"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val lower = text.lowercase()
        Regex("[a-z0-9]+", RegexOption.IGNORE_CASE).findAll(lower).forEach { match ->
            val token = match.value
            if (token.length > 1 && token !in STOP_WORDS) tokens += token
        }

        val cjkChars = lower.filter { it.isCjk() }
        cjkChars.forEach { char ->
            val token = char.toString()
            if (token !in STOP_WORDS) tokens += token
        }
        for (i in 0 until cjkChars.length - 1) {
            tokens += cjkChars.substring(i, i + 2)
        }
        return tokens
    }

    private fun Char.isCjk(): Boolean = this in '一'..'鿿'

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_DOCUMENT_CHARS = 200_000
        private const val CHUNK_SIZE_CHARS = 1000
        private const val CHUNK_OVERLAP_CHARS = 150
        private const val MAX_CHUNKS_PER_DOCUMENT = 250
        private const val MIN_CHUNK_CHARS = 40
        private const val MAX_CHUNKS_PER_RESULT_DOCUMENT = 2
        private const val BM25_K1 = 1.2
        private const val BM25_B = 0.75

        private val STOP_WORDS = setOf(
            "the", "and", "or", "to", "of", "in", "is", "are", "a", "an",
            "的", "了", "是", "在", "和", "与", "及", "或", "就", "都"
        )
    }
}
