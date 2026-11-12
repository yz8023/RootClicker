package com.roottap.clicker.data

import android.os.Build
import androidx.compose.runtime.Immutable

/** Device summary shown on the home page. */
@Immutable
data class DeviceInfo(
    val model: String,
    val androidVersion: String,
    val sdkLevel: Int,
)

/** Source of [DeviceInfo]. */
fun interface DeviceInfoProvider {
    suspend fun load(): DeviceInfo
}

/** Reads [DeviceInfo] from the platform build constants. */
class AndroidDeviceInfoProvider : DeviceInfoProvider {
    override suspend fun load(): DeviceInfo = DeviceInfo(
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = Build.VERSION.RELEASE,
        sdkLevel = Build.VERSION.SDK_INT,
    )
}
