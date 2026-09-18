"""
PRC Jobs — automated job source pipeline.

Scrapes real job postings with python-jobspy (Indeed + LinkedIn + Google),
filters for Kenya, maps each job to the app's professional category
taxonomy, dedupes against what's already in Firestore, and uploads new
jobs as provider postings (they appear in the app like admin-posted jobs).

Designed to run on GitHub Actions every 6 hours (free, no VPS needed) or
anywhere Python 3.10+ runs.

Usage:
    python scrape_and_upload.py                 # scrape + upload
    python scrape_and_upload.py --dry-run       # scrape only, print summary
    python scrape_and_upload.py --out jobs.csv  # also save raw results to CSV

Environment:
    FIREBASE_SERVICE_ACCOUNT  — path to the service-account JSON (upload mode)
    SCRAPE_HOURS_OLD          — how fresh jobs must be (default 24)
    SCRAPE_RESULTS_PER_QUERY  — max results per (site, term) pair (default 60)
"""

import argparse
import hashlib
import json
import os
import re
import sys
from datetime import datetime, timezone
from typing import Optional

import pandas as pd


def patch_unsupported_countries() -> None:
    """python-jobspy's Country enum has no Kenya entry, so Indeed/LinkedIn
    raise 'Invalid country string: kenya'. Patch it to fall back to
    WORLDWIDE — the location parameter (e.g. 'Nairobi, Kenya') does the
    real geo-filtering. This makes LinkedIn work for Kenya and lets
    Indeed accept the call (Indeed worldwide still honors the location
    string, returning Kenyan postings when they exist)."""
    import jobspy.model as _m
    _orig = _m.Country.from_string.__func__

    def _patched(cls, s):
        try:
            return _orig(cls, s)
        except ValueError:
            return cls.WORLDWIDE

    _m.Country.from_string = classmethod(_patched)


patch_unsupported_countries()

# ============================ configuration ============================

# Search terms tuned for the app's blue/grey-collar categories — these are
# where the volume is in Kenya. Each term runs on each enabled site.
SEARCH_TERMS = [
    "driver", "delivery rider", "shop attendant", "sales attendant",
    "cashier", "waiter", "waitress", "chef", "cook", "housekeeper",
    "house help", "nanny", "cleaner", "security guard", "office assistant",
    "receptionist", "data entry clerk", "customer service", "storekeeper",
    "farm worker", "salesperson", "mason", "carpenter", "electrician",
    "plumber", "welder", "mechanic", "accountant", "nurse", "teacher",
    "intern", "attendant",
]

LOCATIONS = ["Nairobi", "Kenya"]

# JobSpy site list: indeed works best from datacenter IPs (GitHub Actions).
# LinkedIn is flaky there — kept enabled but failures are non-fatal.
SITES = ["indeed", "linkedin", "google"]

# Map (keyword -> app category). Checked against title+description, first
# match wins; fallback is "Retail" (the app's generic fallback too).
CATEGORY_RULES = [
    (r"\b(driv|chauffeur|rider|motorbike|boda|courier)", "Driving"),
    (r"\b(deliver|logistic|dispatch|fleet|warehouse|store.?keep|stock)", "Logistics & Supply Chain"),
    (r"\b(waiter|waitress|chef|cook|kitchen|barista|bartender|catering|hotel|restaurant|hospitality)", "Hospitality & Tourism"),
    (r"\b(house ?(girl|help|keeper|maid)|nanny|domestic|babysit)", "Domestic"),
    (r"\b(farm|agricultur|agri|poultry|dairy|greenhouse|horticultur)", "Agriculture"),
    (r"\b(cashier|shop|retail|attendant|salesperson|merchandis|supermarket|sales)", "Retail"),
    (r"\b(security|guard|watchman)", "Retail"),  # security fits Retail/Services bucket in-app
    (r"\b(carpent|mason|weld|plumb|electrician|mechanic|technician|fitter|artisan)", "Skilled trade"),
    (r"\b(construction|site foreman|builder|civil)", "Construction"),
    (r"\b(nurse|medical|health|clinic|pharma|doctor)", "Medicine & Health"),
    (r"\b(teacher|tutor|educat|school|lecturer|trainer)", "Education"),
    (r"\b(account|finance|audit|bookkeep|bank|mpesa)", "Finance & Accounting"),
    (r"\b(marketing|social media|graphic|design|content|media|photograph|video|writer|journalis)", "Media & Creative"),
    (r"\b(hr|human resource|recruit|talent)", "Human Resources"),
    (r"\b(law|legal|advocate|paralegal|governance|admin officer)", "Law & Governance"),
    (r"\b(software|develop|program|it support|ict|data analyst|network|system admin|tech)", "IT & Software"),
    (r"\b(receptionist|office assistant|data entry|clerk|secretar|front desk|admin)", "Retail"),
]

APPLY_EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+\w")
APPLY_URL_RE = re.compile(r"https?://(?!www\.linkedin\.com)[^\s\)\]\"']{10,}", re.IGNORECASE)


def extract_apply_channel(title: str, description: str, url: str, site: str):
    """Find a usable apply channel. LinkedIn 'job_url's force users through
    the LinkedIn login wall, so they are a last resort only. Preference:
    1. apply email found in the description (mailto)
    2. external apply URL found in the description (company careers page,
       Indeed, Greenhouse, Lever, Workable...)
    3. Indeed posting URL (browsable without login)
    4. LinkedIn URL (login-walled — kept but flagged so the app can apply
       in-app instead)
    Returns (channel, needs_linkedin) where channel is '' when none found.
    """
    desc = description or ""
    m = APPLY_EMAIL_RE.search(desc)
    if m:
        email = m.group(0).rstrip('.').lower()
        if not any(x in email for x in ("example.com", "noemails", "no-reply", "noreply")):
            return email, False
    m = APPLY_URL_RE.search(desc)
    if m:
        return m.group(0).rstrip('.,;'), False
    if site == "indeed":
        return url, False
    # LinkedIn without any external channel: no good link — signal the app
    # to accept in-app applications instead.
    return "", True


# Posters that are clearly companies/organizations (used for isCompany).
COMPANY_HINT = re.compile(
    r"(ltd|limited|company|group|agency|hospital|school|hotel|safaricom|"
    r"university|college|foundation|ngo|enterprises|industries|services|"
    r"consultancy|solutions|bank|farm\s+\w|&\s*sons|international)",
    re.IGNORECASE,
)

# Salary text in descriptions like "KSh 25,000" (kept as the app shows it).
SALARY_RE = re.compile(r"(ksh|kes|kshs)\s*([\d,]{3,9})", re.IGNORECASE)


def classify(title: str, description: str) -> str:
    text = f"{title} {description[:600]}".lower()
    for pattern, category in CATEGORY_RULES:
        if re.search(pattern, text, re.IGNORECASE):
            return category
    return "Retail"


def detect_company(name: str) -> bool:
    return bool(COMPANY_HINT.search(name or ""))


def extract_salary(description: str) -> str:
    if not description:
        return "Negotiable"
    m = SALARY_RE.search(description)
    return f"KSh {m.group(2)}" if m else "Negotiable"


def _is_kenya(location: str) -> bool:
    """Kenya leak-guard: with the worldwide patch some rows come from other
    countries; only keep rows mentioning Kenya/Kenyan cities."""
    loc = (location or "").lower()
    kenya_markers = (
        "kenya", "nairobi", "mombasa", "kisumu", "nakuru", "eldoret",
        "thika", "machakos", "malindi", "kakamega", "naivasha", "kericho",
        "kitale", "nyeri", "ruiru", "kikuyu", "athiriver", "mavoko",
    )
    return any(m in loc for m in kenya_markers)


def job_type_to_type(jt: Optional[str]) -> str:
    jt = (jt or "").lower()
    if "part" in jt:
        return "Part-time"
    if "intern" in jt or "contract" in jt or "temporary" in jt:
        return "Contract"
    if "full" in jt or "permanent" in jt:
        return "Full-time"
    return "Full-time"


def dedupe_key(title: str, company: str) -> str:
    """Stable fingerprint: normalized title+company. Same job reposted on
    multiple boards collapses to one."""
    norm = re.sub(r"[^a-z0-9]+", " ", f"{title} {company}".lower()).strip()
    return hashlib.sha1(norm.encode()).hexdigest()[:20]


# ============================ scraping ============================

def scrape_all(hours_old: int, results_per_query: int, linkedin: bool) -> pd.DataFrame:
    from jobspy import scrape_jobs

    sites = list(SITES)
    if not linkedin:
        sites = [s for s in sites if s != "linkedin"]

    frames = []
    for location in LOCATIONS:
        try:
            df = scrape_jobs(
                site_name=sites,
                location=location,
                results_wanted=results_per_query,
                hours_old=hours_old,
                country_indeed="worldwide",   # patched; location does the filtering
                linkedin_fetch_description=True,
                verbose=0,
            )
            if df is not None and not df.empty:
                frames.append(df)
                print(f"  [{location}] {len(df)} jobs")
        except Exception as e:
            print(f"  [{location}] scrape failed (non-fatal): {e}", file=sys.stderr)

    # Targeted term passes per search term (Indeed + LinkedIn). This is
    # where the blue/grey-collar volume comes from.
    for term in SEARCH_TERMS:
        try:
            df = scrape_jobs(
                site_name=sites,
                search_term=term,
                location="Nairobi, Kenya",
                results_wanted=results_per_query // 2,
                hours_old=hours_old * 2,  # wider window for term passes
                country_indeed="worldwide",
                verbose=0,
            )
            if df is not None and not df.empty:
                frames.append(df)
        except Exception as e:
            print(f"  term '{term}' failed (non-fatal): {e}", file=sys.stderr)

    if not frames:
        return pd.DataFrame()
    return pd.concat(frames, ignore_index=True)


# ============================ firestore ============================

def upload_to_firestore(rows: list[dict], sa_path: str) -> int:
    import firebase_admin
    from firebase_admin import credentials, firestore

    cred = credentials.Certificate(sa_path)
    try:
        app = firebase_admin.initialize_app(cred)
    except ValueError:
        app = firebase_admin.get_app()
    db = firestore.client(app)

    jobs_col = db.collection("jobs")

    # Pull existing fingerprints for dedupe (provider + community).
    existing = set()
    for doc in jobs_col.stream():
        fp = doc.to_dict().get("fp")
        if fp:
            existing.add(fp)

    batch = db.batch()
    ops = 0
    uploaded = 0
    fixed = 0
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)

    # Map fingerprints to existing docs so we can repair old LinkedIn-walled
    # apply links in place (from before the channel-extraction fix).
    fp_to_doc = {}
    for doc in jobs_col.stream():
        doc_fp = doc.to_dict().get("fp")
        if doc_fp:
            fp_to_doc[doc_fp] = doc

    for row in rows:
        fp = row["fp"]
        doc_id = f"scrape_{fp}"
        if fp in existing:
            doc = fp_to_doc.get(fp)
            if doc is not None:
                d = doc.to_dict() or {}
                old_url = (d.get("applicationUrl") or "")
                is_walled = "linkedin.com" in old_url and not d.get("needsLinkedin", False)
                if is_walled:
                    # Re-derive the channel from the stored description.
                    site = d.get("site", "linkedin")
                    ch, needs_li = extract_apply_channel(
                        d.get("title", ""), d.get("description", ""), old_url, site
                    )
                    batch.update(doc.reference, {
                        "applicationUrl": ch,
                        "posterContact": ch,
                        "needsLinkedin": needs_li,
                    })
                    ops += 1
                    fixed += 1
                    if ops >= 450:
                        batch.commit()
                        batch = db.batch()
                        ops = 0
            continue
        existing.add(fp)
        doc_ref = jobs_col.document(doc_id)
        batch.set(doc_ref, row["doc"])
        ops += 1
        uploaded += 1
        if ops >= 450:  # Firestore batch limit is 500
            batch.commit()
            batch = db.batch()
            ops = 0

    if ops:
        batch.commit()
    if fixed:
        print(f"Repaired {fixed} existing jobs with LinkedIn-walled apply links.")
    return uploaded


# ============================ main ============================

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="scrape, don't upload")
    ap.add_argument("--out", help="save raw results to CSV path")
    ap.add_argument("--linkedin", action="store_true", default=True)
    ap.add_argument("--no-linkedin", dest="linkedin", action="store_false")
    args = ap.parse_args()

    hours_old = int(os.environ.get("SCRAPE_HOURS_OLD", "24"))
    per_query = int(os.environ.get("SCRAPE_RESULTS_PER_QUERY", "60"))

    print(f"== PRC scraper: sites={SITES} hours_old={hours_old} per_query={per_query} ==")
    df = scrape_all(hours_old, per_query, args.linkedin)
    if df.empty:
        print("No jobs scraped this run (possibly blocked — will retry next run).")
        return 0
    print(f"Scraped {len(df)} raw rows.")

    if args.out:
        df.to_csv(args.out, index=False)
        print(f"Raw saved to {args.out}")

    # ---- normalize + filter for Kenya ----
    rows: list[dict] = []
    seen_fp = set()
    for _, r in df.iterrows():
        title = str(r.get("title") or "").strip()
        company = str(r.get("company") or "").strip() or "Not stated"
        description = str(r.get("description") or "")
        location = str(r.get("location") or "Kenya")
        url = str(r.get("job_url") or "")

        if not title or not url:
            continue
        if not _is_kenya(location):
            continue   # worldwide patch may leak non-Kenyan rows — hard filter

        fp = dedupe_key(title, company)
        if fp in seen_fp:
            continue
        seen_fp.add(fp)

        salary = extract_salary(description)
        category = classify(title, description)
        is_company = detect_company(company) or company.lower() != "not stated"
        site = str(r.get("site") or "")
        apply_channel, needs_linkedin = extract_apply_channel(title, description, url, site)

        posted = r.get("date_posted")
        posted_ms = (
            int(pd.Timestamp(posted).timestamp() * 1000)
            if posted is not None and not pd.isna(posted)
            else now_ms_safe()
        )

        rows.append({
            "fp": fp,
            "doc": {
                "title": title[:120],
                "postedBy": company[:60],
                # Apply channel: email/careers-URL when one was found; a
                # LinkedIn login-walled URL only as a last resort. When no
                # channel exists at all, leave blank so the app takes
                # in-app applications instead.
                "posterContact": apply_channel,
                "applicationUrl": apply_channel,
                "needsLinkedin": needs_linkedin,
                "site": site,
                "location": location[:60],
                "pay": salary,
                "type": job_type_to_type(r.get("job_type") or ""),
                "category": category,
                "description": description[:2000] or title,
                "requirements": [],
                "skills": [],
                "source": "provider",
                "isCompany": bool(is_company),
                "postedAtMillis": posted_ms,
                "postedAgo": "Just now",
                "deadlineDays": 14,            # scraped postings expire in 2 weeks
                "openings": 1,
                "remoteOk": bool(r.get("is_remote")),
                "draft": False,
                "closed": False,
                "fp": fp,
            },
        }) if _is_kenya(location) else None
        if not _is_kenya(location):
            continue

    print(f"{len(rows)} unique jobs after dedupe (scraped-level).")

    if args.dry_run:
        by_cat: dict[str, int] = {}
        for row in rows:
            by_cat[row["doc"]["category"]] = by_cat.get(row["doc"]["category"], 0) + 1
        print("Category breakdown:", json.dumps(by_cat, indent=2))
        print("DRY RUN — nothing uploaded.")
        return 0

    sa_path = os.environ.get("FIREBASE_SERVICE_ACCOUNT", "service-account.json")
    if not os.path.exists(sa_path):
        print(f"ERROR: service account file not found at {sa_path}. "
              "Set FIREBASE_SERVICE_ACCOUNT or place service-account.json here.", file=sys.stderr)
        return 1

    uploaded = upload_to_firestore(rows, sa_path)
    print(f"Uploaded {uploaded} new jobs to Firestore.")
    return 0


def now_ms_safe() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


if __name__ == "__main__":
    sys.exit(main())
