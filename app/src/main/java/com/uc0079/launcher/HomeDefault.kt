package com.uc0079.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Detects whether Z GUNDAM OS is the actual Home target.
 *
 * After sideloaded APK updates, Android often drops the preferred-activity
 * entry for HOME. Home then opens the system chooser on every press until
 * the user picks again with "Always".
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

    /** Settings screen where the user can set / rebind the Home app. */
    fun repairIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}
