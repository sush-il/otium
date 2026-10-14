package com.focusblock.domain

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMatcherTest {
    private val sites = setOf("instagram.com", "youtube.com")
    private val keywords = setOf("casino")

    @Test fun `blocks exact host`() {
        assertTrue(SiteMatcher.blockedReason("instagram.com", sites, keywords) != null)
    }

    @Test fun `blocks subdomain`() {
        assertTrue(SiteMatcher.blockedReason("https://m.youtube.com/watch?v=x", sites, keywords) != null)
        assertTrue(SiteMatcher.blockedReason("www.instagram.com/reels", sites, keywords) != null)
    }

    @Test fun `does not block a lookalike host`() {
        assertNull(SiteMatcher.blockedReason("notinstagram.com", sites, keywords))
        assertNull(SiteMatcher.blockedReason("youtube.com.evil.com", sites, keywords))
    }

    @Test fun `allows unrelated sites and empty text`() {
        assertNull(SiteMatcher.blockedReason("wikipedia.org", sites, keywords))
        assertNull(SiteMatcher.blockedReason("   ", sites, keywords))
    }

    @Test fun `blocks a keyword anywhere in the text`() {
        assertTrue(SiteMatcher.blockedReason("best online casino bonus", sites, keywords) != null)
        assertTrue(SiteMatcher.blockedReason("casino.org", sites, keywords) != null)
    }

    @Test fun `extractHost returns null for search phrases`() {
        assertNull(SiteMatcher.extractHost("how to focus better"))
        assertTrue(SiteMatcher.extractHost("https://news.ycombinator.com/item?id=1") == "news.ycombinator.com")
    }
}
