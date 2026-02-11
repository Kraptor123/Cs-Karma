// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DramaFlixPlugin: Plugin() {
    override fun load() {
        registerMainAPI(DramaFlix())
    }
}