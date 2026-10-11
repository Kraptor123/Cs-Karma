// ! This Extension Made By @ByAyzen for GizliKeyif

package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class HackstorePlugin: Plugin() {
    override fun load() {
        registerMainAPI(Hackstore())
        registerExtractorAPI(VimeosExtractor())
        registerExtractorAPI(VideoApp())
        registerExtractorAPI(Goodstream())
        registerExtractorAPI(Hlswish())
    }
}