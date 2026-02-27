package com.bobbyesp.docucraft.core.domain.analytics

data class AnalyticsEvent(
    val type: String,
    val extras: List<Param> = emptyList(),
) {
    // Standard analytics types.
    object Types {
        const val SCREEN_VIEW = "screen_view" // (eg. extras: SCREEN_NAME)
        const val SELECT_ITEM = "select_item"
        const val BUTTON_CLICK = "button_click"
        const val SUBMIT_RATING = "submit_rating"
    }

    /**
     * A key-value pair used to supply extra context to an 
     * analytics event.
     *
     * @param key - the parameter key. Wherever possible use 
     * one of the standard `ParamKeys`, however, if no suitable 
     * key is available you can define your own as long as it is 
     * configured in your backend analytics system (for example, 
     * by creating a Firebase Analytics custom parameter).
     *
     * @param value - the parameter value.
     */
    data class Param(val key: String, val value: String)

    // Standard parameter keys.
    object ParamKeys {
        const val SCREEN_NAME = "screen_name"
        const val BUTTON_ID = "button_id"
        const val ITEM_ID = "item_id"
        const val ITEM_NAME = "item_name"
        const val RATING_TYPE = "rating_type"
        const val RATING_CONTENT = "rating_content"
    }
}
