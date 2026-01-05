package com.byayzen

import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class GnulaHDPlugin: Plugin() {
    override fun load() {
        registerMainAPI(GnulaHD())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(Tubeless())
        registerExtractorAPI(Simpulumlamerop())
        registerExtractorAPI(Urochsunloath())
        registerExtractorAPI(NathanFromSubject())
        registerExtractorAPI(Yipsu())
        registerExtractorAPI(MetaGnathTuggers())
        registerExtractorAPI(Voe1())
        registerExtractorAPI(CrystalTreatmentEast())
        registerExtractorAPI(Voe())
    }
}