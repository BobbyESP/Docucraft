package com.bobbyesp.docucraft.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateTime {

    // ─────────────────────────────────────────────
    // Enums
    // ─────────────────────────────────────────────

    enum class DateFormat(val pattern: String?) {
        /** Localized long format: "March 23, 2026" (based on device locale) */
        LOCALIZED_LONG(null),

        /** Localized medium format: "Mar 23, 2026" */
        LOCALIZED_MEDIUM(null),

        /** Numeric format: DD-MM-YYYY → "23-03-2026" */
        DAY_MONTH_YEAR("dd-MM-yyyy"),

        /** Numeric format with slashes: DD/MM/YYYY → "23/03/2026" */
        DAY_MONTH_YEAR_SLASH("dd/MM/yyyy"),

        /** Numeric format: MM-DD-YYYY → "03-23-2026" */
        MONTH_DAY_YEAR("MM-dd-yyyy"),

        /** Numeric format: YYYY-MM-DD → "2026-03-23" (ISO 8601) */
        YEAR_MONTH_DAY("yyyy-MM-dd"),

        /** Numeric format with underscores: YYYY_MM_DD → "2026_03_23" */
        YEAR_MONTH_DAY_UNDERSCORE("yyyy_MM_dd"),
    }

    enum class TimeFormat(val pattern: String?) {
        /** Localized short format: "3:45 PM" or "15:45" depending on locale */
        LOCALIZED_SHORT(null),

        /** Localized medium format: "3:45:30 PM" */
        LOCALIZED_MEDIUM(null),

        /** 24-hour format: HH:mm → "15:45" */
        HOURS_MINUTES_24H("HH:mm"),

        /** 24-hour format with seconds: HH:mm:ss → "15:45:30" */
        HOURS_MINUTES_SECONDS_24H("HH:mm:ss"),

        /** 12-hour format: hh:mm a → "03:45 PM" */
        HOURS_MINUTES_12H("hh:mm a"),

        /** 12-hour format with seconds: hh:mm:ss a → "03:45:30 PM" */
        HOURS_MINUTES_SECONDS_12H("hh:mm:ss a"),

        /** Compact 24-hour format without separator: HHmm → "1545" */
        COMPACT_24H("HHmm"),
    }

    enum class DateTimeFormat(val pattern: String?) {
        /** Localized long date + short time: "March 23, 2026 at 3:45 PM" */
        LOCALIZED_LONG(null),

        /** Localized medium date + medium time: "Mar 23, 2026, 3:45:30 PM" */
        LOCALIZED_MEDIUM(null),

        /** ISO 8601: "2026-03-23T15:45:30" */
        ISO_LOCAL("yyyy-MM-dd'T'HH:mm:ss"),

        /** File-safe format: "2026-03-23_15-45-30" */
        FILE_SAFE("yyyy-MM-dd_HH-mm-ss"),

        /** Compact format: "20260323_154530" */
        COMPACT("yyyyMMdd_HHmmss"),

        /** Human-readable: "23-03-2026 15:45" */
        DAY_MONTH_YEAR_TIME("dd-MM-yyyy HH:mm"),
    }

    // ─────────────────────────────────────────────
    // Date formatting
    // ─────────────────────────────────────────────

    /**
     * Converts a timestamp in milliseconds to a formatted date string.
     *
     * @param timestampMillis Timestamp in milliseconds since epoch.
     * @param format Desired date format. Defaults to [DateFormat.LOCALIZED_LONG].
     * @param locale Locale to use for localized formats. Defaults to system locale.
     * @param zoneId Time zone. Defaults to the device's local time zone.
     * @return Formatted date as a String.
     */
    fun formatDate(
        timestampMillis: Long,
        format: DateFormat = DateFormat.LOCALIZED_LONG,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val localDate = Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .toLocalDate()

        val formatter = when (format) {
            DateFormat.LOCALIZED_LONG -> DateTimeFormatter
                .ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)

            DateFormat.LOCALIZED_MEDIUM -> DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)

            else -> DateTimeFormatter.ofPattern(requireNotNull(format.pattern), locale)
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
    fun formatDate(
        timestampMillis: Long,
        pattern: String,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val localDate = Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .toLocalDate()

        return localDate.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    // ─────────────────────────────────────────────
    // Time formatting
    // ─────────────────────────────────────────────

    /**
     * Converts a timestamp in milliseconds to a formatted time string.
     *
     * @param timestampMillis Timestamp in milliseconds since epoch.
     * @param format Desired time format. Defaults to [TimeFormat.LOCALIZED_SHORT].
     * @param locale Locale to use for localized formats. Defaults to system locale.
     * @param zoneId Time zone. Defaults to the device's local time zone.
     * @return Formatted time as a String.
     */
    fun formatTime(
        timestampMillis: Long,
        format: TimeFormat = TimeFormat.LOCALIZED_SHORT,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val localTime = Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .toLocalTime()

        val formatter = when (format) {
            TimeFormat.LOCALIZED_SHORT -> DateTimeFormatter
                .ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale)

            TimeFormat.LOCALIZED_MEDIUM -> DateTimeFormatter
                .ofLocalizedTime(FormatStyle.MEDIUM)
                .withLocale(locale)

            else -> DateTimeFormatter.ofPattern(requireNotNull(format.pattern), locale)
        }

        return localTime.format(formatter)
    }

    /**
     * Converts a timestamp in milliseconds to a formatted time string using a custom pattern.
     *
     * @param timestampMillis Timestamp in milliseconds since epoch.
     * @param pattern Custom pattern (e.g. "HH'h'mm'm'").
     * @param locale Locale to use. Defaults to system locale.
     * @param zoneId Time zone. Defaults to the device's local time zone.
     * @return Formatted time as a String.
     */
    fun formatTime(
        timestampMillis: Long,
        pattern: String,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val localTime = Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .toLocalTime()

        return localTime.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    // ─────────────────────────────────────────────
    // DateTime formatting
    // ─────────────────────────────────────────────

    /**
     * Converts a timestamp in milliseconds to a formatted date-time string.
     *
     * @param timestampMillis Timestamp in milliseconds since epoch.
     * @param format Desired date-time format. Defaults to [DateTimeFormat.LOCALIZED_LONG].
     * @param locale Locale to use for localized formats. Defaults to system locale.
     * @param zoneId Time zone. Defaults to the device's local time zone.
     * @return Formatted date-time as a String.
     */
    fun formatDateTime(
        timestampMillis: Long,
        format: DateTimeFormat = DateTimeFormat.LOCALIZED_LONG,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val localDateTime = Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .toLocalDateTime()

        val formatter = when (format) {
            DateTimeFormat.LOCALIZED_LONG -> DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(locale)

            DateTimeFormat.LOCALIZED_MEDIUM -> DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale)

            else -> DateTimeFormatter.ofPattern(requireNotNull(format.pattern), locale)
        }

        return localDateTime.format(formatter)
    }

    /**
     * Converts a timestamp in milliseconds to a formatted date-time string using a custom pattern.
     *
     * @param timestampMillis Timestamp in milliseconds since epoch.
     * @param pattern Custom pattern (e.g. "dd/MM/yyyy HH:mm:ss").
     * @param locale Locale to use. Defaults to system locale.
     * @param zoneId Time zone. Defaults to the device's local time zone.
     * @return Formatted date-time as a String.
     */
    fun formatDateTime(
        timestampMillis: Long,
        pattern: String,
        locale: Locale = Locale.getDefault(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val localDateTime = Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .toLocalDateTime()

        return localDateTime.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
