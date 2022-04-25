package searchengine.searchIndexer

import io.ktor.http.*
import libraries.Page
import libraries.PageRepository
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import searchengine.pagerank.Pagerank
import searchengine.pagerank.cUrl

class ComputeDoc(
    private val pagerankPage: Pagerank.PagerankPage,
    private val repoDoc: PageRepository.Page?,
    private val dbClient: PageRepository.Client
) {
    private val parse = if (repoDoc != null) Jsoup.parse(repoDoc.content) else null
    private val targetUrls = pagerankPage.backLinks.map { it.url } + pagerankPage.url

    private suspend fun getBacklinkAnchorText() = pagerankPage.backLinks.distinctBy { it.url }.mapNotNull {
        val searchedRepoDoc = dbClient.find(it.url).firstOrNull()
        if (searchedRepoDoc != null) anchorTextOnDoc(Jsoup.parse(searchedRepoDoc.content))
        else null
    }

    private fun anchorTextOnDoc(doc: Document): String {
        val links = doc.select("a")
        return links.mapNotNull {
            try {
                val url = Url(it.attr("href")).cUrl()
                if (url in targetUrls) it.text() else null
            } catch (e: Exception) {
                null
            }
        }.filter { it.isNotBlank() }.joinToString(" ")
    }


    suspend fun compute(totalDocsCount: Int): Page.Page {
        return Page.Page(pagerankPage.url,
            Page.Ranks(pagerankPage.rank.last(), pagerankPage.rank.last() * totalDocsCount),
            Page.Content(
                parse?.title() ?: "",
                parse?.select("meta[name=description]")?.attr("content") ?: "",
                listOf(), // possibly more words that may be relevant
                getBacklinkAnchorText(), // inner text of backlinks to this page
                parse?.select("b")?.map { it.text() } ?: listOf(),
                Page.Headings(parse?.select("h1")?.map { it.text() } ?: listOf(),
                    parse?.select("h2")?.map { it.text() } ?: listOf(),
                    parse?.select("h3")?.map { it.text() } ?: listOf(),
                    parse?.select("h4")?.map { it.text() } ?: listOf(),
                    parse?.select("h5")?.map { it.text() } ?: listOf(),
                    parse?.select("h6")?.map { it.text() } ?: listOf()),
                parse?.select("p")?.mapNotNull { if (it.text().count() > 40) it.text() else null } ?: listOf(),
            ))
    }


}