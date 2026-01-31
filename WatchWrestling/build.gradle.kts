// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 5

cloudstream {
    authors     = listOf("kraptor")
    language    = "en"
    description = "Watch WWE | WWE Raw | Smackdown Live"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://watchwrestling.ae/&size=32"
}