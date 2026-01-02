// ! Bu araç @ByAyzen tarafından | @CS-KARMA için yazılmıştır.
version = 1

cloudstream {
    authors     = listOf("kraptor" ,"ByAyzen")
    language    = "en"
    description = "Watch drama online in high quality. Free download high quality drama. Various formats from 240p to 720p HD (or even 1080p). Feel Free To Watch!"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("AsianDrama") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://kisskh.id/&size=256"
}