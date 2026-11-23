package com.bidaboss.healthy.data

/** Exécute un bloc dans une seule transaction : tout est enregistré, ou rien. */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
