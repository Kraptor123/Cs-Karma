package com.byayzen

object TVGardenesyalari {
    const val API_BASE_URL = "https://raw.githubusercontent.com/famelack/famelack-data/refs/heads/main/tv/raw/countries"
    
    val countries = listOf(
        "tr", "us", "uk", "de", "fr", "es", "it", "nl", "ru", "jp",
        "kr", "cn", "in", "br", "mx", "ar", "ca", "au", "az", "ch",
        "at", "be", "se", "no", "dk", "fi", "pt", "gr", "ua",
        "eg", "dz", "ma", "tn", "ly"
    )

    fun getFlagUrl(countryCode: String): String {
        val correctedCode = if (countryCode.lowercase() == "uk") "gb" else countryCode.lowercase()
        return "https://flagcdn.com/w320/$correctedCode.png"
    }

    fun extractYouTubeId(url: String): String {
        return when {
            url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&").substringBefore("#")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").substringBefore("#")
            url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("?").substringBefore("#")
            else -> url.substringAfterLast("/")
        }
    }
}