package com.svce.issuegram.ui.components

import android.content.Context
import android.content.Intent
import com.svce.issuegram.R

fun shareIssue(context: Context, ticketId: String, title: String) {
    val text = context.getString(R.string.share_issue_message, ticketId, title)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null)
    if (context !is android.app.Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}
