package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.R

class ChibiPetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChibiPetWidget()
}

class ChibiPetWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val petName = prefs[KEY_PET_NAME] ?: context.getString(R.string.default_pet_name)
            val level = prefs[KEY_LEVEL] ?: 1L
            val mood = prefs[KEY_MOOD] ?: 85L
            val completed = prefs[KEY_COMPLETED_TODAY] ?: 0
            val total = prefs[KEY_TOTAL_HABITS] ?: 0
            val topStreak = prefs[KEY_TOP_STREAK] ?: 0L

            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(20.dp)
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.splash_logo),
                            contentDescription = context.getString(R.string.app_name),
                            modifier = GlanceModifier.size(44.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(10.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = context.getString(R.string.widget_pet_level, petName, level.toInt()),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = context.getString(R.string.widget_mood_streak, mood.toInt(), topStreak.toInt()),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (total > 0) {
                                context.getString(R.string.widget_habits_today, completed, total)
                            } else {
                                context.getString(R.string.widget_tap_open)
                            },
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                            maxLines = 1
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Button(
                            text = context.getString(R.string.widget_snap_button),
                            onClick = actionStartActivity<MainActivity>()
                        )
                    }
                }
            }
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        val fallback = RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
            setTextViewText(android.R.id.text1, context.getString(R.string.widget_fallback_text))
        }
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
    }

    companion object {
        val KEY_PET_NAME = stringPreferencesKey("widget_pet_name")
        val KEY_LEVEL = longPreferencesKey("widget_level")
        val KEY_MOOD = longPreferencesKey("widget_mood")
        val KEY_COMPLETED_TODAY = intPreferencesKey("widget_completed_today")
        val KEY_TOTAL_HABITS = intPreferencesKey("widget_total_habits")
        val KEY_TOP_STREAK = longPreferencesKey("widget_top_streak")

        suspend fun pushUpdate(
            context: Context,
            petName: String,
            level: Long,
            mood: Long,
            completedToday: Int,
            totalHabits: Int,
            topStreak: Long
        ) {
            val appContext = context.applicationContext
            val manager = GlanceAppWidgetManager(appContext)
            val widget = ChibiPetWidget()
            val ids = manager.getGlanceIds(ChibiPetWidget::class.java)
            ids.forEach { glanceId ->
                updateAppWidgetState(appContext, glanceId) { prefs ->
                    prefs[KEY_PET_NAME] = petName
                    prefs[KEY_LEVEL] = level
                    prefs[KEY_MOOD] = mood
                    prefs[KEY_COMPLETED_TODAY] = completedToday
                    prefs[KEY_TOTAL_HABITS] = totalHabits
                    prefs[KEY_TOP_STREAK] = topStreak
                }
                widget.update(appContext, glanceId)
            }
        }
    }
}
