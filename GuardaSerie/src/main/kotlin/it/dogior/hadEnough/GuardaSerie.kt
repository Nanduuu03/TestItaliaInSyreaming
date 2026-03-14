package it.dogior.hadEnough

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import it.dogior.hadEnough.extractors.DroploadExtractor
import it.dogior.hadEnough.extractors.SupervideoExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class GuardaSerie : MainAPI() {
    override var mainUrl = "https://guarda-serie.click"
    override var name = "GuardaSerie"
    override val supportedTypes = setOf(TvType.TvSeries)
    override var lang = "it"
    override val hasMainPage = true
    override val hasQuickSearch = false

    override val mainPage = mainPageOf(
        "$mainUrl/serietv-popolari/" to "Popolari",
        "$mainUrl/netflix-gratis/" to "Netflix",
        "$mainUrl/top-imdb/" to "Top IMDb",
        "$mainUrl/coming-soon/" to "In Arrivo...",
        "$mainUrl/animazione/" to "Animazione",
        "$mainUrl/avventura/" to "Avventura",
        "$mainUrl/azione/" to "Azione",
        "$mainUrl/commedia/" to "Commedia",
        "$mainUrl/crime/" to "Crime",
        "$mainUrl/documentario/" to "Documentario",
        "$mainUrl/dramma/" to "Dramma",
        "$mainUrl/drammatico/" to "Drammatico",
        "$mainUrl/fantascienza/" to "Fantascienza",
        "$mainUrl/fantastico/" to "Fantastico",
        "$mainUrl/fantasy/" to "Fantasy",
        "$mainUrl/famiglia/" to "Famiglia",
        "$mainUrl/giallo/" to "Giallo",
        "$mainUrl/guerra/" to "Guerra",
        "$mainUrl/horror/" to "Horror",
        "$mainUrl/intrattenimento/" to "Intrattenimento",
        "$mainUrl/miniserie-tv/" to "Miniserie",
        "$mainUrl/musicale/" to "Musicale",
        "$mainUrl/mistero/" to "Mistero",
        "$mainUrl/poliziesco/" to "Poliziesco",
        "$mainUrl/reality/" to "Reality",
        "$mainUrl/romantico/" to "Romantico",
        "$mainUrl/sentimentale/" to "Sentimentale",
        "$mainUrl/sitcom/" to "Sitcom",
        "$mainUrl/soap-opera/" to "Soap Opera",
        "$mainUrl/storico/" to "Storico",
        "$mainUrl/talent-show/" to "Talent Show",
        "$mainUrl/kids/" to "Kids",
        "$mainUrl/talk-show/" to "Talk Show",
        "$mainUrl/thriller/" to "Thriller",
        "$mainUrl/tv-show/" to "Tv Show"
    )

    data class EpisodeData(
        val season: Int,
        val episode: Int,
        val title: String?,
        val description: String?,
        val mirrors: List<MirrorLink>
    )

    data class MirrorLink(
        val name: String,
        val url: String
    )

    private fun getOriginalPoster(thumbUrl: String): String {
        return thumbUrl
            .replace("/thumb/60x85-0-85", "/posts")
            .replace(Regex("/thumb/\\d+x\\d+-\\d+-\\d+/"), "/posts/")
            .replace("/thumb/", "/posts/")
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data == mainUrl) {
            mainUrl
        } else {
            request.data
        }
        
        val doc = app.get(url).document
        val items = mutableListOf<SearchResponse>()
        
        if (request.data == mainUrl) {
            items.addAll(doc.select(".slider .item").mapNotNull { element ->
                val link = element.select("a").attr("href")
                val title = element.select("img").attr("alt")
                val thumbUrl = element.select("img").attr("src")
                val poster = getOriginalPoster(thumbUrl)
                
                if (link.isNotEmpty() && title.isNotEmpty()) {
                    newTvSeriesSearchResponse(title, fixUrl(link)) {
                        this.posterUrl = fixUrlNull(poster)
                        this.posterHeaders = emptyMap()
                        this.quality = null
                    }
                } else null
            })
        }
        
        if (request.data != mainUrl) {
            items.addAll(doc.select(".mlnew").mapNotNull { element ->
                val link = element.select(".mlnh-thumb a").attr("href")
                val title = element.select("h2 a").text()
                val thumbUrl = element.select("img").attr("src")
                val poster = getOriginalPoster(thumbUrl)
                
                if (link.isNotEmpty() && title.isNotEmpty()) {
                    newTvSeriesSearchResponse(title, fixUrl(link)) {
                        this.posterUrl = fixUrlNull(poster)
                        this.posterHeaders = emptyMap()
                        this.quality = null
                    }
                } else null
            })
        }
        
        val hasNext = doc.select(".pagenavi a:contains(Next)").isNotEmpty()
        
        return newHomePageResponse(
            HomePageList(request.name, items, isHorizontalImages = false),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post(
            "$mainUrl/index.php?do=search",
            data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "story" to query
            )
        ).document
        
        return doc.select(".mlnew").mapNotNull { element ->
            val link = element.select(".mlnh-thumb a").attr("href")
            val title = element.select("h2 a").text()
            val thumbUrl = element.select("img").attr("src")
            val poster = getOriginalPoster(thumbUrl)
            
            if (link.isNotEmpty() && title.isNotEmpty()) {
                newTvSeriesSearchResponse(title, fixUrl(link)) {
                    this.posterUrl = fixUrlNull(poster)
                    this.posterHeaders = emptyMap()
                }
            } else null
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        
        val title = doc.select("h1.front_title, .front_title").text()
            .replace("streaming", "")
            .trim()
        val poster = doc.select("#cover, .poster img, .tv_info_right img").attr("src")
        
        val plot = doc.select(".tv_info_right").text()
            .substringAfter("Trama")
            .substringBefore("!")
            .replace("trama completa", "")
            .replace("Trama completa", "")
            .replace("clicca qui", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        val ratingText = doc.select(".post-ratings .rating-value, .entry-imdb").text()
            .replace("IMDb", "")
            .trim()
        
        val yearText = doc.select(".tv_info_list ul:contains(Anno) li:last-child").text()
        val year = Regex("\\d{4}").find(yearText)?.value?.toIntOrNull()
        
        val genres = doc.select(".tv_info_list ul:contains(Categoria) li:last-child a").map { it.text() }
        val status = if (yearText.contains("In Lavorazione")) ShowStatus.Ongoing else ShowStatus.Completed
        
        val episodes = getEpisodes(doc, poster)
        
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = plot
            this.tags = genres
            this.year = year
            this.showStatus = status
            addScore(ratingText)
        }
    }

    private fun getEpisodes(doc: Document, poster: String?): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        val seasonTabs = doc.select(".tt_season ul li a")
        
        if (seasonTabs.isNotEmpty()) {
            for (seasonTab in seasonTabs) {
                val seasonNumber = seasonTab.text().toIntOrNull() ?: continue
                val seasonId = seasonTab.attr("href").removePrefix("#")
                val episodeContainer = doc.select("#$seasonId ul li")
                
                for (episodeItem in episodeContainer) {
                    val episodeLink = episodeItem.select("a").first()
                    val episodeText = episodeLink?.text()?.toIntOrNull() ?: continue
                    
                    val fullTitle = episodeLink?.attr("data-title") ?: ""
                    val (episodeTitle, episodeDescription) = if (fullTitle.contains(":")) {
                        val parts = fullTitle.split(":", limit = 2)
                        parts[0].trim() to parts.getOrNull(1)?.trim()
                    } else {
                        fullTitle to null
                    }
                    
                    val mirrors = episodeItem.select(".mirrors a.mr").map { 
                        MirrorLink(it.text(), it.attr("data-link"))
                    }.filter { it.url.isNotEmpty() }
                    
                    if (mirrors.isNotEmpty()) {
                        val episodeData = EpisodeData(seasonNumber, episodeText, episodeTitle, episodeDescription, mirrors)
                        episodes.add(
                            newEpisode(episodeData.toJson()) {
                                this.name = episodeTitle
                                this.description = episodeDescription
                                this.season = seasonNumber
                                this.episode = episodeText
                                this.posterUrl = fixUrlNull(poster)
                            }
                        )
                    }
                }
            }
        }
        
        if (episodes.isEmpty()) {
            val spoilers = doc.select(".su-spoiler")
            
            for (spoiler in spoilers) {
                val seasonTitle = spoiler.select(".su-spoiler-title").text()
                val seasonNumber = Regex("\\d+").find(seasonTitle)?.value?.toIntOrNull() ?: continue
                val content = spoiler.select(".su-spoiler-content")
                
                val lines = content.html().split("<br />")
                for (line in lines) {
                    val episodeMatch = Regex("(\\d+)x(\\d+)").find(line)
                    if (episodeMatch != null) {
                        val episodeNum = episodeMatch.groupValues[2].toInt()
                        
                        val mirrors = mutableListOf<MirrorLink>()
                        val supervideoMatch = Regex("href=\"([^\"]+)\">([^<]+)").findAll(line)
                        
                        for (match in supervideoMatch) {
                            val url = match.groupValues[1]
                            val name = match.groupValues[2]
                            if (url.isNotEmpty() && (url.contains("supervideo") || url.contains("dr0pstream"))) {
                                mirrors.add(MirrorLink(name, url))
                            }
                        }
                        
                        if (mirrors.isNotEmpty()) {
                            val episodeData = EpisodeData(seasonNumber, episodeNum, "Episodio $episodeNum", null, mirrors)
                            episodes.add(
                                newEpisode(episodeData.toJson()) {
                                    this.season = seasonNumber
                                    this.episode = episodeNum
                                    this.posterUrl = fixUrlNull(poster)
                                }
                            )
                        }
                    }
                }
            }
        }
        
        return episodes.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val episodeData = parseJson<EpisodeData>(data)
            
            episodeData.mirrors.forEach { mirror ->
                when {
                    mirror.url.contains("supervideo") -> {
                        SupervideoExtractor().getUrl(mirror.url, mainUrl, subtitleCallback, callback)
                    }
                    mirror.url.contains("dr0pstream") || mirror.url.contains("dropload") -> {
                        DroploadExtractor().getUrl(mirror.url, mainUrl, subtitleCallback, callback)
                    }
                    else -> {
                        loadExtractor(mirror.url, mainUrl, subtitleCallback, callback)
                    }
                }
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
