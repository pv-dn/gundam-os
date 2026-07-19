package com.uc0079.launcher

data class AppFolder(
    val id: String,
    val name: String,
    val packageNames: List<String> = emptyList()
)
