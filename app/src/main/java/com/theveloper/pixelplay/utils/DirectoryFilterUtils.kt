package com.theveloper.pixelplay.utils

/**
 * Shared helper for computing allowed parent directories using the whitelist rules.
 */
object DirectoryFilterUtils {
    suspend fun computeAllowedParentDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String> = emptySet(),
        getAllParentDirs: suspend () -> List<String>,
        normalizePath: (String) -> String
    ): Pair<List<String>, Boolean> {
        val resolver = DirectoryRuleResolver(
            allowed = allowedDirs.map(normalizePath).toSet()
        )
        val allParentDirs = getAllParentDirs()
        val allowedParentDirs = allParentDirs.filter { parentDir ->
            resolver.isAllowed(normalizePath(parentDir))
        }
        return Pair(allowedParentDirs, true)
    }

    suspend fun computeAllowedParentDirs(
        allowedDirs: Set<String>,
        getAllParentDirs: suspend () -> List<String>,
        normalizePath: (String) -> String
    ): Pair<List<String>, Boolean> = computeAllowedParentDirs(
        allowedDirs = allowedDirs,
        blockedDirs = emptySet(),
        getAllParentDirs = getAllParentDirs,
        normalizePath = normalizePath
    )
}
