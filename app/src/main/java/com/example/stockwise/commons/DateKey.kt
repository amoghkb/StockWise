package com.example.stockwise.commons

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun makeDateKey(year: Int, month: Int, day: Int): String {
    val cal = Calendar.getInstance().apply {
        set(year, month, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

/** Inverse: parse "yyyy-MM-dd" back into a Calendar */
fun parseDateKey(dateKey: String): Calendar {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    fmt.parse(dateKey)?.let { cal.time = it }
    return cal
}