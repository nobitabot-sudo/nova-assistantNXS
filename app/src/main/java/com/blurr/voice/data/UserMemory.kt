package com.blurr.voice.data

import java.util.Date

data class UserMemory(
    val id: String = "",
    val text: String = "",
    val source: String = "User",
    val createdAt: Date = Date()
) {
    constructor() : this("", "", "User", Date())
}
