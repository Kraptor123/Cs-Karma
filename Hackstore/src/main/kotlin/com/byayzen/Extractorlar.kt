package com.byayzen

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.newExtractorLink

private val userAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:157.0) Gecko/20100101 Firefox/157.0"

open class VimeosExtractor : ExtractorApi() {
    override val name            = "Vimeos"
    override val mainUrl         = "https://vimeos.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(
            url,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Referer"    to (referer ?: "$mainUrl/")
            )
        ).document

        val packedScript = document
            .selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data().orEmpty()
        val unpacked = JsUnpacker(packedScript).unpack() ?: return

        val videoUrl = Regex("""sources:\[\{file:"([^"]+)"""").find(unpacked)?.groupValues?.get(1)
            ?: return
        Log.d("Vimeos", "videoUrl=$videoUrl")

        Regex("\"(https://s1\\.vimeos\\.net/vtt/[^\"]+\\.vtt)\"")
            .findAll(unpacked)
            .map { it.groupValues[1] }
            .distinct()
            .forEach { subUrl ->
                val lang = subUrl.substringAfterLast("_").substringBeforeLast(".vtt")
                subtitleCallback(newSubtitleFile(lang = lang, url = subUrl))
            }

        callback(
            newExtractorLink(name, name, videoUrl, ExtractorLinkType.M3U8) {
                this.referer = "$mainUrl/"
                this.headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer"    to "$mainUrl/"
                )
            }
        )
    }
}

class VideoApp : ExtractorApi() {
    override val name            = "VideoApp"
    override val mainUrl         = "https://videoapp.zip"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(
            url,
            headers = mapOf("User-Agent" to userAgent)
        ).document

        val iframe   = document.selectFirst("iframe") ?: return
        val embedUrl = iframe.attr("src").ifEmpty { return }
        Log.d("Vimeos", "embedUrl=$embedUrl")
        VimeosExtractor().getUrl(embedUrl, url, subtitleCallback, callback)
    }
}

class Goodstream : ExtractorApi() {
    override val name            = "Goodstream"
    override val mainUrl         = "https://goodstream.one"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = app.get(
            url,
            headers = mapOf(
                "User-Agent" to userAgent,
                "Referer"    to (referer ?: "$mainUrl/")
            )
        ).text

        val m3u8 = Regex("""file:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)?.groupValues?.get(1)
            ?: return
        Log.d("Vimeos", "m3u8=$m3u8")

        callback(
            newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                this.referer = "$mainUrl/"
                this.headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer"    to "$mainUrl/"
                )
            }
        )
    }
}

class Hlswish : StreamWishExtractor() {
    override val name            = "Streamwish"
    override val mainUrl         = "https://hlswish.com"
    override val requiresReferer = true
}
