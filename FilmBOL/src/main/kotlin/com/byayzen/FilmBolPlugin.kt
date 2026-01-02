// ! Bu araç @byayzen tarafından | @cs-karma için yazılmıştır.
package com.byayzen

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin // <<< BU ÇOK ÖNEMLİ: Plugin import edildi

@CloudstreamPlugin
class FilmBolPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmBol())

        this.openSettings = { ctx: Context ->
            FilmbolAyarlar.showSettingsDialog(ctx as AppCompatActivity) {
                MainActivity.reloadHomeEvent.invoke(true)
            }
        }
    }
}