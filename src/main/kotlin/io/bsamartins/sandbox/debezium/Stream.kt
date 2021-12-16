package io.bsamartins.sandbox.debezium

import org.apache.kafka.streams.kstream.Named

fun named(name: String): Named {
    return Named.`as`(name)
}