package com.alleyz15.farmtwinai

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform