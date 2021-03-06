package dev.neeffect.nee.effects.tx

import io.vavr.collection.List
import io.vavr.control.Option
import io.vavr.kotlin.toVavrList

internal class DBException(msg: String) : RuntimeException(msg)

internal class DBLike {

    private var failOnNext: Boolean = false
    private var exceptionOnNext: Boolean = false

    private var connected = false
    private var txLevel = 0
    private var log: MutableList<DBOperation> = mutableListOf()
    private var answers: List<String> = List.empty()

    internal fun raiseExceptionOnNext(): Unit {
        fail()
        exceptionOnNext = true
    }

    internal fun fail(): Unit {
        failOnNext = true
    }

    internal fun unFail(): Unit {
        failOnNext = false
    }

    internal fun disconnect(): Unit {
        connected = false
        txLevel = 0
    }

    internal fun getLog() = log.toVavrList()

    internal fun connected() = connected

    internal fun appendAnswer(ans: String) {
        answers = answers.append(ans)
    }

    internal fun connect(): Boolean {
        return if (!failOnNext) {
            log(DBOperation.Connected(connected))
            connected = true
            true
        } else {
            log(DBOperation.Fail("(!) on connect"))
            false
        }
    }

    internal fun begin(): Boolean {
        return if (!failOnNext) {
            if (connected) {
                txLevel = txLevel + 1
                log(DBOperation.TxBegin(txLevel))
                true
            } else {
                log(DBOperation.Fail("DB not connected, cannot begin"))
                false
            }
        } else {
            log(DBOperation.Fail("(!) on begin transaction"))
            false
        }
    }

    internal fun continueTransaction(): Boolean {
        return if (!failOnNext) {
            if (connected) {
                if (txLevel > 0) {
                    log(DBOperation.TxCont(txLevel))
                    true
                } else {
                    log(DBOperation.Fail("Cannot continue transaction - there is none - level =  ${txLevel}"))
                    false
                }
            } else {
                log(DBOperation.Fail("DB not connected, cannot continue"))
                false
            }
        } else {
            log(DBOperation.Fail("(!) on continue transaction"))
            false
        }
    }

    internal fun commit(): Boolean {
        return if (!failOnNext) {
            if (connected) {
                if (txLevel > 0) {
                    txLevel = txLevel - 1
                    log(DBOperation.TxCommitted(txLevel))
                    true
                } else {
                    log(DBOperation.Fail("Cannot commit transaction - there is none - level =  ${txLevel}"))
                    false
                }
            } else {
                log(DBOperation.Fail("DB not connected, cannot commit"))
                false
            }
        } else {
            log(DBOperation.Fail("(!) on commit transaction"))
            false
        }
    }

    internal fun rollback(): Boolean {
        return if (!failOnNext) {
            if (connected) {
                if (txLevel > 0) {
                    txLevel = txLevel - 1
                    log(DBOperation.TxRollback(txLevel))
                    true
                } else {
                    log(DBOperation.Fail("Cannot rollback transaction - there is none - level =  ${txLevel}"))
                    false
                }
            } else {
                log(DBOperation.Fail("DB not connected, cannot rollback"))
                false
            }
        } else {
            log(DBOperation.Fail("(!) on rollback transaction"))
            false
        }
    }

    internal fun query(stmt: String): Option<String> {
        return if (!failOnNext) {
            if (connected) {
                log(DBOperation.Query(stmt))
                val res = answers.headOption()
                answers = answers.pop()
                res
            } else {
                log(DBOperation.Fail("DB not connected, cannot query"))
                Option.none<String>()
            }
        } else if (exceptionOnNext) {
            throw DBException("query $stmt failed")
        } else {
            log(DBOperation.Fail("(!) on query"))
            Option.none<String>()
        }
    }

    internal fun execute(stmt: String): Option<String> {
        return if (!failOnNext) {
            if (connected) {
                if (txLevel > 0) {
                    log(DBOperation.Execute(stmt))
                    val res = answers.headOption()
                    answers = answers.pop()
                    res
                } else {
                    log(DBOperation.Fail("Cannot execute stmt - there is none tx - level =  ${txLevel}"))
                    Option.none<String>()
                }
            } else {
                log(DBOperation.Fail("DB not connected, cannot execute"))
                Option.none<String>()
            }
        } else {
            log(DBOperation.Fail("(!) on rollback transaction"))
            Option.none<String>()
        }
    }

    private fun log(op: DBOperation) {
        log.add(op)
    }

    internal fun transactionLevel(): Int = this.txLevel

    internal fun close() {
        log(DBOperation.Close)
        if (!connected) {
            log(DBOperation.Fail("not connected"))
        }
        connected = false
    }

}


internal sealed class DBOperation(val msg: String) {
    class Connected(val before: Boolean) : DBOperation("Connected DB - before was ${before}")
    class TxBegin(val level: Int) : DBOperation("Transaction started - current level = ${level}")
    class TxCont(val level: Int) : DBOperation("Transaction continued - current level = ${level}")
    class TxRollback(val level: Int) : DBOperation("Transaction rolled bac - current level = ${level}")
    class TxCommitted(val level: Int) : DBOperation("Transaction committed - current level = ${level}")
    class Query(msg: String) : DBOperation("Query: $msg")
    class Execute(msg: String) : DBOperation("Exec: $msg")
    class Fail(msg: String) : DBOperation("Fail: ${msg}")
    object Close : DBOperation("Closed")
}
