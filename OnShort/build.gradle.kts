// ! This Extension Made By @kraptor for CsKarma
version = 1

cloudstream {
    authors     = listOf("kraptor")
    language    = "en"
    description = "Discover dubbed and subtitled short dramas from multiple platforms and open every available episode on ONShort."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/

    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?sz=64&domain=https://onshort.net"
}