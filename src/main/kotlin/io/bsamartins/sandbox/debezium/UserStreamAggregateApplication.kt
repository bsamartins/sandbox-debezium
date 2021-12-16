package io.bsamartins.sandbox.debezium

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.Joined

import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.state.KeyValueStore
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun main() {
//    val logger = LogManager.getLogger("streams")
    val timestampFormatter = DateTimeFormatter.ofPattern("YYYYMMddHHmmss")
    val timestamp = LocalDateTime.now().format(timestampFormatter)

    val props = Properties()
    props[StreamsConfig.APPLICATION_ID_CONFIG] = "io.bsamartins.debezium.user-aggregate-$timestamp  "
    props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
    props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String()::class.java
    props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String()::class.java
    props[StreamsConfig.POLL_MS_CONFIG] = Duration.ofSeconds(1).toMillis()

    val builder = StreamsBuilder()

    val debeziumKeySerde = jsonSerde<DebeziumChangeRecord>()
    val debeziumValueSerde = jsonSerde<DebeziumChangeRecordValue>()
    val debeziumPayloadSerde = jsonSerde<DebeziumPayloadChange>()
    val defaultKeySerde = Serdes.Integer()
    val userSerde = jsonSerde<User>()
    val userContactWrapperSerde = jsonSerde<UserContactWrapper>()
    val userContactAggregateSerde = jsonSerde<UserContactsAggregate>()
    val userAggregateSerde = jsonSerde<UserAggregate>()

    val userStream = builder.stream(Topic.USER_TOPIC, Consumed.with(debeziumKeySerde, debeziumValueSerde))
    val userContactStream =
        builder.stream(Topic.USER_CONTACT_TOPIC, Consumed.with(debeziumKeySerde, debeziumValueSerde))

    val userContactTempTable = userContactStream
        .map({ key, value ->
            val id = key.payload["id"].asInt()
            val payload = value.payload
            KeyValue(id, payload)
        }, named("user-contact-mapping"))
        .groupByKey(Grouped.with(defaultKeySerde, debeziumPayloadSerde))
        .aggregate(
            { null },
            { key, value, latest: UserContactWrapper? ->
                println("contact: key=${key}, contact=${value}, latest=${latest}")

                val userId: Int
                val contact: UserContact?
                if (value.after != null) {
                    contact = UserContact(
                        id = value.after["id"].asInt(),
                        userId = value.after["user_id"].asInt(),
                        contact = value.after["contact"].asText()
                    )
                    userId = contact.userId
                } else {
                    contact = null
                    userId = latest!!.contact!!.userId
                }

                UserContactWrapper(
                    id = key,
                    userId = userId,
                    contact = contact
                )
            },
            Materialized.`as`<Int, UserContactWrapper, KeyValueStore<Bytes, ByteArray>>(Topic.USER_CONTACT_TOPIC + "_table_temp")
                .withKeySerde(defaultKeySerde)
                .withValueSerde(userContactWrapperSerde)
        )

    val userContactTable = userContactTempTable.toStream()
        .map { _, latest: UserContactWrapper ->
            println("userContactTable map -> $latest")
            KeyValue(latest.userId, latest)
        }
        .groupByKey(Grouped.with(defaultKeySerde, userContactWrapperSerde))
        .aggregate(
            { UserContactsAggregate() },
            ::aggregateUserContact,
            Materialized.`as`<Int, UserContactsAggregate, KeyValueStore<Bytes, ByteArray>>(Topic.USER_CONTACT_TOPIC + "_table_aggregate")
                .withKeySerde(defaultKeySerde)
                .withValueSerde(userContactAggregateSerde)
        )

    val dddAggregate = userStream.map({ key, value ->
        val id = key.payload["id"].asInt()
        val payload = value.payload.after!!
        val user = User(
            id = payload["id"].asInt(),
            name = payload["name"].asText()
        )
        println("ddd-aggregate map -> $id")
        KeyValue(id, user)
    }, named("user-stream-mapping")).join(userContactTable, { value, contacts ->
        UserAggregate(
            id = value.id,
            name = value.name,
            contacts = contacts.contacts
        )
    }, Joined.with(defaultKeySerde, userSerde, userContactAggregateSerde))

    dddAggregate.to(Topic.USER_AGGREGATE_TOPIC, Produced.with(defaultKeySerde, userAggregateSerde))

    val topology = builder.build()
    val streamsInnerJoin = KafkaStreams(topology, props)
    streamsInnerJoin.start()
    println(topology.describe())
//    logger.info("Started streams")
}

private fun aggregateUserContact(
    key: Int,
    latest: UserContactWrapper,
    contactsAggregate: UserContactsAggregate
): UserContactsAggregate {
    val newContacts: List<UserContact> = if (latest.contact == null) {
        contactsAggregate.contacts.filter { it.id != latest.id }
    } else {
        val index = contactsAggregate.contacts.indexOfFirst { it.id == latest.id }
        if (index >= 0) {
            contactsAggregate.contacts
                .toMutableList()
                .apply { removeAt(index) }
        } else {
            contactsAggregate.contacts
                .toMutableList()
                .apply { add(latest.contact) }
        }
    }
    return UserContactsAggregate(contacts = newContacts)
}

data class UserAggregate(
    val id: Int,
    val name: String,
    val contacts: List<UserContact>
)

data class User(
    val id: Int,
    val name: String,
)

data class UserContact(
    val id: Int,
    val userId: Int,
    val contact: String,
)

data class UserContactsAggregate(
    val contacts: List<UserContact> = emptyList(),
)

data class UserContactWrapper(
    val id: Int,
    val userId: Int,
    val contact: UserContact?,
)

data class DebeziumChangeRecord(
    val schema: JsonNode,
    val payload: JsonNode,
)

data class DebeziumChangeRecordValue(
    val schema: JsonNode,
    val payload: DebeziumPayloadChange,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DebeziumPayloadChange(
    val before: JsonNode?,
    val after: JsonNode?,
    val source: JsonNode,
    val timestamp: Long,
)
