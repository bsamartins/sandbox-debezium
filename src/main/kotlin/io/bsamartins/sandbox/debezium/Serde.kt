package io.bsamartins.sandbox.debezium

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

class JsonDeserializer<T : Any>(private val typeReference: TypeReference<T>) : Deserializer<T> {
    companion object {
        private val mapper = jacksonObjectMapper()
    }
    override fun deserialize(topic: String, data: ByteArray?): T? {
        return data?.let { mapper.readValue(data, typeReference) }
    }
}

class JsonSerializer<T> : Serializer<T> {
    companion object {
        private val mapper = jacksonObjectMapper()
    }
    override fun serialize(topic: String, data: T?): ByteArray? {
        return data?.let { mapper.writeValueAsBytes(it) }
    }
}

inline fun <reified T : Any> jsonSerde(): Serde<T> = Serdes.WrapperSerde(
    JsonSerializer(),
    JsonDeserializer(object : TypeReference<T>() {})
)
