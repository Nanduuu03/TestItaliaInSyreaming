package it.dogior.hadEnough.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class DroploadExtractor : ExtractorApi() {
    override var name = "Dropload"
    override var mainUrl = "https://dr0pstream.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language" to "it-IT,it;q=0.9,en;q=0.8",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1",
            "Cache-Control" to "max-age=0"
        )

        try {
            Log.i("DroploadExtractor", "Trying to extract: $url")
            
            val response = app.get(url, headers = headers, referer = referer ?: mainUrl, interceptor = CloudflareKiller())
            
            if (!response.isSuccessful) {
                Log.e("DroploadExtractor", "HTTP error: ${response.code}")
                return
            }
            
            val body = response.body.string()
            Log.i("DroploadExtractor", "Page loaded, body length: ${body.length}")

            // Cerca eval packer
            val evalRegex = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*(?:r|d)\s*\)""")
            val evalBlock = evalRegex.find(body)?.value ?: run {
                Log.e("DroploadExtractor", "No eval block found, trying direct video...")
                
                // Prova a cercare video direttamente nella pagina
                val directVideo = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(body)?.groupValues?.get(1)
                if (!directVideo.isNullOrEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "Dropload",
                            url = directVideo,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    return
                }
                
                // Prova a cercare iframe
                val iframe = Regex("""<iframe.*?src=["']([^"']+)["']""").find(body)?.groupValues?.get(1)
                if (!iframe.isNullOrEmpty() && iframe.contains("dropload")) {
                    getUrl(iframe, url, subtitleCallback, callback)
                    return
                }
                return
            }

            var unpacked = evalBlock
            var videoUrl: String? = null

            for (i in 1..5) {
                unpacked = getAndUnpack(unpacked)
                videoUrl = Regex("""file\s*:\s*"([^"]+\.m3u8[^"]*)""")
                    .find(unpacked)?.groupValues?.get(1)
                
                if (!videoUrl.isNullOrEmpty()) {
                    Log.i("DroploadExtractor", "Found video URL at pass $i: $videoUrl")
                    break
                }
            }

            if (videoUrl.isNullOrEmpty()) {
                Log.e("DroploadExtractor", "No video URL found")
                return
            }

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Dropload",
                    url = videoUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
            
        } catch (e: Exception) {
            Log.e("DroploadExtractor", "Error: ${e.message}")
        }
    }
}
