/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

/**
 * The schema of version 5, as SQL: what the migration from version 4 creates.
 *
 * It is a copy of what Room exported to `app/schemas/.../5.json`, statement for statement, and it
 * is frozen. A migration to version 5 must keep creating version 5 whatever the entities become
 * later, so it cannot be derived from them. `SchemaParityTest` compares a migrated database with a
 * new one, which is what fails if a statement here does not match the export.
 */
internal object SchemaVersion5 {

    /** In an order that creates a table before the ones that refer to it. */
    val TABLES: List<String> =
        listOf(
            "CREATE TABLE IF NOT EXISTS `folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, " +
                "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, " +
                "`parent_id` INTEGER, `color` TEXT, `icon` TEXT, `pinned_at` INTEGER, " +
                "`sort_criteria` TEXT, `sort_order` TEXT, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`parent_id`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE " +
                "RESTRICT )",
            "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, " +
                "`color` TEXT, `home_position` INTEGER, `created_at` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, " +
                "`uuid` TEXT NOT NULL, `custody` TEXT NOT NULL, `origin` TEXT, " +
                "`original_name` TEXT NOT NULL, `title` TEXT, `suggested_title` TEXT, " +
                "`description` TEXT, `mime_type` TEXT NOT NULL DEFAULT 'application/pdf', " +
                "`size_bytes` INTEGER, `page_count` INTEGER, `content_hash` TEXT, " +
                "`is_encrypted` INTEGER NOT NULL, `pdf_author` TEXT, `pdf_subject` TEXT, " +
                "`pdf_keywords` TEXT, `pdf_created_at` INTEGER, `document_date` INTEGER, " +
                "`folder_id` INTEGER, `is_favorite` INTEGER NOT NULL, " +
                "`ocr_enabled` INTEGER NOT NULL, `file_path` TEXT, `uri` TEXT, " +
                "`has_persisted_permission` INTEGER, `source_uri` TEXT, " +
                "`source_modified_at` INTEGER, `captured_at` INTEGER, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "`content_updated_at` INTEGER NOT NULL, `trashed_at` INTEGER, " +
                "FOREIGN KEY(`folder_id`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE " +
                "RESTRICT )",
            "CREATE TABLE IF NOT EXISTS `document_activity` (`document_id` INTEGER NOT NULL, " +
                "`last_opened_at` INTEGER, `last_activity_at` INTEGER NOT NULL, " +
                "`reading_page` INTEGER, `reading_offset` REAL, `availability` TEXT NOT NULL, " +
                "`availability_checked_at` INTEGER, PRIMARY KEY(`document_id`), " +
                "FOREIGN KEY(`document_id`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON " +
                "DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `document_tags` (`document_id` INTEGER NOT NULL, " +
                "`tag_id` INTEGER NOT NULL, `tagged_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`document_id`, `tag_id`), " +
                "FOREIGN KEY(`document_id`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON " +
                "DELETE CASCADE , " +
                "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE " +
                ")",
            "CREATE TABLE IF NOT EXISTS `pages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, " +
                "`document_id` INTEGER NOT NULL, `page_index` INTEGER NOT NULL, `width_pt` REAL, " +
                "`height_pt` REAL, `text_status` TEXT NOT NULL, `text_origin` TEXT, " +
                "`confidence` REAL, `engine` TEXT, `extractor_version` INTEGER, `language` TEXT, " +
                "`attempts` INTEGER NOT NULL, `extracted_at` INTEGER, " +
                "FOREIGN KEY(`document_id`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON " +
                "DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `page_texts` (`page_id` INTEGER NOT NULL, " +
                "`text` TEXT NOT NULL, PRIMARY KEY(`page_id`), " +
                "FOREIGN KEY(`page_id`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE " +
                "CASCADE )",
            "CREATE TABLE IF NOT EXISTS `page_layouts` (`page_id` INTEGER NOT NULL, " +
                "`format_version` INTEGER NOT NULL, `data` BLOB NOT NULL, " +
                "PRIMARY KEY(`page_id`), " +
                "FOREIGN KEY(`page_id`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE " +
                "CASCADE )",
        )

    /** The indices of [TABLES]. */
    val INDICES: List<String> =
        listOf(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_documents_uuid` ON `documents` (`uuid`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_documents_file_path` ON `documents` " +
                "(`file_path`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_documents_uri` ON `documents` (`uri`)",
            "CREATE INDEX IF NOT EXISTS `index_documents_folder_id` ON `documents` (`folder_id`)",
            "CREATE INDEX IF NOT EXISTS `index_documents_content_hash` ON `documents` " +
                "(`content_hash`)",
            "CREATE INDEX IF NOT EXISTS `index_documents_trashed_at` ON `documents` " +
                "(`trashed_at`)",
            "CREATE INDEX IF NOT EXISTS `index_document_activity_last_activity_at` ON " +
                "`document_activity` (`last_activity_at`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_uuid` ON `folders` (`uuid`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_parent_id_normalized_name` ON " +
                "`folders` (`parent_id`, " +
                "`normalized_name`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_uuid` ON `tags` (`uuid`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalized_name` ON `tags` " +
                "(`normalized_name`)",
            "CREATE INDEX IF NOT EXISTS `index_document_tags_tag_id` ON `document_tags` " +
                "(`tag_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_pages_document_id_page_index` ON `pages` " +
                "(`document_id`, " +
                "`page_index`)",
            "CREATE INDEX IF NOT EXISTS `index_pages_text_status` ON `pages` (`text_status`)",
        )

    /** The full-text indexes, which hold no text: they point at `documents` and `page_texts`. */
    val SEARCH_INDEXES: List<String> =
        listOf(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `documents_fts` USING FTS4(`title` TEXT, " +
                "`original_name` TEXT NOT NULL, `suggested_title` TEXT, `description` TEXT, " +
                "`pdf_author` TEXT, `pdf_subject` TEXT, `pdf_keywords` TEXT, tokenize=unicode61, " +
                "content=`documents`)",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `page_texts_fts` USING FTS4(`text` TEXT NOT " +
                "NULL, " +
                "tokenize=unicode61, content=`page_texts`)",
        )

    /** The triggers Room keeps each full-text index in step with its table with. */
    val SEARCH_INDEX_SYNC: List<String> =
        listOf(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_documents_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `documents` BEGIN DELETE FROM `documents_fts` WHERE " +
                "`docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_documents_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `documents` BEGIN DELETE FROM `documents_fts` WHERE " +
                "`docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_documents_fts_AFTER_UPDATE AFTER " +
                "UPDATE ON `documents` BEGIN INSERT INTO `documents_fts`(`docid`, " +
                "`title`, `original_name`, `suggested_title`, `description`, `pdf_author`, " +
                "`pdf_subject`, `pdf_keywords`) VALUES (NEW.`rowid`, NEW.`title`, " +
                "NEW.`original_name`, NEW.`suggested_title`, NEW.`description`, " +
                "NEW.`pdf_author`, NEW.`pdf_subject`, NEW.`pdf_keywords`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_documents_fts_AFTER_INSERT AFTER " +
                "INSERT ON `documents` BEGIN INSERT INTO `documents_fts`(`docid`, " +
                "`title`, `original_name`, `suggested_title`, `description`, `pdf_author`, " +
                "`pdf_subject`, `pdf_keywords`) VALUES (NEW.`rowid`, NEW.`title`, " +
                "NEW.`original_name`, NEW.`suggested_title`, NEW.`description`, " +
                "NEW.`pdf_author`, NEW.`pdf_subject`, NEW.`pdf_keywords`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_page_texts_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `page_texts` BEGIN DELETE FROM `page_texts_fts` WHERE " +
                "`docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_page_texts_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `page_texts` BEGIN DELETE FROM `page_texts_fts` WHERE " +
                "`docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_page_texts_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON `page_texts` BEGIN INSERT INTO `page_texts_fts`(`docid`, " +
                "`text`) VALUES (NEW.`rowid`, NEW.`text`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_page_texts_fts_AFTER_INSERT " +
                "AFTER INSERT ON `page_texts` BEGIN INSERT INTO `page_texts_fts`(`docid`, " +
                "`text`) VALUES (NEW.`rowid`, NEW.`text`); END",
        )

    /** The views, which need their tables to exist. */
    val VIEWS: List<String> =
        listOf(
            "CREATE VIEW `library_documents` AS SELECT * FROM documents WHERE custody = " +
                "'MANAGED' AND trashed_at IS NULL",
            "CREATE VIEW `document_text_status` AS SELECT document_id, COUNT(*) AS pages, " +
                "SUM(text_status = 'PENDING') AS pending, SUM(text_status = 'FAILED') AS failed, " +
                "SUM(text_origin IS 'RECOGNIZED') AS recognized FROM pages GROUP BY document_id",
        )
}
