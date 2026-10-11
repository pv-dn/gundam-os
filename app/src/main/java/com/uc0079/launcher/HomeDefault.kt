package com.uc0079.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Home-app binding helpers.
 *
 * Sideloaded APK updates often clear the preferred HOME activity. On Android 10+
 * the sticky fix is ROLE_HOME (RoleManager); Settings alone can look set while
 * the Home button still shows the system chooser.
 */
object HomeDefault {

    /** [RoleManager.createRequestRoleIntent] action. */
    const val ACTION_REQUEST_ROLE = "android.app.role.action.REQUEST_ROLE"

    private const val ROLE_HOME = "android.app.role.HOME"

    fun isDefaultHome(context: Context): Boolean {
        // Preferred activity is what the Home button actually uses.
        if (isPreferredHomeActivity(context)) return true
        // Some OEMs resolve late; role held is still a strong signal.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isHomeRoleHeld(context)
    }

    /**
     * True when Home has no usable default (chooser will appear on Home press).
     * Role held but preferred cleared counts as broken.
     */
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

    fun isHomeRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return roleCall(context) { rm, cls ->
            val available = cls.getMethod("isRoleAvailable", String::class.java)
                .invoke(rm, ROLE_HOME) as Boolean
            val held = cls.getMethod("isRoleHeld", String::class.java)
                .invoke(rm, ROLE_HOME) as Boolean
            available && held
        } ?: false
    }

    /**
     * Best UI to rebind Home:
     * - Role not held → RoleManager request (sticks on Android 10+)
     * - Role held / old Android / role unavailable → Home settings
     */
    fun bestRepairIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isHomeRoleHeld(context)) {
            val request = roleCall(context) { rm, cls ->
                val available = cls.getMethod("isRoleAvailable", String::class.java)
                    .invoke(rm, ROLE_HOME) as Boolean
                if (!available) return@roleCall null
                cls.getMethod("createRequestRoleIntent", String::class.java)
                    .invoke(rm, ROLE_HOME) as Intent
            }
            if (request != null) return request
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun isRoleRequestIntent(intent: Intent): Boolean =
        intent.action == ACTION_REQUEST_ROLE

    private fun <T> roleCall(context: Context, block: (Any, Class<*>) -> T): T? {
        return runCatching {
            val cls = Class.forName("android.app.RoleManager")
            val rm = context.getSystemService(cls) ?: return null
            block(rm, cls)
        }.getOrNull()
    }
}
