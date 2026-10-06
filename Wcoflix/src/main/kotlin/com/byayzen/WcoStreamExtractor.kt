package com.byayzen

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Log

open class WcoStreamExtractor : ExtractorApi() {
    override val name            = "WcoStream"
    override val mainUrl         = "https://embed.wcostream.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("kraptor_Wco", url)

        val vjsUrl = when {
            url.contains("video-js.php") -> url
            url.contains("index.php")    -> url.replace("index.php", "video-js.php")
            url.contains("embed.php")    -> url.replace("embed.php", "video-js.php")
            else                         -> url
        }

        val vjsResponse = try {
            app.get(
                vjsUrl,
                referer = referer ?: "$mainUrl/"
            )
        } catch (e: Exception) {
            Log.d("kraptor_Wco", e.toString())
            null
        }

        val cookies      = vjsResponse?.cookies ?: emptyMap()
        val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        val apiPath = vjsResponse?.text?.let { html ->
            Regex("""getJSON\(["']([^"']*getvidlink[^"']*)["']""").find(html)?.groupValues?.get(1)?.let {
                fixUrl(it)
            }
        } ?: run {
            val qp      = url.substringAfter("?", "").split("&")
                .associate { val p = it.split("=", limit = 2); p[0] to (p.getOrNull(1) ?: "") }
            val fileRaw = qp["file"] ?: return
            val embed   = qp["embed"] ?: ""
            val v       = if (qp.containsKey("fullhd")) {
                "$embed/${fileRaw.replace(".flv", ".mp4").replace("%2F", "/")}"
            } else {
                fileRaw.replace(".flv", ".mp4").replace("%2F", "/")
            }
            val hdParam = if (qp.containsKey("fullhd")) "fullhd=${qp["fullhd"] ?: "1"}" else "hd=${qp["hd"] ?: "1"}"
            "$mainUrl/inc/embed/getvidlink.php?v=$v&embed=$embed&$hdParam"
        }

        Log.d("kraptor_Wco", apiPath)

        val cevap = try {
            val response = app.get(
                apiPath,
                referer = vjsUrl,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                cookies = cookies
            )
            Log.d("kraptor_Wco", response.text)
            mapper.readValue<WcoCevap>(response.text)
        } catch (e: Exception) {
            Log.d("kraptor_Wco", e.toString())
            null
        } ?: return

        val host = (cevap.server ?: cevap.cdn ?: "").replace("\\", "").trim()
            .let { if (it.endsWith("/")) it else "$it/" }

        Log.d("kraptor_Wco", host)

        if (!cevap.sub.isNullOrEmpty()) {
            val subUrl = fixUrl("$host/getvid?evid=${cevap.sub}")
            Log.d("kraptor_Wco", subUrl)
            subtitleCallback(newSubtitleFile(lang = "en", url = subUrl))
        }

        val headersMap = if (cookieHeader.isNotEmpty()) {
            mapOf(
                "Referer" to "$mainUrl/",
                "Cookie"  to cookieHeader
            )
        } else {
            mapOf("Referer" to "$mainUrl/")
        }

        listOfNotNull(
            cevap.fullhd?.takeIf { it.isNotEmpty() }?.let { it to "FHD" },
            cevap.hd?.takeIf { it.isNotEmpty() }?.let { it to "HD" },
            cevap.enc?.takeIf { it.isNotEmpty() }?.let { it to "SD" }
        ).forEach { (evid, kalite) ->
            Log.d("kraptor_Wco", "$kalite: $evid")
            try {
                val vidPath = "$host/getvid?evid=$evid&json"
                val raw     = app.get(
                    vidPath,
                    referer = "$mainUrl/",
                    headers = mapOf("Origin" to mainUrl),
                    cookies = cookies
                ).text.trim().replace("\"", "").replace("\\", "")
                Log.d("kraptor_Wco", raw)

                val normalized = when {
                    raw.startsWith("http") -> raw.replace("//getvid", "/getvid")
                    raw.startsWith("/")    -> fixUrl(raw.replace("//getvid", "/getvid"))
                    else                   -> ""
                }

                if (normalized.isNotEmpty()) {
                    callback(
                        newExtractorLink(
                            source  = name,
                            name    = "Wcoflix $kalite",
                            url     = normalized,
                            type    = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "$mainUrl/"
                            this.headers = headersMap
                        }
                    )
                }
            } catch (e: Exception) {
                Log.d("kraptor_Wco", e.toString())
            }
        }
    }
}

data class WcoCevap(
    val enc: String?    = null,
    val server: String? = null,
    val cdn: String?    = null,
    val hd: String?     = null,
    val fullhd: String? = null,
    val sub: String?    = null
)