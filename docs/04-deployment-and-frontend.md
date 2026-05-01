# Deployment and frontend

## Deployment strategy for hobby budget
Recommended low-cost architecture:
- Spring Boot backend deployed as one public app
- PostgreSQL hosted database
- Python recommender runs as a scheduled job, not a full-time service
- recommendations are written back to the database
- Java API serves precomputed recommendations

Current plan:
- Java app on AWS EC2 (t2.micro free tier, 12-month limit)
- PostgreSQL on AWS RDS (db.t3.micro free tier, 12-month limit)
- Infrastructure managed with Terraform (learning goal)
- Training job on GitHub Actions schedule
- Migrate to Oracle Cloud Always Free tier when the AWS free period runs out

## Frontend direction
For BetterReads v2, Next.js is the better default choice over Thymeleaf.

Recommended split:
- Next.js frontend on Vercel
- Spring Boot backend on AWS EC2
- PostgreSQL on AWS RDS

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

## AWS free tier direction
AWS is the primary deployment target for learning Terraform and cloud infrastructure. The free tier gives 12 months of EC2 and RDS, which is enough time to build and deploy the app.

Important safety rules:
- stay within free-tier resource types (t2.micro EC2, db.t3.micro RDS)
- set up billing alerts early
- do not accidentally provision paid services
- treat the card as verification, not permission to spend freely

After the 12-month window, migrate compute to Oracle Cloud Always Free tier (ARM instances, no expiry). The migration itself is a useful exercise.

## Terraform
All AWS infrastructure should be defined in Terraform. This covers VPC, subnets, security groups, EC2, RDS, and IAM roles. Keep the Terraform code in a separate directory or repo from the application code.

When Phase 6 adds Kafka, the broker infrastructure (MSK or self-managed) gets added to the same Terraform setup.

## Tomcat note
Apache Tomcat is the server that runs Java web applications.
Spring Boot usually already includes embedded Tomcat, so you often do not need to install Tomcat separately.

For BetterReads, deploying the Spring Boot app directly is usually simpler than managing external Tomcat.
