/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.util

import android.content.Context

internal fun Context.longLivedContext(): Context = applicationContext ?: this
