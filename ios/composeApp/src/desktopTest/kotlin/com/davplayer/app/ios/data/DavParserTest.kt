package com.davplayer.app.ios.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DavParserTest {

    /** 标准 WebDAV：目录带 <collection/> */
    @Test
    fun parsesStandardCollectionDirectory() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/115/</D:href>
                <D:propstat><D:prop>
                  <D:displayname>115</D:displayname>
                  <D:resourcetype><D:collection/></D:resourcetype>
                </D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/115/movie.mkv</D:href>
                <D:propstat><D:prop>
                  <D:displayname>movie.mkv</D:displayname>
                  <D:resourcetype/>
                  <D:getcontentlength>123</D:getcontentlength>
                </D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entries = parseMultiStatus(xml, "/dav/")
        assertEquals(2, entries.size)
        assertTrue(entries.single { it.name == "115" }.isDir)
        assertFalse(entries.single { it.name == "movie.mkv" }.isDir)
        assertEquals(123L, entries.single { it.name == "movie.mkv" }.size)
    }

    /** AList 某些存储：目录没有 <collection/>，只能靠 href 末尾的 "/" */
    @Test
    fun infersDirectoryFromTrailingSlash() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/aliyun/</D:href>
                <D:propstat><D:prop><D:resourcetype/></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/aliyun/photo.jpg</D:href>
                <D:propstat><D:prop><D:resourcetype/></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entries = parseMultiStatus(xml, "/dav/")
        assertTrue(entries.single { it.name == "aliyun" }.isDir)
        assertFalse(entries.single { it.name == "photo.jpg" }.isDir)
    }

    @Test
    fun decodesPercentEncodedName() {
        val xml = """
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/%E7%94%B5%E5%BD%B1/</D:href>
                <D:propstat><D:prop><D:resourcetype/></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entries = parseMultiStatus(xml, "/dav/")
        assertEquals("电影", entries.single().name)
        assertTrue(entries.single().isDir)
    }
}
