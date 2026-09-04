import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.dsl.TestExtension

/**
 * The one emulator this project runs instrumentation on.
 *
 * Named once and declared once, because two modules now need it and a device
 * described in two files is a device that will eventually be described two
 * different ways — the baseline profile would then be recorded on one API level
 * and the migrations proven on another, with nothing to say so.
 *
 * **A fixed device, so a result does not quietly depend on whose phone ran it.**
 * API 34 matches the Galaxy S23 this project is tested on. `aosp` rather than
 * `google`: a baseline profile should describe this app rather than Play
 * Services warming up, and a migration cares about SQLite, which is the same
 * either way.
 */
const val MANAGED_DEVICE = "pixel6Api34"

/** Declares [MANAGED_DEVICE] on a library or application module. */
internal fun CommonExtension<*, *, *, *, *, *>.configureManagedDevice() {
    testOptions.managedDevices.allDevices.create(
        MANAGED_DEVICE,
        ManagedVirtualDevice::class.java,
    ) {
        device = "Pixel 6"
        apiLevel = 34
        systemImageSource = "aosp"
    }
}

/**
 * The same, for a `com.android.test` module.
 *
 * `TestExtension` is not a `CommonExtension`, so the declaration cannot simply
 * be shared by type — only the values can be, which is the half that matters.
 */
internal fun TestExtension.configureManagedDevice() {
    testOptions.managedDevices.allDevices.create(
        MANAGED_DEVICE,
        ManagedVirtualDevice::class.java,
    ) {
        device = "Pixel 6"
        apiLevel = 34
        systemImageSource = "aosp"
    }
}
