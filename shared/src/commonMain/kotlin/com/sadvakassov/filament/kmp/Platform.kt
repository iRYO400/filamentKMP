package com.sadvakassov.filament.kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform