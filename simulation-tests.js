const axios = require('axios');
const { MongoClient } = require('mongodb');

const API_URL = 'http://127.0.0.1:8080/api/v1';
const MONGODB_URI = 'mongodb://127.0.0.1:27017';

async function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function runSimulations() {
    console.log("=========================================");
    console.log("STARTING HARDENING SIMULATION TESTS");
    console.log("=========================================\n");

    const mongoClient = new MongoClient(MONGODB_URI);
    await mongoClient.connect();
    const db = mongoClient.db('ai-interview');

    const email = `simuser_${Date.now()}@example.com`;
    let token = '';

    try {
        console.log("Setting up simulation user...");
        await axios.post(`${API_URL}/auth/signup`, {
            firstName: "Sim",
            lastName: "Test",
            email: email,
            phone: "+15550009999"
        });
        
        const otpKey = `OTP_SIGNUP:${email}`;
        const redis = require('redis');
        const redisClient = redis.createClient({ url: 'redis://127.0.0.1:6379' });
        await redisClient.connect();
        const otp = await redisClient.get(otpKey);
        await redisClient.quit();

        await axios.post(`${API_URL}/auth/verify-otp`, {
            email,
            otp,
            password: "Password123"
        });

        const loginRes = await axios.post(`${API_URL}/auth/login`, {
            email,
            password: "Password123"
        });
        token = loginRes.data.token;
        const headers = { Authorization: `Bearer ${token}` };

        await axios.post(`${API_URL}/profiles`, {
            skills: ["Java"],
            technologies: ["Kafka"],
            technologies: ["Spring Boot"],
            role: "Software Engineer",
            level: "Intermediate",
            summary: "Sim test"
        }, { headers });

        console.log("Simulation user set up successfully.");

        // --- 1. HEALTH AND METRICS CHECK ---
        console.log("\n--- SIMULATION 1: SYSTEM MONITORING ENDPOINTS ---");
        try {
            const liveRes = await axios.get('http://127.0.0.1:8080/health/live');
            console.log("Liveness Probe [/health/live]:", liveRes.data);
            const readyRes = await axios.get('http://127.0.0.1:8080/health/ready');
            console.log("Readiness Probe [/health/ready]:", readyRes.data);
            const metricsRes = await axios.get('http://127.0.0.1:8080/metrics/system');
            console.log("Telemetry Metrics [/metrics/system]:", metricsRes.data);
        } catch (e) {
            console.log("Monitoring check error:", e.response ? e.response.data : e.message);
        }

        // --- 2. DUPLICATE SUBMISSION ATTACK SIMULATION ---
        console.log("\n--- SIMULATION 2: DUPLICATE SUBMISSION ATTACK ---");
        const testRes = await axios.post(`${API_URL}/tests/start`, { role: "Software Engineer", level: "Intermediate" }, { headers });
        const sessionId = testRes.data.testId;
        console.log(`Generated session ID: ${sessionId}`);

        const submitPayload = {
            mcqAnswers: [],
            codingAnswers: [],
            scenarioAnswers: [],
            projectAnswers: [],
            violations: 0
        };

        console.log("Sending two concurrent submit requests...");
        const p1 = axios.post(`${API_URL}/tests/${sessionId}/submit`, submitPayload, { headers });
        const p2 = axios.post(`${API_URL}/tests/${sessionId}/submit`, submitPayload, { headers });

        const results = await Promise.allSettled([p1, p2]);
        results.forEach((res, i) => {
            if (res.status === 'fulfilled') {
                console.log(`Request ${i+1} Succeeded [${res.value.status}]: ${res.value.data}`);
            } else {
                console.log(`Request ${i+1} Failed: ${res.reason.message}`);
            }
        });

        console.log("Waiting for Kafka processor...");
        await delay(12000);

        const evalCount = await db.collection('results').countDocuments({ testId: sessionId });
        console.log(`[Idempotency Verification] Number of result documents in DB: ${evalCount} (Expected: 1)`);


        // --- 3. HIGH CONCURRENCY EVALUATION LOAD TEST ---
        console.log("\n--- SIMULATION 3: HIGH CONCURRENCY LOAD TEST (100+ requests) ---");
        console.log("Launching 100 concurrent requests to test endpoints...");
        
        const concurrentRequests = [];
        for (let i = 0; i < 50; i++) {
            concurrentRequests.push(axios.get('http://127.0.0.1:8080/health/live'));
            concurrentRequests.push(axios.get('http://127.0.0.1:8080/metrics/system'));
        }

        const loadResults = await Promise.allSettled(concurrentRequests);
        const succeeded = loadResults.filter(r => r.status === 'fulfilled').length;
        const failed = loadResults.filter(r => r.status === 'rejected').length;
        console.log(`Completed 100 concurrent requests. Succeeded: ${succeeded}, Failed: ${failed}`);
        
        try {
            const updatedMetrics = await axios.get('http://127.0.0.1:8080/metrics/system');
            console.log("Updated Telemetry Metrics:", updatedMetrics.data);
        } catch (err) {
            console.log("Metrics collection rate-limited:", err.response ? err.response.data : err.message);
        }

        console.log("\n=========================================");
        console.log("SIMULATIONS COMPLETED SUCCESSFULLY");
        console.log("=========================================");

    } catch (error) {
        console.error("Simulation script failed:", error.message);
        if (error.response) {
            console.error(error.response.status, error.response.data);
        }
    } finally {
        await mongoClient.close();
    }
}

runSimulations();
