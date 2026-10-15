/* ==========================================================================
   AMORÉ — local frontend dev server
   --------------------------------------------------------------------------
   WHY THIS EXISTS: the backend's CORS allowlist (backend WebConfig) accepts
   http://localhost:* / http://127.0.0.1:* only. Pages opened by double-click
   (file://) send Origin: null and the backend correctly rejects them — the
   browser then shows "Cannot reach the salon server right now." Serving the
   frontend over http://localhost fixes this WITHOUT touching the backend.

   Run:  npm run dev          (or: node serve-frontend.js)
   Then open:  http://localhost:8081/booking.html

   Port override:  PORT=3000 npm run dev   (or set PORT before run-frontend.cmd)

   No dependencies — plain Node http/fs. Dev tool only; never deploy this.
   ========================================================================== */
"use strict";

const http = require("http");
const fs = require("fs");
const path = require("path");

const FRONTEND = path.resolve(__dirname, "frontend");
const PORT = Number(process.env.PORT) || 8081;

const MIME = {
	".html": "text/html; charset=utf-8",
	".js": "application/javascript; charset=utf-8",
	".css": "text/css; charset=utf-8",
	".svg": "image/svg+xml",
	".png": "image/png",
	".jpg": "image/jpeg",
	".jpeg": "image/jpeg",
	".gif": "image/gif",
	".webp": "image/webp",
	".ico": "image/x-icon",
	".json": "application/json",
	".txt": "text/plain; charset=utf-8",
	".woff": "font/woff",
	".woff2": "font/woff2",
};

const server = http.createServer((req, res) => {
	let urlPath;
	try {
		urlPath = decodeURIComponent(req.url.split("?")[0]);
	} catch (e) {
		res.writeHead(400, { "Content-Type": "text/plain" });
		res.end("bad request");
		return;
	}
	if (urlPath === "/") {
		urlPath = "/index.html";
	}
	const filePath = path.join(FRONTEND, urlPath);
	// stay inside the frontend directory (no traversal)
	if (!filePath.startsWith(FRONTEND)) {
		res.writeHead(403, { "Content-Type": "text/plain" });
		res.end("forbidden");
		return;
	}
	fs.readFile(filePath, (err, data) => {
		if (err) {
			res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
			res.end("not found: " + urlPath);
			return;
		}
		res.writeHead(200, {
			"Content-Type": MIME[path.extname(filePath).toLowerCase()] || "application/octet-stream",
			// dev server: never let Chrome's heuristic cache serve stale assets
			"Cache-Control": "no-store",
		});
		res.end(data);
	});
});

server.listen(PORT, () => {
	console.log("");
	console.log("  Amoré frontend dev server");
	console.log("  -------------------------");
	console.log("  Frontend:  http://localhost:" + PORT + "/booking.html");
	console.log("  Backend:   http://localhost:8080  (must be running — see backend/run-dev.cmd)");
	console.log("");
	console.log("  NOTE: open pages via the http:// address above, NOT by");
	console.log("  double-clicking the HTML files (file:// is blocked by CORS).");
	console.log("  Press Ctrl+C to stop.");
	console.log("");
});
