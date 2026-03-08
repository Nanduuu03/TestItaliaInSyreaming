package it.dogior.hadEnough.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class GuardaHDExtractor : ExtractorApi() {
    override var name = "GuardaHD"
    override var mainUrl = "https://guardahd.stream"
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
            
            
            val iframe = document.select("iframe").attr("src")
            if (iframe.isNotEmpty()) {
                val iframeResponse = app.get(iframe, referer = url)
                val iframeDoc = iframeResponse.document
                
                val videoUrl = iframeDoc.select("video source").attr("src").ifEmpty {
                    iframeDoc.select("video").attr("src")
                }
                
                if (videoUrl.isNotEmpty() && videoUrl.contains(".m3u8")) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "GuardaHD",
                            url = videoUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = iframe
                            quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            
        }
    }
}
