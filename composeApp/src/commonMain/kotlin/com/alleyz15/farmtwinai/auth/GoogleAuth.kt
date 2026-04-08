package com.alleyz15.farmtwinai.auth

data class AuthUser(
    val userId: String,
    val email: String,
    val displayName: String?,
    val idToken: String?,
)
