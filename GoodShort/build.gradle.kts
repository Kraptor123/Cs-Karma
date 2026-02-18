// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.
version = 2

cloudstream {
    authors     = listOf("ByAyzen")
    language    = "en"
    description = "GoodShort"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 0 // will be 3 if unspecified
    tvTypes = listOf("TvSeries") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://www.goodshort.com/public/favicon.ico"
}