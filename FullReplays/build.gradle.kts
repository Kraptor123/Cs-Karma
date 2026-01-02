version = 1

cloudstream {
    authors     = listOf("kerimmkirac")
    language    = "en"
    description = "Watch latest football full match replay of Premier League, Champions League, LaLiga, Serie A, Bundesliga, Ligue 1, Europa League, and more..."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.fullreplays.com&sz=%size%"
}