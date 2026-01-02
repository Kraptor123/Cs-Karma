version = 1

cloudstream {
    authors     = listOf("kerimmkirac")
    language    = "en"
    description = "Formula 1 is one of the most popular and dramatic sports in the world. You can watch Formula One races replays videos online and enjoy every moment of this exciting sports event. We offer to watch F1 Full Races replays FREE in HD"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=fullraces.com&sz=%size%"
}