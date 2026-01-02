// ! Bu araç @ByAyzen tarafından | @CS-KARMA için yazılmıştır.
package com.byayzen

import com.byayzen.Dubbindo
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DubbindoPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Dubbindo())
    }
}