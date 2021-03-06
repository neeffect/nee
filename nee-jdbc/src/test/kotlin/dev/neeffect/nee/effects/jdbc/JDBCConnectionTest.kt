package dev.neeffect.nee.effects.jdbc

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vavr.collection.List
import io.vavr.control.Option
import java.sql.Connection
import java.sql.ResultSet

class JDBCConnectionTest : DescribeSpec({
    describe("jdbc connection") {
        val cfg = JDBCConfig("org.h2.Driver", "jdbc:h2:mem:test_mem", "sa")
        val provider = { JDBCProvider(cfg) }

        describe("connection") {
            val conn = provider().getConnection()
            it("is created") {
                conn shouldNotBe null
            }
            it("gives access to physical jdbc") {
                conn.getResource() shouldNotBe null
            }
            it("reads from dual") {
                val res = simpleQuery(conn.getResource(), "select 4 from dual")
                res[0] shouldBe "4"
            }
            it("should start with no transaction") {
                conn.hasTransaction() shouldBe false
            }
            it("connection should close ") {
                conn.close()
            }
        }
        describe("creation of data") {
            val conn = provider().getConnection()
            it("should create table") {
                val res = simpleUpdate(
                    conn.getResource(),
                    """ CREATE TABLE PLANETS (
                            	ID INT not null,
	                            NAME VARCHAR not null,
                                PRIMARY KEY (ID));
                    """
                )
                res shouldBe 0
            }
            it("should insert row") {
                val res = simpleUpdate(
                    conn.getResource(),
                    """ INSERT INTO PLANETS VALUES (1,'MERCURY')"""
                )
                res shouldBe 1
            }
            it("should read single row") {
                val res = conn.getResource().q(
                    "SELECT NAME FROM PLANETS"
                )
                res[0] shouldBe "MERCURY"
            }

        }
        describe("transactions") {
            val conn = provider().getConnection()
            describe("simple trx") {
                val trx = conn.continueTx()
                it("should start  trx") {
                    trx.isRight shouldBe (true)
                }
                describe("for rollback") {
                    val trConnection = trx.get()
                    it("should insert row") {
                        val res = trConnection.getResource().x(" INSERT INTO PLANETS VALUES (2,'VENUS')")
                        res shouldBe 1
                    }
                    it("should read this row") {
                        val res = trConnection.getResource().q(
                            "SELECT NAME FROM PLANETS WHERE ID = 2;"
                        )
                        res[0] shouldBe ("VENUS")
                    }
                    it("after rollback") {
                        trConnection.rollback()
                        val res = conn.getResource().q(
                            "SELECT COUNT(ID) FROM PLANETS"
                        )
                        res[0] shouldBe ("1")
                    }
                }
            }
            describe("nested transaction") {
                val trx = conn.continueTx()
                it("should start  trx") {
                    trx.isRight shouldBe (true)
                }
                describe("level 1 trx") {
                    val trConnection = trx.get()
                    it("should insert row") {
                        val res = trConnection.getResource().x(" INSERT INTO PLANETS VALUES (2,'VENUS')")
                        res shouldBe 1
                    }
                    describe("level 2 trx") {
                        val trx2 = trConnection.begin()
                        it("trx2 object ") {
                            trx2.isRight shouldBe (true)
                        }
                        val trConnection2 = trx2.get()
                        it("should insert row") {
                            val res = trConnection2.getResource().x(" INSERT INTO PLANETS VALUES (3,'EARTH')")
                            res shouldBe 1
                        }
                        it("read inserted planets") {
                            val res = conn.getResource().q(
                                "SELECT COUNT(ID) FROM PLANETS"
                            )
                            res[0] shouldBe ("3")
                        }
                        it("rollback nested") {
                            trConnection2.rollback()
                            val res = conn.getResource().q(
                                "SELECT COUNT(ID) FROM PLANETS"
                            )
                            res[0] shouldBe ("2")
                        }
                        it("commit  upper") {
                            val committed = trConnection.commit()
                            committed.first shouldBe Option.none()
                        }
                        it("outer connection") {
                            val res = conn.getResource().q(
                                "SELECT COUNT(ID) FROM PLANETS"
                            )
                            res[0] shouldBe ("2")
                        }
                    }
                }
            }
        }
    }
})


fun simpleQuery(jdbcConnection: Connection, sql: String) = jdbcConnection.let { conn ->
    conn.createStatement().use { stmt ->
        val result = stmt.executeQuery(sql)
        resultToRow(result, List.empty())
    }
}


fun simpleUpdate(jdbcConnection: Connection, sql: String) = jdbcConnection.let { conn ->
    conn.createStatement().use { stmt ->
        stmt.executeUpdate(sql)
    }
}

fun Connection.q(sql: String) = simpleQuery(this, sql)

fun Connection.x(sql: String) = simpleUpdate(this, sql)

tailrec fun resultToRow(resultSet: ResultSet, rows: List<String>): List<String> =
    if (!resultSet.next()) {
        rows
    } else {
        val stringRes = resultSet.getString(1)
        resultToRow(resultSet, rows.append(stringRes))
    }
