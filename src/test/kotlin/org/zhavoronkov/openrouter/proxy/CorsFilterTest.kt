package org.zhavoronkov.openrouter.proxy

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito

@DisplayName("CorsFilter Tests")
class CorsFilterTest {

    @Test
    fun `doFilter should short-circuit options`() {
        val filter = CorsFilter()
        val req = Mockito.mock(HttpServletRequest::class.java)
        val resp = Mockito.mock(HttpServletResponse::class.java)
        val chain = Mockito.mock(FilterChain::class.java)
        Mockito.`when`(req.method).thenReturn("OPTIONS")

        filter.doFilter(req, resp, chain)

        Mockito.verify(resp).setStatus(HttpServletResponse.SC_OK)
        Mockito.verify(chain, Mockito.never()).doFilter(req, resp)
    }

    @Test
    fun `doFilter should continue for non-options`() {
        val filter = CorsFilter()
        val req = Mockito.mock(HttpServletRequest::class.java)
        val resp = Mockito.mock(HttpServletResponse::class.java)
        val chain = Mockito.mock(FilterChain::class.java)
        Mockito.`when`(req.method).thenReturn("GET")

        filter.doFilter(req, resp, chain)

        Mockito.verify(chain).doFilter(req, resp)
    }
}
