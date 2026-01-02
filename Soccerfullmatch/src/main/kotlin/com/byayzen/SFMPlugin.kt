// ! Bu araç @Kraptor123 tarafından | @cs-kraptor için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class SFMPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Soccerfullmatch())
        registerExtractorAPI(DhcPlayExtractor())
    }
}