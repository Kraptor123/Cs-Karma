// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
version = 7

cloudstream {
    authors     = listOf("kraptor", "ByAyzen")
    language    = "tr"
    description = "1950’lerden günümüze… 40.000’den fazla maç ve 100.000’den fazla oyuncu."
    status  = 1
    tvTypes = listOf("Live")
    iconUrl = "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://footballia.net/tr&size=48"
}

// Gerekli Android kütüphanelerini ekliyoruz.
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.core:core-ktx:1.9.0")
}