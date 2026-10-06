package com.example.videoshield

import org.json.JSONArray
import org.json.JSONObject

data class RulePack(
    val schemaVersion: Int,
    val ruleVersion: Int,
    val name: String,
    val blockedHosts: List<String>,
    val blockedPathFragments: List<String>,
    val trackerFragments: List<String>,
    val adSelectors: List<String>,
    val skipSelectors: List<String>,
    val shortsSelectors: List<String>,
    val recommendationSelectors: List<String>,
    val commentSelectors: List<String>,
    val endScreenSelectors: List<String>,
    val openInAppSelectors: List<String>,
    val rawJson: String
) {
    // RulePack is immutable. DOM selectors are identical for every injection until the
    // pack changes, so build this JSON once instead of allocating JSONArray/JSONObject
    // trees again on every navigation/policy re-apply.
    private val cachedDomRulesJson: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        JSONObject().apply {
            put("version", ruleVersion)
            put("adSelectors", JSONArray(adSelectors))
            put("skipSelectors", JSONArray(skipSelectors))
            put("annoyances", JSONObject().apply {
                put("shorts", JSONArray(shortsSelectors))
                put("recommendations", JSONArray(recommendationSelectors))
                put("comments", JSONArray(commentSelectors))
                put("endScreen", JSONArray(endScreenSelectors))
                put("openInApp", JSONArray(openInAppSelectors))
            })
        }.toString()
    }

    fun domRulesJson(): String = cachedDomRulesJson

    companion object {
        const val SUPPORTED_SCHEMA = 1
        private const val MAX_RULES_PER_LIST = 256
        private const val MAX_RULE_LENGTH = 320

        fun parse(json: String): RulePack {
            require(json.length <= 256 * 1024) { "Rule pack is too large" }
            val root = JSONObject(json)
            val schema = root.getInt("schemaVersion")
            require(schema == SUPPORTED_SCHEMA) { "Unsupported rule schema $schema" }
            val version = root.getInt("ruleVersion")
            require(version > 0) { "Invalid rule version" }
            val name = root.optString("name", "Rules $version").take(120)
            val network = root.getJSONObject("network")
            val dom = root.getJSONObject("dom")
            val annoyances = dom.getJSONObject("annoyances")

            return RulePack(
                schemaVersion = schema,
                ruleVersion = version,
                name = name,
                blockedHosts = readList(network, "blockedHosts").map { it.lowercase() },
                blockedPathFragments = readList(network, "blockedPathFragments").map { it.lowercase() },
                trackerFragments = readList(network, "trackerFragments").map { it.lowercase() },
                adSelectors = readList(dom, "adSelectors"),
                skipSelectors = readList(dom, "skipSelectors"),
                shortsSelectors = readList(annoyances, "shorts"),
                recommendationSelectors = readList(annoyances, "recommendations"),
                commentSelectors = readList(annoyances, "comments"),
                endScreenSelectors = readList(annoyances, "endScreen"),
                openInAppSelectors = readList(annoyances, "openInApp"),
                rawJson = root.toString()
            )
        }

        private fun readList(parent: JSONObject, key: String): List<String> {
            val array = parent.optJSONArray(key) ?: JSONArray()
            require(array.length() <= MAX_RULES_PER_LIST) { "$key has too many rules" }
            val out = ArrayList<String>(array.length())
            for (i in 0 until array.length()) {
                val value = array.getString(i).trim()
                require(value.isNotEmpty() && value.length <= MAX_RULE_LENGTH) { "Invalid rule in $key" }
                out += value
            }
            return out.distinct()
        }
    }
}
