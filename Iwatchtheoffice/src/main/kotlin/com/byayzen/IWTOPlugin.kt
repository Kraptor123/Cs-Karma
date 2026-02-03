// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class IWTOPlugin: Plugin() {
    override fun load() {
        registerMainAPI(IWTO())
    }
}