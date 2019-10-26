package pl.setblack.nee.effects.jdbc

import io.kotlintest.shouldBe
import io.kotlintest.specs.DescribeSpec
import io.kotlintest.shouldNotBe
import java.sql.Connection
import java.sql.ResultSet
import io.vavr.collection.List
import io.vavr.control.Option
import pl.setblack.nee.effects.tx.TxError

class JDBCConnectionTest : DescribeSpec({
    describe("jdbc connection") {
        val cfg = JDBCConfig("org.h2.Driver", "jdbc:h2:mem:test_mem", "sa")
        context("connection") {
            val conn = JDBCConnection(cfg)
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
        context("creation of data") {
            val conn = JDBCConnection(cfg)
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
        context("transactions") {
            val conn = JDBCConnection(cfg)
            context("simple trx") {
                val trx = conn.cont()
                it("should start  trx") {
                    trx.isRight shouldBe (true)
                }
                context("for rollback") {
                    val trConnection = trx.get()
                    it("should insert row") {
                        val res = trConnection.getResource().x(" INSERT INTO PLANETS VALUES (2,'VENUS')")
                        res shouldBe 1
                    }
                    it ("should read this row") {
                        val res = trConnection.getResource().q(
                            "SELECT NAME FROM PLANETS WHERE ID = 2;")
                        res[0] shouldBe ("VENUS")
                    }
                    it("after rollback") {
                        trConnection.rollback()
                        val res = conn.getResource().q(
                            "SELECT COUNT(ID) FROM PLANETS")
                        res[0] shouldBe ("1")
                    }
                }
            }
            context("nested transaction") {
                val trx = conn.cont()
                it("should start  trx") {
                    trx.isRight shouldBe (true)
                }
                context("level 1 trx") {
                    val trConnection = trx.get()
                    it("should insert row") {
                        val res = trConnection.getResource().x(" INSERT INTO PLANETS VALUES (2,'VENUS')")
                        res shouldBe 1
                    }
                    context("level 2 trx") {
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
                                "SELECT COUNT(ID) FROM PLANETS")
                            res[0] shouldBe ("3")
                        }
                        it("rollback nested") {
                            trConnection2.rollback()
                            val res = conn.getResource().q(
                                "SELECT COUNT(ID) FROM PLANETS")
                            res[0] shouldBe ("2")
                        }
                        it ("commit  upper") {
                            val committed = trConnection.commit()
                            committed.first shouldBe Option.none()
                        }
                        it ("outer connection")
                        val res = conn.getResource().q(
                            "SELECT COUNT(ID) FROM PLANETS")
                        res[0] shouldBe ("2")
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
