package com.example.crickzy.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun parseDate(dateStr: String): Date? {
        return try {
            dateFormatter.parse(dateStr)
        } catch (e: Exception) {
            null
        }
    }

    fun formatDate(date: Date): String {
        return dateFormatter.format(date)
    }

    /**
     * Validates if a date range is logical.
     * @param start dd/MM/yyyy
     * @param end dd/MM/yyyy
     * @return true if start is before or equal to end
     */
    fun isDateRangeValid(start: String, end: String): Boolean {
        val startDate = parseDate(start) ?: return false
        val endDate = parseDate(end) ?: return false
        return !startDate.after(endDate)
    }

    /**
     * Validates if a date is not in the too distant past or future.
     * For booking players/teams, past dates are allowed but should be recent.
     */
    fun isBookingDateLogical(dateStr: String): Boolean {
        return isFutureDate(dateStr)
    }

    /**
     * For tournaments, the date must be today or in the future.
     */
    fun isFutureDate(dateStr: String): Boolean {
        val date = parseDate(dateStr) ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        return !date.before(today)
    }
}
