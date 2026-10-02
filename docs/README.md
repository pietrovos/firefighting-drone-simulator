# Project supporting material

These files were collected from the original SYSC 3303 group repository and
final submission. The application is based on that submission with a redesigned
runtime dashboard; the CSV inputs, build file, and launcher match the final
submission. The submitted tests include
corrections to the metrics API calls and queued-task test timing. Earlier iteration
documents describe previous designs, and recorded measurements refer to their
original sessions.

## Demo and reports

- [Final demonstration video](demo/final_demo_video.mp4): the full submitted
  demonstration, re-encoded to H.264 at 1920 × 1080 with its audio retained so
  the file fits GitHub's regular-file size limit. The original recording is
  2560 × 1440.
- [Final project report](reports/final_Report.pdf).
- [Final verification and validation report](reports/final_vv_report.pdf).

## Design and development history

- [Original group README](historical/group-README.md): architecture and Mermaid
  diagrams, system behavior, team contributions, measured results, and design
  reflections.
- [Iteration archive](historical/archive/): the original five iterations of
  design notes, diagram images, verification plans, and refactoring notes.
- [Final iteration V&V report](historical/archive/iteration05/iteration05_vvReport.md).
- [Original CSV inputs](historical/inputs/): the submitted event and zone files,
  including sample and test scenarios. Kept with the historical results for
  reference.

The original group repository is
[Averrryyy/Winter2026Sysc3303FinalProjectGroup6](https://github.com/Averrryyy/Winter2026Sysc3303FinalProjectGroup6),
at commit `d6bfdb6` when these materials were collected. Its Git history remains
available there for contribution and development-history review.

The original README and archived documents are preserved verbatim. Their
relative links reflect the original layout and may need to be followed in that
repository; use the links on this page to navigate the collected files.

## Recorded simulation runs

[Submission logs](evidence/submission-run/) contains `assignments.log`,
`events.log`, `metrics.log`, `queue.log`, and `telemetry.log`. These are preserved
records, separate from the generated `logs/` directory used by current runs.
The files contain multiple sessions, so select a session's summary when quoting
results rather than treating the whole file as one run.

The historical group README also records a 20-drone final-scenario run with
22 of 22 fires extinguished, 82 trips, and a 6.74-second average response time.
Those figures describe that recorded localhost simulation, not a benchmark of
the current portfolio revision. The submission logs contain their own session
results and should not be assumed to be the source of that README's figures.

## Sources

- `SYSC3303_A3_Final_Submission/`: video, both PDFs, CSV inputs, and recorded logs.
- `Winter2026Sysc3303FinalProjectGroup6/`: original README and iteration archive.

The PDFs and source trees in the two folders were identical when compared;
only one copy of each report is included here. Team authorship and the reuse
terms in the repository's main README apply to these materials as well.
