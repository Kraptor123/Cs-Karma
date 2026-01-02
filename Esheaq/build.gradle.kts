// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 5

cloudstream {
    authors     = listOf("kraptor")
    language    = "ar"
    description = "قصة عشق الأصلي لأحدث المسلسلات التركية والدراما المترجمة علي قصه عشق ، يمكنك من خلال موقع قصة عشق مشاهدة كل جديد في عالم الدراما التركية"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://w.esheaq.onl/&size=128"
}