package com.habitrpg.common.habitica.modelsCommon.notifications

open class AchievementData : NotificationData {
    val isLastOnboardingAchievement: Boolean = false
    var achievement: String? = null
    var message: String? = null
    var modalText: String? = null
}
