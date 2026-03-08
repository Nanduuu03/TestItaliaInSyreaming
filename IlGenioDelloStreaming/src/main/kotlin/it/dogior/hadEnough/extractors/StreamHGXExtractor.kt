package it.dogior.hadEnough.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class StreamHGXExtractor : ExtractorApi() {
    override var name = "StreamHG"
    override var mainUrl = "https://streamhgx.com"
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
            
            val videoUrl = document.select("video source").attr("src").ifEmpty {
                document.select("video").attr("src")
            }
            
            if (videoUrl.isNotEmpty() && videoUrl.contains(".m3u8")) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "StreamHG",
                        url = videoUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        quality = Qualities.Unknown.value
                    }
                )
                return
            }
            
            val script = document.select("script").find { it.data().contains("sources") }
            val jsVideoUrl = Regex("file:\"([^\"]+)\"").find(script?.data() ?: "")?.groupValues?.get(1)?.replace("\\/", "/")
            
            if (!jsVideoUrl.isNullOrEmpty() && jsVideoUrl.contains(".m3u8")) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "StreamHG",
                        url = jsVideoUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = url
                        quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (e: Exception) {
            // Ignora errori
        }
    }
}
