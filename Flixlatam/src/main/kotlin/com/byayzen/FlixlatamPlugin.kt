// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Streamwish2
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FlixlatamPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Flixlatam())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(FileMoon2())
        registerExtractorAPI(FileMoonIn())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Bysedikamoum())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(HgLink())
        registerExtractorAPI(Mwish())
        registerExtractorAPI(Dwish())
        registerExtractorAPI(Ewish())
        registerExtractorAPI(WishembedPro())
        registerExtractorAPI(Kswplayer())
        registerExtractorAPI(Wishfast())
        registerExtractorAPI(Streamwish2())
        registerExtractorAPI(SfastwishCom())
        registerExtractorAPI(Strwish())
        registerExtractorAPI(Strwish2())
        registerExtractorAPI(FlaswishCom())
        registerExtractorAPI(Awish())
        registerExtractorAPI(Obeywish())
        registerExtractorAPI(Jodwish())
        registerExtractorAPI(Swhoi())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(UqloadsXyz())
        registerExtractorAPI(Doodporn())
        registerExtractorAPI(CdnwishCom())
        registerExtractorAPI(Asnwish())
        registerExtractorAPI(Nekowish())
        registerExtractorAPI(Nekostream())
        registerExtractorAPI(Swdyu())
        registerExtractorAPI(Wishonly())
        registerExtractorAPI(Playerwish())
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
        registerExtractorAPI(Vidspeeder())
        registerExtractorAPI(Travid())
        registerExtractorAPI(Moorearn())
    }
}