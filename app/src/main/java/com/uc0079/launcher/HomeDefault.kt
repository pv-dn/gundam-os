package com.uc0079.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Home binding for stock Android (Pixel 9 Pro and similar).
 *
 * On Pixel, [RoleManager] ROLE_HOME is the supported way to become the
 * default launcher. Preferred-activity is still checked for the Home button.
 * No FakeHome toggle (that fights Pixel's role model).
 */
object HomeDefault {

    const val ACTION_REQUEST_ROLE = "android.app.role.action.REQUEST_ROLE"
    private const val ROLE_HOME = "android.app.role.HOME"

    fun isPixelDevice(): Boolean {
        val mfr = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val model = Build.MODEL.orEmpty()
        return (mfr.equals("Google", true) || brand.equals("google", true)) &&
            model.contains("Pixel", ignoreCase = true)
    }

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
        return roleCall(context) { rm ->
            val available = rm.javaClass.getMethod("isRoleAvailable", String::class.java)
                .invoke(rm, ROLE_HOME) as Boolean
            val held = rm.javaClass.getMethod("isRoleHeld", String::class.java)
                .invoke(rm, ROLE_HOME) as Boolean
            available && held
        } ?: false
    }

    /**
     * Pixel path: request ROLE_HOME when not held (system confirmation UI).
     * If role already held but preferred is stale → Home settings.
     * Non-Pixel: same logic (Role still best on stock-ish Android 10+).
     */
    fun repairIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isHomeRoleHeld(context)) {
            val request = roleCall(context) { rm ->
                val available = rm.javaClass.getMethod("isRoleAvailable", String::class.java)
                    .invoke(rm, ROLE_HOME) as Boolean
                if (!available) return@roleCall null
                rm.javaClass.getMethod("createRequestRoleIntent", String::class.java)
                    .invoke(rm, ROLE_HOME) as Intent
            }
            if (request != null) return request
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun isRoleRequestIntent(intent: Intent): Boolean =
        intent.action == ACTION_REQUEST_ROLE

    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    private fun <T> roleCall(context: Context, block: (Any) -> T): T? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val rm = context.getSystemService(Context.ROLE_SERVICE) ?: return null
            block(rm)
        }.getOrNull()
    }
}
