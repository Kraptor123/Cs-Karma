// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.
version = 4

cloudstream {
    authors     = listOf("ByAyzen")
    language    = "ar"
    description = "An arabic site for watching turkish dramas."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://7ebtv.net/wp-content/uploads/2019/01/cropped-32349259_596013244109809_3526474166435840000_n-1-1-150x150.png"
}