package com.dhyan.app.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ShareToDeskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (savedInstanceState != null || intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return
            val title = (intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE))?.toString()?.take(1024)
            val item = if (text.length <= 16_384) {
                ShareToDeskManager.parseIncomingShare(text, title, System.currentTimeMillis())
            } else null
            val manager = ShareToDeskManager(applicationContext)
            val saved = item != null && manager.save(item)
            if (saved) manager.scheduleSync()
            Toast.makeText(this, if (saved) "Saved to Hunch Desk — queued for delivery"
                else "Could not save shared text", Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }
}
