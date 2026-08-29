package com.repforth.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android Auto Backup must stay off.
 *
 * Two independent reasons, either sufficient. Cloud backup is an explicit MVP
 * non-goal (guideline section 4). And Auto Backup restores a data directory
 * across app versions, while Room refuses to open a database whose identity
 * hash is not the one it compiled against — so a restore that lands an older
 * schema is a guaranteed crash at launch, not a degraded experience. That is
 * not hypothetical: it happened on a real device the first time the schema
 * gained the user tables.
 *
 * The default is `true`, and the default is invisible: delete the attribute and
 * the manifest looks fine while backup quietly turns back on. So the assertion
 * is that the attribute is present and false, never merely that it is not true.
 */
class BackupPolicyTest {

    @Test
    fun `auto backup is explicitly disabled`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue(
            "Expected ${manifest.absolutePath} to exist; is the unit test running " +
                "from the module dir?",
            manifest.exists(),
        )
        val source = manifest.readText()

        assertTrue(
            "android:allowBackup is missing from the manifest. Absent means true, " +
                "which uploads the workout database to Google and crashes Room on " +
                "restore across a schema change. Declare it false.",
            """android:allowBackup="false"""" in source,
        )
        assertFalse(
            "android:allowBackup is declared true somewhere in the manifest.",
            """android:allowBackup="true"""" in source,
        )
    }
}
