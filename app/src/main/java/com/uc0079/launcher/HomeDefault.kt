package com.uc0079.launcher

import android.app.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Detects whether Z GUNDAM OS is the actual Home target.
 *
 * After sideloaded APK updates, Android often drops the preferred-activity
 * entry for HOME while ROLE_HOME can still look "held". Home then opens the
 * system chooser on every press until the user picks again.
 */
object HomeDefault {

    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) ?: return false
        val pkg = resolved.activityInfo?.packageName ?: return false
        // No default → ResolverActivity / android package.
        if (pkg == "android" ||
            resolved.activityInfo.name.contains("Resolver", ignoreCase = true)
        ) {
            return false
        }
        return pkg == context.packageName
    }

    fun holdsHomeRole(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    /** True when role says we're Home but resolveActivity disagrees (stale). */
    fun isHomeBindingStale(context: Context): Boolean =
        holdsHomeRole(context) && !isDefaultHome(context)

    /**
     * Best intent to repair Home binding.
     * Prefer Settings when stale (RoleManager no-ops if role already held).
     * Otherwise RoleManager request on Q+, else Home settings.
     */
    fun repairIntent(context: Context): Intent {
        if (isHomeBindingStale(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Intent(Settings.ACTION_HOME_SETTINGS)
        }
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null &&
            rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !rm.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
