/**
 * PK AI - HackerEarth Code Runner Proxy Serverless Function
 *
 * This Node.js / Express / Firebase Cloud Function acts as a secure backend proxy
 * for the PK AI Android App. It receives the source code and language from the app,
 * attaches the secret HackerEarth API credentials server-side, submits the request to
 * HackerEarth Code API v4, polls for the execution status, and returns the result back
 * to the app without exposing the secret key.
 *
 * Environment variables required:
 *   - HACKEREARTH_CLIENT_ID
 *   - HACKEREARTH_CLIENT_SECRET
 */

const express = require('express');
const axios = require('axios');
const app = express();

app.use(express.json());

const CLIENT_ID = process.env.HACKEREARTH_CLIENT_ID || 'ad4ca5cea7728af7ab22cbce1f222d8868d1c425dcfc.api.hackerearth.com';
const CLIENT_SECRET = process.env.HACKEREARTH_CLIENT_SECRET;

app.post('/api/run-code', async (req, res) => {
    try {
        const { source, lang, input, memory_limit, time_limit } = req.body;

        if (!source || !lang) {
            return res.status(400).json({
                error: 'Missing required parameters: source and lang are required.'
            });
        }

        if (!CLIENT_SECRET) {
            return res.status(500).json({
                error: 'Server misconfiguration: HACKEREARTH_CLIENT_SECRET is missing.'
            });
        }

        // 1. Submit code to HackerEarth API
        const submitResponse = await axios.post(
            'https://api.hackerearth.com/v4/partner/code-evaluation/submissions/',
            {
                source,
                lang,
                input: input || '',
                memory_limit: memory_limit || 262144,
                time_limit: time_limit || 5
            },
            {
                headers: {
                    'Content-Type': 'application/json',
                    'client-secret': CLIENT_SECRET
                }
            }
        );

        const heId = submitResponse.data.he_id;
        if (!heId) {
            return res.status(500).json({
                error: 'Failed to obtain submission ID from HackerEarth.'
            });
        }

        // 2. Poll status until completed (max 10 retries, 1s interval)
        let statusData = submitResponse.data;
        let attempts = 0;
        const maxAttempts = 10;

        while (attempts < maxAttempts) {
            const statusCode = statusData?.request_status?.code;
            if (statusCode === 'REQUEST_COMPLETED' || statusCode === 'REQUEST_FAILED') {
                break;
            }

            await new Promise((resolve) => setTimeout(resolve, 1000));

            const statusResponse = await axios.get(
                `https://api.hackerearth.com/v4/partner/code-evaluation/submissions/${heId}/`,
                {
                    headers: {
                        'client-secret': CLIENT_SECRET
                    }
                }
            );
            statusData = statusResponse.data;
            attempts++;
        }

        // 3. If output URL is available, fetch stdout text
        let stdout = null;
        if (statusData?.result?.run_status?.output) {
            try {
                const outputRes = await axios.get(statusData.result.run_status.output);
                stdout = typeof outputRes.data === 'string' ? outputRes.data : JSON.stringify(outputRes.data);
            } catch (err) {
                console.error('Error fetching output URL:', err.message);
            }
        }

        return res.json({
            he_id: heId,
            request_status: statusData.request_status,
            compile_status: statusData.result?.compile_status,
            run_status: statusData.result?.run_status?.status,
            stdout: stdout,
            stderr: statusData.result?.run_status?.stderr || null,
            time_used: statusData.result?.run_status?.time_used || 0.0,
            memory_used: statusData.result?.run_status?.memory_used || 0
        });

    } catch (error) {
        console.error('Proxy Error:', error.response?.data || error.message);
        return res.status(error.response?.status || 500).json({
            error: error.response?.data || error.message || 'Internal proxy error'
        });
    }
});

const PORT = process.env.PORT || 3000;
if (require.main === module) {
    app.listen(PORT, () => console.log(`HackerEarth proxy server listening on port ${PORT}`));
}

module.exports = app;
