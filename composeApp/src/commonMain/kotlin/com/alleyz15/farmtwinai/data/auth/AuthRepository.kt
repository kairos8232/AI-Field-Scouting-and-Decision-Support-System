package com.alleyz15.farmtwinai.data.auth

import com.alleyz15.farmtwinai.auth.AuthUser

interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthUser

    suspend fun signUp(email: String, password: String, displayName: String): AuthUser
}
