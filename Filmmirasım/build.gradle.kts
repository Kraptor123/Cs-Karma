// ! Bu araç @ByAyzen tarafından | @cs-kraptor için yazılmıştır.
version = 1

cloudstream {
    authors     = listOf("byayzen")
    language    = "tr"
    description = "Kültür ve Turizm Bakanlığı, kültürel mirasımızın görsel belleğini oluşturan filmlerin muhafazası ve gelecek kuşaklara aktarılması amacıyla Sinema Genel Müdürlüğü bünyesinde oluşturduğu arşivdeki filmleri dijital ortama aktarmakta ve restore etmektedir. "

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Documentary") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://filmmirasim.ktb.gov.tr/favicon-r.png"
}