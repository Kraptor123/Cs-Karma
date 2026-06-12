// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FootreplaysPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Footreplays())
        registerExtractorAPI(HQCloud())
        registerExtractorAPI(VkCom())
        registerExtractorAPI(VkExtractor())
        registerExtractorAPI(HQLinks())
    }
}