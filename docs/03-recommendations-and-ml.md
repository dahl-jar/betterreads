# Recommendations and ML

## Recommendation system direction
Recommendations are not the same thing as search.

Search answers:
- what books match this query

Recommendations answer:
- what books should this user probably read next

Start with user behavior, not advanced AI.

Useful recommendation signals:
- viewed
- saved
- currently reading
- finished
- rated
- reviewed
- commented
- club activity

## Recommendation pipeline stages
### Stage 1 - rules-based recommendations
Use simple logic first:
- trending books
- same genre
- same author
- books liked by users with similar shelves
- club-based suggestions

### Stage 2 - simple recommender logic
Use user-book interaction data:
- build a user-item interaction table
- assign weights to interactions
- compute user similarity or item similarity
- recommend unseen books

### Stage 3 - stronger ML / hybrid recommendations
Later combine:
- popularity
- content-based recommendations
- collaborative filtering
- ranking logic

## Machine learning approach
Machine learning should come after enough user interaction data has been collected.

Do not start by training a model live during requests.

Instead:
- collect events from day one
- begin with simple recommendation logic
- train models later when data quality is better
- run training in the background on a schedule

A realistic first ML-style approach is collaborative or similarity-based recommendation using interaction weights.

## Training and inference strategy
For a hobby project, do not deploy a constantly running ML inference service first.

Recommended approach:
- train recommendations offline in the background
- generate top recommendations per user
- store results in PostgreSQL
- let the Java API read and serve those recommendations

This is cheaper and simpler than live inference.

## Python vs Java split
Keep the main backend in Java.
Use Python for recommender training if and when ML is added.

Reasonable split:
- Java/Spring Boot for API, users, books, reviews, collections, auth
- Python for recommendation training jobs (separate `ml-api` repo)
- PostgreSQL as the shared source of truth

The ML/recommendation service lives in its own repository to keep concerns separated.
