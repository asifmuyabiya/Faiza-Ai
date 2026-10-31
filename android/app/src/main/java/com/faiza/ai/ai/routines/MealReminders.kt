package com.faiza.ai.ai.routines

import android.content.Context
import com.faiza.ai.FaizaApp

/**
 * Meal-time check-ins (owner's idea): four daily routines — breakfast, lunch, afternoon snack, dinner. They are
 * ordinary scheduled tasks, so they show up in Routines and can be edited or paused there one by one.
 */
object MealReminders {

    data class Meal(val id: String, val label: String, val hour: Int, val minute: Int, val what: String)

    val MEALS = listOf(
        Meal("meal-breakfast", "Breakfast check-in", 9, 30, "breakfast (সকালের নাস্তা)"),
        Meal("meal-lunch", "Lunch check-in", 14, 30, "lunch (দুপুরের খাবার)"),
        Meal("meal-snack", "Snack check-in", 17, 30, "a light afternoon snack (বিকেলের নাস্তা)"),
        Meal("meal-dinner", "Dinner check-in", 22, 0, "dinner (রাতের খাবার)")
    )

    /** Turns the four check-ins on (creates / re-enables them) or off (removes them). */
    fun apply(ctx: Context, on: Boolean) {
        FaizaApp.instance.prefs.isMealReminders = on
        MEALS.forEach { m ->
            if (on) {
                RoutineCenter.upsertTask(
                    ctx, ScheduledTask(
                        id = m.id,
                        label = m.label,
                        instruction = "It's time for ${m.what}. Ask the user in one short, warm sentence whether they have " +
                                "had ${m.what} yet, in their language. If they say no, kindly suggest they eat something " +
                                "soon; if yes, say you're glad. Keep it brief and don't repeat the question.",
                        hour = m.hour,
                        minute = m.minute,
                        repeat = Repeat.DAILY
                    )
                )
            } else {
                RoutineCenter.deleteTask(ctx, m.id)
            }
        }
    }
}
