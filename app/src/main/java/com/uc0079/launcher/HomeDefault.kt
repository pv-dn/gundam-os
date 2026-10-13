package com.uc0079.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Home-app binding helpers.
 *
 * After sideload updates, Android often drops the preferred-activity entry for
 * HOME while ROLE_HOME can still look held. Home then shows the system chooser
 * on every press. Role request no-ops when the role is already held — the
 * reliable repair is [forceSystemHomeChooser] (FakeHome toggle).
 */
object HomeDefault {

    const val ACTION_REQUEST_ROLE = "android.app.role.action.REQUEST_ROLE"
    private const val ROLE_HOME = "android.app.role.HOME"

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
            name.contains("Chooser", ignoreCase = true) ||
            name.contains("FakeHome", ignoreCase = true)
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

    fun isHomeBindingStale(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            isHomeRoleHeld(context) &&
            !isPreferredHomeActivity(context)

    /**
     * Prefer RoleManager when the role is not held yet.
     * When role is held but preferred is gone (stale), Role request is a no-op —
     * caller should use [forceSystemHomeChooser] instead.
     */
    fun roleRequestIntentOrNull(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (isHomeRoleHeld(context)) return null
        return roleCall(context) { rm, cls ->
            val available = cls.getMethod("isRoleAvailable", String::class.java)
                .invoke(rm, ROLE_HOME) as Boolean
            if (!available) return@roleCall null
            cls.getMethod("createRequestRoleIntent", String::class.java)
                .invoke(rm, ROLE_HOME) as Intent
        }
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    fun isRoleRequestIntent(intent: Intent): Boolean =
        intent.action == ACTION_REQUEST_ROLE

    /**
     * Temporarily enables [FakeHomeActivity] so Android drops the preferred HOME
     * entry, then starts the Home intent (native Always / Just once UI).
     * FakeHome is always disabled again in finally.
     */
    fun forceSystemHomeChooser(context: Context) {
        ensureFakeHomeDisabled(context)
        val pm = context.packageManager
        val fake = ComponentName(context, FakeHomeActivity::class.java)
        pm.setComponentEnabledSetting(
            fake,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } finally {
            pm.setComponentEnabledSetting(
                fake,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /** Safety: if FakeHome was left enabled, turn it off (avoids perpetual chooser). */
    fun ensureFakeHomeDisabled(context: Context) {
        val pm = context.packageManager
        val fake = ComponentName(context, FakeHomeActivity::class.java)
        val state = pm.getComponentEnabledSetting(fake)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(
                fake,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun <T> roleCall(context: Context, block: (Any, Class<*>) -> T): T? {
        return runCatching {
            val cls = Class.forName("android.app.RoleManager")
            val rm = context.getSystemService(cls) ?: return null
            block(rm, cls)
        }.getOrNull()
    }
}
