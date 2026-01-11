// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 21

cloudstream {
    authors     = listOf("kraptor")
    language    = "en"
    description = "Streamed ile Canlı Spor Yayınlarını İzleyebilirsiniz."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://streamed.pk/&size=128"
}