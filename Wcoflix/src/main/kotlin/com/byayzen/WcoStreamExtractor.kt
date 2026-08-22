package com.byayzen

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Log

open class WcoStreamExtractor : ExtractorApi() {
    override val name = "WcoStream"
    override val mainUrl = "https://embed.wcostream.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("kraptor_Wco", url)
        val qp = url.substringAfter("?", "").split("&")
            .associate { val p = it.split("=", limit = 2); p[0] to (p.getOrNull(1) ?: "") }

        val fileRaw = qp["file"] ?: return
        val embed = qp["embed"] ?: ""

        val v: String
        val apiPath: String

        if (qp.containsKey("fullhd")) {
            val fullhdVal = qp["fullhd"] ?: "1"
            v = "$embed/${fileRaw.replace(".flv", ".mp4").replace("%2F", "/")}"
            apiPath = "$mainUrl/inc/embed/getvidlink.php?v=$v&embed=$embed&fullhd=$fullhdVal"
        } else {
            val hdVal = qp["hd"] ?: "1"
            v = fileRaw.replace(".flv", ".mp4").replace("%2F", "/")
            apiPath = "$mainUrl/inc/embed/getvidlink.php?v=$v&embed=$embed&hd=$hdVal"
        }

        Log.d("kraptor_Wco", v)

        val cevap = try {
            val response = app.get(
                apiPath,
                referer = url,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest")
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
            val subUrl = "$host/getvid?evid=${cevap.sub}"
            Log.d("kraptor_Wco", subUrl)
            subtitleCallback(SubtitleFile(lang = "en", url = subUrl))
        }

        listOfNotNull(
            cevap.fullhd?.takeIf { it.isNotEmpty() }?.let { it to "FHD" },
            cevap.hd?.takeIf { it.isNotEmpty() }?.let { it to "HD" },
            cevap.enc?.takeIf { it.isNotEmpty() }?.let { it to "SD" }
        ).forEach { (evid, kalite) ->
            Log.d("kraptor_Wco", "$kalite: $evid")
            try {
                val vidPath = "$host/getvid?evid=$evid&json"
                val raw = app.get(
                    vidPath,
                    referer = "$mainUrl/",
                    headers = mapOf("Origin" to mainUrl)
                ).text.trim().replace("\"", "").replace("\\", "")
                Log.d("kraptor_Wco", raw)

                if (raw.startsWith("http")) {
                    var videoUrl = raw
                    if (videoUrl.contains("/getvid?evid=")) {
                        try {
                            val resp = app.get(
                                videoUrl,
                                referer = host,
                                headers = mapOf("Origin" to host.removeSuffix("/"))
                            )
                            val finalUrl = resp.url
                            if (finalUrl.startsWith("http") && !finalUrl.contains("/getvid?evid=")) {
                                videoUrl = finalUrl
                            }
                        } catch (e: Exception) {
                            Log.d("kraptor_Wco", e.toString())
                        }
                    }
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "Wcoflix $kalite",
                            url = videoUrl,
                            type = INFER_TYPE
                        ) {
                            this.referer = "$mainUrl/"
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
    val enc: String? = null,
    val server: String? = null,
    val cdn: String? = null,
    val hd: String? = null,
    val fullhd: String? = null,
    val sub: String? = null
)