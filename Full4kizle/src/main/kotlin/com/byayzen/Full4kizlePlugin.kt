// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import com.kraptor.HotStreamExtractor
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Full4kizlePlugin: Plugin() {
    override fun load() {
        registerMainAPI(Full4kizle())
        registerExtractorAPI(HotStreamExtractor())
    }
}