package com.eraherm.hermchat.tools.search

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String = "",
)

interface SearchProvider {
    val id: String
    suspend fun search(query: String, limit: Int = 5): List<SearchHit>
}

class SearchProviderException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
