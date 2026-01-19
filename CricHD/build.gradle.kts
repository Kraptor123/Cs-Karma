// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 2

cloudstream {
    authors     = listOf("kraptor")
    language    = "en"
    description = "Watch Live Cricket Streaming Online on our website CricHD. Crichd offer free cricket streams for every match on laptop, iphone, ipad and mobile."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://www.google.com/s2/favicons?sz=64&domain=crichd.asia"
}