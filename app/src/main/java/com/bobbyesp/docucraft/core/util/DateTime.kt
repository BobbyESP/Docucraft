package com.bobbyesp.docucraft.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateTime {

    enum class DateFormat(val pattern: String?) {
        /** Localized long format: "March 23, 2026" (based on device locale) */
        LOCALIZED_LONG(null),

        /** Localized medium format: "Mar 23, 2026" */
        LOCALIZED_MEDIUM(null),

        /** Numeric format: DD-MM-YYYY → "23-03-2026" */
        DAY_MONTH_YEAR("dd-MM-yyyy"),

        /** Numeric format: YYYY-MM-DD → "2026-03-23" (ISO 8601) */
        YEAR_MONTH_DAY("yyyy-MM-dd"),

        /** Numeric format: MM-DD-YYYY → "03-23-2026" */
        MONTH_DAY_YEAR("MM-dd-yyyy"),

        /** Numeric format with slashes: DD/MM/YYYY → "23/03/2026" */
        DAY_MONTH_YEAR_SLASH("dd/MM/yyyy"),
    }

    /**
     * Converts a timestamp in milliseconds to a formatted date string.
     *
     * @param timestampMillis Timestamp in milliseconds since epoch.
     * @param format Desired date format. Defaults to [DateFormat.LOCALIZED_LONG].
     * @param locale Locale to use for localized formats. Defaults to system locale.
     * @param zoneId Time zone. Defaults to the device's local time zone.
     * @return Formatted date as a String.
     */
    fun formatTimestamp(
        timestampMillis: Long,
        format: DateFormat = DateFormat.LOCALIZED_LONG,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val instant = Instant.ofEpochMilli(timestampMillis)
        val localDate = instant.atZone(zoneId).toLocalDate()

        val formatter = when (format) {
            DateFormat.LOCALIZED_LONG -> DateTimeFormatter
                .ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)

            DateFormat.LOCALIZED_MEDIUM -> DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)

            else -> DateTimeFormatter
                .ofPattern(requireNotNull(format.pattern), locale)
        }

        return localDate.format(formatter)
    }

    /**
     * Converts a timestamp in milliseconds to a formatted date string using a custom pattern.
     *
     * @param timestampMillis Timestamp in milliseconds since epoch.
     * @param pattern Custom pattern (e.g. "dd 'de' MMMM 'de' yyyy").
     * @param locale Locale to use. Defaults to system locale.
     * @param zoneId Time zone. Defaults to the device's local time zone.
     * @return Formatted date as a String.
     */
    fun formatTimestamp(
        timestampMillis: Long,
        pattern: String,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val instant = Instant.ofEpochMilli(timestampMillis)
        val localDate = instant.atZone(zoneId).toLocalDate()
        val formatter = DateTimeFormatter.ofPattern(pattern, locale)
        return localDate.format(formatter)
    }
}