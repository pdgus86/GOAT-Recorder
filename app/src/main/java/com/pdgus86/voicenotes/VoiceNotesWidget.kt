package com.pdgus86.voicenotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget: one big button. Tap to start recording, tap again
 * to stop. Works without opening the app. The button sends a broadcast to
 * [WidgetToggleReceiver], which flips the recording state.
 */
class VoiceNotesWidget : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, VoiceNotesWidget::class.java)
            )
            if (ids.isEmpty()) return
            val views = buildViews(context)
            manager.updateAppWidget(ids, views)
        }

        private fun buildViews(context: Context): RemoteViews {
            val recording = RecordingController.isRecording(context)
            val views = RemoteViews(context.packageName, R.layout.widget_recorder)
            views.setImageViewResource(
                R.id.widget_record_button,
                if (recording) R.drawable.ic_stop else R.drawable.ic_mic
            )
            views.setTextViewText(
                R.id.widget_status,
                if (recording) {
                    context.getString(R.string.widget_recording)
                } else {
                    context.getString(R.string.widget_tap_to_record)
                }
            )
            val toggle = Intent(context, WidgetToggleReceiver::class.java).apply {
                action = RecordingController.ACTION_TOGGLE_RECORDING
            }
            val pending = PendingIntent.getBroadcast(
                context, 0, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_record_button, pending)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = buildViews(context)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == RecordingController.ACTION_RECORDING_STATE_CHANGED) {
            updateAllWidgets(context)
        }
    }
}
