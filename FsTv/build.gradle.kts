version = 1

cloudstream {
    authors     = listOf("kerimmkirac")
    language    = "en"
    description = "Watch live sport on tv with full HD streaming videos for football, tennis, basketball games world-wide and more. Get live scores, results, highlights on Fstv"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live")
    iconUrl = "https://www.google.com/s2/favicons?domain=fstv.us/live-tv.html&sz=%size%"
}