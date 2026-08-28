package com.repforth.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The DI root. Exists only to host the generated Hilt component — application
 * classes attract initialisation logic, and anything put here runs on every cold
 * start before the first frame (§16).
 */
@HiltAndroidApp
class RepForthApplication : Application()
