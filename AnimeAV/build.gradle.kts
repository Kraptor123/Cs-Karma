// ! Bu araç @Kraptor123 tarafından | @cs-kraptor için yazılmıştır.
version = 3

cloudstream {
    authors     = listOf("kraptor")
    language    = "es"
    description = "Disfruta de los últimos episodios y animes agregados en HD y Sub Español. Miles de series, películas y OVAs disponibles para ver online totalmente gratis."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Anime") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://www.google.com/s2/favicons?sz=64&domain=animeav1.com"
}