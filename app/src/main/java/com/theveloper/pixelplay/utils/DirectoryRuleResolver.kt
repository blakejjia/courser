package com.theveloper.pixelplay.utils

/**
 * Resolves directory whitelist rules.
 * In Courser, only directories explicitly selected by the user (allowed roots)
 * are permitted. All other paths are blocked by default.
 */
class DirectoryRuleResolver(
    allowed: Set<String>,
    blocked: Set<String> = emptySet()
) {
    private val allowedRoots = allowed.mapNotNull { normalize(it) }.toSet()

    /**
     * Checks if a path is allowed under the whitelist rules.
     * A path is allowed if and only if allowedRoots is not empty and the path
     * is equal to or a subfolder of at least one allowed root.
     */
    fun isAllowed(path: String): Boolean {
        if (allowedRoots.isEmpty()) return false
        val normalized = normalize(path) ?: return false
        return allowedRoots.any { root -> isParentOrSame(root, normalized) }
    }

    /**
     * Checks if a path is blocked (i.e. NOT in the whitelist).
     */
    fun isBlocked(path: String): Boolean = !isAllowed(path)

    private fun normalize(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.endsWith("/")) path.dropLast(1) else path
    }

    private fun isParentOrSame(root: String, path: String): Boolean {
        if (!path.startsWith(root, ignoreCase = true)) return false
        if (path.length == root.length) return true
        return path[root.length] == '/'
    }
}
