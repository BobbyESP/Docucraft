package com.bobbyesp.docucraft.core.util

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.parent

fun PlatformFile.ensure(mustCreate: Boolean = false) {
    if (!this.exists()) {
        this.createDirectories(mustCreate = mustCreate)
    }
}

fun PlatformFile.ensureParent(mustCreate: Boolean = false) {
    this.parent()?.ensure(mustCreate = mustCreate)
}
