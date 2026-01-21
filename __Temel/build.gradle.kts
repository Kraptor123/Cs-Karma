// ! Bu araç @Kraptor123 tarafından | @cs-karma için yazılmıştır.
version = 0

cloudstream {
    authors     = listOf("kraptor")
    language    = "tr"
    description = "filmdizi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://www.google.com/s2/favicons?sz=64&domain=yandex.com.tr"
}