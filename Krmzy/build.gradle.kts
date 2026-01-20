// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 1

cloudstream {
    authors     = listOf("kraptor")
    language    = "ar"
    description = "قرمزي – المنصة رقم #1 لمشاهدة المسلسلات التركية مترجمة الى العربية وبجودة عالية، مع تحديثات يومية للحلقات فور صدورها. مجاني بالكامل وبدون إشتراك."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://www.google.com/s2/favicons?sz=64&domain=krmzy.org"
}