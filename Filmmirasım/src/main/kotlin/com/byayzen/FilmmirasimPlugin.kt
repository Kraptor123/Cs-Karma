// ! Bu araç @ByAyzen tarafından | @cs-kraptor için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class FilmmirasimPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Filmmirasim())
    }
}