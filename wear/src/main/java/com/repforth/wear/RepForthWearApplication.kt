package com.repforth.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The watch application.
 *
 * Holds nothing. §11 gives the watch no database, no preferences and no
 * credential, so there is nothing to initialise here — the Hilt graph exists
 * only to hand one singleton to a service and a ViewModel.
 */
@HiltAndroidApp
class RepForthWearApplication : Application()
