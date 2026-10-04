/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

/**
 * The colors a folder or a tag can be given. A closed set of names, never a color value: the
 * catalogue keeps the [key], and the theme decides what that color looks like in light, in dark and
 * beside the user's own colors.
 *
 * A key the app does not know, written by a version that had more of them, is read as no color at
 * all, which is the default.
 */
enum class LabelColor(val key: String) {
    RED("red"),
    TERRACOTTA("terracotta"),
    AMBER("amber"),
    LIME("lime"),
    GREEN("green"),
    TEAL("teal"),
    SKY("sky"),
    INDIGO("indigo"),
    VIOLET("violet"),
    PINK("pink");

    companion object {
        /** The color [key] names, or `null` for none and for a key that is not in the palette. */
        fun of(key: String?): LabelColor? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The icons a folder can be given, kept by [key] for the same reason as [LabelColor]: an id of a
 * drawable changes from one build to the next.
 */
enum class FolderIcon(val key: String) {
    FOLDER("folder"),
    RECEIPT("receipt_long"),
    WORK("work"),
    HOME("home"),
    SCHOOL("school"),
    HEALTH("medical_services"),
    BANK("account_balance"),
    TRAVEL("flight"),
    CAR("directions_car"),
    IDENTITY("badge"),
    LEGAL("gavel"),
    FAMILY("family_restroom"),
    HEART("favorite"),
    STAR("star");

    companion object {
        /** What a folder shows when it was given no icon, or one the app does not know. */
        val Default = FOLDER

        fun of(key: String?): FolderIcon = entries.firstOrNull { it.key == key } ?: Default
    }
}

/** The folder's color, or `null` when it has none. */
val Folder.labelColor: LabelColor?
    get() = LabelColor.of(color)

val Folder.folderIcon: FolderIcon
    get() = FolderIcon.of(icon)

/** The tag's color, or `null` when it has none. */
val Tag.labelColor: LabelColor?
    get() = LabelColor.of(color)
