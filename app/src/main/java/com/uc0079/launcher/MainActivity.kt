package com.uc0079.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.uc0079.launcher.ui.LauncherApp

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private lateinit var widgets: WidgetHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgets = WidgetHostController(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LauncherApp(vm, widgets)
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleShareIntent(intent)) return
        // Home button while already in the launcher (e.g. all-apps screen).
        if (intent.hasCategory(Intent.CATEGORY_HOME) ||
            intent.action == Intent.ACTION_MAIN
        ) {
            vm.onHomeIntent()
        }
    }

    /** @return true if this was a share intent we handled. */
    private fun handleShareIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_SEND) return false
        if (intent.type?.startsWith("text/") != true) return false
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        vm.handleSharedText(text, subject)
        // Consume so rotation / resume doesn't re-add.
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(Intent.EXTRA_SUBJECT)
        return true
    }

    override fun onStart() {
        super.onStart()
        widgets.start()
    }

    override fun onStop() {
        super.onStop()
        widgets.stop()
    }

    override fun onResume() {
        super.onResume()
        // Reflect installs / uninstalls that happened while we were away.
        vm.refresh()
        vm.checkForUpdate()
    }
}
