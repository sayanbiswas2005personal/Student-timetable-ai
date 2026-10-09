# PDF timetable import

An administrator uploads a published timetable PDF. The system extracts what it can, shows every row
for review, and only builds a timetable from the rows a human accepted.

The whole point of the review step is that **automated extraction is a proposal, not data**.

## The pipeline

```
Upload
  │  extension, content type and magic bytes checked; internal filename generated
  ▼
Extract text with Apache PDFBox 3, page by page
  │
  │  a page with too little text is flagged as needing OCR, not treated as empty
  ▼
OCR fallback (optional, separate Python service)
  │
  │  failure or not configured stops here with a clear error
  ▼
Read the grid from geometry
  │  header row with time ranges → column boundaries; weekday column on the left
  ▼
Split each cell into subject, faculty and room
  │
  │  raw text and confidence kept for every row
  ▼
Review: edit, map to database records, accept or discard
  ▼
Create a DRAFT timetable   ← still not visible to staff
  ▼
Publish (separate, explicit step)
```

## Step 1: Upload

Three independent checks, because any one can be bypassed:

1. the file name ends in `.pdf`;
2. the declared content type is `application/pdf` or `application/octet-stream`;
3. the first four bytes are `%PDF`.

The third is the one that cannot be fooled by renaming a file, so a renamed file is refused here
rather than turning into a confusing failure later.

Limits, all configurable: 25 MB, 100 pages.

The uploaded name is **never** used to build a path. The server generates
`import-<timestamp>-<random hex>.pdf` and keeps the original only as a display label. That removes
path traversal, extension confusion and filename collisions in one step.

## Step 2: Text extraction

`PdfTextExtractor` walks the pages one at a time and records, for each:

- the plain text, kept for review;
- every word with its position;
- whether the page carries enough text to be worth parsing, and if not, why.

**A PDF is not required to contain text.** A scanned timetable is a perfectly valid PDF that yields
an empty string. Treating that as "an empty timetable" would silently destroy data, so it is
detected instead.

One detail that matters in practice: PDFBox reports one `TextPosition` per glyph, so the raw output
is `"M"`, `"i"`, `"n"`, `"o"`, `"r"`... Left that way a cell would read
`"M i n o r _ C S E 1 4 0 5 0"` and no subject pattern would ever match. `WordStripper` merges
adjacent glyphs on the same line back into words before anything else looks at them.

## Step 3: OCR fallback

When a page needs OCR and no service is configured, the import **stops** with
`422 OCR_NOT_CONFIGURED`, listing the pages that need attention. It does not create rows from an
empty page, and it does not mark the job successful.

With a service configured, `OcrClient` posts the document to it with the list of pages. If the call
fails, the job is left in `OCR_PENDING` with an error summary.

To run one, see `ocr-service/README.md`. OCR confidence is stored next to every row and shown in the
review screen; it is never rounded up.

## Step 4: Reading the grid

`TimetableGridParser` reads the table structurally rather than as text:

1. **Rows** are words whose vertical centres agree. PDFBox reports y measured downwards from the top
   of the page, so ascending y is top to bottom, which is the order a timetable is read in.
2. **Columns** come from a header row containing at least two time ranges. The midpoint between
   consecutive ranges is the column boundary, and the first column starts after the weekday column,
   because cell text is often printed further left than its header label.
3. **Day rows** are detected from weekday abbreviations in English (`Mo`, `Tu`, `Wed`, `Thu`...) and
   German (`Mo`, `Di`, `Mi`, `Do`, `Fr`), which both appear in exported academic timetables.
4. **Cells** accumulate across consecutive rows, because a timetable cell is usually several lines
   tall. Text from different lines in one cell is joined rather than emitted as separate rows.
5. **Weekday attribution** uses the weekday label nearest to each row vertically, not "the last
   label seen above". College timetables print the weekday label at different heights inside its
   own band, and a band often prints its heading *above* the label, so a simple top down rule
   shifts whole cells into the previous day.

The boundary detector is an interface (`ColumnBoundaryDetector`). A different college template needs
a new implementation, not a rewrite of the rest of the pipeline.

If no time-slot header is found, the page produces no cells and a warning naming the page, rather
than an empty result that looks like a legitimately empty timetable.

## Step 5: Splitting a cell

`CellContentParser` works left to right, in the order these things appear in real cells:

1. **Room codes** such as `AU6-4304`, printed on their own line.
2. **Subject code**, in the form `CSE11036`, `MGT11402`, or a prefixed variant like
   `MINOR_CSE14050`. Everything before it is the subject name.
3. **Faculty**, from the staff code in brackets after the subject: `(56312)` with the name in front.

Anything it cannot recognise is reported in `unresolved` and the confidence is kept low. It never
fills a field with a plausible guess.

## Verification against a real timetable

The parser was run against a genuine 52 page college timetable: eight time slots a day, five
weekday bands per page, one page per section. Three defects were found and fixed that no synthetic
fixture had exposed:

- **Page furniture leaked into cells.** The first word of a row never initialised that row's
  vertical position, so the row's y stayed `NaN`. A `NaN` fails every bounds comparison silently, so
  the letterhead was read as timetable content. Non finite glyph positions are now dropped at
  extraction, and rows without a usable position are skipped.
- **The upper half of a weekday belonged to the previous day**, because the label is not always at
  the top of its band.
- **Multi line cells were split into several periods**, so one class appeared as three rows with
  no subject code between them.

After those fixes the same page reads as five weekday bands with correct times, subject codes,
faculty names and rooms. The parser still flags days whose period count differs noticeably from the
page median, which is how a boundary that is off by a row gets noticed rather than silently
imported.

What remains imperfect, and why the review screen is not optional:

- A subject name split across two visual lines reads as one fragment plus the code, for example
  `and Algorithms` with `(CSE11104)`. The code is right; the label needs one edit.
- A boundary that falls a row early makes one weekday look short. The parser says so explicitly.
- Column boundaries come from the header row, not from the table's drawn rules. Drawn rules live in
  a different coordinate space from PDFBox's reported text positions, and mapping between them
  reliably is a larger piece of work than it appears. Until that is done, the header heuristic is
  the honest choice: it is in the same space as the text it is compared against.

## Step 6: Review

The review screen shows every extracted row with:

- the day and time it was assigned to;
- what the parser read for each field;
- the **raw source text**, expandable, so the machine can be compared against the page;
- the parser's confidence;
- anything it could not resolve, in plain words.

For each row the administrator can map it to a real subject, room or faculty record, add a note,
accept it as extracted, or discard it. A row is only importable once it has been decided.

Mapping to a **database record**, not to free text, is what makes a row publishable. A label the
parser guessed is never enough.

## Step 7: Approve and publish

Approval builds a **draft** timetable from the accepted rows, in one transaction. It is not
published: the timetable screen shows what was created and what is still wrong.

Publishing is a separate call. It re-validates, closes the previous published version the day before
the new one starts, and marks the new one `PUBLISHED`. Only then does the lookup engine start using
it.

OCR output is never published automatically. There is no path from "uploaded" to "visible to staff"
that does not go through a human.

## Worked example

```bash
# Sign in.
CSRF=$(curl -s -c /tmp/c http://localhost:8080/api/auth/csrf | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -s -b /tmp/c -c /tmp/c -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"username":"admin","password":"..."}'

# Upload.
CSRF=$(grep XSRF-TOKEN /tmp/c | awk '{print $7}')
curl -s -b /tmp/c -X POST http://localhost:8080/api/imports \
  -H "X-XSRF-TOKEN: $CSRF" -F 'file=@semester-5.pdf'

# Review the rows.
curl -s -b /tmp/c http://localhost:8080/api/imports/1/candidates

# Accept one.
curl -s -b /tmp/c -X PUT http://localhost:8080/api/imports/1/candidates/1 \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"subjectId":12,"reviewStatus":"APPROVED","reviewerNotes":"checked page 1"}'

# Create the draft, then publish it.
curl -s -b /tmp/c -X POST http://localhost:8080/api/imports/1/approve \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"sectionId":4,"effectiveFrom":"2026-08-01"}'

curl -s -b /tmp/c -X POST 'http://localhost:8080/api/imports/1/publish?timetableId=7' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $CSRF" -d '{}'
```

## When parsing goes wrong

| Symptom | Cause and fix |
| --- | --- |
| No rows at all, warning mentions "no recognisable time-slot header" | The layout does not look like a header of time ranges. Add a `ColumnBoundaryDetector` for that template. |
| Rows found but times wrong | The header row also contained another time range. Check the raw page text in the review screen. |
| Cells merged or split oddly | Column boundaries fell in the wrong place. Raise or lower `COLUMN_TOLERANCE`. |
| `pagesNeedingOcr` is non-zero | The pages are scans. Run OCR, see `ocr-service/README.md`. |
| Subject codes unresolved | The code format differs. Extend `SUBJECT_CODE` in `CellContentParser`, or map them by hand in the review screen. |
| Publish rejected | `GET /api/timetables/{id}/validate` lists every blocking problem. |
| Upload rejected as `NOT_A_PDF` | The file is not really a PDF, or it is an image renamed with a `.pdf` extension. |

## Testing the pipeline

`PdfTextExtractorTest` and `TimetableGridParserTest` build real PDFs with PDFBox, extract them and
assert on the result, so the round trip through positions is genuinely covered. `CellContentParserTest`
covers the cell grammar directly. `PdfImportIntegrationTest` runs the whole workflow: upload,
review, correct one row, approve, publish, and confirm the lookup engine then serves the imported
data.
## Bulk import of a published "section wise timetable"

Reviewing 52 pages one at a time is the right workflow for an unfamiliar document. A college that
publishes a single section-per-page timetable does not need it, so `TimetableBootstrapRunner` reads
the whole document at startup and creates the catalogue and timetable for every section at once.

It is **off by default**, because it infers the catalogue instead of asking for it. Turn it on
deliberately, once, on an empty database:

```bash
IMPORT_BOOTSTRAP_ENABLED=true \
IMPORT_BOOTSTRAP_SOURCE="./data/imports/Section wise Timetable V15.pdf" \
IMPORT_BOOTSTRAP_YEAR=2026-27 \
IMPORT_BOOTSTRAP_FROM=2026-07-20 \
IMPORT_BOOTSTRAP_TO=2027-06-30 \
IMPORT_BOOTSTRAP_PUBLISH=true \
./mvnw spring-boot:run
```

| Setting | Meaning |
| --- | --- |
| `IMPORT_BOOTSTRAP_ENABLED` | Master switch. Off by default. |
| `IMPORT_BOOTSTRAP_SOURCE` | The document, relative to the working directory or absolute. |
| `IMPORT_BOOTSTRAP_YEAR` | Academic year. Not printed in the document; confirm it. |
| `IMPORT_BOOTSTRAP_FROM` / `_TO` | Term dates. Derived from the printed "w.e.f." line and **must be confirmed**. |
| `IMPORT_BOOTSTRAP_PUBLISH` | Publish each section immediately instead of leaving drafts. |
| `IMPORT_BOOTSTRAP_REPUBLISH` | Re-import a section that already has a published timetable. Off by default so a second run cannot silently overwrite live data. |

The runner prints a report to the log listing every section, its period count and anything it could
not read. It:

- creates departments, programmes, terms and sections derived from the headings, and reuses existing
  rows rather than duplicating them;
- imports a period as `CLASS` when the cell carries a subject code, otherwise as `OTHER`;
- marks rows it could not fully read `UNVERIFIED`, so they can be filtered in the review screen;
- strips repeated page footers such as `generated: 1/9/2026` out of the cell they merged into,
  instead of discarding the whole cell;
- refuses to publish a section whose periods overlap, and leaves it as a draft;
- writes one `TIMETABLE_BOOTSTRAP_PUBLISH` audit row per published timetable.

It does not create students. The document has no student directory, and inventing one would be
worse than having none.
