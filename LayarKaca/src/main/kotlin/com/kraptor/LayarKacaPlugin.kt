// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.kraptor

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class LayarKacaPlugin: Plugin() {
    override fun load() {
        registerMainAPI(LayarKaca())
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(Co4nxtrl())
        registerExtractorAPI(Furher())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Turbovidhls())
    }
}