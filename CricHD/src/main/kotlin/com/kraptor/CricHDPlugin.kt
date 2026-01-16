// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CricHDPlugin: Plugin() {
    override fun load() {
        registerMainAPI(CricHD())
        registerExtractorAPI(SoPlayExtractor())
    }
}