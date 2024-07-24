package com.habitrpg.common.habitica.modelsCommon.auth

data class UserAuth(
    val username: String="",
    val password: String="",
    val confirmPassword: String="",
    val email: String=""
)
