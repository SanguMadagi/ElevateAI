const axios = require('axios');
const { MongoClient } = require('mongodb');
const { createClient } = require('redis');

const API_URL = 'http://127.0.0.1:8080/api/v1';
const MONGODB_URI = 'mongodb://127.0.0.1:27017';
const REDIS_URL = 'redis://127.0.0.1:6379';

async function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function runE2ETests() {
    console.log("=========================================");
    console.log("STARTING END-TO-END SYSTEM VERIFICATION");
    console.log("=========================================\n");

    const mongoClient = new MongoClient(MONGODB_URI);
    await mongoClient.connect();
    const db = mongoClient.db('ai-interview');
    
    const redisClient = createClient({ url: REDIS_URL });
    await redisClient.connect();

    let authToken = '';
    let sessionId = '';
    const email = `testuser_${Date.now()}@example.com`;

    try {
        // --- 1. AUTHENTICATION FLOW ---
        console.log("--- TEST 1: SIGNUP FLOW ---");
        console.log("Frontend Route: /signup");
        const signupPayload = {
            firstName: "End",
            lastName: "User",
            email: email,
            phone: "+15551234567"
        };
        console.log(`Endpoint: POST /api/v1/auth/signup`);
        console.log(`Payload:`, signupPayload);
        const signupRes = await axios.post(`${API_URL}/auth/signup`, signupPayload);
        console.log(`Response [${signupRes.status}]:`, signupRes.data);
        
        // Verify Redis for OTP
        const otpKey = `OTP_SIGNUP:${email}`;
        const storedOtp = await redisClient.get(otpKey);
        console.log(`[Redis Check] OTP stored: ${storedOtp}`);
        
        console.log("\n--- TEST 2: OTP VERIFICATION ---");
        console.log("Frontend Route: /verify-otp");
        const verifyPayload = {
            email: email,
            otp: storedOtp,
            password: "SecurePassword123"
        };
        console.log(`Endpoint: POST /api/v1/auth/verify-otp`);
        console.log(`Payload:`, verifyPayload);
        const verifyRes = await axios.post(`${API_URL}/auth/verify-otp`, verifyPayload);
        console.log(`Response [${verifyRes.status}]:`, verifyRes.data);
        
        const userDoc = await db.collection('users').findOne({ email });
        console.log(`[MongoDB Check] User created:`, userDoc ? "YES" : "NO");

        console.log("\n--- TEST 3: LOGIN ---");
        console.log("Frontend Route: /login");
        const loginPayload = { email, password: "SecurePassword123" };
        console.log(`Endpoint: POST /api/v1/auth/login`);
        const loginRes = await axios.post(`${API_URL}/auth/login`, loginPayload);
        console.log(`Response [${loginRes.status}]: JWT Token Received`);
        authToken = loginRes.data.token;

        const authHeaders = { headers: { Authorization: `Bearer ${authToken}` } };

        // --- 2. PROFILE FLOW ---
        console.log("\n--- TEST 4: PROFILE SAVE (Post-Resume Upload) ---");
        console.log("Frontend Route: /onboarding (Step 3)");
        const profilePayload = {
            skills: ["Java", "Spring Boot", "React"],
            technologies: ["Kafka", "MongoDB"],
            technologies: ["Redis", "MongoDB"],
            role: "Java Backend Developer",
            level: "Intermediate",
            summary: "Experienced developer.",
            experience: [],
            education: [],
            projects: []
        };
        console.log(`Endpoint: POST /api/v1/profiles`);
        const profileRes = await axios.post(`${API_URL}/profiles`, profilePayload, authHeaders);
        console.log(`Response [${profileRes.status}]:`, profileRes.data.id ? "Profile Saved" : "Failed");

        const profileDoc = await db.collection('profiles').findOne({ userId: userDoc._id.toString() });
        console.log(`[MongoDB Check] Profile found with skills:`, profileDoc?.skills);

        // --- 3. ASSESSMENT GENERATION ---
        console.log("\n--- TEST 5: ASSESSMENT GENERATION ---");
        console.log("Frontend Route: /dashboard -> /test/:id");
        console.log(`Endpoint: POST /api/v1/tests/start`);
        const genPayload = { role: profilePayload.role, level: profilePayload.level };
        const genRes = await axios.post(`${API_URL}/tests/start`, genPayload, authHeaders);
        sessionId = genRes.data.testId;
        console.log(`Response [${genRes.status}]: Generated Test Session ID -> ${sessionId}`);
        console.log(`Includes MCQs: ${genRes.data.mcqQuestions?.length}, Coding: ${genRes.data.codingQuestions?.length}, Scenarios: ${genRes.data.scenarioQuestions?.length}, Projects: ${genRes.data.projectQuestions?.length}`);
        console.log(`Includes MCQs: ${genRes.data.mcqQuestions?.length}, Scenarios: ${genRes.data.scenarioQuestions?.length}, Projects: ${genRes.data.projectQuestions?.length}`);

        const testCache = await redisClient.get(`test:${email}:${sessionId}`);
        console.log(`[Redis Check] Test Session cached:`, testCache ? "YES" : "NO");

        // --- 4. ASSESSMENT SUBMISSION & ANTI-CHEATING ---
        console.log("\n--- TEST 6: TEST SUBMISSION & ANTI-CHEATING ---");
        console.log("Frontend Route: /test/:id (Submit)");
        const submitPayload = {
            mcqAnswers: [{ question: genRes.data.mcqQuestions[0]?.question, selectedOption: "A" }],
            codingAnswers: [{ questionId: genRes.data.codingQuestions[0]?.id, code: "public class Solution {}" }],
            scenarioAnswers: [{ question: "Scenario Q", answer: "My approach is X." }],
            projectAnswers: [{ question: "Project Q", answer: "I used Y." }],
            violations: 2 // Anti-Cheating simulation
        };
        console.log(`Endpoint: POST /api/v1/tests/${sessionId}/submit`);
        const submitRes = await axios.post(`${API_URL}/tests/${sessionId}/submit`, submitPayload, authHeaders);
        console.log(`Response [${submitRes.status}]:`, submitRes.data);

        // Allow Kafka listener to process
        console.log("Waiting for Kafka Evaluation Worker...");
        // Allow Spring AI asynchronous evaluation pipeline to process
        console.log("Waiting for Spring AI Async Evaluation Worker...");
        await delay(25000); 

        const resultDoc = await db.collection('results').findOne({ testId: sessionId });
        console.log(`[MongoDB Check] Result created:`, resultDoc ? "YES" : "NO");
        if (resultDoc) {
            console.log(`Final Score: ${resultDoc.finalScore}%`);
            console.log(`Hiring Recommendation: ${resultDoc.hiringRecommendation}`);
            console.log(`Violations Logged: ${resultDoc.totalViolations}`);
        }


    } catch (error) {
        console.error("TEST FAILED!");
        if (error.response) {
            console.error(error.response.status, error.response.data);
        } else {
            console.error(error.message);
        }
    } finally {
        await mongoClient.close();
        await redisClient.quit();
        console.log("\n=========================================");
        console.log("END OF EXECUTION");
        console.log("=========================================");
    }
}

runE2ETests();