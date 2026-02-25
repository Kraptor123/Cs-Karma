//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.byayzen

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
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
        Log.d("kraptor_Wco","url = $url")

        val request = app.get(url, referer = referer)

        val digerIstek = fixUrl(request.document.selectFirst("div#d-player a")?.attr("href") ?: request.document.selectFirst("iframe")?.attr("src") ?: "")

        val ikinciReq = app.get(digerIstek, referer = request.url)
        val script = ikinciReq.document.select("script").find {
            it.data().contains("getvidlink.php") || it.data().contains("token")
        }?.data() ?: ""

        Log.d("kraptor_Wco","script = $script")

        val vRegex = Regex("""v\s*[:=]\s*["']([^"']+)["']""")
        val embedRegex = Regex("""embed\s*[:=]\s*["']([^"']+)["']""")
        val hdRegex = Regex("""hd\s*[:=]\s*["']([^"']+)["']""")
        val fullhdRegex = Regex("""fullhd\s*[:=]\s*["']([^"']+)["']""")
        val tokenRegex = Regex("""token\s*[:=]\s*["']([^"']+)["']""")

        val vWco = vRegex.find(script)?.groupValues?.get(1)?.decodeUri()?.trim() ?: ""
        val embedWco = embedRegex.find(script)?.groupValues?.get(1)?.trim() ?: ""
        val hdWco = hdRegex.find(script)?.groupValues?.get(1)?.trim() ?: ""
        val fullhdWco = fullhdRegex.find(script)?.groupValues?.get(1)?.trim() ?: ""
        val tokenWco = tokenRegex.find(script)?.groupValues?.get(1)?.trim() ?: ""

        Log.d("kraptor_Wco","vWco = $vWco")
        Log.d("kraptor_Wco","embedWco = $embedWco")
        Log.d("kraptor_Wco","hdWco = $hdWco")
        Log.d("kraptor_Wco","fullhdWco= $fullhdWco")
        Log.d("kraptor_Wco","tokenWco = $tokenWco")

        if (vWco.isEmpty()) return

        val postIstegi = app.get("${mainUrl}/inc/embed/getvidlink.php",
            referer = digerIstek,
            params = mapOf(
                "v" to vWco,
                "embed" to embedWco,
                "hd" to hdWco,
                "fullhd" to fullhdWco,
                "token" to tokenWco,
            ),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).text

        Log.d("kraptor_Wco","postIstegi = $postIstegi")

        val mapper = try { mapper.readValue<WcoCevap>(postIstegi) } catch(e: Exception) { null } ?: return

        var baseHost = (mapper.server ?: mapper.cdn ?: "").replace("\\", "").trim()
        if (baseHost.isNotEmpty() && !baseHost.endsWith("/")) baseHost += "/"

        // Kalite etiketleri ile birlikte eşleştirme yapıldı
        val videoIstek = listOfNotNull(
            mapper.fullhd?.let { if (it.isNotEmpty()) Pair("${baseHost}getvid?evid=${it.removePrefix("/")}&json", "FHD") else null },
            mapper.hd?.let { if (it.isNotEmpty()) Pair("${baseHost}getvid?evid=${it.removePrefix("/")}&json", "HD") else null },
            mapper.enc?.let { if (it.isNotEmpty()) Pair("${baseHost}getvid?evid=${it.removePrefix("/")}&json", "SD") else null }
        )

        Log.d("kraptor_Wco","videoIstek = $videoIstek")

        videoIstek.forEach { (videoUrl, kalite) ->
            if (videoUrl.contains("null&json")) {
                return@forEach
            } else {
                Log.d("kraptor_Wco", "video = $videoUrl")

                val videoAl = app.get(videoUrl, referer = "${mainUrl}/", headers = mapOf("Origin" to mainUrl)).text

                Log.d("kraptor_Wco", "videoAl = $videoAl")

                val videomuz = videoAl.replace("\"", "").replace("\\", "").trim()

                Log.d("kraptor_Wco", "videomuz = $videomuz")

                if (videomuz.startsWith("http")) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} $kalite", // SD, HD, FHD eklemesi burada yapıldı
                            url = videomuz,
                            type = INFER_TYPE
                        ) {
                            this.referer = "${mainUrl}/"
                        }
                    )
                }
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
)