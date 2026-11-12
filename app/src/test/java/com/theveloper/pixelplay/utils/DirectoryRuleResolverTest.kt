package com.theveloper.pixelplay.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectoryRuleResolverTest {

    @Test
    fun emptyAllowedDirectories_blocksAllPaths() {
        val resolver = DirectoryRuleResolver(allowed = emptySet())
        assertTrue(resolver.isBlocked("/storage/emulated/0/Music"))
        assertTrue(resolver.isBlocked("/storage/emulated/0/Courses/Kotlin"))
        assertFalse(resolver.isAllowed("/storage/emulated/0/Music"))
    }

    @Test
    fun allowedCourseDirectory_allowsCourseAndSubfolders() {
        val resolver = DirectoryRuleResolver(allowed = setOf("/storage/emulated/0/Courses/Kotlin"))

        // Exact match
        assertTrue(resolver.isAllowed("/storage/emulated/0/Courses/Kotlin"))
        assertFalse(resolver.isBlocked("/storage/emulated/0/Courses/Kotlin"))

        // Subfolder match
        assertTrue(resolver.isAllowed("/storage/emulated/0/Courses/Kotlin/Chapter1"))
        assertFalse(resolver.isBlocked("/storage/emulated/0/Courses/Kotlin/Chapter1"))

        // Unselected sibling path is blocked
        assertFalse(resolver.isAllowed("/storage/emulated/0/Courses/Spanish"))
        assertTrue(resolver.isBlocked("/storage/emulated/0/Courses/Spanish"))

        // Unselected parent directory is not allowed
        assertFalse(resolver.isAllowed("/storage/emulated/0/Courses"))
        assertTrue(resolver.isBlocked("/storage/emulated/0/Courses"))
    }

    @Test
    fun trailingSlash_isHandledCleanly() {
        val resolver = DirectoryRuleResolver(allowed = setOf("/storage/emulated/0/Courses/Kotlin/"))

        assertTrue(resolver.isAllowed("/storage/emulated/0/Courses/Kotlin"))
        assertTrue(resolver.isAllowed("/storage/emulated/0/Courses/Kotlin/Lesson1"))
        assertFalse(resolver.isBlocked("/storage/emulated/0/Courses/Kotlin/Lesson1"))
    }
}
