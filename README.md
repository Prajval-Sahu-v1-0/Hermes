# Hermes — YouTube Creator Discovery Platform

<div align="center">

![Java](https://img.shields.io/badge/Java-17+-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

**Discover the perfect YouTube creators for your brand with AI-powered search and intelligent ranking.**

</div>

---

## ✨ Features

- 🔍 **AI-Powered Search** — Natural language queries converted to high-signal YouTube searches
- ⚡ **Session-Based Caching** — Search once, paginate infinitely with zero API calls
- 📊 **Multi-Dimensional Scoring** — Genre relevance, audience fit, engagement quality, and more
- 🎯 **Advanced Filtering** — Multi-select filters for audience size, engagement, competitiveness
- 📈 **Sortable Results** — 6 precomputed sort keys for instant re-ordering
- 💰 **Quota Optimized** — YouTube API usage reduced by 90%+ through intelligent caching

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (HTML/JS)                     │
├─────────────────────────────────────────────────────────────┤
│                    REST API (Spring Boot)                   │
├───────────────┬───────────────┬───────────────┬─────────────┤
│ SearchService │ SessionService│ RankingService│ QueryGenSvc │
├───────────────┴───────────────┴───────────────┴─────────────┤
│           L1 Cache (Caffeine) + L2 Cache (PostgreSQL)       │
├─────────────────────────────────────────────────────────────┤
│              External APIs (YouTube, Cohere)                │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 15+
- YouTube Data API v3 key ([Get one here](https://console.cloud.google.com/apis/credentials))
- Cohere API key ([Get one here](https://dashboard.cohere.com/api-keys))

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/hermes.git
   cd hermes
   ```

2. **Create the database**
   ```sql
   CREATE DATABASE Hermes;
   ```

3. **Configure credentials**
   ```bash
   cd backend/src/main/resources
   cp application.properties.template application.properties
   # Edit application.properties with your credentials
   ```

4. **Run the backend**
   ```bash
   cd backend
   mvn spring-boot:run
   ```

5. **Open the frontend**
   - Open `index.html` in your browser
   - Or serve with a local server: `npx serve .`

---

## 📡 API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/search` | POST | Execute search (creates session) |
| `/api/v1/search/session/{id}` | GET | Paginate results (zero API calls) |
| `/api/v1/search/session/{id}/filtered` | GET | Filter + sort + paginate |
| `/api/v1/admin/stats` | GET | View cache/quota statistics |
| `/api/v1/admin/features` | GET | Feature flags status |

### Example Search Request

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{"genre": "tech reviewers", "page": 0, "pageSize": 10}'
```

---

## ⚙️ Configuration

Key settings in `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `hermes.session.ttl-minutes` | 30 | Session cache TTL |
| `hermes.youtube.max-queries-per-search` | 5 | Max YouTube queries per search |
| `hermes.youtube.daily-quota` | 10000 | Daily YouTube quota budget |
| `hermes.llm.daily-token-budget` | 1000000 | Daily Cohere token budget |

---

## 📁 Project Structure

```
hermes/
├── backend/
│   └── src/main/java/com/hermes/
│       ├── controller/     # REST endpoints
│       ├── service/        # Business logic
│       ├── repository/     # Data access
│       ├── domain/         # Entities, DTOs
│       ├── cache/          # Query normalization, digest
│       ├── governor/       # Quota management
│       └── feature/        # Feature flags
├── index.html              # Landing page
├── results.html            # Search results page
├── style.css               # Landing page styles
├── results.css             # Results page styles
└── search.js / results.js  # Frontend logic
```

---

## 🔒 Security Notes

- **NEVER commit `application.properties`** — it contains API keys
- Use `application.properties.template` as a reference
- All sensitive values are excluded via `.gitignore`

---

## 📜 License

MIT License — see [LICENSE](LICENSE) for details.

---

<div align="center">
  <sub>Built with ☕ and persistence</sub>
</div>
