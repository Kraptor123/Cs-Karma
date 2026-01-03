// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 3

cloudstream {
    authors     = listOf("kraptor")
    language    = "en"
    description = "Latest NBA Videos Regular season and Playoffs"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Live") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://avatars.mds.yandex.net/i?id=8b7c3551ce8cda8add11cbea791bb11b7e742f22-5332070-images-thumbs&n=13"
}