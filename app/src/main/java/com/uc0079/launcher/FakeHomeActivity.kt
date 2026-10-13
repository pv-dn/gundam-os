package com.uc0079.launcher

import android.app.Activity
import android.os.Bundle

/**
 * Disabled-by-default HOME stub. Briefly enabled only to invalidate a stale
 * preferred-HOME entry so the system shows Always / Just once again.
 * Must never remain enabled (that would cause the chooser on every Home press).
 */
class FakeHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
