// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KrmzyPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Krmzy())
        registerExtractorAPI(TurkveArabExtractor())
        registerExtractorAPI(ArabveTurk())
        registerExtractorAPI(iPlayerHls())
    }
}