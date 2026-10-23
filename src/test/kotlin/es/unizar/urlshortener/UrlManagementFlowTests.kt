package es.unizar.urlshortener

import com.jayway.jsonpath.JsonPath.read
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class UrlManagementFlowTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `deactivating a link stops redirects`() {
        val (hash, key) = createManagedLink()

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "https://example.com/managed"))

        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer $key"))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isGone)
            .andExpect(jsonPath("$.status").value(410))

        // Repetir la desactivacion no debe dar error.
        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer $key"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `a wrong key cannot deactivate a link`() {
        val (hash, key) = createManagedLink()

        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer wrong-key"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.status").value(403))

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)

        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer $key"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deactivation requires a key`() {
        val (hash, _) = createManagedLink()

        mockMvc
            .perform(delete("/api/managed-links/$hash"))
            .andExpect(status().isForbidden)

        mockMvc
            .perform(delete("/api/managed-links/$hash").header("Authorization", "Bearer "))
            .andExpect(status().isForbidden)

        mockMvc
            .perform(get("/$hash"))
            .andExpect(status().isTemporaryRedirect)
    }

    @Test
    fun `unknown link cannot be deactivated`() {
        mockMvc
            .perform(delete("/api/managed-links/nonexistent").header("Authorization", "Bearer some-key"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `managed link creation validates urls`() {
        mockMvc
            .perform(
                post("/api/managed-links")
                    .contentType(APPLICATION_FORM_URLENCODED)
                    .param("url", "not-a-valid-url"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `each managed link has a different key`() {
        val first = createManagedLink()
        val second = createManagedLink()

        assertNotEquals(first.first, second.first)
        assertNotEquals(first.second, second.second)
    }

    private fun createManagedLink(): Pair<String, String> {
        val result =
            mockMvc
                .perform(
                    post("/api/managed-links")
                        .contentType(APPLICATION_FORM_URLENCODED)
                        .param("url", "https://example.com/managed"),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.hash").isString)
                .andExpect(jsonPath("$.managementKey").isString)
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()

        val hash = read<String>(result.response.contentAsString, "$.hash")
        val key = read<String>(result.response.contentAsString, "$.managementKey")
        return Pair(hash, key)
    }
}
