/**
 * Catalog search wiring backed by a Meilisearch StatefulSet.
 *
 * <p>The book rows in Postgres are canonical; the search index is rebuilt from them by the
 * reconciler in this package.
 */
package com.betterreads.search;
