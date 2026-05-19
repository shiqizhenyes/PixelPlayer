package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM-side tests for the Context-free entry points on [FileDeletionUtils].
 * The Android-specific MediaStore deletion paths are excluded — they
 * require an emulator and live in `androidTest/`.
 */
class FileDeletionUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun canDeleteFile_existingRegularFile_returnsTrue() = runTest {
        val file = tempFolder.newFile("song.mp3").apply { writeText("data") }
        assertThat(FileDeletionUtils.canDeleteFile(file.absolutePath)).isTrue()
    }

    @Test
    fun canDeleteFile_missingFile_returnsFalse() = runTest {
        val missing = File(tempFolder.root, "nope.mp3").absolutePath
        assertThat(FileDeletionUtils.canDeleteFile(missing)).isFalse()
    }

    @Test
    fun canDeleteFile_directory_returnsFalse() = runTest {
        val dir = tempFolder.newFolder("not_a_file")
        assertThat(FileDeletionUtils.canDeleteFile(dir.absolutePath)).isFalse()
    }

    @Test
    fun canDeleteFile_blankPath_returnsFalse() = runTest {
        assertThat(FileDeletionUtils.canDeleteFile("")).isFalse()
    }

    @Test
    fun getFileInfo_populatesAllFields() = runTest {
        val file = tempFolder.newFile("track.flac").apply { writeText("abcdefg") }
        val info = FileDeletionUtils.getFileInfo(file.absolutePath)
        assertThat(info.exists).isTrue()
        assertThat(info.isFile).isTrue()
        assertThat(info.size).isEqualTo(7L)
        assertThat(info.canRead).isTrue()
    }

    @Test
    fun getFileInfo_missingFile_returnsFalsy() = runTest {
        val info = FileDeletionUtils.getFileInfo(File(tempFolder.root, "missing").absolutePath)
        assertThat(info.exists).isFalse()
        assertThat(info.size).isEqualTo(0L)
    }

    @Test
    fun getFileInfo_directory_distinguishesFromFile() = runTest {
        val dir = tempFolder.newFolder("subdir")
        val info = FileDeletionUtils.getFileInfo(dir.absolutePath)
        assertThat(info.exists).isTrue()
        assertThat(info.isFile).isFalse()
    }
}
