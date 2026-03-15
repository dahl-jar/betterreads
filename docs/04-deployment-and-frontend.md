# Deployment and frontend

## Deployment strategy for hobby budget
Recommended low-cost architecture:
- Spring Boot backend deployed as one public app
- PostgreSQL hosted database
- Python recommender runs as a scheduled job, not a full-time service
- recommendations are written back to the database
- Java API serves precomputed recommendations

Good free-friendly setup:
- Java app on Oracle Cloud free compute or another hobby host
- PostgreSQL on Supabase or Neon
- training job on GitHub Actions schedule

## Frontend direction
For BetterReads v2, Next.js is the better default choice over Thymeleaf.

Recommended split:
- Next.js frontend on Vercel
- Spring Boot backend on Oracle Cloud
- PostgreSQL on Supabase or Neon

Why this is the better fit:
- cleaner separation between UI and backend logic
- better user experience for search, filtering, and book pages
- easier path to richer features like recommendations and saved state
- stronger portfolio value because the project shows a modern full-stack architecture

Thymeleaf is still a reasonable option for a simpler Java-only MVP.
But for BetterReads v2, Next.js is the preferred direction.

## PostgreSQL and Supabase
Supabase is not a different database engine from PostgreSQL.
It is managed PostgreSQL plus extra platform features.

Design for PostgreSQL.
If Supabase makes hosting easier, it can still be a good provider choice.

## Oracle Cloud direction
For free hobby deployment, Oracle Cloud is generally a stronger long-term free option than AWS for a small Java app.

Important safety rules:
- stay on the free-tier account
- use only resources clearly marked `Always Free`
- do not accidentally provision paid services
- monitor billing after setup
- treat the card as verification, not permission to spend freely

Oracle is a reasonable place to run the Java app.
For the database, hosted PostgreSQL like Supabase or Neon may still be simpler.

## Tomcat note
Apache Tomcat is the server that runs Java web applications.
Spring Boot usually already includes embedded Tomcat, so you often do not need to install Tomcat separately.

For BetterReads, deploying the Spring Boot app directly is usually simpler than managing external Tomcat.
