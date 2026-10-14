package com.uc0079.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Detects whether Z GUNDAM OS is the preferred Home target.
 * Repair opens the system Home-app settings only (no FakeHome toggle).
 */
object HomeDefault {

    fun needsRepair(context: Context): Boolean = !isPreferredHomeActivity(context)

    fun isPreferredHomeActivity(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) ?: return false
        val info = resolved.activityInfo ?: return false
        val pkg = info.packageName ?: return false
        if (pkg == "android") return false
        val name = info.name.orEmpty()
        if (name.contains("Resolver", ignoreCase = true) ||
            name.contains("Chooser", ignoreCase = true)
        ) {
            return false
        }
        return pkg == context.packageName
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}
