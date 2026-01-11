// ! Bu araç @ByAyzen tarafından | @cs-kraptor için yazılmıştır.
version = 5

cloudstream {
    authors     = listOf("ByAyzen")
    language    = "tr"
    description = "TVGarden ile yurtiçi ve yurtdışından aradığınız yerel ve legal kanalları izleyebilirsiniz."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://favicons.teamtailor-cdn.com/icon?size=80..120..200&url=tv.garden"
}