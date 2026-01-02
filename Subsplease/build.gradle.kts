// ! Bu araç @ByAyzen tarafından | @kekikanime için yazılmıştır.
version = 1

cloudstream {
    authors     = listOf("ByAyzen")
    language    = "en"
    description = "One stop shop for new animes!"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Anime") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://subsplease.org/favicon.ico"
}