// ! Bu araç @Kraptor123 tarafından | @cs-kraptor için yazılmıştır.
version = 3

cloudstream {
    authors     = listOf("ByAyzen")
    language    = "tr"
    description = "COOKIE GEREKLİ! HESABINIZ YOK İSE KULLANAMAZSINIZ."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie") //Movie, AnimeMovie, TvSeries, Cartoon, Anime, OVA, Torrent, Documentary, AsianDrama, Live, NSFW, Others, Music, AudioBook, CustomMedia, Audio, Podcast,
    iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=filmbol.org"
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
}