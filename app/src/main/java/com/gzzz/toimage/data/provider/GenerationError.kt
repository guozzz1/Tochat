package com.gzzz.toimage.data.provider

sealed class GenerationError {
    object NetworkUnavailable : GenerationError()
    data class ApiError(val code: Int, val message: String) : GenerationError()
    object Timeout : GenerationError()
    object ContentRejected : GenerationError()
    object Cancelled : GenerationError()
    data class Unknown(val throwable: Throwable) : GenerationError()
}

fun GenerationError.toChineseMessage(): String = when (this) {
    is GenerationError.NetworkUnavailable -> "网络不可用，请检查网络连接"
    is GenerationError.ApiError -> when (code) {
        429 -> "请求过于频繁，请稍后再试"
        else -> "请求失败：$message"
    }
    is GenerationError.Timeout -> "生成超时，请重试"
    is GenerationError.ContentRejected -> "内容不合规，请修改 Prompt"
    is GenerationError.Cancelled -> ""
    is GenerationError.Unknown -> "未知错误：${throwable.localizedMessage}"
}
