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
        val qp = url.substringAfter("?", "").split("&")
            .associate { val p = it.split("=", limit = 2); p[0] to (p.getOrNull(1) ?: "") }

        val fileRaw = qp["file"] ?: return
        val embed = qp["embed"] ?: ""
        val fullhd = qp["fullhd"] ?: "1"
        val v = "$embed/${fileRaw.replace(".flv", ".mp4").replace("%2F", "/")}"

        val cevap = try {
            mapper.readValue<WcoCevap>(
                app.get(
                    "$mainUrl/inc/embed/getvidlink.php?v=$v&embed=$embed&fullhd=$fullhd",
                    referer = url,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).text
            )
        } catch (e: Exception) {
            Log.d("kraptor_Wco", "hata = ${e.message}")
            null
        } ?: return

        val host = (cevap.server ?: cevap.cdn ?: "").replace("\\", "").trim()
            .let { if (it.endsWith("/")) it else "$it/" }

        if (!cevap.sub.isNullOrEmpty()) {
            subtitleCallback(SubtitleFile(lang = "en", url = "$host/getvid?evid=${cevap.sub}"))
        }

        listOfNotNull(
            cevap.fullhd?.takeIf { it.isNotEmpty() }?.let { it to "FHD" },
            cevap.hd?.takeIf { it.isNotEmpty() }?.let { it to "HD" },
            cevap.enc?.takeIf { it.isNotEmpty() }?.let { it to "SD" }
        ).forEach { (evid, kalite) ->
            try {
                val raw = app.get(
                    "$host/getvid?evid=$evid&json",
                    referer = "$mainUrl/",
                    headers = mapOf("Origin" to mainUrl)
                ).text.trim().replace("\"", "").replace("\\", "")

                if (raw.startsWith("http")) {
                    callback(
                        newExtractorLink(source = name, name = "Wcoflix $kalite", url = raw, type = INFER_TYPE) {
                            this.referer = "$mainUrl/"
                        }
                    )
                }
            } catch (e: Exception) {
                Log.d("kraptor_Wco", "video hatasi ($kalite) = ${e.message}")
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