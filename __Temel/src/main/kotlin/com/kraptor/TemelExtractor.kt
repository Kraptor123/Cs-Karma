//Bu araç @kraptor tarafından | Cs-Karma için yazılmıştır!

package com.kraptor

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class TemelExtractor : ExtractorApi() {
    override val name = "Temel"
    override val mainUrl = "https://"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d("kraptor_Temel","url = $url")
        val document = app.get(url, referer = referer).document

        callback.invoke(
            newExtractorLink(
            name = this.name,
            source = this.name,
            url = "",
            type = INFER_TYPE, /* ExtractorLinkType.M3U8, ExtractorLinkType.VIDEO*/
            initializer = {
                this.referer = "${mainUrl}/"
            }
        ))
    }
}