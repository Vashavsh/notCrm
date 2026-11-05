# learn-frontend

The course front end (learn.shabash-hub.ru): React 19 + TypeScript + Vite. It is the app Timur built for the GoPractice course, **with its design and code unchanged**; the only changes are that the dev server proxies `/api` and `/assets/course` to `learn-backend` on port 8081 and that a user's `id` is typed as a string (the new back end uses UUIDs). The back end keeps the old API contract (`../learn-backend/README.md`), so nothing else had to move.

```bash
npm install
npm run dev      # http://127.0.0.1:5173, needs learn-backend on :8081
npm run build    # tsc --noEmit && vite build, output in dist/
```

In production nginx serves `dist/` for `learn.shabash-hub.ru` and proxies `/api/` and `/assets/course/` to the learn backend, so the browser sees one origin (no CORS, cookies stay same-site). The course content (lessons, pictures) is not part of this app: the back end serves it to logged-in students only.
