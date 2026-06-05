// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class LatanimePlugin: Plugin() {
    override fun load() {
        registerMainAPI(Latanime())
    }
}