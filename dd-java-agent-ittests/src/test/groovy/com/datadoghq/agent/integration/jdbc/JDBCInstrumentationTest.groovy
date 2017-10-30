package com.datadoghq.agent.integration.jdbc

import com.datadoghq.trace.DDTracer
import com.datadoghq.trace.writer.ListWriter
import io.opentracing.util.GlobalTracer
import org.apache.derby.jdbc.EmbeddedDriver
import org.h2.Driver
import org.hsqldb.jdbc.JDBCDriver
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Field
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

class JDBCInstrumentationTest extends Specification {

  ListWriter writer = new ListWriter()
  DDTracer tracer = new DDTracer(writer)

  @Shared
  private Map<String, Connection> connections

  def setupSpec() {
    Connection h2Connection = new Driver().connect("jdbc:h2:mem:integ-test", null)
    Connection hsqlConnection = new JDBCDriver().connect("jdbc:hsqldb:mem:integTest", null)
    Connection derbyConnection = new EmbeddedDriver().connect("jdbc:derby:memory:integTest;create=true", null)

    connections = [
      h2    : h2Connection,
      derby : derbyConnection,
      hsqldb: hsqlConnection,
    ]
  }

  def cleanupSpec() {
    connections.values().each {
      it.close()
    }
  }

  def setup() {
    try {
      GlobalTracer.register(tracer)
    } catch (final Exception e) {
      // Force it anyway using reflection
      final Field field = GlobalTracer.getDeclaredField("tracer")
      field.setAccessible(true)
      field.set(null, tracer)
    }
    writer.start()
    assert GlobalTracer.isRegistered()
  }

  @Unroll
  def "basic statement on #driver generates spans"() {
    setup:
    Statement statement = connection.createStatement()
    ResultSet resultSet = statement.executeQuery(query)

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    writer.size() == 1

    def trace = writer.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == 7

    cleanup:
    statement.close()

    where:
    driver   | connection                | query
    "h2"     | connections.get("h2")     | "SELECT 3"
    "derby"  | connections.get("derby")  | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | connections.get("hsqldb") | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
  }

  @Unroll
  def "prepared statement execute on #driver generates a span"() {
    setup:
    PreparedStatement statement = connection.prepareStatement(query)
    assert statement.execute()
    ResultSet resultSet = statement.resultSet

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    writer.size() == 1

    def trace = writer.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-prepared_statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == 7

    cleanup:
    statement.close()

    where:
    driver   | connection                | query
    "h2"     | connections.get("h2")     | "SELECT 3"
    "derby"  | connections.get("derby")  | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | connections.get("hsqldb") | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
  }

  @Unroll
  def "prepared statement query on #driver generates a span"() {
    setup:
    PreparedStatement statement = connection.prepareStatement(query)
    ResultSet resultSet = statement.executeQuery()

    expect:
    resultSet.next()
    resultSet.getInt(1) == 3
    writer.size() == 1

    def trace = writer.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-prepared_statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == 7

    cleanup:
    statement.close()

    where:
    driver   | connection                | query
    "h2"     | connections.get("h2")     | "SELECT 3"
    "derby"  | connections.get("derby")  | "SELECT 3 FROM SYSIBM.SYSDUMMY1"
    "hsqldb" | connections.get("hsqldb") | "SELECT 3 FROM INFORMATION_SCHEMA.SYSTEM_USERS"
  }

  @Unroll
  def "statement update on #driver generates a span"() {
    setup:
    Statement statement = connection.createStatement()
    def sql = connection.nativeSQL(query)

    expect:
    !statement.execute(sql)
    statement.updateCount == 0

    writer.size() == 1

    def trace = writer.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == 7

    cleanup:
    statement.close()

    where:
    driver   | connection                | query
    "h2"     | connections.get("h2")     | "CREATE TABLE S_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "derby"  | connections.get("derby")  | "CREATE TABLE S_DERBY (id INTEGER not NULL, PRIMARY KEY ( id ))"
    "hsqldb" | connections.get("hsqldb") | "CREATE TABLE PUBLIC.S_HSQLDB (id INTEGER not NULL, PRIMARY KEY ( id ))"
  }

  @Unroll
  def "prepared statement update on #driver generates a span"() {
    setup:
    def sql = connection.nativeSQL(query)
    PreparedStatement statement = connection.prepareStatement(sql)

    expect:
    statement.executeUpdate() == 0
    writer.size() == 1

    def trace = writer.firstTrace()
    trace.size() == 1
    def span = trace[0]

    span.context().operationName == "${driver}.query"
    span.serviceName == driver
    span.resourceName == query
    span.type == "sql"
    !span.context().getErrorFlag()
    span.context().parentId == 0


    def tags = span.context().tags
    tags["db.type"] == driver
    tags["span.kind"] == "client"
    tags["component"] == "java-jdbc-prepared_statement"

    tags["db.jdbc.url"].contains(driver)
    tags["span.origin.type"] != null

    tags["thread.name"] != null
    tags["thread.id"] != null
    tags.size() == 7

    cleanup:
    statement.close()

    where:
    driver   | connection                | query
    "h2"     | connections.get("h2")     | "CREATE TABLE PS_H2 (id INTEGER not NULL, PRIMARY KEY ( id ))"
    // Derby calls executeLargeUpdate from executeUpdate thus generating a nested span breaking this test.
    "hsqldb" | connections.get("hsqldb") | "CREATE TABLE PUBLIC.PS_HSQLDB (id INTEGER not NULL, PRIMARY KEY ( id ))"
  }
}
