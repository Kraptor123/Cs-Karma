// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 1
dependencies {
    implementation("androidx.room:room-ktx:2.8.4")
}

cloudstream {
    authors     = listOf("ByAyzen")
    language    = "es"
    description = "Mira tus Series, Películas y Animes en Latino Online!"
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://flixlatam.com&size=128"
}