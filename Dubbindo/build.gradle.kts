// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.
version = 1

cloudstream {
    authors     = listOf("ByAyzen")
    language    = "id"
    description = "Serial TV Bahasa Indonesia"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("AsianDrama") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://www.dubbindo.site/themes/dubbindo/img/icon.png"
}