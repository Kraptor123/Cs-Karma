version = 6

cloudstream {
    authors     = listOf("kraptor")
    language    = "tr"
    description = "AyzenTV ile canlı seyirin dorukları."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live")
    iconUrl = "https://raw.githubusercontent.com/Kraptor123/Cs-Karma/refs/heads/master/.github/logo/faviconAyzen.png"
}
