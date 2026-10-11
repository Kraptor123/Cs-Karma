// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.cskarma.ortak.extractor.*

@CloudstreamPlugin
class FootreplaysPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Footreplays())
        FileMoonExtractor.DOMAINS.forEach { registerExtractorAPI(FileMoonExtractor(it)) }
        registerExtractorAPI(HQCloud())
        registerExtractorAPI(VkCom())
        registerExtractorAPI(VkExtractor())
        registerExtractorAPI(HQLinks())
    }
}