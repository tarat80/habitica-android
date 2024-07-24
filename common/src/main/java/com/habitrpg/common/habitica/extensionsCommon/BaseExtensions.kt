package com.habitrpg.common.habitica.extensionsCommon

import com.habitrpg.common.habitica.helpersCommon.launchCatching
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Created by phillip on 01.02.18.
 */

fun runDelayed(
    interval: Long,
    timeUnit: DurationUnit,
    function: () -> Unit,
) {
    MainScope().launchCatching {
        delay(interval.toDuration(timeUnit))
        function()
    }
}
