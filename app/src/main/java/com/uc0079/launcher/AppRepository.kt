package com.uc0079.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object AppRepository {

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val myPackage = context.packageName

        return resolveInfos
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == myPackage) return@mapNotNull null
                val label = ri.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                val icon = runCatching { ri.loadIcon(pm).toImageBitmap() }.getOrNull()
                    ?: return@mapNotNull null
                AppInfo(label = label, packageName = pkg, icon = icon)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun Drawable.toImageBitmap(fallbackSize: Int = 144): ImageBitmap {
        if (this is BitmapDrawable) {
            bitmap?.let { return it.asImageBitmap() }
        }
        val width = if (intrinsicWidth > 0) intrinsicWidth else fallbackSize
        val height = if (intrinsicHeight > 0) intrinsicHeight else fallbackSize
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bmp.asImageBitmap()
    }
}
