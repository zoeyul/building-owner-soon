# 🏢 Building Owner Soon

A relationship-oriented financial service designed to record and share financial histories through interpersonal stories. 

Beyond simple transaction tracking, this service allows users to manage financial dealings with friends, partners, and family based on trust. It transforms mundane spending into shared memories, creating a new financial experience centered on "Memory & Record."

---

## ✨ Core Features
* **Shared Financial History:** Record transactions as episodes within relationships.
* **Trust-based Tracking:** Collaboratively track repayment flows with friends and family.
* **Flexible Structure:** Supports everything from personal private records to shared collaborative transactions.

## 🛠 Tech Stack
* **Language:** Kotlin
* **Framework:** Spring Boot, WebFlux (Reactive Programming)
* **Database:** RDB (MySQL / MariaDB)
* **Design:** ERD managed via MermaidChart

## 🏗 Architecture
* **Domain-Driven Design (DDD):** Architecture focused on core domain logic and complex business requirements.

## ⚙️ Convention & Automation
To maintain high code quality, we utilize the following tools:
* **Linting:** `ktlint`, `detekt` for static code analysis.
* **Git Hooks:** * `pre-commit`: Checks diff files before committing.
    * `pre-push`: Performs a full-file check before pushing to the remote repository.

## 🌿 Git Strategy
### Branching Model
* **master**: Production-ready code.
* **staging**: QA and testing environment.
* **develop**: Main development branch.

### Commit Convention
* `feature`: New features
* `fix`: Bug fixes
* `hotfix`: Critical production fixes
* `chore`: Configuration & Maintenance
* `docs`: Documentation updates
* `style`: Formatting and code style changes
