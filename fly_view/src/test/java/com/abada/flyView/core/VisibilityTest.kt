package com.abada.flyView.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisibilityTest {
    @Test
    fun foregroundAndBackgroundFollowTheApp() {
        val fg = VisibilityConditions(appInForeground = true)
        val bg = VisibilityConditions(appInForeground = false)
        assertTrue(Visibility.Foreground.isVisible(fg))
        assertFalse(Visibility.Foreground.isVisible(bg))
        assertFalse(Visibility.Background.isVisible(fg))
        assertTrue(Visibility.Background.isVisible(bg))
        assertTrue(Visibility.Always.isVisible(fg))
        assertTrue(Visibility.Always.isVisible(bg))
    }

    @Test
    fun ownerNeedsTheMinimumLevel() {
        val activity = "activityA"
        val rule = Visibility.Owner(activity)
        assertFalse(rule.isVisible(VisibilityConditions(true, mapOf(activity to LifecycleLevel.Created))))
        assertTrue(rule.isVisible(VisibilityConditions(true, mapOf(activity to LifecycleLevel.Started))))
        assertTrue(rule.isVisible(VisibilityConditions(true, mapOf(activity to LifecycleLevel.Resumed))))
        assertFalse(rule.isVisible(VisibilityConditions(true, mapOf(activity to LifecycleLevel.Destroyed))))
    }

    @Test
    fun anUnknownOwnerIsNotVisible() {
        assertFalse(Visibility.Owner("gone").isVisible(VisibilityConditions(true)))
    }

    @Test
    fun ownersAreIndependent() {
        val c = VisibilityConditions(true, mapOf("a" to LifecycleLevel.Resumed, "b" to LifecycleLevel.Created))
        assertTrue(Visibility.Owner("a").isVisible(c))
        assertFalse(Visibility.Owner("b").isVisible(c))
    }

    @Test
    fun aMinimumOfResumedNeedsResumed() {
        val rule = Visibility.Owner("a", LifecycleLevel.Resumed)
        assertFalse(rule.isVisible(VisibilityConditions(true, mapOf("a" to LifecycleLevel.Started))))
        assertTrue(rule.isVisible(VisibilityConditions(true, mapOf("a" to LifecycleLevel.Resumed))))
    }
}

class KeyedSlotsTest {
    @Test
    fun aDuplicateKeyReplacesTheOldContent() {
        val slots = KeyedSlots<String, String>()
        slots.put("note", "old")
        slots.put("note", "new")
        assertEquals("new", slots["note"])
        assertEquals(1, slots.size)
    }

    @Test
    fun anOldWriterCanNotRemoveWhatANewerOnePut() {
        val slots = KeyedSlots<String, String>()
        val old = slots.put("note", "old")
        val new = slots.put("note", "new")
        assertFalse(slots.remove("note", old))
        assertEquals("new", slots["note"])
        assertTrue(slots.remove("note", new))
        assertNull(slots["note"])
    }

    @Test
    fun updateOnlyWorksForTheOwner() {
        val slots = KeyedSlots<String, String>()
        val old = slots.put("note", "old")
        val new = slots.put("note", "new")
        assertFalse(slots.update("note", old, "x"))
        assertTrue(slots.update("note", new, "y"))
        assertEquals("y", slots["note"])
        assertFalse(slots.isOwner("note", old))
    }

    @Test
    fun differentKeysCoexistInInsertionOrder() {
        val slots = KeyedSlots<String, Int>()
        slots.put("b", 1)
        slots.put("a", 2)
        assertEquals(listOf("b", "a"), slots.keys())
        assertEquals(listOf(1, 2), slots.values())
    }
}
