package com.hsr.railfocus.ui.history

import android.content.Context
import com.hsr.railfocus.R
import com.hsr.railfocus.domain.model.Station
import java.text.SimpleDateFormat
import java.util.Locale

object TrainTicketHelper {
    val DATE_FORMAT = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
    val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.CHINA)

    val TRAIN_PREFIXES = listOf("G", "D", "C", "Z", "T", "K")
    val SEAT_ROWS = (1..16)
    val SEAT_LETTERS = listOf("A", "B", "C", "D", "F")
    val SEAT_CLASSES = listOf("二等座", "一等座", "商务座")

    fun determineTrainSeries(
        plannedMin: Int,
        createdAtMillis: Long = System.currentTimeMillis(),
        focusType: String? = null
    ): Triple<TrainSeries, String, Int> {
        val isExplicitSleeper = (focusType?.contains("卧") == true) || (focusType?.contains("夜") == true)
        return when {
            isExplicitSleeper -> Triple(TrainSeries.SLEEPER, "D", 250)
            plannedMin in 1..29 -> Triple(TrainSeries.C_SERIES, "C", 200)
            plannedMin in 30..59 -> Triple(TrainSeries.D_SERIES, "D", 250)
            else -> Triple(TrainSeries.G_SERIES, "G", 350)
        }
    }

    fun synthesizeTrainNumber(
        prefix: String,
        startStation: Station,
        endStation: Station,
        plannedDurationMin: Int
    ): String {
        val start = startStation.id.firstOrNull()?.uppercaseChar() ?: 'A'
        val end = endStation.id.firstOrNull()?.uppercaseChar() ?: 'B'
        val digits = ((start.code + end.code + plannedDurationMin) % 900 + 100).toString()
        return "$prefix$digits"
    }

    fun synthesizeSeatInfo(context: Context, startStation: Station, endStation: Station, series: TrainSeries): String {
        val row = SEAT_ROWS.elementAt((startStation.id.hashCode()).mod(SEAT_ROWS.count()).coerceIn(0, SEAT_ROWS.count() - 1))
        val letter = SEAT_LETTERS.elementAt((endStation.id.hashCode()).mod(SEAT_LETTERS.size))
        if (series == TrainSeries.SLEEPER) {
            val bunk = if (row % 2 == 0) context.getString(R.string.ticket_bunk_lower) else context.getString(R.string.ticket_bunk_upper)
            return context.getString(R.string.ticket_bunk_format, row.toString(), (row % 8 + 1), bunk)
        }
        return context.getString(R.string.ticket_seat_format, row.toString(), "$row$letter")
    }

    fun synthesizeSeatClass(context: Context, startStation: Station, series: TrainSeries): String {
        if (series == TrainSeries.SLEEPER) return context.getString(R.string.ticket_seat_class_sleeper)
        val classes = listOf(
            context.getString(R.string.ticket_seat_class_second),
            context.getString(R.string.ticket_seat_class_first),
            context.getString(R.string.ticket_seat_class_business)
        )
        return classes.elementAt((startStation.id.hashCode()).mod(classes.size))
    }

    fun synthesizeSeatInfo(startStation: Station, endStation: Station, series: TrainSeries): String {
        val row = SEAT_ROWS.elementAt((startStation.id.hashCode()).mod(SEAT_ROWS.count()).coerceIn(0, SEAT_ROWS.count() - 1))
        val letter = SEAT_LETTERS.elementAt((endStation.id.hashCode()).mod(SEAT_LETTERS.size))
        if (series == TrainSeries.SLEEPER) {
            val bunk = if (row % 2 == 0) "下铺" else "上铺"
            return "${row}车${(row % 8 + 1)}$bunk"
        }
        return row.toString() + "车" + row + letter
    }

    fun synthesizeSeatClass(startStation: Station, series: TrainSeries): String {
        if (series == TrainSeries.SLEEPER) return "高级动卧"
        return SEAT_CLASSES.elementAt((startStation.id.hashCode()).mod(SEAT_CLASSES.size))
    }

    private fun Int.mod(other: Int): Int = (this % other).let { if (it < 0) it + other else it }
}
