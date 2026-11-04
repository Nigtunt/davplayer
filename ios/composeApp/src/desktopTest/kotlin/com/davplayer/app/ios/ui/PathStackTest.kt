package com.davplayer.app.ios.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PathStackTest {

    @Test
    fun rootOnly() {
        assertEquals(listOf("/dav/"), buildPathStack("/dav/", "/dav/"))
    }

    @Test
    fun nestedPathIncludesAllParents() {
        assertEquals(
            listOf("/dav/", "/dav/115/", "/dav/115/comic/"),
            buildPathStack("/dav/", "/dav/115/comic/")
        )
    }

    @Test
    fun targetOutsideRootFallsBackToRoot() {
        assertEquals(listOf("/dav/"), buildPathStack("/dav/", "/other/115/"))
    }

    @Test
    fun missingTrailingSlashNormalized() {
        assertEquals(
            listOf("/dav/", "/dav/115/"),
            buildPathStack("/dav/", "/dav/115")
        )
    }
}
