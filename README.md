# CarbonScope Backend


  <h3>Scalable API Service for Carbon Footprint Tracking & AI Analysis</h3>

  <p>
    This is the backend server for the <strong><a href="https://github.com/thughari/carbon-scope">CarbonScope Frontend</a></strong>. 
    <br />
    It integrates with <strong>ClimateTRACE</strong> for data and <strong>Google Gemini</strong> for AI-powered insights.
    <br />
    <br />
    <a href="https://github.com/thughari/carbonscope/issues">Report Bug</a>
    ·
    <a href="https://github.com/thughari/carbonscope/issues">Request Feature</a>
  </p>


---

## 📖 About The Project

The **CarbonScope Backend** is a robust RESTful API that powers the CarbonScope platform. Beyond standard data processing, it leverages Generative AI to analyze complex emission data and provide actionable summaries.

**Related Repository:**
*   🖥️ **Frontend:** [https://github.com/thughari/carbon-scope](https://github.com/thughari/carbon-scope) (Angular + Tailwind CSS)

### ✨ Key Features

*   **🌍 ClimateTRACE Integration:** Fetches real-time global emission data filtered by sector, country, and gas type.
*   **🤖 AI-Powered Insights:** Uses **Google Gemini 2.0 Flash** to analyze emission trends and generate human-readable environmental impact reports.
*   **🔐 CORS Configured:** Pre-configured to work securely with the frontend hosted on GitHub Pages (`thughari.github.io`) and Localhost.
*   **🐳 Dockerized:** Optimized multi-stage Docker build for easy deployment.

---

## 🛠️ Tech Stack

*   **Language:** [Java 21](https://aws.amazon.com/corretto/)
*   **Framework:** [Spring Boot 3](https://spring.io/projects/spring-boot)
*   **AI Model:** [Google Gemini 2.0 Flash](https://deepmind.google/technologies/gemini/)
*   **Data Source:** [ClimateTRACE API](https://climatetrace.org/)
*   **Containerization:** Docker

---

## ⚙️ Configuration & API Keys

To run this application, you **must** provide a Google Gemini API Key.

### 1. Get a Gemini API Key
Visit [Google AI Studio](https://aistudio.google.com/) to generate a free API key.

### 2. Configure the Application
You can set the key in `src/main/resources/application-dev.properties` (for local dev) or pass it as an environment variable.

**Option A: properties file (Local Dev)**
Open `src/main/resources/application-dev.properties`:
```properties
spring.application.name=carbonscope

# AI Configuration
GEMINI_API_KEY=YOUR_ACTUAL_API_KEY_HERE
gemini.model=gemini-2.0-flash

# CORS Configuration
app.cors.allowed-origins=https://thughari.github.io,http://localhost:4200
app.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
```

**Option B: Environment Variable**

---

## 🚀 Getting Started

### Prerequisites
*   **Java 21**
*   **Maven**

### 📥 Installation & Run

1.  **Clone the repository**
    ```bash
    git clone https://github.com/thughari/carbonscope.git
    cd carbonscope
    ```

2.  **Build**
    ```bash
    mvn clean package
    ```

3.  **Run (Local)**
    Ensure you have set your API key in the properties file before running.
    ```bash
    java -jar target/carbonscope-0.0.1-SNAPSHOT.jar
    ```

---

## 🐳 Docker Deployment

When running with Docker, pass the API key as an environment variable (`-e`).

1.  **Build Image**
    ```bash
    docker build -t carbonscope-backend .
    ```

2.  **Run Container**
    ```bash
    docker run -p 8082:8082 \
      -e GEMINI_API_KEY="your_api_key_here" \
      carbonscope-backend
    ```

---

## 📂 Project Structure

```text
src/
├── main/
│   ├── java/com/carbonscope/
│   │   ├── client/          # ClimateTraceClient.java
│   │   ├── config/          # CorsConfig, AppConfig
│   │   ├── controller/      # REST Controllers (AiController, EmissionController)
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── model/           # Data Entities
│   │   ├── service/         # Business Logic
│   │   └── CarbonscopeApplication.java
│   └── resources/
│       ├── application-dev.properties
│       ├── application-prod.properties
│       └── application.properties
└── Dockerfile
```

---


## 📧 Contact

**Thughari** - [Portfolio](https://thughari.github.io/)

**Email** - haribabutatikonda3@gmail.com

*   **Backend Repo:** [https://github.com/thughari/carbonscope](https://github.com/thughari/carbonscope)
*   **Frontend Repo:** [https://github.com/thughari/carbon-scope](https://github.com/thughari/carbon-scope)
