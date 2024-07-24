package com.habitrpg.android.habitica.apiService

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.habitrpg.common.habitica.BuildConfig
import com.habitrpg.common.habitica.helpersCommon.KeyHelper

class HostConfig {
    var address: String
    var port: String
    var apiKey: String
    var userID: String

    constructor(userID: String, apiKey: String) {
        this.port = BuildConfig.PORT
        this.address = BuildConfig.BASE_URL
        this.userID = userID
        this.apiKey = apiKey
    }

    constructor(sharedPreferences: SharedPreferences, keyHelper: KeyHelper?, context: Context) {
        this.port = BuildConfig.PORT
        if (BuildConfig.DEBUG) {
            this.address = BuildConfig.BASE_URL
            if (BuildConfig.TEST_USER_ID.isNotBlank()) {
                userID = BuildConfig.TEST_USER_ID
                apiKey = BuildConfig.TEST_USER_KEY
                return
            }
        } else {
            val address = sharedPreferences.getString("server_url", null)
            if (!address.isNullOrEmpty()) {
                this.address = address
            } else {
                this.address = context.getString(com.habitrpg.common.habitica.R.string.base_url)
            }
        }
        this.userID = sharedPreferences.getString(context.getString(com.habitrpg.common.habitica.R.string.SP_userID), null) ?: ""
        this.apiKey = loadAPIKey(sharedPreferences, keyHelper)
    }

    private fun loadAPIKey(
        sharedPreferences: SharedPreferences,
        keyHelper: KeyHelper?,
    ): String {
        return if (sharedPreferences.contains(userID)) {
            val encryptedKey = sharedPreferences.getString(userID, null) ?: ""

            if (encryptedKey.isNotBlank()) { keyHelper?.decrypt(encryptedKey) } else ""

        } else {
            val aPIToken = sharedPreferences.getString("APIToken", null) ?: ""
            if (aPIToken.isNotBlank() ) {
                sharedPreferences.edit {
                    putString(userID, keyHelper?.encrypt(aPIToken))
                    remove("APIToken")
                }
            }
            aPIToken
        } ?: ""
    }

    constructor(address: String, port: String, api: String, user: String) {
        this.address = address
        this.port = port
        this.apiKey = api
        this.userID = user
    }

    fun hasAuthentication(): Boolean {
        return userID.isNotEmpty() && apiKey.isNotEmpty()
    }
}
