# Frontend

React 19, Vite, TypeScript in strict mode, Tailwind CSS v4.

```bash
npm install
cp .env.example .env.local
npm run dev
```

The dev server proxies `/api` to the backend, so the browser stays on a single origin. That is what
makes the session cookie and CSRF header behave exactly as they do in production, without any CORS
configuration.

| Command | Purpose |
| --- | --- |
| `npm run dev` | Development server |
| `npm run build` | Type check, then production build into `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run test` | Vitest, once |
| `npm run test:watch` | Vitest, watching |
| `npm run typecheck` | TypeScript only |
| `npm run lint` | oxlint |

Full setup and troubleshooting are in [`../docs/SETUP.md`](../docs/SETUP.md).

## Layout

```
src/
├── api/        typed API client, CSRF handling, auth provider
├── components/ common widgets, layout, search forms, timetable views, admin
├── hooks/      async state, the college clock, recent lookups
├── pages/      one file per screen
├── types/      the API contract, typed from the backend DTOs
├── utils/      how a lookup status is presented in words
└── test/       shared test setup
```

## Conventions

- **Types come from `types/api.ts`.** If the backend contract changes and the types are not updated,
  the build fails rather than a lookup silently breaking.
- **The API is never called directly from a component.** Components use `api/endpoints.ts` and the
  `useAsync` hook, so loading, error and cancellation behave the same everywhere.
- **Status wording is data, not markup.** `utils/presentation.ts` maps each lookup status to its
  label and sentence, and a test fails the build if any of those sentences implies that a student did
  something wrong.
- **Recent lookups stay in the browser.** They are written to `sessionStorage` and never sent to the
  server, because a record of who staff have been asking about is student data.
