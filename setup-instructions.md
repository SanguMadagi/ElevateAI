# AI Interview & Career Preparation Platform — Setup Instructions

## Prerequisites
- Docker & Docker Compose
- Java 17+
- Node.js (v18+)
- Google Gemini API Key (from Google AI Studio)

---

## Step 1: Start Infrastructure Services
Start MongoDB, Redis Stack, and MariaDB using Docker Compose:
```bash
docker compose up -d
```

---

## Step 2: Backend Configuration & Startup

1. Set your environment variables (or configure `.env`):
   - `GEMINI_API_KEY`: Your Google Gemini API Key
   - `GEMINI_MODEL`: Optional Gemini model name (defaults to `gemini-3.6-flash`)
   - `JWT_SECRET`: Secret key for JWT signing (minimum 32 characters)
   - `MAIL_USERNAME`: Gmail address used to send OTP verification emails
   - `MAIL_PASSWORD`: 16-character Google App Password for that Gmail account

   In PowerShell:
   ```powershell
   $env:GEMINI_API_KEY = "your-gemini-api-key"
   $env:MAIL_USERNAME = "your-account@gmail.com"
   $env:MAIL_PASSWORD = "your-16-character-app-password"
   ```

2. Run the Spring Boot application:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
   *(Backend runs on `http://localhost:8080`)*

---

## Step 3: Frontend Client Startup

Navigate to the frontend directory, install dependencies, and start Vite dev server:
```bash
cd frontend
npm install
npm run dev
```
*(Frontend runs on `http://localhost:5173`)*

---

## Step 4: Core Features Walkthrough

1. **Signup & Onboarding**:
   - Register with your email and verify via OTP.
   - Upload your resume PDF and configure your target role and experience level.

2. **AI Assessments**:
   - Launch a 3-round evaluation covering dynamic MCQs, Architectural Scenarios, and Resume Project questions.
   - Real-time speech recognition, anti-cheat proctoring, and comprehensive automated scoring.

3. **AI Mock Interview**:
   - Engage in multi-turn technical mock interviews with persistent memory backed by MariaDB `JdbcChatMemory`.
   - Receive turn-by-turn feedback and a final evaluation scorecard.

4. **Personal Career Agent**:
   - Conversational AI assistant grounded in your authenticated profile, assessment results, and skill scores via Spring AI tools and Redis vector RAG.

5. **AI Career Workspace**:
   - Generate and explore scoped Markdown preparation documents (`Resume-Analysis.md`, `Resume-Questions.md`, `Weak-Topics.md`, `Skill-Progress.md`).