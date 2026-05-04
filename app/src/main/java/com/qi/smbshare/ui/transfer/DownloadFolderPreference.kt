package com.qi.smbshare.ui.transfer

import android.content.Context
import android.net.Uri

private const val DOWNLOAD_TREE_PREF = "transfer_manager_prefs"
private const val KEY_DOWNLOAD_TREE_URI = "download_tree_uri"

internal fun getPersistedDownloadTreeUri(context: Context): Uri? {
    val prefs = context.getSharedPreferences(DOWNLOAD_TREE_PREF, Context.MODE_PRIVATE)
    val uriString = prefs.getString(KEY_DOWNLOAD_TREE_URI, null)
    return uriString?.let { Uri.parse(it) }
}

internal fun persistDownloadTreeUri(context: Context, uri: Uri) {
    val prefs = context.getSharedPreferences(DOWNLOAD_TREE_PREF, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_DOWNLOAD_TREE_URI, uri.toString()).apply()
}

internal fun clearPersistedDownloadTreeUri(context: Context) {
    val prefs = context.getSharedPreferences(DOWNLOAD_TREE_PREF, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_DOWNLOAD_TREE_URI).apply()
}

internal fun hasPersistedDownloadTreePermission(context: Context, uri: Uri): Boolean {
    return context.contentResolver.persistedUriPermissions.any { persisted ->
        persisted.uri == uri && persisted.isReadPermission
    }
}
