// ! Bu araç @ByAyzen tarafından | @cs-karma için yazılmıştır.
version = 31

cloudstream {
    authors     = listOf("ByAyzen")
    language    = "fr"
    description = "Explorez les films, séries, collections, alertes et recommandations Movix."
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries", "Anime") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://movix.fun/&size=128"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("com.google.android.material:material:1.14.0")
}