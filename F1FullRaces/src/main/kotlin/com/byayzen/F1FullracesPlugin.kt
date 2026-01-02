// ! Bu araç @ByAyzen tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import android.content.Context
import com.lagradost.cloudstream3.extractors.Gofile
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class F1FullracesPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(F1Fullraces())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(MixDropBz())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(MixDropCh())
        registerExtractorAPI(MixDropTo())
        registerExtractorAPI(MixDrop977())
    }
}