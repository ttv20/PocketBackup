package com.ttv20.rsyncbackup.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcePathPickerTest {
    @Test
    fun mapsPrimaryTreeToSharedStoragePath() {
        assertEquals(
            "/storage/emulated/0",
            sharedStoragePathFromTreeDocumentId("primary:"),
        )
        assertEquals(
            "/storage/emulated/0/DCIM/Camera",
            sharedStoragePathFromTreeDocumentId("primary:DCIM/Camera"),
        )
    }

    @Test
    fun mapsHomeTreeToDocumentsPath() {
        assertEquals(
            "/storage/emulated/0/Documents",
            sharedStoragePathFromTreeDocumentId("home:"),
        )
        assertEquals(
            "/storage/emulated/0/Documents/Backups",
            sharedStoragePathFromTreeDocumentId("home:Backups"),
        )
    }

    @Test
    fun rejectsUnsupportedStorageVolumes() {
        assertNull(sharedStoragePathFromTreeDocumentId("0123-4567:Photos"))
        assertNull(sharedStoragePathFromTreeDocumentId(""))
    }
}
