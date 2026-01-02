// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class `7Ebtvplugin`: BasePlugin() {
    override fun load() {
        registerMainAPI(`7Ebtv`())
        registerExtractorAPI(LulusStream())
        registerExtractorAPI(Uqload())
        registerExtractorAPI(VidHidePro())
        registerExtractorAPI(VidHidePro1())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(VidHidePro2())
        registerExtractorAPI(VidHidePro3())
        registerExtractorAPI(VidHidePro4())
        registerExtractorAPI(VidHidePro7())
        registerExtractorAPI(VidHidePro5())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Dhtpre())
        registerExtractorAPI(Peytonepre())
        registerExtractorAPI(Dintezuvio())
        registerExtractorAPI(Movearnpre())
    }
}