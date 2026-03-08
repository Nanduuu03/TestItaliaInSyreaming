package it.dogior.hadEnough.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class MixdropExtractor : ExtractorApi() {
    override var name = "Mixdrop"
    override var mainUrl = "https://mixdrop.co"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val response = app.get(url, referer = referer ?: mainUrl)
            val document = response.document
            
            val script = document.select("script").find { it.data().contains("MDCore") }
            
            if (script != null) {
                val data = script.data()
                
                var videoUrl = Regex("MDCore\\.wurl\\s*=\\s*\"([^\"]+)\"").find(data)?.groupValues?.get(1)?.replace("\\/", "/")
                
                if (videoUrl.isNullOrEmpty()) {
                    videoUrl = Regex("wurl\\s*=\\s*\"([^\"]+)\"").find(data)?.groupValues?.get(1)?.replace("\\/", "/")
                }
                
                if (!videoUrl.isNullOrEmpty()) {
                    if (!videoUrl.startsWith("http")) {
                        videoUrl = "https:$videoUrl"
                    }
                    
                    if (videoUrl.contains(".m3u8")) {
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "Mixdrop",
                                url = videoUrl,
                                type = ExtractorLinkType.M3U8
                            ) {
                                this.referer = url
                                quality = Qualities.Unknown.value
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            
        }
    }
}
