// ! Bu araç @Kraptor123 tarafından | @CS-Karma için yazılmıştır.
package com.byayzen

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.OkRuSSL

@CloudstreamPlugin
class BasketballReplaysPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(BasketballReplays())
        registerExtractorAPI(FileMoon2())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(FileMoonIn())
        registerExtractorAPI(F75())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Bysedikamoum())
    }
}