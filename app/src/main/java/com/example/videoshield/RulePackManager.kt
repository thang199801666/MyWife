package com.example.videoshield

import android.content.Context
import java.io.File

class RulePackManager(private val context: Context) {
    private val ruleDir = File(context.filesDir, "rules").apply { mkdirs() }
    private val activeFile = File(ruleDir, "active.json")
    private val previousFile = File(ruleDir, "previous.json")
    private val tempFile = File(ruleDir, "active.tmp")

    private val bundledJson: String by lazy {
        context.assets.open("rules/default_rules.json").bufferedReader().use { it.readText() }
    }
    private val bundledPack: RulePack by lazy { RulePack.parse(bundledJson) }

    @Volatile private var cachedActive: RulePack? = null

    @Synchronized
    fun active(): RulePack {
        cachedActive?.let { return it }
        val loaded = loadFile(activeFile) ?: bundledPack
        cachedActive = loaded
        return loaded
    }

    fun bundled(): RulePack = bundledPack

    fun isUsingBundled(): Boolean {
        val current = active()
        return current.ruleVersion == bundledPack.ruleVersion && current.rawJson == bundledPack.rawJson
    }

    fun hasRollback(): Boolean = previousFile.isFile

    @Synchronized
    fun install(json: String): InstallResult {
        val candidate = try {
            RulePack.parse(json)
        } catch (e: Exception) {
            return InstallResult(false, active().ruleVersion, "Invalid rules: ${e.message.orEmpty()}")
        }

        val current = active()
        if (candidate.ruleVersion <= current.ruleVersion) {
            return InstallResult(false, current.ruleVersion, "Rule version ${candidate.ruleVersion} is not newer than ${current.ruleVersion}")
        }

        return try {
            previousFile.writeText(current.rawJson)
            tempFile.writeText(candidate.rawJson)
            if (activeFile.exists() && !activeFile.delete()) throw IllegalStateException("Cannot replace active rules")
            if (!tempFile.renameTo(activeFile)) {
                activeFile.writeText(candidate.rawJson)
                tempFile.delete()
            }
            cachedActive = candidate
            InstallResult(true, candidate.ruleVersion, "Installed ${candidate.name}")
        } catch (e: Exception) {
            tempFile.delete()
            InstallResult(false, current.ruleVersion, "Install failed: ${e.message.orEmpty()}")
        }
    }

    @Synchronized
    fun rollback(): Boolean {
        val previous = loadFile(previousFile) ?: return false
        return try {
            val current = active()
            activeFile.writeText(previous.rawJson)
            previousFile.writeText(current.rawJson)
            cachedActive = previous
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun resetToBundled(): Boolean {
        return try {
            val current = active()
            if (current.ruleVersion != bundledPack.ruleVersion || activeFile.exists()) {
                previousFile.writeText(current.rawJson)
            }
            if (activeFile.exists() && !activeFile.delete()) return false
            tempFile.delete()
            cachedActive = bundledPack
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadFile(file: File): RulePack? = try {
        if (!file.isFile) null else RulePack.parse(file.readText())
    } catch (_: Exception) {
        null
    }

    data class InstallResult(val installed: Boolean, val version: Int, val message: String)
}
