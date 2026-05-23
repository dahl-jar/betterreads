/**
 * Catalog search wiring backed by a Meilisearch StatefulSet.
 *
 * <p>Postgres is the source of truth; the search index is a derived view that
 * the reconciler in this package keeps in sync.
 */
package com.betterreads.search;
