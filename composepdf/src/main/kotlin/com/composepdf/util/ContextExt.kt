package com.composepdf.util

import android.content.Context

internal fun Context.longLivedContext(): Context = applicationContext ?: this

