// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 2

cloudstream {
    authors     = listOf("kraptor")
    language    = "en"
    description = "Watch latest football full match replay of Premier League, Champions League, LaLiga, Serie A, Bundesliga, Ligue 1, Europa League, and more... FullMatchShows - Watch Football Full Match Replay and Shows"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://fullmatchshows.com&size=128"
}