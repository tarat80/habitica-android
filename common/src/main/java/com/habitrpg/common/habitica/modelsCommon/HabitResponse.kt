package com.habitrpg.common.habitica.modelsCommon

class HabitResponse<T> {
    var data: T? = null
    var notifications: List<Notification>? = null
    var success: Boolean? = null
    var message: String? = null
}
