// ! Bu araç @Kraptor123 tarafından | @CSKARMA için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FilmatekPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Filmatek())
        registerExtractorAPI(FilmatekExtractor())
    }
}