package onl.ycode.argos

/**
 * Represents a key-value pair from command-line arguments.
 * Equality and hash code are based only on the key, making sets behave like maps
 * where later values overwrite earlier ones for the same key.
 *
 * @property key The key part of the key-value pair
 * @property value The value part of the key-value pair
 */
class KeyValue(val key: String, val value: String) {
    /**
     * Compares this KeyValue with another object for equality.
     * Two KeyValue objects are equal if they have the same key (values are ignored).
     *
     * @param other The object to compare with
     * @return true if other is a KeyValue with the same key
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyValue) return false
        return key == other.key
    }

    /**
     * Returns the hash code for this KeyValue based only on its key.
     *
     * @return Hash code of the key
     */
    override fun hashCode(): Int {
        return key.hashCode()
    }

    /**
     * Returns a string representation of this KeyValue in the format "key=value".
     *
     * @return String representation of the key-value pair
     */
    override fun toString(): String {
        return "$key=$value"
    }
}

/**
 * A specialized immutable Set implementation for KeyValue that provides map-like replacement behavior.
 * When building from a collection, KeyValues with duplicate keys are replaced by the last occurrence
 * instead of being ignored (as would happen with a standard LinkedHashSet).
 *
 * This preserves insertion order and ensures the latest value for each key is kept.
 */
class KeyValueSet(values: Collection<KeyValue>) : AbstractSet<KeyValue>() {

    private val backing: LinkedHashMap<String, KeyValue> = LinkedHashMap<String, KeyValue>().apply {
        // Process values in order, allowing later entries to replace earlier ones with same key
        for (kv in values) {
            this[kv.key] = kv
        }
    }

    /**
     * Returns the number of unique keys in this set.
     */
    override val size: Int get() = backing.size

    /**
     * Returns an iterator over the KeyValue elements in this set, preserving insertion order.
     *
     * @return An iterator over the KeyValue elements
     */
    override fun iterator(): Iterator<KeyValue> = backing.values.iterator()

    /**
     * Checks if this set contains a KeyValue with the same key as the specified element.
     *
     * @param element The KeyValue to check for
     * @return true if a KeyValue with the same key exists in this set
     */
    override fun contains(element: KeyValue): Boolean = backing.containsKey(element.key)

    /**
     * Compares this KeyValueSet with another object for equality.
     *
     * @param other The object to compare with
     * @return true if other is a Set with the same elements
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Set<*>) return false
        return size == other.size && containsAll(other)
    }

    /**
     * Returns the hash code for this KeyValueSet.
     *
     * @return Hash code computed from all KeyValue elements
     */
    override fun hashCode(): Int = backing.values.sumOf { it.hashCode() }

    /**
     * Returns a string representation of this KeyValueSet.
     *
     * @return String representation of the set
     */
    override fun toString(): String = backing.values.toString()
}

/**
 * Converts a collection of KeyValue pairs to a Map.
 *
 * For KeyValueSet collections, this properly handles duplicate keys where the last value wins.
 *
 * @receiver Collection of KeyValue pairs
 * @return Map<String, String> with keys mapped to their values
 */
fun Collection<KeyValue>.toMap() = associate { it.key to it.value }
