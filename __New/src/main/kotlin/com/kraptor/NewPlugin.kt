// ! This Extension Made By @Kraptor123 for GizliKeyif

package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NewPlugin: Plugin() {
    override fun load() {
        registerMainAPI(New())
    }
}