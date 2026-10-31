package com.betterreads.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.betterreads.testsupport.ContainerizedTest;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class PagedCollectionFetchIntegrationTest extends ContainerizedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    private static final String FLAG = "hibernate.query.fail_on_pagination_over_collection_fetch";

    private static final String FETCH_GRAPH_HINT = "jakarta.persistence.fetchgraph";

    private static final String CREDITS = "credits";

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldRejectPagedJoinFetch() {
        final String joinFetch = "SELECT b FROM Book b JOIN FETCH b.credits";
        final TypedQuery<Book> query = entityManager.createQuery(joinFetch, Book.class);
        query.setMaxResults(1);

        final PersistenceException rejected = assertThrows(PersistenceException.class, query::getResultList);

        assertThat(rejected).hasMessageContaining(FLAG);
    }

    @Test
    void shouldRejectPagedEntityGraph() {
        final EntityGraph<Book> graph = entityManager.createEntityGraph(Book.class);
        graph.addAttributeNodes(CREDITS);
        final TypedQuery<Book> query = entityManager.createQuery("SELECT b FROM Book b", Book.class);
        query.setHint(FETCH_GRAPH_HINT, graph);
        query.setMaxResults(1);

        final PersistenceException rejected = assertThrows(PersistenceException.class, query::getResultList);

        assertThat(rejected).hasMessageContaining(FLAG);
    }
}
