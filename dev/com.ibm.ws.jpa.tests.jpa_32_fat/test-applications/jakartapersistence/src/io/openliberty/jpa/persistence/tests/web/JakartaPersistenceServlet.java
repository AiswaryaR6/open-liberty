/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.jpa.persistence.tests.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import componenttest.app.FATServlet;
import io.openliberty.jpa.persistence.tests.models.Priority;
import io.openliberty.jpa.persistence.tests.models.Product;
import io.openliberty.jpa.persistence.tests.models.QueryDateTimeEntity;
import io.openliberty.jpa.persistence.tests.models.Ticket;
import io.openliberty.jpa.persistence.tests.models.TicketStatus;
import io.openliberty.jpa.persistence.tests.models.User;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.annotation.WebServlet;
import jakarta.transaction.UserTransaction;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = "/JakartaPersistence32")
public class JakartaPersistenceServlet extends FATServlet {
    @PersistenceContext(unitName = "JakartaPersistenceUnit")
    private EntityManager em;

    @Resource
    private UserTransaction tx;

    @Test
    public void testSetOperationsJPQL() {
        // UNION
        List<String> unionResult = em.createQuery(
                                                  "SELECT p.name FROM Person p " +
                                                  "UNION " +
                                                  "SELECT o.name FROM Organization o", String.class)
                        .getResultList();
        assertNotNull(unionResult);

        // INTERSECT
        List<String> intersectResult = em.createQuery(
                                                      "SELECT p.name FROM Person p " +
                                                      "INTERSECT " +
                                                      "SELECT o.name FROM Organization o", String.class)
                        .getResultList();
        assertNotNull(intersectResult);

        // EXCEPT
        List<String> exceptResult = em.createQuery(
                                                   "SELECT p.name FROM Person p " +
                                                   "EXCEPT " +
                                                   "SELECT o.name FROM Organization o", String.class)
                        .getResultList();
        assertNotNull(exceptResult);
    }

    /**
     * Method for testing || in JPQL queries.
     *
     * @throws Exception
     */
    @Test
    public void testJpqlConcat() throws Exception {
        deleteAllEntities(User.class);

        User user1 = User.of(1, "John", "Doe");
        User user2 = User.of(2, "Harry", "Potter");
        User user3 = User.of(3, "Hermione", "Granger");

        tx.begin();
        em.persist(user1);
        em.persist(user2);
        em.persist(user3);
        tx.commit();

        try {
            String concatJPQL = "SELECT u.firstName || ' ' || u.lastName FROM User u where u.lastName = ?1";
            String fullName = em.createQuery(concatJPQL, String.class)
                            .setParameter(1, "Doe")
                            .getSingleResult();

            String concatJPQLFrom = "SELECT u.firstName FROM User u where u.firstName || ' ' || u.lastName = ?1";
            String firstName = em.createQuery(concatJPQLFrom, String.class)
                            .setParameter(1, "Harry Potter")
                            .getSingleResult();

            assertEquals("John Doe", fullName);
            assertEquals("Harry", firstName);

        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * In previous version, Enumerated annotations were used for mapping Java Enum types to database column values.
     *
     * The Annotation @Enumerated are used with EnumType (ORDINAL or STRING)
     * EnumeratedValue in 3.2, Specifies that an annotated field of a Java enum type is the source of database column values for an enumerated mapping.
     * The annotated field must be declared final, and must be of type:
     * byte, short, or int for EnumType.ORDINAL, or
     * String for EnumType.STRING.
     * https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/enumeratedvalue
     *
     * @throws Exception
     */
    @Test
    public void testEnumeratedValue() throws Exception {

        Ticket ticket1 = Ticket.of(1, "ticket1", TicketStatus.OPEN, Priority.HIGH);
        Ticket ticket2 = Ticket.of(2, "ticket2", TicketStatus.CLOSED, Priority.LOW);
        Ticket ticket3 = Ticket.of(3, "ticket3", TicketStatus.CANCELLED, Priority.MEDIUM);

        // Checking SQL logs whether the mapping is done as below in the insert queries
        // TicketStatus.OPEN ENUM property value will be mapped to Table column value 0
        // Priority.HIGH property value will be mapped to Table column value 'H'
        tx.begin();
        em.persist(ticket1);
        em.persist(ticket2);
        em.persist(ticket3);
        tx.commit();

        /*
         * The INSERT statements present in the log is missing value mapping:
         * INSERT INTO TICKET (ID, NAME, PRIORITY, STATUS) VALUES (?, ?, ?, ?)
         * bind => [1, ticket1, HIGH, 0]
         * Persisted Values in column PRIORITY & STATUS, in MySQL Server do not match
         * the specification description
         */
        tx.begin();
        List<Ticket> results = em.createQuery("SELECT t FROM Ticket t ORDER BY t.id", Ticket.class).getResultList();
        tx.commit();

        System.out.println("***** testEnumeratedValue results: " + results);
        // Assert against status value of first element
        assertEquals(TicketStatus.OPEN, results.get(0).getStatus());
        assertFalse(TicketStatus.CLOSED.equals(results.get(0).getStatus()));
        assertFalse(TicketStatus.CANCELLED.equals(results.get(0).getStatus()));
        // Assert against status value of second element
        assertEquals(TicketStatus.CLOSED, results.get(1).getStatus());
        assertFalse(TicketStatus.OPEN.equals(results.get(1).getStatus()));
        assertFalse(TicketStatus.CANCELLED.equals(results.get(1).getStatus()));
        // Assert against status value of third element
        assertEquals(TicketStatus.CANCELLED, results.get(2).getStatus());
        assertFalse(TicketStatus.OPEN.equals(results.get(2).getStatus()));
        assertFalse(TicketStatus.CLOSED.equals(results.get(2).getStatus()));
        // Assert against priority value of first element
        assertEquals(Priority.HIGH, results.get(0).getPriority());
        assertFalse(Priority.MEDIUM.equals(results.get(0).getPriority()));
        assertFalse(Priority.LOW.equals(results.get(0).getPriority()));
        // Assert against priority value of second element
        assertEquals(Priority.LOW, results.get(1).getPriority());
        assertFalse(Priority.HIGH.equals(results.get(1).getPriority()));
        assertFalse(Priority.MEDIUM.equals(results.get(1).getPriority()));
        // Assert against priority value of third element
        assertEquals(Priority.MEDIUM, results.get(2).getPriority());
        assertFalse(Priority.HIGH.equals(results.get(2).getPriority()));
        assertFalse(Priority.LOW.equals(results.get(2).getPriority()));

    }

    /**
     * Specifies the precedence of null values within query result sets.
     * https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2#a5587
     *
     * @throws Exception
     */
    @Test
    public void testNullPrecedenceWithJPQL() throws Exception {
        deleteAllEntities(Product.class);
        Product product1 = Product.of("testSnapshot", "product1", 10.50f);
        Product product2 = Product.of(null, "product2", 20.50f);
        Product product3 = Product.of("sample products", "product3", 30.50f);
        tx.begin();
        em.persist(product1);
        em.persist(product2);
        em.persist(product3);
        tx.commit();

        /*
         * Specifies the precedence of null values within query result sets.
         */
        List<Product> productsNullFirst;
        try {

            tx.begin();
            productsNullFirst = em.createQuery("FROM Product ORDER BY description DESC NULLS FIRST",
                                               Product.class)
                            .getResultList();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
        assertEquals(3, productsNullFirst.size());
        assertEquals("Sorted based on 'description' in desc order with NULLS FIRST, Expecting first element to be 'product2'", "product2", productsNullFirst.get(0).name);

        /*
         * Null values occur at the end of the result set.
         */
        List<Product> productsNullLast;
        try {

            tx.begin();
            productsNullLast = em.createQuery("FROM Product ORDER BY description DESC NULLS LAST",
                                              Product.class)
                            .getResultList();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
        assertEquals(3, productsNullLast.size());
        assertEquals("Sorted based on 'description' in desc order with NULLS LAST, Expecting last element to be 'product2'", "product2", productsNullLast.get(2).name);

    }

    /**
     * Specifies the precedence of null values within query result sets.
     * https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2#nulls
     *
     * @throws Exception
     */
    @Test
    public void testNullPrecedenceWithCriteriaQuery() throws Exception {
        deleteAllEntities(Product.class);
        Product p1 = Product.of("testSnapshot", "product1", 10.50f);
        Product p2 = Product.of(null, "product2", 20.50f);
        Product p3 = Product.of("sample products", "product3", 30.50f);
        tx.begin();
        em.persist(p1);
        em.persist(p2);
        em.persist(p3);
        tx.commit();

        /*
         * Null values occur at the beginning of the result set.
         */
        List<Product> productsNullFirst;
        try {
            tx.begin();
            CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
            CriteriaQuery<Product> criteriaQuery = criteriaBuilder.createQuery(Product.class);
            Root<Product> from = criteriaQuery.from(Product.class);
            CriteriaQuery<Product> select = criteriaQuery.select(from);
            criteriaQuery.orderBy(criteriaBuilder.desc(from.get("description"), Nulls.FIRST));
            productsNullFirst = em.createQuery(criteriaQuery).getResultList();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
        assertEquals(3, productsNullFirst.size());
        assertEquals("Sorted based on 'description' in desc order with NULLS FIRST, Expecting first element to be 'product2'", "product2", productsNullFirst.get(0).name);

        /*
         * Null values occur at the end of the result set.
         */
        List<Product> productsNullLast;
        try {

            tx.begin();
            CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
            CriteriaQuery<Product> criteriaQuery = criteriaBuilder.createQuery(Product.class);
            Root<Product> from = criteriaQuery.from(Product.class);
            CriteriaQuery<Product> select = criteriaQuery.select(from);
            criteriaQuery.orderBy(criteriaBuilder.desc(from.get("description"), Nulls.LAST));
            productsNullLast = em.createQuery(criteriaQuery).getResultList();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
        assertEquals(3, productsNullLast.size());
        assertEquals("Sorted based on 'description' in desc order with NULLS LAST, Expecting last element to be 'product2'", "product2", productsNullLast.get(2).name);
    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the calendar YEAR from java.time.LocalDate
     *
     * @throws Exception
     */
    @Test
    public void testExtractYearFromLocalData() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateField<Integer> yearLocalDateField = jakarta.persistence.criteria.LocalDateField.YEAR;
        jakarta.persistence.criteria.Expression<Integer> yearExpression = criteriaBuilder.extract(yearLocalDateField, from.get("localDateData"));
        criteriaQuery.select(yearExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals("Extracted Year should be 2021", Integer.valueOf(2021), result.get(1));
        assertEquals("Extracted Year should be 2020", Integer.valueOf(2020), result.get(2));
        assertEquals("Extracted Year should be 2022", Integer.valueOf(2022), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the QUARTER of the year numbered from 1 to 4 from java.time.LocalDate
     *
     * @throws Exception
     */
    @Test
    public void testExtractQuarterFromLocalData() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateField<Integer> quarterLocalDateField = jakarta.persistence.criteria.LocalDateField.QUARTER;
        jakarta.persistence.criteria.Expression<Integer> quarterExpression = criteriaBuilder.extract(quarterLocalDateField, from.get("localDateData"));
        criteriaQuery.select(quarterExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals("Extracted Quarter should be 1", Integer.valueOf(1), result.get(1));
        assertEquals("Extracted Quarter should be 4", Integer.valueOf(4), result.get(2));
        assertEquals("Extracted Quarter should be 2", Integer.valueOf(2), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the MONTH of the year numbered from 1 from java.time.LocalDate
     *
     * @throws Exception
     */
    @Test
    public void testExtractMonthFromLocalData() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateField<Integer> monthLocalDateField = jakarta.persistence.criteria.LocalDateField.MONTH;
        jakarta.persistence.criteria.Expression<Integer> monthExpression = criteriaBuilder.extract(monthLocalDateField, from.get("localDateData"));
        criteriaQuery.select(monthExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals("Extracted Month should be 1", Integer.valueOf(1), result.get(1));
        assertEquals("Extracted Month should be 12", Integer.valueOf(12), result.get(2));
        assertEquals("Extracted Month should be 6", Integer.valueOf(6), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the DAY of the year numbered from 1 from java.time.LocalDate
     *
     * @throws Exception
     */
    @Test
    public void testExtractDayFromLocalData() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateField<Integer> dayLocalDateField = jakarta.persistence.criteria.LocalDateField.DAY;
        jakarta.persistence.criteria.Expression<Integer> dayExpression = criteriaBuilder.extract(dayLocalDateField, from.get("localDateData"));
        criteriaQuery.select(dayExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals("Extracted day should be 1", Integer.valueOf(1), result.get(1));
        assertEquals("Extracted day should be 31", Integer.valueOf(31), result.get(2));
        assertEquals("Extracted day should be 7", Integer.valueOf(7), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract ISO-8601 week number java.time.LocalDate
     *
     * @throws Exception
     */
    @Test
    public void testExtractWeekFromLocalData() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Number> criteriaQuery = criteriaBuilder.createQuery(Number.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateField<Integer> weekLocalDateField = jakarta.persistence.criteria.LocalDateField.WEEK;
        jakarta.persistence.criteria.Expression<Integer> weekExpression = criteriaBuilder.extract(weekLocalDateField, from.get("localDateData"));
        criteriaQuery.select(weekExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Number> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        System.out.println("***** testExtractWeekFromLocalData **** results: " + result);
        assertEquals(null, result.get(0));
        assertEquals("Extracted Week should be 0", Long.valueOf(0), Long.valueOf(result.get(1).longValue()));
        assertEquals("Extracted Week should be 53", Long.valueOf(53), Long.valueOf(result.get(2).longValue()));
        assertEquals("Extracted week should be 23", Long.valueOf(23), Long.valueOf(result.get(3).longValue()));
    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the The hour of the day in 24-hour time, numbered from 0 to 23 from java.time.LocalTime
     *
     * @throws Exception
     */
    @Test
    public void testExtractHourFromLocalTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalTimeField<Integer> hourLocalTimeField = jakarta.persistence.criteria.LocalTimeField.HOUR;
        jakarta.persistence.criteria.Expression<Integer> hourExpression = criteriaBuilder.extract(hourLocalTimeField, from.get("localTimeData"));
        criteriaQuery.select(hourExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();

        System.out.println("******** testExtractHourFromLocalTime *******" + result);
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals(Integer.valueOf(0), result.get(1));
        assertEquals(Integer.valueOf(1), result.get(2));
        assertEquals(Integer.valueOf(12), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract The minute of the hour, numbered from 0 to 59 from java.time.LocalTime
     *
     * @throws Exception
     */
    @Test
    public void testExtractMinuteFromLocalTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalTimeField<Integer> minuteLocalTimeField = jakarta.persistence.criteria.LocalTimeField.MINUTE;
        jakarta.persistence.criteria.Expression<Integer> minuteExpression = criteriaBuilder.extract(minuteLocalTimeField, from.get("localTimeData"));
        criteriaQuery.select(minuteExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals(Integer.valueOf(30), result.get(1));
        assertEquals(Integer.valueOf(59), result.get(2));
        assertEquals(Integer.valueOf(0), result.get(3));
    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract The second of the minute, numbered from 0 to 59, including a fractional part representing fractions of a second java.time.LocalTime
     *
     * @throws Exception
     */
    @Test
    public void testExtractSecondFromLocalTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(00, 0), LocalDateTime.of(2020, 01, 01, 00, 0));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 0), LocalDateTime.of(2120, 01, 01, 00, 0));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Double> criteriaQuery = criteriaBuilder.createQuery(Double.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalTimeField<Double> secondLocalTimeField = jakarta.persistence.criteria.LocalTimeField.SECOND;
        jakarta.persistence.criteria.Expression<Double> secondExpression = criteriaBuilder.extract(secondLocalTimeField, from.get("localTimeData"));
        criteriaQuery.select(secondExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Double> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals(Double.valueOf(0), result.get(1));
        assertEquals(Double.valueOf(0), result.get(2));
        assertEquals(Double.valueOf(0), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the calendar YEAR from java.time.LocalDateTime
     *
     * @throws Exception
     */
    @Test
    public void testExtractYearFromLocalDateTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateTimeField<Integer> yearLocalDateField = jakarta.persistence.criteria.LocalDateTimeField.YEAR;
        jakarta.persistence.criteria.Expression<Integer> yearExpression = criteriaBuilder.extract(yearLocalDateField, from.get("localDateTimeData"));
        criteriaQuery.select(yearExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals("Extracted Year should be 2021", Integer.valueOf(2021), result.get(1));
        assertEquals("Extracted Year should be 2020", Integer.valueOf(2020), result.get(2));
        assertEquals("Extracted Year should be 2022", Integer.valueOf(2022), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the QUARTER of the year numbered from 1 to 4 from java.time.LocalDateTime
     *
     * @throws Exception
     */
    @Test
    public void testExtractQuarterFromLocalDateTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateTimeField<Integer> quarterLocalDateField = jakarta.persistence.criteria.LocalDateTimeField.QUARTER;
        jakarta.persistence.criteria.Expression<Integer> quarterExpression = criteriaBuilder.extract(quarterLocalDateField, from.get("localDateTimeData"));
        criteriaQuery.select(quarterExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals("Extracted Quarter should be 1", Integer.valueOf(1), result.get(1));
        assertEquals("Extracted Quarter should be 4", Integer.valueOf(4), result.get(2));
        assertEquals("Extracted Quarter should be 2", Integer.valueOf(2), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the MONTH of the year numbered from 1 from java.time.LocalDateTime
     *
     * @throws Exception
     */
    @Test
    public void testExtractMonthFromLocalDateTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateTimeField<Integer> monthLocalDateField = jakarta.persistence.criteria.LocalDateTimeField.MONTH;
        jakarta.persistence.criteria.Expression<Integer> monthExpression = criteriaBuilder.extract(monthLocalDateField, from.get("localDateTimeData"));
        criteriaQuery.select(monthExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals("Extracted Month should be 1", Integer.valueOf(1), result.get(1));
        assertEquals("Extracted Month should be 12", Integer.valueOf(12), result.get(2));
        assertEquals("Extracted Month should be 6", Integer.valueOf(6), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract the DAY of the year from java.time.LocalDateTime
     *
     * @throws Exception
     */
    @Test
    public void testExtractDayFromLocalDateTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Integer> criteriaQuery = criteriaBuilder.createQuery(Integer.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateTimeField<Integer> dayLocalDateField = jakarta.persistence.criteria.LocalDateTimeField.DAY;
        jakarta.persistence.criteria.Expression<Integer> dayExpression = criteriaBuilder.extract(dayLocalDateField, from.get("localDateTimeData"));
        criteriaQuery.select(dayExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Integer> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        assertEquals(null, result.get(0));
        assertEquals("Extracted day should be 1", Integer.valueOf(1), result.get(1));
        assertEquals("Extracted day should be 31", Integer.valueOf(31), result.get(2));
        assertEquals("Extracted day should be 7", Integer.valueOf(7), result.get(3));

    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * this test extract ISO-8601 week number from java.time.LocalDateTime
     *
     * @throws Exception
     */
    @Test
    public void testExtractWeekFromLocalDateTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Number> criteriaQuery = criteriaBuilder.createQuery(Number.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateTimeField<Integer> weekLocalDateField = jakarta.persistence.criteria.LocalDateTimeField.WEEK;
        jakarta.persistence.criteria.Expression<Integer> weekExpression = criteriaBuilder.extract(weekLocalDateField, from.get("localDateTimeData"));
        criteriaQuery.select(weekExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<Number> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        System.out.println("***** testExtractWeekFromLocalData **** results: " + result);
        assertEquals(null, result.get(0));
        assertEquals("Extracted Week should be 0", Long.valueOf(0), Long.valueOf(result.get(1).longValue()));
        assertEquals("Extracted Week should be 53", Long.valueOf(53), Long.valueOf(result.get(2).longValue()));
        assertEquals("Extracted week should be 23", Long.valueOf(23), Long.valueOf(result.get(3).longValue()));
    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * Extracts LocalTime part of a DateTime
     *
     * @throws Exception
     */
    @Ignore("Throws exceptions with message 'Unknown EXTRACT function datetime_field: TIME'")
    @Test
    public void testExtractTimeFromLocalDateTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<LocalTime> criteriaQuery = criteriaBuilder.createQuery(LocalTime.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateTimeField<LocalTime> timeLocalDateField = jakarta.persistence.criteria.LocalDateTimeField.TIME;
        jakarta.persistence.criteria.Expression<LocalTime> timeExpression = criteriaBuilder.extract(timeLocalDateField, from.get("localDateTimeData"));
        criteriaQuery.select(timeExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<LocalTime> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        System.out.println("***** testExtractWeekFromLocalData **** results: " + result);
        assertEquals(null, result.get(0));
        assertEquals(LocalTime.of(00, 30), result.get(1));
        assertEquals(LocalTime.of(01, 59), result.get(2));
        assertEquals(LocalTime.of(12, 0), result.get(3));
    }

    /**
     * Jakarta Persistence 3.2 adds extract() to CriteriaBuilder
     * Extracts LocalDate part of a DateTime
     *
     * @throws Exception
     */
    @Ignore("Throws exceptions with message 'Unknown EXTRACT function datetime_field: DATE'")
    @Test
    public void testExtractDateFromLocalDateTime() throws Exception {
        deleteAllEntities(QueryDateTimeEntity.class);
        QueryDateTimeEntity q1 = new QueryDateTimeEntity(1, "q1", LocalDate.of(2022, 06, 07), LocalTime.of(12, 0), LocalDateTime.of(2022, 06, 07, 12, 0));
        QueryDateTimeEntity q2 = new QueryDateTimeEntity(2, "q2", LocalDate.of(2020, 12, 31), LocalTime.of(01, 59), LocalDateTime.of(2020, 12, 31, 01, 59));
        QueryDateTimeEntity q3 = new QueryDateTimeEntity(3, "q3", LocalDate.of(2021, 01, 01), LocalTime.of(00, 30), LocalDateTime.of(2021, 01, 01, 00, 30));
        QueryDateTimeEntity q4 = new QueryDateTimeEntity(10000);

        tx.begin();
        em.persist(q1);
        em.persist(q2);
        em.persist(q3);
        em.persist(q4);
        tx.commit();

        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<LocalDate> criteriaQuery = criteriaBuilder.createQuery(LocalDate.class);
        Root<QueryDateTimeEntity> from = criteriaQuery.from(QueryDateTimeEntity.class);
        jakarta.persistence.criteria.LocalDateTimeField<LocalDate> dateLocalDateField = jakarta.persistence.criteria.LocalDateTimeField.DATE;
        jakarta.persistence.criteria.Expression<LocalDate> dateExpression = criteriaBuilder.extract(dateLocalDateField, from.get("localDateTimeData"));
        criteriaQuery.select(dateExpression);
        criteriaQuery.orderBy(criteriaBuilder.desc(from.get("name"), Nulls.FIRST));
        List<LocalDate> result = em.createQuery(criteriaQuery).getResultList();
        assertEquals(4, result.size());
        System.out.println("***** testExtractWeekFromLocalData **** results: " + result);
        assertEquals(null, result.get(0));
        assertEquals(LocalDate.of(2021, 01, 01), result.get(1));
        assertEquals(LocalDate.of(2020, 12, 31), result.get(2));
        assertEquals(LocalDate.of(2022, 06, 07), result.get(3));
    }

    /**
     * Utility method to drop all entities from table.
     *
     * Order to tests is not guaranteed and thus we should be pessimistic and
     * delete all entities when we reuse an entity between tests.
     *
     * @param clazz - the entity class
     */
    private void deleteAllEntities(Class<?> clazz) throws Exception {
        tx.begin();
        em.createQuery("DELETE FROM " + clazz.getSimpleName())
                        .executeUpdate();
        tx.commit();
    }
}
