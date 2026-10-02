# Dispatch policy benchmark

Generated 2026-10-02 by `DispatchBenchmark` from `scenarios/Final_event_file_w26.csv`. Each row averages up to 3 full runs of the real scheduler, drone threads, and UDP messaging in one headless JVM per run. Times are wall-clock seconds from the scheduler receiving a fire report. Averages include only completed runs, where every reported fire was put out.

| Drones | Policy | Completed | Avg response | P95 response | Avg extinguish | P95 extinguish | Max extinguish | Scenario time | Drones used | Fleet distance |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | SINGLE_DRONE | 3/3 | 3.3 s | 4.9 s | 12.2 s | 17.2 s | 20.0 s | 582 s | 9.0 | 294236 m |
| 10 | WATER_MATCHED | 3/3 | 3.2 s | 4.8 s | 7.3 s | 8.9 s | 9.6 s | 574 s | 10.0 | 291730 m |
| 20 | SINGLE_DRONE | 3/3 | 3.3 s | 4.9 s | 12.2 s | 17.2 s | 20.0 s | 582 s | 19.0 | 294236 m |
| 20 | WATER_MATCHED | 3/3 | 3.2 s | 4.8 s | 7.3 s | 8.8 s | 9.0 s | 574 s | 19.0 | 291730 m |

### Individual runs

```text
SINGLE_DRONE,10,true,582368,50,50,3325.2,4948,5069,12220.2,17206,20008,86,9,5,294236.4
SINGLE_DRONE,10,true,582356,50,50,3325.0,4947,5068,12219.4,17208,20006,86,9,5,294236.4
SINGLE_DRONE,10,true,582360,50,50,3325.4,4952,5068,12220.2,17205,20008,86,9,5,294236.4
WATER_MATCHED,10,true,573918,50,50,3235.9,4765,4980,7316.8,8947,9597,85,10,5,291729.5
WATER_MATCHED,10,true,573916,50,50,3235.5,4764,4965,7316.5,8947,9598,85,10,5,291729.5
WATER_MATCHED,10,true,573921,50,50,3234.9,4765,4972,7316.5,8952,9597,85,10,5,291729.5
SINGLE_DRONE,20,true,582366,50,50,3326.6,4949,5074,12221.7,17208,20014,86,19,5,294236.4
SINGLE_DRONE,20,true,582362,50,50,3325.4,4946,5066,12220.5,17206,20007,86,19,5,294236.4
SINGLE_DRONE,20,true,582356,50,50,3325.4,4950,5068,12220.2,17207,20010,86,19,5,294236.4
WATER_MATCHED,20,true,573917,50,50,3236.0,4765,4996,7297.4,8765,8996,85,19,5,291729.5
WATER_MATCHED,20,true,573916,50,50,3235.0,4762,4963,7296.5,8763,8963,85,19,5,291729.5
WATER_MATCHED,20,true,573919,50,50,3235.7,4763,4974,7297.0,8763,8975,85,19,5,291729.5
```

Columns: policy, drones, completed, duration ms, fires reported, fires extinguished, avg/p95/max response ms, avg/p95/max extinguish ms, trips, drones used, faults, fleet distance m.
