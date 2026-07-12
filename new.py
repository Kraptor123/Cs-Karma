import os, shutil, re, sys

VARS_TEMPLATE = r"""
        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description     = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        val year            = document.selectFirst("div.extra span.C a")?.text()?.trim()?.toIntOrNull()
        val tags            = document.select("div.sgeneros a").map { it.text() }
        val scoreText       = document.selectFirst("span.dt_rating_vgs")?.text()?.trim()
        val duration        = document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("div.srelacionados article").mapNotNull { it.toRecommendationResult() }
        val actors          = document.select("span.valor a").map { Actor(it.text()) }
        val trailer         = Regex(""" + '"""embed\\/(.*)\\?rel"""' + r""").find(document.html())?.groupValues?.get(1)?.let { "https://www.youtube.com/embed/$it" }
"""

LOAD_MOVIE = """
    override suspend fun load(url: String): LoadResponse? {
        Log.d(name, "Load aşaması: $url")
        val document = app.get(url).document
        {vars}
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.tags            = tags
            this.score           = Score.from10(scoreText)
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

"""

LOAD_TV = """
    override suspend fun load(url: String): LoadResponse? {
        Log.d(name, "Load aşaması: $url")
        val document = app.get(url).document
        {vars}
        val episodes = document.select("div.episodios li a, div.season-list a").mapNotNull { ep ->
            val epUrl = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
            newEpisode(epUrl) {
                this.name = ep.text()?.trim() ?: "Bölüm"
                this.season = ep.selectFirst(".se-t, .season")?.text()?.trim()?.toIntOrNull() ?: 1
                this.episode = ep.selectFirst(".num-ep, .episode")?.text()?.trim()?.toIntOrNull() ?: 1
            }
        }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.tags            = tags
            this.score           = Score.from10(scoreText)
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

"""

LOAD_BOTH = """
    override suspend fun load(url: String): LoadResponse? {
        Log.d(name, "Load aşaması: $url")
        val document = app.get(url).document
        {vars}
        val isTv = document.select("div.episodios li, div.season-list, table.episodes").isNotEmpty()
        if (isTv) {
            val episodes = document.select("div.episodios li a, div.season-list a").mapNotNull { ep ->
                val epUrl = fixUrlNull(ep.attr("href")) ?: return@mapNotNull null
                newEpisode(epUrl) {
                    this.name = ep.text()?.trim()
                    this.season = ep.selectFirst(".se-t")?.text()?.toIntOrNull() ?: 1
                    this.episode = ep.selectFirst(".num-ep")?.text()?.toIntOrNull() ?: 1
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.plot            = description
                this.year            = year
                this.tags            = tags
                this.score           = Score.from10(scoreText)
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl       = poster
                this.plot            = description
                this.year            = year
                this.tags            = tags
                this.score           = Score.from10(scoreText)
                this.duration        = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))
        val isTv = this.selectFirst(".type, .episodios, .serie-tag") != null
        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

"""

def main():
    print("\n" + "Cloudstream Eklenti Kitapçığı".center(50))
    print("-" * 50)

    name = (sys.argv[1] if len(sys.argv) > 1 else input("Ad: ")).strip().replace("-", "")
    if not name: return
    url = (sys.argv[2] if len(sys.argv) > 2 else input("URL (Varsayılan: https://ornek.com): ")).strip() or "https://ornek.com"
    if url and not url.startswith("http"): url = "https://" + url
    pkg = input(f"Paket: ").strip() or "com.kraptor"
    if not pkg.startswith("com."): pkg = f"com.{pkg}"
    user = input("Yazar: ").strip() or "kraptor"
    lang = input("Dil: ").strip() or "tr"
    desc = input("Açıklama: ").strip() or f"{name} eklentisi."
    icon = input("Favicon: ").strip() or (f"https://www.google.com/s2/favicons?sz=64&domain={url}" if url else "https://")
    typ = input("Tür (1:Film, 2:TV, 3:Both): ").strip() or "3"
    is_search_same = input("Arama ile ana menü eşit mi? (Evet/Hayır): ").strip().lower() == "evet"

    base = os.getcwd()
    src, dst = os.path.join(base, "__New"), os.path.join(base, name)

    if os.path.exists(dst):
        if input(f"'{name}' silinsin mi? (y/n): ").lower() == 'y': shutil.rmtree(dst)
        else: return
    shutil.copytree(src, dst)

    root = os.path.join(dst, "src", "main", "kotlin")
    o_pkg, n_pkg = os.path.join(root, "com", "kraptor"), os.path.join(root, *pkg.split('.'))
    tmp_pkg = os.path.join(dst, "tmp_pkg_move")

    if os.path.exists(o_pkg):
        os.makedirs(tmp_pkg, exist_ok=True)
        for i in os.listdir(o_pkg): shutil.move(os.path.join(o_pkg, i), tmp_pkg)
        shutil.rmtree(os.path.join(root, "com"))
        os.makedirs(n_pkg, exist_ok=True)
        for i in os.listdir(tmp_pkg): shutil.move(os.path.join(tmp_pkg, i), n_pkg)
        shutil.rmtree(tmp_pkg)

    types = {"1": "TvType.Movie", "2": "TvType.TvSeries", "3": "TvType.Movie"}
    sets = {"1": "setOf(TvType.Movie)", "2": "setOf(TvType.TvSeries)", "3": "setOf(TvType.Movie, TvType.TvSeries)"}
    loads = {"1": LOAD_MOVIE, "2": LOAD_TV, "3": LOAD_BOTH}

    for r, ds, fs in os.walk(dst, topdown=False):
        for f in fs:
            p = os.path.join(r, f)
            try:
                with open(p, 'r', encoding='utf-8') as fl: c = fl.read()
                c = c.replace("@Kraptor123", f"@{user}").replace("New", name)
                c = re.sub(r'package\s+[\w.]+', f'package {pkg}', c)
                c = re.sub(r'authors\s*=\s*listOf\(.*?\)', f'authors     = listOf("{user}")', c)
                c = re.sub(r'language\s*=\s*".*?"', f'language    = "{lang}"', c)
                c = re.sub(r'description\s*=\s*".*?"', f'description = "{desc}"', c)
                c = re.sub(r'override var lang\s*=\s*".*?"', f'override var lang                 = "{lang}"', c)
                if url:
                    c = re.sub(r'(mainUrl\s*=\s*)".*?"', f'\\1"{url}"', c)
                    c = re.sub(r'(iconUrl\s*=\s*)".*?"', f'\\1"{icon}"', c)
                if f.endswith(".kt") and "Plugin" not in f:
                    c = c.replace("TvType.NSFW", types[typ]).replace("setOf(TvType.Movie)", sets[typ])
                    l_code = loads[typ].replace("{vars}", VARS_TEMPLATE)
                    c = re.sub(r'override suspend fun load\(url: String\): LoadResponse\? \{.*?(?=override suspend fun loadLinks)', l_code, c, flags=re.DOTALL)
                    if is_search_same:
                        c = c.replace("it.toSearchResult()", "it.toMainPageResult()")
                        c = re.sub(r'private fun Element\.toSearchResult\(\): SearchResponse\? \{.*?\}\s*\}', '', c, flags=re.DOTALL)
                with open(p, 'w', encoding='utf-8') as fl: fl.write(c)
            except: pass
            if "New" in f: os.rename(p, os.path.join(r, f.replace("New", name)))
        for d in ds:
            if "New" in d: os.rename(os.path.join(r, d), os.path.join(r, d.replace("New", name)))
    print(f"\n✅ '{name}' tamamlandı.")

if __name__ == "__main__": main()
