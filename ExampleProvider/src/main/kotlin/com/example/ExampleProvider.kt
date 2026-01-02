package com.example

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

class ExampleProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://example.com/" 
    override var name = "Example provider"
    override val supportedTypes = setOf(TvType.Movie)

    override var lang = "en"

    // Enable this when your provider has a main page
    override val hasMainPage = true

    // This function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf()
    }
}