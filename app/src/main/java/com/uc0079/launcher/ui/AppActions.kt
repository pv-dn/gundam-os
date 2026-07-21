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
import com.uc0079.launcher.UpdateChecker

/** Opens the system uninstall screen; falls back to app info if blocked. */
fun Context.openUninstall(packageName: String) {
    val pkgUri = Uri.parse("package:$packageName")
    fun launch(intent: Intent): Boolean {
        // Activity context: avoid NEW_TASK (some OEMs drop the uninstall UI).
        // Application context: NEW_TASK is required.
        if (this !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { startActivity(intent) }.isSuccess
    }

    val delete = Intent(Intent.ACTION_DELETE).apply {
        data = pkgUri
        addCategory(Intent.CATEGORY_DEFAULT)
    }
    if (launch(delete)) return

    @Suppress("DEPRECATION")
    val legacy = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
        data = pkgUri
        addCategory(Intent.CATEGORY_DEFAULT)
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
    if (launch(legacy)) return

    // Last resort: app info screen (user can tap Uninstall there).
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
    displayName: String,
    isFavorite: Boolean,
    folders: List<AppFolder>,
    currentFolder: AppFolder?,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToFolder: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Single dialog only  Enested AlertDialogs break uninstall on some OEMs.
    var confirmUninstall by remember { mutableStateOf(false) }

    if (!confirmUninstall) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = G.Dialog,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = app.icon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = displayName,
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
                    ActionLine("\u25B6  起勁E, G.White) {
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
                    if (onMoveUp != null) {
                        ActionLine("\u25B2  お気に入りで上へ", G.Cyan) {
                            onDismiss()
                            onMoveUp()
                        }
                    }
                    if (onMoveDown != null) {
                        ActionLine("\u25BC  お気に入りで下へ", G.Cyan) {
                            onDismiss()
                            onMoveDown()
                        }
                    }
                    ActionLine("\u270E  名前を変更", G.White) {
                        onDismiss()
                        onRename()
                    }
                    ActionLine("\u25A3  フォルダにしまぁE, G.White) {
                        onDismiss()
                        onAddToFolder()
                    }
                    if (currentFolder != null) {
                        ActionLine(
                            "\u25A3  フォルダから出ぁE(${currentFolder.name})",
                            G.Cyan
                        ) {
                            onDismiss()
                            onRemoveFromFolder()
                        }
                    }
                    ActionLine("\u2139  アプリ惁E��", G.White) {
                        onDismiss()
                        context.openAppInfo(app.packageName)
                    }
                    Spacer(Modifier.height(8.dp))
                    ActionLine("\u2715  アンインスト�Eル", G.Red) {
                        confirmUninstall = true
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("閉じめE, color = G.Dim, fontFamily = FontFamily.Monospace)
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            containerColor = G.Dialog,
            title = {
                Text("アンインスト�Eル", color = G.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "、E{displayName}」を削除します、En次の画面で「アンインスト�Eル」を押してください、E,
                    color = G.Dim,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val pkg = app.packageName
                    onDismiss()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        context.openUninstall(pkg)
                    }
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
        containerColor = G.Dialog,
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
                    "${folder.packageNames.size} 個�Eアプリ",
                    color = G.Dim,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ActionLine("\u25B6  フォルダを開ぁE, G.White) {
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
                Text("閉じめE, color = G.Dim, fontFamily = FontFamily.Monospace)
            }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = G.Dialog,
            title = { Text("フォルダ削除", color = G.White) },
            text = {
                Text(
                    "、E{folder.name}」を削除します、En中のアプリは A〜Z 一覧に戻ります、E,
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
fun HelpSheet(
    onDismiss: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onCheckUpdate: () -> Unit,
    versionLabel: String,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.Dialog,
        title = {
            Text("使ぁE��", color = G.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        },
        text = {
            Column {
                Text(
                    "ぁE��の牁E $versionLabel",
                    color = G.Cyan,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HelpLine("タチE�E … アプリを起勁E)
                HelpLine("右の ⋮ … 名前変更・お気に入り�Eフォルダ・アンインスト�Eル")
                HelpLine("長押ぁE… ⋮ と同じメニュー")
                HelpLine("お気に入り�E ⋮ … 上へ�E�下へで頁E��変更")
                HelpLine("セクション右の �E�E… ウィジェチE���E�フォルダ追加")
                HelpLine("右丁EMENU … 使ぁE��・更新確認�Eランチャー設宁E)
                HelpLine("UPDATE 表示 … タチE�EでダウンローチE)
                HelpLine("▲ ALL UNITS … 全アプリ一覧")
                HelpLine("右端 A〜Z … かな�E�漢字もローマ字頭斁E��で刁E��E)
                Spacer(Modifier.height(12.dp))
                ActionLine("\u21BB  更新を確誁E, G.Cyan) {
                    onDismiss()
                    onCheckUpdate()
                }
                ActionLine("\u2699  ホ�Eムアプリ�E�ランチャー�E��E変更", G.Cyan) {
                    onDismiss()
                    onOpenLauncherSettings()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じめE, color = G.Dim, fontFamily = FontFamily.Monospace)
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

// ─── Update dialog ─────────────────────────────────────────────────────────

@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: (apkUrl: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = G.Dialog,
        title = {
            Text(
                "UPDATE AVAILABLE",
                color = G.Cyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column {
                Text(
                    info.message,
                    color = G.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "新しいバ�Eジョンが利用可能です、Enダウンロードしてインスト�Eルしますか�E�E,
                    color = G.Dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(info.apkUrl); onDismiss() }) {
                Text("ダウンローチE, color = G.Cyan, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("後で", color = G.Dim, fontFamily = FontFamily.Monospace)
            }
        }
    )
}
