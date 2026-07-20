package com.uc0079.launcher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc0079.launcher.AppFolder
import com.uc0079.launcher.AppInfo

/** Opens the system uninstall screen; falls back to app info if blocked. */
fun Context.openUninstall(packageName: String) {
    val pkgUri = Uri.parse("package:$packageName")
    val delete = Intent(Intent.ACTION_DELETE, pkgUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { startActivity(delete) }.isSuccess) return

    @Suppress("DEPRECATION")
    val legacy = Intent(Intent.ACTION_UNINSTALL_PACKAGE, pkgUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
    if (runCatching { startActivity(legacy) }.isSuccess) return

    openAppInfo(packageName)
}

fun Context.openAppInfo(packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

@Composable
fun MenuDotsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = "\u22EE",
        color = G.Cyan,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .padding(6.dp),
    )
}

@Composable
fun AppActionSheet(
    app: AppInfo,
    isFavorite: Boolean,
    folders: List<AppFolder>,
    currentFolder: AppFolder?,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToFolder: () -> Unit,
    onRemoveFromFolder: () -> Unit,
) {
    val context = LocalContext.current
    var confirmUninstall by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.PanelStrong,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = app.label,
                    color = G.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column {
                ActionLine("\u25B6  起動", G.White) {
                    onDismiss()
                    onLaunch()
                }
                ActionLine(
                    if (isFavorite) "\u2605  お気に入りから外す" else "\u2605  お気に入りに追加",
                    if (isFavorite) G.Yellow else G.White
                ) {
                    onDismiss()
                    onToggleFavorite()
                }
                ActionLine("\u25A3  フォルダにしまう", G.White) {
                    onDismiss()
                    onAddToFolder()
                }
                if (currentFolder != null) {
                    ActionLine(
                        "\u25A3  フォルダから出す (${currentFolder.name})",
                        G.Cyan
                    ) {
                        onDismiss()
                        onRemoveFromFolder()
                    }
                }
                ActionLine("\u2139  アプリ情報", G.White) {
                    onDismiss()
                    context.openAppInfo(app.packageName)
                }
                Spacer(Modifier.height(8.dp))
                ActionLine("\u2715  アンインストール", G.Red) {
                    confirmUninstall = true
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる", color = G.Dim, fontFamily = FontFamily.Monospace)
            }
        }
    )

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            containerColor = G.PanelStrong,
            title = {
                Text("アンインストール", color = G.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "「${app.label}」を削除します。\n次の画面で「アンインストール」を押してください。",
                    color = G.Dim,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    onDismiss()
                    context.openUninstall(app.packageName)
                }) {
                    Text("削除する", color = G.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) {
                    Text("キャンセル", color = G.Dim)
                }
            }
        )
    }
}

@Composable
private fun ActionLine(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontSize = 16.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}

@Composable
fun FolderActionSheet(
    folder: AppFolder,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.PanelStrong,
        title = {
            Text(
                text = "\u25A3  ${folder.name}",
                color = G.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column {
                Text(
                    "${folder.packageNames.size} 個のアプリ",
                    color = G.Dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ActionLine("\u25B6  フォルダを開く", G.White) {
                    onDismiss()
                    onOpen()
                }
                ActionLine("\u270E  名前を変更", G.White) {
                    onDismiss()
                    onRename()
                }
                ActionLine("\u2715  フォルダを削除", G.Red) {
                    confirmDelete = true
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる", color = G.Dim, fontFamily = FontFamily.Monospace)
            }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = G.PanelStrong,
            title = { Text("フォルダ削除", color = G.White) },
            text = {
                Text(
                    "「${folder.name}」を削除します。\n中のアプリは A〜Z 一覧に戻ります。",
                    color = G.Dim,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDismiss()
                    onDelete()
                }) {
                    Text("削除", color = G.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("キャンセル", color = G.Dim)
                }
            }
        )
    }
}

@Composable
fun HelpSheet(onDismiss: () -> Unit, onOpenLauncherSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.PanelStrong,
        title = {
            Text("使い方", color = G.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        },
        text = {
            Column {
                HelpLine("タップ … アプリを起動")
                HelpLine("右の ⋮ … お気に入り・フォルダ・アンインストール")
                HelpLine("長押し … ⋮ と同じメニュー")
                HelpLine("▲ ALL UNITS … 全アプリ一覧")
                HelpLine("右端 A〜Z … 指でなぞってジャンプ")
                Spacer(Modifier.height(12.dp))
                ActionLine("\u2699  ホームアプリ（ランチャー）の変更", G.Cyan) {
                    onDismiss()
                    onOpenLauncherSettings()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる", color = G.Dim, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun HelpLine(text: String) {
    Text(
        text = text,
        color = G.Dim,
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
