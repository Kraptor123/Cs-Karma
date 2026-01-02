// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FullMatchShowsPlugin: Plugin() {
    override fun load() {
        registerMainAPI(FullMatchShows())
        registerExtractorAPI(HgLink())
        registerExtractorAPI(StreamHgExtractor())
        registerExtractorAPI(Uasopt())
        registerExtractorAPI(Dumbalag())
        registerExtractorAPI(Cavanhabg())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(MixDropBz())
        registerExtractorAPI(MixDrop977())
        registerExtractorAPI(MixDropCh())
        registerExtractorAPI(MixDropTo())
    }
}