package com.pureenhance.ai.utilities

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager

enum class DeviceTier { LOW, MID, HIGH, ULTRA }

data class DeviceProfile(
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val tier: DeviceTier,
    val cores: Int,
) {
    /** "Maximum" quality is only offered on devices with >= ~6 GB RAM. */
    val maximumQualityAllowed: Boolean get() = tier >= DeviceTier.HIGH

    companion object {
        private const val GB = 1_073_741_824.0

        /** Reported totals are lower than the marketing number (4 GB phone ≈ 3.7 GB). */
        fun tierFor(totalBytes: Long): DeviceTier {
            val gb = totalBytes / GB
            return when {
                gb < 3.0 -> DeviceTier.LOW     // 2–3 GB
                gb < 5.0 -> DeviceTier.MID     // 4–5 GB
                gb < 7.0 -> DeviceTier.HIGH    // 6–7 GB
                else -> DeviceTier.ULTRA       // 8 GB+
            }
        }

        fun read(context: Context): DeviceProfile {
            val am = context.getSystemService(ActivityManager::class.java)
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            return DeviceProfile(
                totalRamBytes = info.totalMem,
                availRamBytes = info.availMem,
                tier = tierFor(info.totalMem),
                cores = Runtime.getRuntime().availableProcessors(),
            )
        }
    }
}

object ThermalGuard {
    /**
     * Returns how long to pause between tiles. Throws [EnhanceException.Overheated]
     * when the device reports a critical thermal state.
     */
    fun throttleMs(context: Context): Long {
        val pm = context.getSystemService(PowerManager::class.java) ?: return 0L
        val status = pm.currentThermalStatus
        return when {
            status >= PowerManager.THERMAL_STATUS_CRITICAL -> throw EnhanceException.Overheated()
            status >= PowerManager.THERMAL_STATUS_SEVERE -> 400L
            status >= PowerManager.THERMAL_STATUS_MODERATE -> 80L
            else -> 0L
        }
    }
}
