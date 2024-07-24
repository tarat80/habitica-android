package com.habitrpg.common.habitica.modelsCommon.auth

data class UserAuthSocial (
    val network: String? = null,
    val authResponse: UserAuthSocialTokens? = null
)
