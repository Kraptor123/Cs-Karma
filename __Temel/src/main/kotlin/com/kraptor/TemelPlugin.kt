// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TemelPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Temel())
        registerExtractorAPI(TemelExtractor())
    }
}