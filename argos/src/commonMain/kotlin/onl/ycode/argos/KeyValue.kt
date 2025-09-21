package onl.ycode.argos

/**
 * Represents a key-value pair from command-line arguments.
 * Equality and hash code are based only on the key, making sets behave like maps
 * where later values overwrite earlier ones for the same key.
 */
class KeyValue(val key: String, val value: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyValue) return false
        return key == other.key
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

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

    override val size: Int get() = backing.size

    override fun iterator(): Iterator<KeyValue> = backing.values.iterator()

    override fun contains(element: KeyValue): Boolean = backing.containsKey(element.key)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Set<*>) return false
        return size == other.size && containsAll(other)
    }

    override fun hashCode(): Int = backing.values.sumOf { it.hashCode() }

    override fun toString(): String = backing.values.toString()
}

fun Collection<KeyValue>.toMap() = associate { it.key to it.value }
