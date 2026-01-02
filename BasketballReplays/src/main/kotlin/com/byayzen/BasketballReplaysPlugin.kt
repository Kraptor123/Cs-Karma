// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.Odnoklassniki
import com.lagradost.cloudstream3.extractors.OkRuSSL
import com.lagradost.cloudstream3.extractors.Okrulink

@CloudstreamPlugin
class BasketballReplaysPlugin : Plugin() {
    override fun load(context: Context) {
        // Ana API
        registerMainAPI(BasketballReplays())
        registerExtractorAPI(FileMoon())
    }
}