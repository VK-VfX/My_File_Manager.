package com.vfxsal.filemanager.feature.files.util

import java.net.URLDecoder
import java.net.URLEncoder

private const val ENCODING = "UTF-8"

/**
 * Nav routes carry filesystem paths as a single path segment, but paths contain
 * '/' themselves, so the raw path is percent-encoded before being placed in the
 * route and decoded back out of the nav argument.
 */
fun encodePath(path: String): String = URLEncoder.encode(path, ENCODING)

fun decodePath(encodedPath: String): String = URLDecoder.decode(encodedPath, ENCODING)
