
package nl.blauw.pipplayer

interface JsonDeserializable<T> {
    fun fromJsonString(jsonString: String): T
}