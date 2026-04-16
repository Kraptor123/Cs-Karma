// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MovixPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Movix())
        registerExtractorAPI(Uqload())
        registerExtractorAPI(UqloadIo())
        registerExtractorAPI(DoodStream())
        registerExtractorAPI(Vide0Net())
        registerExtractorAPI(DoodDoply())
        registerExtractorAPI(GhBrisk())
        registerExtractorAPI(VidHidePro())
        registerExtractorAPI(RyderJet())
        registerExtractorAPI(LuluVdo())
        registerExtractorAPI(VtbeTo())
        registerExtractorAPI(SaveFiles())
        registerExtractorAPI(DhcPlay())
        registerExtractorAPI(FileLionsLive())
        registerExtractorAPI(FileLionsOnline())
        registerExtractorAPI(FileLionsTo())
        registerExtractorAPI(KinogerBe())
        registerExtractorAPI(VidHideHub())
        registerExtractorAPI(VidHideVip())
        registerExtractorAPI(VidHidePre())
        registerExtractorAPI(SmoothPre())
        registerExtractorAPI(DhtPre())
        registerExtractorAPI(PeytonePre())
        registerExtractorAPI(MovearnPre())
        registerExtractorAPI(Dintezuvio())
        registerExtractorAPI(Vidzy())
        registerExtractorAPI(Coflix())
        registerExtractorAPI(VeevToExtractor())
        registerExtractorAPI(Embedseek())
        registerExtractorAPI(Ralphy())
        registerExtractorAPI(Uqloadcx())
    }
}