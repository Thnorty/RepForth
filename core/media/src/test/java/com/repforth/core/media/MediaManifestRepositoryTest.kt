package com.repforth.core.media

import com.repforth.core.media.manifest.MediaManifest
import com.repforth.core.media.manifest.MediaManifestAsset
import com.repforth.core.media.manifest.MediaManifestEntry
import com.repforth.core.model.ExerciseId
import com.repforth.core.model.MediaRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaManifestRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val sampleCommit = "1111111111111111111111111111111111111111"

    private val sampleJson = """
        {
          "commit": "$sampleCommit",
          "mediaVersion": 1,
          "baseUrl": "https://raw.githubusercontent.com/example/exercises-dataset/$sampleCommit/",
          "attribution": "© Gym visual",
          "entries": [
            {
              "id": "0001",
              "thumbnail": {
                "path": "images/0001.jpg",
                "sha256": "52b897152a76309a61be8bc917196bfe3558e7d55861f2d44b8375e4d5720286",
                "bytes": 6108
              },
              "animation": {
                "path": "videos/0001.gif",
                "sha256": "b572f07a646ec97da6bf243a3e4db3dac563d8347814e6dc9fb7b6f84814dcbe",
                "bytes": 92828
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `media manifest deserializes correctly`() {
        val manifest = json.decodeFromString<MediaManifest>(sampleJson)
        assertEquals(sampleCommit, manifest.commit)
        assertEquals(1, manifest.mediaVersion)
        assertEquals(1, manifest.entries.size)

        val entry = manifest.entries.first()
        assertEquals("0001", entry.id)
        assertEquals("images/0001.jpg", entry.thumbnail.path)
        assertEquals("52b897152a76309a61be8bc917196bfe3558e7d55861f2d44b8375e4d5720286", entry.thumbnail.sha256)
        assertEquals(6108L, entry.thumbnail.bytes)
    }

    @Test
    fun `placeholder resolver returns unavailable without network`() = runTest {
        val resolver = PlaceholderMediaResolver()
        val id = ExerciseId("0001")

        assertEquals(MediaRef.Unavailable, resolver.resolveThumbnail(id))
        assertEquals(MediaRef.Unavailable, resolver.resolveAnimation(id))
    }
}
