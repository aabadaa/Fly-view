package com.abada.flyView.core

/**
 * Entries by key where the last writer wins (a duplicate key replaces the old content),
 * and an old writer can't remove what a newer one put.
 */
class KeyedSlots<K, V> {
    private data class Slot<V>(val token: Long, val value: V)

    private val slots = LinkedHashMap<K, Slot<V>>()
    private var counter = 0L

    /** Puts [value] for [key], replacing the old one. Returns the token that owns the key now. */
    fun put(key: K, value: V): Long {
        val token = ++counter
        slots[key] = Slot(token, value)
        return token
    }

    /** Replaces the value only if [token] still owns [key]. Returns `false` if it was replaced meanwhile. */
    fun update(key: K, token: Long, value: V): Boolean {
        val slot = slots[key] ?: return false
        if (slot.token != token) return false
        slots[key] = Slot(token, value)
        return true
    }

    /** Removes [key] only if [token] still owns it. Returns `true` if it was removed. */
    fun remove(key: K, token: Long): Boolean {
        if (slots[key]?.token != token) return false
        slots.remove(key)
        return true
    }

    fun isOwner(key: K, token: Long): Boolean = slots[key]?.token == token
    operator fun get(key: K): V? = slots[key]?.value
    fun keys(): List<K> = slots.keys.toList()
    fun values(): List<V> = slots.values.map { it.value }
    val size: Int get() = slots.size
}
