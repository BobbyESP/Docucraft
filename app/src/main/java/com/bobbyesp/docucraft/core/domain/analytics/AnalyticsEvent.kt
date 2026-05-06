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

        const val SCAN_STARTED = "scan_started"
        const val SCAN_COMPLETED = "scan_completed"
        const val SCAN_CANCELLED = "scan_cancelled"
        const val DOCUMENT_DELETED = "document_deleted"
        const val DOCUMENT_SHARED = "document_shared"
        const val DOCUMENT_EXPORTED = "document_exported"
        const val SEARCH_PERFORMED = "search_performed"
        const val FILTER_APPLIED = "filter_applied"
        const val PDF_VIEWER_SETTING_CHANGED = "pdf_viewer_setting_changed"
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

        const val PAGE_COUNT = "page_count"
        const val FILE_SIZE = "file_size"
        const val STATUS = "status"
        const val QUERY_LENGTH = "query_length"
        const val SORT_BY = "sort_by"
        const val FILTER_TYPE = "filter_type"
        const val SETTING_NAME = "setting_name"
        const val SETTING_VALUE = "setting_value"
    }
}
