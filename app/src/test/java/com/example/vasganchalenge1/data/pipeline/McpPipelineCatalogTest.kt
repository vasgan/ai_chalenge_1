package com.example.vasganchalenge1.data.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpPipelineCatalogTest {

    @Test
    fun `cross server pipeline is available only when all required tools exist`() {
        val required = setOf(
            "github_get_user",
            "github_get_repo",
            "github_list_repo_issues",
            "summarize_github_report",
            "save_summary_to_file"
        )
        val missingUtility = required - setOf("summarize_github_report", "save_summary_to_file")

        val availableWithAll = McpPipelineCatalog.availableFor(required)
        val availableWithMissing = McpPipelineCatalog.availableFor(missingUtility)

        assertTrue(availableWithAll.any { it.name == "cross_server_github_report_flow" })
        assertFalse(availableWithMissing.any { it.name == "cross_server_github_report_flow" })
    }
}
