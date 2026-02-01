package org.zhavoronkov.openrouter.proxy

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.never

@DisplayName("CorsFilter Tests")
class CorsFilterTest {

    @Test
    fun `doFilter should short-circuit options`() {
        val filter = CorsFilter()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)
        `when`(req.method).thenReturn("OPTIONS")

        filter.doFilter(req, resp, chain)

        verify(resp).setStatus(HttpServletResponse.SC_OK)
        verify(chain, never()).doFilter(req, resp)
    }

    @Test
    fun `doFilter should continue for non-options`() {
        val filter = CorsFilter()
        val req = mock(HttpServletRequest::class.java)
        val resp = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)
        `when`(req.method).thenReturn("GET")

        filter.doFilter(req, resp, chain)

        verify(chain).doFilter(req, resp)
    }
}
