# Recommendations and ML

## Direction

Start with user behavior, not advanced ML.

Recommendation signals:

- viewed
- saved
- currently reading
- finished
- rated
- reviewed
- commented
- club activity

## Pipeline

### Stage 1: rules-based

- Trending books
- Same genre
- Same author
- Books liked by users with similar shelves
- Club-based suggestions

### Stage 2: similarity-based

- User-item interaction table.
- Interaction weights per signal type.
- User similarity or item similarity.
- Recommend unseen books.

### Stage 3: hybrid

Combine popularity, content-based recommendations, collaborative filtering, and ranking logic.

## ML approach

ML comes after enough interaction data has been collected. Don't train live during requests.

Flow:

- Collect events from day one.
- Start with simple recommendation logic.
- Train models later, on a schedule, in the background.
- Generate top recommendations per user offline.
- Store results in PostgreSQL.
- Java API reads and serves the precomputed recommendations.

## Service split

- Java/Spring Boot for API, users, books, reviews, collections, auth.
- Python for recommendation training jobs (separate `ml-api` repo).
- PostgreSQL as the shared source of truth.

The ML service lives in its own repository to keep concerns separated.
