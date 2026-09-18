# PRC Jobs — Automated Job Scraper

Pulls real job postings (Indeed, LinkedIn, Google Jobs) with
[python-jobspy](https://github.com/speedyapply/JobSpy), filters for Kenya,
maps them to the app's professional categories, dedupes, and uploads them
to Firestore as provider jobs. They then appear in the app like any other
posting — Companies hiring, featured boosts, deadline auto-close, and the
admin portal all work on them automatically.

Runs on **GitHub Actions every 6 hours — free, no VPS, no machine of yours
needs to be on.**

## One-time setup (5 minutes)

1. **Create the Firebase service account**
   - Firebase console → ⚙ Project settings → Service accounts
   - "Generate new private key" → save the JSON file
2. **Add it as a GitHub secret**
   - GitHub repo → Settings → Secrets and variables → Actions → New secret
   - Name: `FIREBASE_SERVICE_ACCOUNT`
   - Value: paste the **entire JSON file content**
3. **Push this repo to GitHub** (the workflow file is `.github/workflows/scrape.yml`)
4. **Give the service account Firestore write access**
   - Firebase console → Firestore → Rules should allow it, or grant the
     service account the "Firebase Admin" role in Google Cloud IAM
   - ⚠ Also make sure the deployed `firestore.rules` don't block it —
     service accounts bypass security rules via the Admin SDK, so no
     change is needed.

## Running it

- **Automatic:** every 6 hours by the cron schedule
- **Manual:** GitHub repo → Actions tab → "Scrape jobs" → **Run workflow**
- **Locally (testing):**
  ```bash
  cd scraper
  pip install -r requirements.txt
  python scrape_and_upload.py --dry-run    # scrape, print, don't upload
  python scrape_and_upload.py              # full run
  ```

## What it does each run

| Step | Detail |
|---|---|
| 1. Scrape | Broad Kenya/Nairobi pass on Indeed + LinkedIn + Google, then 31 targeted term passes on Indeed (driver, cashier, waiter, nanny, mason…) |
| 2. Filter | Kenya locations only; drops rows without title+URL |
| 3. Classify | Regex maps each job to one of the app's 17 professional categories |
| 4. Dedupe | SHA-1 fingerprint of normalized title+company; skips anything already in Firestore (survives reposts across boards) |
| 5. Upload | Batched Firestore writes (`jobs/scrape_{fp}`), source="provider", `deadlineDays=14` so the app auto-closes stale scrapes |

## Efficiency knobs (env vars / workflow env)

| Var | Default | Meaning |
|---|---|---|
| `SCRAPE_HOURS_OLD` | 24 | Only jobs posted in the last N hours (fresh feed, less noise) |
| `SCRAPE_RESULTS_PER_QUERY` | 60 | Max results per site/query pair |
| `--no-linkedin` | — | Skip LinkedIn if blocks get bad (Indeed still runs) |

Expected yield: **roughly 150–400 unique Kenyan jobs per run** depending on
day-of-week (Mondays are heavy, weekends light), with LinkedIn contributing
when it doesn't block. Every run dedupes against all of history, so the
app's feed only ever grows with genuinely new postings.

## Tuning

- **More volume:** raise `SCRAPE_RESULTS_PER_QUERY`, add terms to `SEARCH_TERMS`
- **Fewer/better:** lower `hours_old`, tighten `CATEGORY_RULES`
- **All scraped jobs land in the admin portal** — you can review/hide any
  bad ones from Provider Jobs as usual.
