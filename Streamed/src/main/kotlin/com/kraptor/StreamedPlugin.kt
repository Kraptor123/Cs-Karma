// Plugin sınıfı için güncellenmiş kod
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.kraptor.EmbedStreams
import com.kraptor.Streamed

@CloudstreamPlugin
class StreamedPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Streamed())
        registerExtractorAPI(EmbedStreams(context))
    }
}