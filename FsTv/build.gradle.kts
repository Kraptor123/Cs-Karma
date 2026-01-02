version = 7

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
    status  = 0 // will be 3 if unspecified
    tvTypes = listOf("Live")
    iconUrl = "https://raw.githubusercontent.com/Kraptor123/Cs-Karma/refs/heads/master/.github/logo/faviconFsTv.png"
}