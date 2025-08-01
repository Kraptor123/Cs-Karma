package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AyzenTvPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AyzenTv())
    }
}