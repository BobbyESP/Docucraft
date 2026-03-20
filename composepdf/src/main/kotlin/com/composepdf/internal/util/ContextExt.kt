package com.composepdf.internal.util

import android.content.Context

internal fun Context.longLivedContext(): Context = applicationContext ?: this

