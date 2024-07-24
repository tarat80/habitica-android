package com.habitrpg.common.habitica.modelsCommon.auth

@Suppress("PropertyName")
data class UserAuthSocialTokens(
    var client_id: String? = null,
    var access_token: String? = null
)
