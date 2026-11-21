package com.douyintv.app.model

import java.io.Serializable

data class DouyinVideo(
    val awemeId: String,
    val desc: String,
    val authorName: String,
    val coverUrl: String,
    val playUrl: String,
    val durationMs: Long,
    val diggCount: Long
) : Serializable
