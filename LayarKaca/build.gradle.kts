// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 3

cloudstream {
    authors     = listOf("kraptor", "Phisher98", "Hexated")
    language    = "id"
    description = "Nonton dan download film & series terbaru di LK21. Streaming sub Indo gratis, kualitas HD. Tersedia drama Korea, anime, film barat, dan Asia lengkap!"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t0.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://tv7.lk21official.cc&size=256"
}